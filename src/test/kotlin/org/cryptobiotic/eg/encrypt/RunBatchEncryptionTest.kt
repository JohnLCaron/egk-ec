package org.cryptobiotic.eg.encrypt

import org.cryptobiotic.eg.cli.RunBatchEncryption
import org.cryptobiotic.eg.cli.RunBatchEncryption.Companion.batchEncryption
import org.cryptobiotic.eg.cli.RunVerifier
import org.cryptobiotic.eg.input.RandomBallotProvider
import org.cryptobiotic.eg.publish.json.ConsumerJson
import org.cryptobiotic.eg.publish.makeConsumer
import org.cryptobiotic.eg.publish.readElectionRecord
import kotlin.test.Test
import kotlin.test.assertContains

class
RunBatchEncryptionTest {
    val nthreads = 25

    @Test
    fun testRunBatchEncryptionWithJsonBallots() {
        RunBatchEncryption.main(
            arrayOf(
                "-in", "src/test/data/workflow/allAvailableEc",
                "-ballots", "src/test/data/fakeBallots",
                "-out", "testOut/encrypt/testRunBatchEncryptionWithJsonBallots",
                "-invalid", "testOut/encrypt/testRunBatchEncryptionWithJsonBallots/invalid_ballots",
                "-nthreads", "$nthreads",
                "-device", "device2",
                "--cleanOutput",
            )
        )
        RunVerifier.runVerifier("testOut/encrypt/testRunBatchEncryptionWithJsonBallots", 11)
    }

    @Test
    fun testRunBatchEncryptionJson() {
        RunBatchEncryption.main(
            arrayOf(
                "-in", "src/test/data/workflow/allAvailableEc",
                "-ballots", "src/test/data/fakeBallots",
                "-out", "testOut/encrypt/testRunBatchEncryptionJson",
                "-invalid", "testOut/encrypt/testRunBatchEncryptionJson/invalid_ballots",
                "-nthreads", "$nthreads",
                "-device", "device2",
                "--cleanOutput",
            )
        )
        RunVerifier.runVerifier("testOut/encrypt/testRunBatchEncryptionJson", 11)
    }

    // @Test
    fun testRunBatchEncryptionJsonWithProtoBallots() {
        RunBatchEncryption.main(
            arrayOf(
                "-in", "src/test/data/workflow/allAvailableEc",
                "-ballots", "src/test/data/fakeBallots",
                "-out", "testOut/encrypt/testRunBatchEncryptionJsonWithProtoBallots",
                "-invalid", "testOut/encrypt/testRunBatchEncryptionJsonWithProtoBallots/invalid_ballots",
                "-nthreads", "$nthreads",
                "-device", "device3",
                "--cleanOutput",
            )
        )
        RunVerifier.runVerifier("testOut/encrypt/testRunBatchEncryptionJsonWithProtoBallots", 11)
    }

    @Test
    fun testRunBatchEncryptionEncryptTwice() {
        RunBatchEncryption.main(
            arrayOf(
                "-in", "src/test/data/workflow/allAvailableEc",
                "-ballots", "src/test/data/fakeBallots",
                "-out", "testOut/encrypt/testRunBatchEncryptionEncryptTwice",
                "-invalid", "testOut/encrypt/testRunBatchEncryptionEncryptTwice/invalid_ballots",
                "-nthreads", "$nthreads",
                "-device", "device4",
                "-check", "EncryptTwice",
                "--cleanOutput",
            )
        )
    }

    @Test
    fun testRunBatchEncryptionVerify() {
        RunBatchEncryption.main(
            arrayOf(
                "-in", "src/test/data/workflow/allAvailableEc",
                "-ballots", "src/test/data/fakeBallots",
                "-out", "testOut/encrypt/testRunBatchEncryptionVerify",
                "-invalid", "testOut/encrypt/testRunBatchEncryptionVerify/invalid_ballots",
                "-nthreads", "$nthreads",
                "-device", "device35",
                "-check", "Verify",
                "--cleanOutput",
            )
        )
    }

    @Test
    fun testRunBatchEncryptionVerifyDecrypt() {
        RunBatchEncryption.main(
            arrayOf(
                "-in", "src/test/data/workflow/allAvailableEc",
                "-ballots", "src/test/data/fakeBallots",
                "-out", "testOut/encrypt/testRunBatchEncryptionVerifyDecrypt",
                "-invalid", "testOut/encrypt/testRunBatchEncryptionVerifyDecrypt/invalid_ballots",
                "-nthreads", "$nthreads",
                "-device", "device42",
                "-check", "DecryptNonce",
                "--cleanOutput",
            )
        )
    }

    @Test
    fun testInvalidBallot() {
        val inputDir = "src/test/data/workflow/allAvailableEc"
        val outputDir = "testOut/testInvalidBallot"
        val invalidDir = "testOut/testInvalidBallot/invalidDir"

        val electionRecord = readElectionRecord(inputDir)

        val ballot = RandomBallotProvider(electionRecord.manifest(), 1).makeBallot()
        val ballots = listOf( ballot.copy(ballotStyle = "badStyleId"))

        batchEncryption(
            inputDir,
            ballots,
            device = "testDevice",
            outputDir = outputDir,
            encryptDir = null,
            invalidDir = invalidDir,
            1,
            "testInvalidBallot",
        )

        val consumerOut = ConsumerJson(invalidDir, false)
        consumerOut.iteratePlaintextBallots(invalidDir, null).forEach {
            println("${it.errors}")
            assertContains(it.errors.toString(), "Ballot.A.1 Ballot Style 'badStyleId' does not exist in election")
        }

    }

}