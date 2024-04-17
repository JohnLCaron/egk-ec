package org.cryptobiotic.eg.cli

import org.cryptobiotic.util.Testing
import kotlin.test.*

class RunAddEncryptedBallotsTest {

    @Test
    fun testAddEncryptedBallotsWithChaining() {
        val inputDir = "src/test/data/encrypt/testBallotChain"
        val ballotDir = "src/test/data/fakeBallots"
        val outputDir = "${Testing.testOut}/encrypt/testAddEncryptedBallotsWithChaining"

        RunAddEncryptedBallots.main(
                arrayOf(
                    "--inputDir", inputDir,
                    "--ballotDir", ballotDir,
                    "--device", "device11",
                    "--outputDir", outputDir,
                    "--challengePct", "10",
                )
            )

        val count = verifyOutput(inputDir, outputDir, true)
        assertTrue(count > 0)
    }

    @Test
    fun testAddEncryptedBallotsNoChaining() {
        val inputDir = "src/test/data/encrypt/testBallotNoChain"
        val ballotDir = "src/test/data/fakeBallots"
        val outputDir = "${Testing.testOut}/encrypt/testAddEncryptedBallotsNoChaining"

        RunAddEncryptedBallots.main(
            arrayOf(
                "--inputDir", inputDir,
                "--ballotDir", ballotDir,
                "--device", "device11",
                "--outputDir", outputDir,
                "--challengePct", "10",
            )
        )

        val count = verifyOutput(inputDir, outputDir, false)
        assertTrue(count > 0)
    }

}

