package org.cryptobiotic.eg.decryptBallot

import org.cryptobiotic.eg.cli.RunTrustedBallotDecryption
import org.cryptobiotic.eg.cli.RunTrustedBallotDecryption.Companion.runDecryptBallots
import org.cryptobiotic.eg.cli.RunTrustedTallyDecryption.Companion.readDecryptingTrustees
import org.cryptobiotic.eg.publish.makeConsumer
import org.cryptobiotic.util.Testing

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Test runDecryptBallots with in-process DecryptingTrustee's. Do not use this in production.
 * Note that when the election record changes, the test dataset must be regenerated. For this test, we need to:
 *   1. run showBallotIds() test to see what the possible ballot ids are
 *   2. modify inputDir/private_data/wantedBallots.txt and add 2 valid ballot ids
 *   3. modify testDecryptBallotsSome() and add 3 valid ballot ids to the command line argument
 */
class RunDecryptBallotsTest {
    val nthreads = 25

    @Test
    fun testDecryptBallotsAll() {
        val inputDir = "src/test/data/workflow/allAvailableEc"
        val trusteeDir = "$inputDir/private_data/trustees"
        val outputDir = "${Testing.testOut}/decrypt/testDecryptBallotsAll"
        println("\ntestDecryptBallotsAll")
        val n = runDecryptBallots(
            inputDir,
            outputDir,
            readDecryptingTrustees(inputDir, trusteeDir),
            "ALL",
            nthreads,
        )
        assertEquals(42, n)
    }

    @Test
    fun testDecryptBallotsSomeFromList() {
        val inputDir = "src/test/data/workflow/someAvailableEc"
        val trusteeDir = "$inputDir/private_data/trustees"
        val outputDir = "${Testing.testOut}/decrypt/testDecryptBallotsSomeFromList"
        println("\ntestDecryptBallotsSomeFromList")
        val n = runDecryptBallots(
            inputDir, outputDir, readDecryptingTrustees(inputDir, trusteeDir, "5"),
            "id-1," +
                    "id-3," +
                    "id-2",
            3,
        )
        assertEquals(3, n)
    }

    @Test
    fun testDecryptBallotsSomeFromFile() {
        val inputDir = "src/test/data/workflow/someAvailableEc"
        val trusteeDir = "$inputDir/private_data/trustees"
        val wantBallots = "$inputDir/private_data/wantedBallots.txt"
        val outputDir = "${Testing.testOut}/decrypt/testDecryptBallotsSomeFromFile"
        println("\ntestDecryptBallotsSomeFromFile")
        val n = runDecryptBallots(
            inputDir, outputDir, readDecryptingTrustees(inputDir, trusteeDir, "4,5"),
            wantBallots,
            2,
        )
        assertEquals(2, n)
    }

    // decrypt all the ballots
    @Test
    fun testDecryptBallotsMainMultiThreaded() {
        println("\ntestDecryptBallotsMainMultiThreaded")
        RunTrustedBallotDecryption.main(
            arrayOf(
                "-in",
                "src/test/data/workflow/someAvailableEc",
                "-trustees",
                "src/test/data/workflow/someAvailableEc/private_data/trustees",
                "-out",
                "${Testing.testOut}/decrypt/testDecryptBallotsMainMultiThreaded",
                "-challenged",
                "all",
                "-nthreads",
                "$nthreads"
            )
        )
    }

    // decrypt the ballots marked challenged
    @Test
    fun testDecryptBallotsMarkedChallenged() {
        println("\ntestDecryptBallotsMarkedChallenged")
        RunTrustedBallotDecryption.main(
            arrayOf(
                "-in",
                "src/test/data/workflow/someAvailableEc",
                "-trustees",
                "src/test/data/workflow/someAvailableEc/private_data/trustees",
                "-out",
                "${Testing.testOut}/decrypt/testDecryptBallotsMarkedChallenged",
                "-nthreads",
                "1"
            )
        )
    }

    @Test
    fun showBallotIds() {
        val inputDir = "src/test/data/workflow/someAvailableEc"
        val ballotDir = "$inputDir/private_data/input/"
        val consumerIn = makeConsumer(inputDir)

        consumerIn.iteratePlaintextBallots(ballotDir, null).forEach {
            println(it.ballotId)
        }
    }
}
