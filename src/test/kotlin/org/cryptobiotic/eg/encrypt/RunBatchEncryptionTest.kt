package org.cryptobiotic.eg.encrypt

import org.cryptobiotic.eg.cli.RunBatchEncryption
import org.cryptobiotic.eg.cli.RunBatchEncryption.Companion.batchEncryption
import org.cryptobiotic.eg.cli.RunVerifier
import org.cryptobiotic.eg.input.RandomBallotProvider
import org.cryptobiotic.eg.publish.json.ConsumerJson
import org.cryptobiotic.eg.publish.readElectionRecord
import org.cryptobiotic.util.Testing
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class
RunBatchEncryptionTest {
    val nthreads = 1

    @Test
    fun testRunBatchEncryptionWithEc() {
        RunBatchEncryption.main(
            arrayOf(
                "-in", "src/test/data/workflow/allAvailableEc",
                "-ballots", "src/test/data/fakeBallots",
                "-out", "${Testing.testOut}/encrypt/testRunBatchEncryptionWithEc",
                "-invalid", "${Testing.testOut}/encrypt/testRunBatchEncryptionWithEc/invalid_ballots",
                "-nthreads", "$nthreads",
                "-device", "device2",
                "--cleanOutput",
                "--noexit"
            )
        )
        RunVerifier.runVerifier("${Testing.testOut}/encrypt/testRunBatchEncryptionWithEc", 11)
    }

    @Test
    fun testRunBatchEncryptionWithInteger() {
        RunBatchEncryption.main(
            arrayOf(
                "-in", "src/test/data/workflow/allAvailable",
                "-ballots", "src/test/data/fakeBallots",
                "-out", "${Testing.testOut}/encrypt/testRunBatchEncryptionWithInteger",
                "-invalid", "${Testing.testOut}/encrypt/testRunBatchEncryptionWithInteger/invalid_ballots",
                "-nthreads", "$nthreads",
                "-device", "device2",
                "--cleanOutput",
                "--noexit"
            )
        )
        RunVerifier.runVerifier("${Testing.testOut}/encrypt/testRunBatchEncryptionWithInteger", 11)
    }

    @Test
    fun testRunBatchEncryptionEncryptTwice() {
        RunBatchEncryption.main(
            arrayOf(
                "-in", "src/test/data/workflow/allAvailableEc",
                "-ballots", "src/test/data/fakeBallots",
                "-out", "${Testing.testOut}/encrypt/testRunBatchEncryptionEncryptTwice",
                "-invalid", "${Testing.testOut}/encrypt/testRunBatchEncryptionEncryptTwice/invalid_ballots",
                "-nthreads", "$nthreads",
                "-device", "device4",
                "-check", "EncryptTwice",
                "--cleanOutput",
                "--noexit"
            )
        )
    }

    @Test
    fun testRunBatchEncryptionVerify() {
        RunBatchEncryption.main(
            arrayOf(
                "-in", "src/test/data/workflow/allAvailableEc",
                "-ballots", "src/test/data/fakeBallots",
                "-out", "${Testing.testOut}/encrypt/testRunBatchEncryptionVerify",
                "-invalid", "${Testing.testOut}/encrypt/testRunBatchEncryptionVerify/invalid_ballots",
                "-nthreads", "$nthreads",
                "-device", "device35",
                "-check", "Verify",
                "--cleanOutput",
                "--noexit"
            )
        )
    }

    @Test
    fun testRunBatchEncryptionVerifyDecrypt() {
        RunBatchEncryption.main(
            arrayOf(
                "-in", "src/test/data/workflow/allAvailableEc",
                "-ballots", "src/test/data/fakeBallots",
                "-out", "${Testing.testOut}/encrypt/testRunBatchEncryptionVerifyDecrypt",
                "-invalid", "${Testing.testOut}/encrypt/testRunBatchEncryptionVerifyDecrypt/invalid_ballots",
                "-nthreads", "$nthreads",
                "-device", "device42",
                "-check", "DecryptNonce",
                "--cleanOutput",
                "--noexit"
            )
        )
    }

    @Test
    fun testInvalidBallot() {
        val inputDir = "src/test/data/workflow/allAvailableEc"
        val outputDir = "${Testing.testOut}/testInvalidBallot"
        val invalidDir = "${Testing.testOut}/testInvalidBallot/invalidDir"

        val electionRecord = readElectionRecord(inputDir)

        val ballot = RandomBallotProvider(electionRecord.manifest(), 1).makeBallot()
        val ballots = listOf( ballot.copy(ballotStyle = "badStyleId"))

        val retval = batchEncryption(
            inputDir,
            ballots,
            device = "testDevice",
            outputDir = outputDir,
            encryptDir = null,
            invalidDir = invalidDir,
            1,
            "testInvalidBallot",
        )
        assertEquals(0, retval)

        val consumerOut = ConsumerJson(invalidDir, electionRecord.group)
        consumerOut.iteratePlaintextBallots(invalidDir, null).forEach {
            println("${it.errors}")
            assertContains(it.errors.toString(), "Ballot.A.1 Ballot Style 'badStyleId' does not exist in election")
        }

    }

}