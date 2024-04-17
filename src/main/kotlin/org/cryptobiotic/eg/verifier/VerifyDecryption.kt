package org.cryptobiotic.eg.verifier

import org.cryptobiotic.eg.core.*
import org.cryptobiotic.eg.election.*
import org.cryptobiotic.util.ErrorMessages
import org.cryptobiotic.util.Stats

import kotlin.collections.mutableSetOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.cryptobiotic.util.Stopwatch

/** Box [9, 10, 11] (tally), and [12, 13, 14] (ballot). can be multithreaded. */
// Note that 12,13,14 (ballot) are almost the same as 9,10,11 (tally). Only diff is 13.B,C
@OptIn(ExperimentalCoroutinesApi::class)
class VerifyDecryption(
    val group: GroupContext,
    val manifest: ManifestIF,
    val publicKey: ElGamalPublicKey,
    val extendedBaseHash: UInt256,
) {

    /** All manifest "contestId/selectionId" strings. */
    val contestAndSelectionSet : Set<String> = manifest.contests.map { contest -> contest.selections.map {
        "${contest.contestId}/${it.selectionId}" } }.flatten().toSet()

    fun verify(decrypted: DecryptedTallyOrBallot, isBallot: Boolean, errs: ErrorMessages, stats: Stats): Boolean {
        val stopwatch = Stopwatch()

        if (decrypted.electionId != extendedBaseHash) {
            errs.add("DecryptedTallyOrBallot has wrong electionId = ${decrypted.electionId}")
            return false
        }

        var nselections = 0

        for (contest in decrypted.contests) {
            val cerrs = errs.nested("Contest ${contest.contestId}")

            // (10.B, 13.D) The contest text label occurs as a contest label in the contests in the election manifest.
            if (manifest.findContest(contest.contestId) == null) {
                val what = if (isBallot) "13.D" else "10.B"
                cerrs.add("    $what contest '${contest.contestId}' not in manifest")
                continue
            }

            val optionLimit = manifest.optionLimit(contest.contestId)
            val contestLimit = manifest.contestLimit(contest.contestId)

            if (contest.decryptedContestData != null) {
                verifyContestData(contest.decryptedContestData, errs)
            }

            val contestSelectionSet = mutableSetOf<String>()
            var contestVotes = 0
            for (selection in contest.selections) {
                val serrs = cerrs.nested("Selection ${selection.selectionId}")

                nselections++
                val here = "${contest.contestId}/${selection.selectionId}"
                contestSelectionSet.add(selection.selectionId)

                // (10.C, 13.E) the option text label occurs as an option label for the contest in the election manifest.
                if (!contestAndSelectionSet.contains(here)) {
                    val what = if (isBallot) "13.E" else "10.C"
                    serrs.add("    $what contest/selection '$here' not in manifest")
                    continue
                }

                if (!selection.proof.r.inBounds()) {
                    val what = if (isBallot) "12.A" else "9.A"
                    serrs.add("    $what response out of bounds")
                }

                // 9.B, 11.B
                if (!selection.verifySelection()) {
                    val what = if (isBallot) "12.B" else "9.B"
                    serrs.add("    $what challenge does not match")
                }

                // T = K^t mod p.
                val tallyQ = selection.tally.toElementModQ(group)
                if (selection.bOverM != publicKey powP tallyQ) {
                    if (isBallot) serrs.add("    10.A incorrect Decryption T = K^t mod p")
                    else serrs.add("    13.A incorrect Decryption S = K^sigma mod p")
                }

                if (isBallot && (selection.tally !in (0..optionLimit))) {
                    serrs.add("     13.B ballot vote ${selection.tally} must be between 0..$optionLimit")
                }
                contestVotes += selection.tally
            }

            // (10.D) For each option in the election manifest, the option occurs in the decrypted tally contest.
            // (13.F) For each option in the election manifest, the option occurs in the decrypted ballot contest.
            val mcontest = manifest.findContest(contest.contestId)!! // already checked existence above
            mcontest.selections.forEach {
                if (!contestSelectionSet.contains(it.selectionId)) {
                    if (isBallot) errs.add("    13.F Manifest contains selection '$it' not on decrypted ballot")
                    else errs.add("    10.D Manifest contains selection '$it' not on decrypted tally ")
                }
            }

            if (isBallot) {
                if (contestVotes !in (0..contestLimit)) {
                    cerrs.add("     13.C sum of votes ${contestVotes} in contest must be between 0..$contestLimit")
                }
            }
        }

        stats.of("verifyDecryption", "selections").accum(stopwatch.stop(), nselections)
        return !errs.hasErrors()
    }

    // Verification 9 (Correctness of tally decryptions)
    private fun DecryptedTallyOrBallot.Selection.verifySelection(): Boolean {
        return this.proof.verifyDecryption(extendedBaseHash, publicKey, this.encryptedVote, this.bOverM)

    }

    private fun verifyContestData(decryptedContestData: DecryptedTallyOrBallot.DecryptedContestData, errs: ErrorMessages){
        // (11.A,14.A) The given value v is in the set Zq.
        if (!decryptedContestData.proof.r.inBounds()) {
            errs.add("     (11.A,14.A) The value v is not in the set Zq.")
        }
        if (!decryptedContestData.proof.verifyContestDataDecryption(publicKey, extendedBaseHash, decryptedContestData.beta, decryptedContestData.encryptedContestData)) {
            errs.add("     (11.B,14.B) The challenge value is wrong")
        }
    }

    ////////////////////////////////////////////////////////////////////////////////
    // coroutines

    fun verifyChallengedBallots(
        ballots: Iterable<DecryptedTallyOrBallot>,
        nthreads: Int,
        errs: ErrorMessages,
        stats: Stats,
        showTime: Boolean,
    ): Pair<Boolean, Int> {
        val stopwatch = Stopwatch()

        runBlocking {
            val verifierJobs = mutableListOf<Job>()
            val ballotProducer = produceTallies(ballots)
            repeat(nthreads) {
                // decrypted: DecryptedTallyOrBallot, isBallot: Boolean, errs: ErrorMessages, stats: Stats)
                verifierJobs.add(launchVerifier(ballotProducer) { dballot ->
                    verify(dballot, isBallot = true, errs.nested("Ballot ${dballot.id}"), stats)
                })
            }
            // wait for all encryptions to be done, then close everything
            joinAll(*verifierJobs.toTypedArray())
        }

        if (showTime) println("   verifyChallengedBallots ${stopwatch.tookPer(count, "ballots")}")
        return Pair(!errs.hasErrors(), count)
    }

    private var count = 0
    private fun CoroutineScope.produceTallies(producer: Iterable<DecryptedTallyOrBallot>): ReceiveChannel<DecryptedTallyOrBallot> =
        produce {
            for (tally in producer) {
                send(tally)
                yield()
                count++
            }
            channel.close()
        }

    private fun CoroutineScope.launchVerifier(
        input: ReceiveChannel<DecryptedTallyOrBallot>,
        verify: (DecryptedTallyOrBallot) -> Boolean
    ) = launch(Dispatchers.Default) {
        for (dballot in input) {
            verify(dballot)
            yield()
        }
    }

}