package org.cryptobiotic.eg.decryptBallot

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
 *   3. modify testDecryptBallotsSome() and add 4 valid ballot ids to the command line argument
 */
class RunDecryptBallotsJsonTest {
    val nthreads = 25

    @Test
    fun testDecryptBallotsAll() {
        val inputDir = "src/test/data/workflow/allAvailableEc"
        val trusteeDir = "$inputDir/private_data/trustees"
        val outputDir = "${Testing.testOut}/decrypt/testDecryptBallotsAllJson"
        println("\ntestDecryptBallotsAll")
        val (retval, n) = runDecryptBallots(
            inputDir,
            outputDir,
            readDecryptingTrustees(inputDir, trusteeDir),
            "ALL",
            nthreads,
        )
        assertEquals(0, retval)
        assertEquals(42, n)
    }

    @Test
    fun testDecryptBallotsSome() {
        val inputDir = "src/test/data/workflow/someAvailableEc"
        val trusteeDir = "$inputDir/private_data/trustees"
        val outputDir = "${Testing.testOut}/decrypt/testDecryptBallotsSomeJson"
        println("\ntestDecryptBallotsAll")
        val (retval, n) = runDecryptBallots(
            inputDir,
            outputDir,
            readDecryptingTrustees(inputDir, trusteeDir),
            "ALL",
            nthreads,
        )
        assertEquals(0, retval)
        assertEquals(42, n)
    }

    @Test
    fun testDecryptBallotsSomeFromList() {
        val inputDir = "src/test/data/workflow/someAvailableEc"
        val trusteeDir = "$inputDir/private_data/trustees"
        val outputDir = "${Testing.testOut}/decrypt/testDecryptBallotsSomeFromListJson"
        println("\ntestDecryptBallotsSomeFromList")
        val (retval, n) = runDecryptBallots(
            inputDir, outputDir, readDecryptingTrustees(inputDir, trusteeDir, "5"),
            "id-6," +
                    "id-7," +
                    "id-10," +
                    "id-1,",
            3,
        )
        assertEquals(0, retval)
        assertEquals(4, n)
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
