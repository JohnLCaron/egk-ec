package org.cryptobiotic.eg.decrypt

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.eg.core.*
import org.cryptobiotic.eg.core.Base16.toHex
import org.cryptobiotic.eg.election.*
import org.cryptobiotic.util.ErrorMessages
import org.cryptobiotic.util.Stats
import org.cryptobiotic.util.Stopwatch

/**
 * Orchestrates the decryption of encrypted Tallies and Ballots with DecryptingTrustees.
 * This is the only way that an EncryptedTally can be decrypted.
 * An EncryptedBallot can also be decrypted if you know the master nonce.
 * Communication with the trustees is with a list of all the ciphertexts from a single ballot / tally at one.
 */
class BallotDecryptor2(
    val group: GroupContext,
    val extendedBaseHash: UInt256,
    val publicKey: ElGamalPublicKey,
    val guardians: Guardians, // all guardians
    private val decryptingTrustees: List<DecryptingTrusteeIF>, // the trustees available to decrypt
) {
    val lagrangeCoordinates: Map<String, LagrangeCoordinate>
    val stats = Stats()
    val nguardians = guardians.guardians.size // number of guardinas
    val quorum = guardians.guardians[0].coefficientCommitments().size
    val decryptor2 = Decryptor2(group, extendedBaseHash, publicKey, guardians, decryptingTrustees)

    init {
        // check that the DecryptingTrustee's match their public key
        val badTrustees = mutableListOf<String>()
        for (trustee in decryptingTrustees) {
            val guardian = guardians.guardianMap[trustee.id()]
            if (guardian == null) {
                badTrustees.add(trustee.id())
            } else {
                if (trustee.guardianPublicKey() != guardian.publicKey()) {
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

    fun decrypt(eballot: EncryptedBallot, errs : ErrorMessages): DecryptedTallyOrBallot? {
        if (eballot.electionId != extendedBaseHash) {
            errs.add("Encrypted Tally/Ballot has wrong electionId = ${eballot.electionId}")
        }

        // TODO ContestData
        val texts: MutableList<ElGamalCiphertext> = mutableListOf()
        for (contest in eballot.contests) {
            for (selection in contest.selections) {
                texts.add(selection.encryptedVote)
            }
        }
        val decryptionAndProofs = decryptor2.decrypt(texts, errs, false)
        if (errs.hasErrors()) {
            return null
        }
        requireNotNull(decryptionAndProofs)

        val result = makeBallot(eballot, decryptionAndProofs, errs.nested("TallyDecryptor.decrypt"))
        return if (errs.hasErrors()) null else result!!
    }

    fun makeBallot(
        eballot: EncryptedBallot,
        decryptions: List<DecryptionAndProof>,
        errs : ErrorMessages,
    ): DecryptedTallyOrBallot? {
        var count = 0
        val contests = eballot.contests.map { econtest ->
            val cerrs = errs.nested("Contest ${econtest.contestId}")
            val selections = econtest.selections.map { eselection ->
                val serrs = cerrs.nested("Selection ${eselection.selectionId}")
                val (decryption, proof) = decryptions[count++]

                DecryptedTallyOrBallot.Selection(
                    eselection.selectionId,
                    decryption.tally!!,
                    decryption.T,
                    decryption.ciphertext,
                    proof
                )
            }
            DecryptedTallyOrBallot.Contest(econtest.contestId, selections, 1, null)
        }
        return if (errs.hasErrors()) null else DecryptedTallyOrBallot(eballot.ballotId, contests, eballot.electionId)
    }

    companion object {
        private val logger = KotlinLogging.logger("BallotDecryptor")
    }
}
