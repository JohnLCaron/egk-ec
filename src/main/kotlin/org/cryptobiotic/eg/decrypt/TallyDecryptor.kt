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
    val decryptor = CipherDecryptor(group, extendedBaseHash, publicKey, guardians, decryptingTrustees)

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
            val selections = econtest.selections.map { eselection ->
                val (decryption, proof) = decryptions[count++]
                val (T, tally) = decryption.decryptCiphertext(publicKey)
                if (tally == null) {
                    errs.add("Cant decrypt tally for ${econtest.contestId}.${eselection.selectionId}")
                }

                DecryptedTallyOrBallot.Selection(
                    eselection.selectionId,
                    tally!!,
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