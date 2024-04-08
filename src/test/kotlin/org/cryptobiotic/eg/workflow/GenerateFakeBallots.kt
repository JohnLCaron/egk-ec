package org.cryptobiotic.eg.workflow

import org.cryptobiotic.eg.election.PlaintextBallot
import org.cryptobiotic.eg.input.RandomBallotProvider
import org.cryptobiotic.eg.publish.makePublisher
import org.cryptobiotic.eg.publish.readElectionRecord
import org.cryptobiotic.util.Testing
import kotlin.test.Test

/** Generate fake ballots for testing. No actual testing here. */
class GenerateFakeBallots {
    val inputDir = "src/test/data/startConfigEc"
    val outputDirJson =  "${Testing.testOut}/fakeBallots"

    @Test
    fun generateFakeBallotsJson() {
        val electionRecord = readElectionRecord(inputDir)

        val nballots = 33

        val ballotProvider = RandomBallotProvider(electionRecord.manifest(), nballots)
        val ballots: List<PlaintextBallot> = ballotProvider.ballots()

        val publisher = makePublisher(outputDirJson, true)
        publisher.writePlaintextBallot(outputDirJson, ballots)
    }
}