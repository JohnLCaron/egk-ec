package org.cryptobiotic.eg.workflow

import com.github.michaelbull.result.unwrap
import org.cryptobiotic.eg.cli.RunAccumulateTally.Companion.runAccumulateBallots
import org.cryptobiotic.eg.cli.RunBatchEncryption.Companion.batchEncryption
import org.cryptobiotic.eg.cli.RunTrustedTallyDecryption.Companion.runDecryptTally
import org.cryptobiotic.eg.decrypt.DecryptingTrusteeIF
import org.cryptobiotic.eg.election.*
import org.cryptobiotic.eg.input.RandomBallotProvider
import org.cryptobiotic.eg.publish.makeConsumer
import org.cryptobiotic.eg.publish.makePublisher
import org.cryptobiotic.eg.publish.readElectionRecord
import org.cryptobiotic.eg.verifier.Verifier
import org.cryptobiotic.util.Stats
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Run complete workflow starting from ElectionConfig in the start directory, all the way through verify.
 * (See RunCreateTestManifestTest to regenerate Manifest)
 * (See RunCreateConfigTest/RunCreateElectionConfig to regenerate ElectionConfig)
 * Note that TestWorkflow uses RunFakeKeyCeremonyTest, not real KeyCeremony.
 *  1. The results can be copied to the test data sets "src/commonTest/data/workflow" whenever the
 *     election record changes.
 *  2. Now that we have fixed ballot ids, dont need to RunDecryptBallotsTest for more damn things to do.
 */
class TestWorkflow {
    private val nballots = 11
    private val nthreads = 11

    @Test
    fun runWorkflow() {
        // runWorkflow("src/test/data/startConfig", "testOut/workflow/allAvailable")
        runWorkflow("src/test/data/startConfigEc", "testOut/workflow/allAvailableEc")
    }

    fun runWorkflow(configDirJson: String, workingDir: String) {
        val privateDir = "$workingDir/private_data"
        val trusteeDir = "${privateDir}/trustees"
        val ballotsDir = "${privateDir}/input"
        val invalidDir = "${privateDir}/invalid"

        val present = listOf(1, 2, 3) // all guardians present
        val nguardians = present.maxOf { it }.toInt()
        val quorum = present.count()

        // delete current workingDir
        makePublisher(workingDir, true)

        // key ceremony
        val (manifest, init) = runFakeKeyCeremony(
            configDirJson,
            workingDir,
            trusteeDir,
            nguardians,
            quorum,
            false
        )
        println("FakeKeyCeremony created ElectionInitialized, guardians = $present")

        // create fake ballots
        val ballotProvider = RandomBallotProvider(manifest, nballots).withSequentialIds()
        val ballots: List<PlaintextBallot> = ballotProvider.ballots()
        val publisher = makePublisher(ballotsDir, false)
        publisher.writePlaintextBallot(ballotsDir, ballots)
        println("RandomBallotProvider created ${ballots.size} ballots")

        // encrypt
        batchEncryption(
            inputDir = workingDir, ballotDir = ballotsDir, device = "runWorkflowAllAvailableJson",
            outputDir = workingDir, null, invalidDir = invalidDir, nthreads, "createdBy"
        )

        // tally
        runAccumulateBallots(workingDir, workingDir, null, "RunWorkflow", "createdBy")

        // decrypt tally
        runDecryptTally(
            workingDir,
            workingDir,
            readDecryptingTrustees(workingDir, trusteeDir, init, present),
            "runWorkflowAllAvailableJson"
        )

        // verify
        println("\nRun Verifier")
        val record = readElectionRecord(workingDir)
        val verifier = Verifier(record)
        val stats = Stats()
        val ok = verifier.verify(stats)
        stats.show()
        println("Verify is $ok")
        assertTrue(ok)
    }

    fun readDecryptingTrustees(
        inputDir: String,
        trusteeDir: String,
        init: ElectionInitialized,
        present: List<Int>,
    ): List<DecryptingTrusteeIF> {
        val consumer = makeConsumer(inputDir)
        return init.guardians.filter { present.contains(it.xCoordinate)}.map { consumer.readTrustee(trusteeDir, it.guardianId).unwrap() }
    }
}