package org.cryptobiotic.eg.verifier

import org.cryptobiotic.eg.election.EncryptedBallot
import org.cryptobiotic.util.Stats
import org.cryptobiotic.eg.publish.readElectionRecord
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.test.Test

class DuplicateTrackingCodesTest {
    private val inputDir = "src/commonTest/data/workflow/allAvailableJson"

    @Test
    fun duplicateTrackingCodes() {
        val electionRecord = readElectionRecord(inputDir)
        val mungedBallots = mutableListOf<EncryptedBallot>()

        var count = 0
        for (ballot in electionRecord.encryptedAllBallots { true }) {
            // println(" munged ballot ${ballot.ballotId}")
            mungedBallots.add(ballot)
            if (count % 3 == 0) {
                mungedBallots.add(ballot)
            }
            if (count > 10) {
                break
            }
            count++
        }

        // verify duplicate ballots fail
        val verifier = Verifier(electionRecord)
        val results = verifier.verifyEncryptedBallots(mungedBallots, Stats())
        println(results)
        assertTrue(results.hasErrors())
    }
}