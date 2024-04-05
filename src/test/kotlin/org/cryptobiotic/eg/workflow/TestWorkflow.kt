package org.cryptobiotic.eg.workflow

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import org.cryptobiotic.eg.cli.RunAccumulateTally.Companion.runAccumulateBallots
import org.cryptobiotic.eg.cli.RunBatchEncryption.Companion.batchEncryption
import org.cryptobiotic.eg.cli.RunTrustedBallotDecryption.Companion.runDecryptBallots
import org.cryptobiotic.eg.cli.RunTrustedTallyDecryption.Companion.runDecryptTally
import org.cryptobiotic.eg.decrypt.DecryptingTrusteeIF
import org.cryptobiotic.eg.election.*
import org.cryptobiotic.eg.encrypt.AddEncryptedBallot
import org.cryptobiotic.eg.encrypt.compare
import org.cryptobiotic.eg.input.BallotInputValidation
import org.cryptobiotic.eg.input.RandomBallotProvider
import org.cryptobiotic.eg.publish.makeConsumer
import org.cryptobiotic.eg.publish.makePublisher
import org.cryptobiotic.eg.publish.readElectionRecord
import org.cryptobiotic.eg.verifier.Verifier
import org.cryptobiotic.util.ErrorMessages
import org.cryptobiotic.util.Stats
import org.cryptobiotic.util.testOut
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Run complete workflow starting from ElectionConfig in the start directory, all the way through verify.
 * (See RunCreateTestManifestTest to regenerate Manifest)
 * (See CreateElectionConfig to regenerate ElectionConfig)
 * (See FakeKeyCeremonyTest to regenerate keyceremony data)
 * (See AddBallotSyncTest to regenerate encrypt test data)
 * Note that TestWorkflow uses RunFakeKeyCeremonyTest, not real KeyCeremony.
 *   1. The results can be copied to the test data sets "src/test/data/workflow" whenever the election record changes.
 */
class TestWorkflow {
    private val nballots = 42
    private val nthreads = 11

    @Test
    fun runWorkflow() {
        runWorkflow("src/test/data/startConfig", "$testOut/workflow/allAvailable", false)
        runWorkflow("src/test/data/startConfigEc", "$testOut/workflow/allAvailableEc", false)
        runWorkflow("src/test/data/startConfig", "$testOut/workflow/someAvailable", true)
        runWorkflow("src/test/data/startConfigEc", "$testOut/workflow/someAvailableEc", true)
    }

    fun runWorkflow(configDirJson: String, workingDir: String, onlySome: Boolean, chained: Boolean = false) {
        val privateDir = "$workingDir/private_data"
        val trusteeDir = "${privateDir}/trustees"
        val ballotsDir = "${privateDir}/input"
        val invalidDir = "${privateDir}/invalid"

        val present = if (onlySome) listOf(1, 2, 5) else listOf(1, 2, 3)
        val nguardians = present.maxOf { it }.toInt()
        val quorum = present.count()

        // delete current workingDir
        makePublisher(workingDir, true)

        // key ceremony
        val (manifest, electionInit) = runFakeKeyCeremony(
            configDirJson,
            workingDir,
            trusteeDir,
            nguardians,
            quorum,
            chained
        )
        println("FakeKeyCeremony created ElectionInitialized, guardians = $present")

        // create fake ballots
        val ballotProvider = RandomBallotProvider(manifest, nballots).withSequentialIds()
        val ballots: List<PlaintextBallot> = ballotProvider.ballots()
        val publisher = makePublisher(ballotsDir, false)
        publisher.writePlaintextBallot(ballotsDir, ballots)
        println("RandomBallotProvider created ${ballots.size} ballots")

        // encrypt
        val encryptor = AddEncryptedBallot(
            manifest,
            BallotInputValidation(manifest),
            electionInit.config.chainConfirmationCodes,
            electionInit.config.configBaux0,
            electionInit.jointPublicKey,
            electionInit.extendedBaseHash,
            device = "runWorkflowDevice",
            outputDir = workingDir,
            invalidDir = invalidDir,
        )
        val nchallenged = encrypt(ballots, encryptor)

        // tally
        runAccumulateBallots(workingDir, workingDir, null, "RunWorkflow", "createdBy")

        // decrypt tally
        val trustees = readDecryptingTrustees(workingDir, trusteeDir, electionInit, present)
        runDecryptTally(
            workingDir,
            workingDir,
            trustees,
            "runWorkflowCreator"
        )

        // decrypt challenged ballots
        val ndecrypted = runDecryptBallots(
            inputDir = workingDir,
            outputDir = workingDir,
            trustees,
            "challenged",
            11
        )
        assertEquals(nchallenged, ndecrypted)

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

    fun encrypt(ballots: List<PlaintextBallot>, encryptor : AddEncryptedBallot): Int {
        var nchallenged = 0
        ballots.forEach { ballot ->
            val encrypted = encryptor.encrypt(ballot, ErrorMessages("testMultipleCalls"))
            assertNotNull(encrypted)

            val random = Random.nextInt(10)
            val challengeThisOne = random < 2 // challenge 2 in 10
            if (challengeThisOne) {
                // println("  challenged ${encrypted.ballotId}")
                val dresult = encryptor.challenge(encrypted.confirmationCode) // dont need to decrypt
                assertTrue( dresult is Ok)
                nchallenged++
            } else {
                encryptor.cast(encrypted.confirmationCode)
            }
        }
        return nchallenged
    }
}