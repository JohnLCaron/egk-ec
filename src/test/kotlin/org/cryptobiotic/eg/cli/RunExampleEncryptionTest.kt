package org.cryptobiotic.eg.cli

import org.cryptobiotic.eg.core.removeAllFiles
import org.cryptobiotic.util.testOut
import java.nio.file.Path
import kotlin.test.*

class RunExampleEncryptionTest {

    @Test
    fun testExampleEncryptionWithChaining() {
        val inputDir = "src/test/data/encrypt/testBallotChain"
        val outputDir = "$testOut/encrypt/testExampleEncryptionWithChaining"
        val nballots = 33

        removeAllFiles(Path.of("$outputDir/encrypted_ballots"))

        RunExampleEncryption.main(
                arrayOf(
                    "--inputDir", inputDir,
                    "--nballots", nballots.toString(),
                    "--plaintextBallotDir", "$outputDir/plaintext",
                    "--encryptBallotDir", "$outputDir/encrypted_ballots",
                    "-device", "device42,device11",
                    "--addDeviceNameToDir",
                )
            )

        val count1 = verifyOutput(inputDir, "$outputDir/encrypted_ballots/device11", true)
        val count2 = verifyOutput(inputDir, "$outputDir/encrypted_ballots/device42", true)
        assertEquals(nballots, count1 + count2)
    }

    @Test
    fun testExampleEncryptionWithNoChaining() {
        val inputDir = "src/test/data/encrypt/testBallotNoChain"
        val outputDir = "$testOut/encrypt/testExampleEncryptionNoChaining"
        val nballots = 33

        removeAllFiles(Path.of("$outputDir/encrypted_ballots"))

        RunExampleEncryption.main(
            arrayOf(
                "--inputDir", inputDir,
                "--nballots", nballots.toString(),
                "--plaintextBallotDir", "$outputDir/plaintext",
                "-device", "device42,device11",
                "--encryptBallotDir", "$outputDir/encrypted_ballots",
                "--addDeviceNameToDir",
            )
        )

        val count1 = verifyOutput(inputDir, "$outputDir/encrypted_ballots/device11", false)
        val count2 = verifyOutput(inputDir, "$outputDir/encrypted_ballots/device42", false)
        assertEquals(nballots, count1 + count2)
    }

}

