package org.cryptobiotic.eg.decrypt

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.eg.core.*
import org.cryptobiotic.eg.election.*
import org.cryptobiotic.util.ErrorMessages

/**
 * Orchestrates the decryption of encrypted Tallies with DecryptingTrustees.
 * Communication with the trustees is with a list of all the ciphertexts from a single ballot / tally at one.
 */
class TallyDecryptor(
    val group: GroupContext,
    val extendedBaseHash: UInt256,
    val publicKey: ElGamalPublicKey,
    val guardians: Guardians, // all guardians
    decryptingTrustees: List<DecryptingTrusteeIF>, // the trustees available to decrypt
) {
    val lagrangeCoordinates: Map<String, LagrangeCoordinate>
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

    fun decrypt(etally: EncryptedTally, errs : ErrorMessages): DecryptedTallyOrBallot? {
        if (etally.electionId != extendedBaseHash) {
            errs.add("Encrypted Tally/Ballot has wrong electionId = ${etally.electionId}")
        }

        val texts: MutableList<Ciphertext> = mutableListOf()
        for (contest in etally.contests) {
            for (selection in contest.selections) {
                texts.add(Ciphertext(selection.encryptedVote))
            }
        }
        val decryptionAndProofs = decryptor.decrypt(texts, errs)
        if (errs.hasErrors()) {
            return null
        }
        requireNotNull(decryptionAndProofs)

        val result = makeTally(etally, decryptionAndProofs, errs.nested("TallyDecryptor.decrypt"))
        return if (errs.hasErrors()) null else result!!
    }

    fun makeTally(
        etally: EncryptedTally,
        decryptions: List<CipherDecryptionAndProof>,
        errs : ErrorMessages,
    ): DecryptedTallyOrBallot? {
        var count = 0
        val contests = etally.contests.map { econtest ->
            val cerrs = errs.nested("Contest ${econtest.contestId}")
            val selections = econtest.selections.map { eselection ->
                val serrs = cerrs.nested("Selection ${eselection.selectionId}")
                val (decryption, proof) = decryptions[count++]
                val (T, tally) = decryption.decryptCiphertext(publicKey)

                DecryptedTallyOrBallot.Selection(
                    eselection.selectionId,
                    tally!!, // TODO errors
                    T,
                    (decryption.cipher as Ciphertext).delegate,
                    proof
                )
            }
            DecryptedTallyOrBallot.Contest(econtest.contestId, selections, econtest.ballot_count, null)
        }
        return if (errs.hasErrors()) null else DecryptedTallyOrBallot(etally.tallyId, contests, etally.electionId)
    }

    companion object {
        private val logger = KotlinLogging.logger("TallyDecryptor")
    }
}