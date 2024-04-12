package org.cryptobiotic.eg.decrypt

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.eg.core.*
import org.cryptobiotic.eg.election.*
import org.cryptobiotic.util.ErrorMessages
import org.cryptobiotic.util.Stats
import org.cryptobiotic.util.Stopwatch

/**
 * Orchestrates the decryption of encrypted Ballots with DecryptingTrustees.
 * An EncryptedBallot can also be decrypted if you know the master nonce.
 * Communication with the trustees is with a list of all the ciphertexts from a single ballot / tally at one.
 */
class BallotDecryptor(
    val group: GroupContext,
    val extendedBaseHash: UInt256,
    val publicKey: ElGamalPublicKey,
    val guardians: Guardians, // all guardians
    decryptingTrustees: List<DecryptingTrusteeIF>, // the trustees available to decrypt
) {
    val lagrangeCoordinates: Map<String, LagrangeCoordinate>
    val stats = Stats()
    val nguardians = guardians.guardians.size // number of guardinas
    val quorum = guardians.guardians[0].coefficientCommitments().size
    val decryptor = CipherDecryptor(group, extendedBaseHash, publicKey, guardians, decryptingTrustees)

    init {
        // check that the DecryptingTrustee's match their public key
        val badTrustees = mutableListOf<String>()
        for (trustee in decryptingTrustees) {
            val guardian = guardians.guardianMap[trustee.id()]
            if (guardian == null) {
                badTrustees.add(trustee.id())
            } else {
                if (trustee.guardianPublicKey().key != guardian.publicKey()) {
                    badTrustees.add(trustee.id())
                    logger.error { "trustee public key = ${trustee.guardianPublicKey()} not equal guardian = ${guardian.publicKey()}" }
                }
            }
        }
        if (badTrustees.isNotEmpty()) {
            throw RuntimeException("DecryptingTrustee(s) ${badTrustees.joinToString(",")} do not match the public record")
        }

        // build the lagrangeCoordinates once and for all
        val dguardians = mutableListOf<LagrangeCoordinate>()
        for (trustee in decryptingTrustees) {
            val present: List<Int> = // available trustees minus me
                decryptingTrustees.filter { it.id() != trustee.id() }.map { it.xCoordinate() }
            val coeff: ElementModQ = group.computeLagrangeCoefficient(trustee.xCoordinate(), present)
            dguardians.add(LagrangeCoordinate(trustee.id(), trustee.xCoordinate(), coeff))
        }
        this.lagrangeCoordinates = dguardians.associateBy { it.guardianId }
    }

    fun decrypt(eballot: EncryptedBallotIF, errs : ErrorMessages): DecryptedTallyOrBallot? {
        if (eballot.electionId != extendedBaseHash) {
            errs.add("Encrypted Tally/Ballot has wrong electionId = ${eballot.electionId}")
        }
        val stopwatch = Stopwatch()

        val texts: MutableList<Ciphertext> = mutableListOf()
        for (contest in eballot.contests) {
            for (selection in contest.selections) {
                texts.add( Ciphertext(selection.encryptedVote))
            }
        }
        val decryptionAndProofs = decryptor.decrypt(texts, errs)
        if (errs.hasErrors()) {
            return null
        }
        requireNotNull(decryptionAndProofs)

        val contestData: MutableList<HashedCiphertext> = mutableListOf()
        for (contest in eballot.contests) {
            if (contest.contestData != null) {
                contestData.add(HashedCiphertext(contest.contestData!!))
            }
        }
        val contestDecryptionAndProofs = decryptor.decrypt(contestData, errs)
        if (errs.hasErrors()) {
            return null
        }
        requireNotNull(contestDecryptionAndProofs)

        val result = makeBallot(eballot, decryptionAndProofs, contestDecryptionAndProofs, errs.nested("BallotDecryptor.decrypt"))
        if (!errs.hasErrors()) {
            val ndecrypt = decryptionAndProofs.size + contestDecryptionAndProofs.size
            stats.of("decryptTally").accum(stopwatch.stop(), ndecrypt)
        }
        return if (errs.hasErrors()) null else result!!
    }

    fun makeBallot(
        eballot: EncryptedBallotIF,
        decryptions: List<CipherDecryptionAndProof>,
        contestDecryptions: List<CipherDecryptionAndProof>,
        errs : ErrorMessages,
    ): DecryptedTallyOrBallot? {
        var selectionCount = 0
        val contests = eballot.contests.mapIndexed { contestIdx, econtest ->
            val selections = econtest.selections.map { eselection ->
                val (decryption, proof) = decryptions[selectionCount++]
                val (T, tally) = decryption.decryptCiphertext(publicKey)

                DecryptedTallyOrBallot.Selection(
                    eselection.selectionId,
                    tally?: 0, // TODO error handling
                    T,
                    (decryption.cipher as Ciphertext).delegate,
                    proof
                )
            }

            // rehydrated ballots dont have contestData
            val contestData = if (econtest.contestData != null) {
                val (decryption, proof) = contestDecryptions[contestIdx]
                decryption.decryptHashedCiphertext(publicKey, extendedBaseHash, econtest.contestId, proof)
            } else {
                null
            }
            DecryptedTallyOrBallot.Contest(econtest.contestId, selections, 1, contestData)
        }
        return if (errs.hasErrors()) null else DecryptedTallyOrBallot(eballot.ballotId, contests, eballot.electionId)
    }

    companion object {
        private val logger = KotlinLogging.logger("BallotDecryptor")
    }
}
