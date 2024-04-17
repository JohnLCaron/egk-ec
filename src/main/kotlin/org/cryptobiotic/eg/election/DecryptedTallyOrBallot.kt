package org.cryptobiotic.eg.election

import org.cryptobiotic.eg.core.*
import org.cryptobiotic.util.ErrorMessages

/**
 * The decryption of one encrypted ballot or encrypted tally.
 * The only difference between a decrypted tally and a decrypted ballot is that only a ballot
 * has DecryptedContestData.
 *
 * @param id matches the tallyId, or the ballotId if its a ballot decryption.
 * @param contests The contests
 */
data class DecryptedTallyOrBallot(
    val id: String,
    val contests: List<Contest>,
    val electionId : UInt256, // prevent accidental mixups
) {

    data class Contest(
        val contestId: String, // matches ContestDescription.contestId
        val selections: List<Selection>,
        val ballot_count: Int = 0,                 // number of ballots voting on this contest, 1 for ballots
        val decryptedContestData: DecryptedContestData? = null, // only for ballots, but still optional
    ) {
        init {
            require(contestId.isNotEmpty())
            require(selections.isNotEmpty())
        }
    }

    data class DecryptedContestData(
        val contestData: ContestData,
        val encryptedContestData: HashedElGamalCiphertext, // same as EncryptedTally.Contest.contestData
        val proof: ChaumPedersenProof,
        var beta: ElementModP, // needed to verify 10.2
    )

    /**
     * The decrypted count of one selection of one contest in the election.
     *
     * @param selectionId equals the Manifest.SelectionDescription.selectionId.
     * @param tally     the decrypted vote count.
     * @param bOverM    T = (B / M) mod p. (spec 2.0, eq 64), needed for verification 9.A.
     * @param encryptedVote The encrypted vote count
     * @param proof     Proof of correctness that ciphertext encrypts tally
     */
    data class Selection(
        val selectionId: String, // matches SelectionDescription.selectionId
        val tally: Int,         // logK(T), ie the decrypted vote
        val bOverM: ElementModP, // T = (B / M) mod p. (spec 2.0, eq 64)
        val encryptedVote: ElGamalCiphertext, // same as EncryptedTally.Selection.encryptedVote
        val proof: ChaumPedersenProof, // proof that M = A^s mod p
    ) {
        init {
            require(selectionId.isNotEmpty())
            require(tally >= 0)
        }
    }

    fun show(details: Boolean, manifest: Manifest): String = buildString {
        appendLine(" DecryptedTallyOrBallot $id")
        contests.sortedBy { it.contestId }.forEach { contest ->
            if (details) {
                appendLine("  Contest ${contest.contestId}")
                contest.selections.sortedBy { -it.tally }.forEach {
                    val candidate =
                        manifest.selectionCandidate["${contest.contestId}/${it.selectionId}"] ?: "unknown"
                    appendLine("   $candidate (${it.selectionId}) = ${it.tally}")
                }
            } else {
                append("  ${contest.contestId}")
                contest.selections.sortedBy { -it.tally }.forEach {
                    val candidate =
                        manifest.selectionCandidate["${contest.contestId}/${it.selectionId}"] ?: "unknown"
                    append("   ${candidate} (${it.tally})")
                }
            }
            appendLine()
        }
    }

    fun compare(pballot: PlaintextBallot, errs: ErrorMessages): Boolean {
        val pcontests = pballot.contests.associateBy { it.contestId }
        contests.forEach { dcontest ->
            val pcontest = pcontests[dcontest.contestId]
            if (pcontest == null) {
                checkAllZeroes(dcontest, errs.nested("PBallot missing contest=${dcontest.contestId}"))
            } else {
                val pselections = pcontest.selections.associateBy { it.selectionId }
                dcontest.selections.forEach { dselection ->
                    val pselection = pselections[dselection.selectionId]
                    if (pselection == null) {
                        if (dselection.tally != 0) {
                            errs.add("${dcontest.contestId}.${dselection.selectionId} is not zero")
                        }
                    } else {
                        if (pselection.vote != dselection.tally) {
                            errs.add("${dcontest.contestId}/${dselection.selectionId} ${pselection.vote} != ${dselection.tally}")
                        }
                    }
                }
            }
        }
        return !errs.hasErrors()
    }

    private fun checkAllZeroes(dcontest: DecryptedTallyOrBallot.Contest, errs: ErrorMessages) {
        dcontest.selections.forEach { dselection ->
            if (dselection.tally != 0) errs.add("${dcontest.contestId}.${dselection.selectionId} is not zero")
        }
    }

}