package org.cryptobiotic.eg.cli

import org.cryptobiotic.eg.core.removeAllFiles
import org.cryptobiotic.util.Testing
import java.nio.file.Path
import kotlin.test.*

class RunExampleEncryptionTest {

    @Test
    fun testExampleEncryptionWithChaining() {
        val inputDir = "src/test/data/encrypt/testBallotChain"
        val outputDir = "${Testing.testOut}/encrypt/testExampleEncryptionWithChaining"
        val nballots = 33

        // removeAllFiles(Path.of("$outputDir/encrypted_ballots"))

        RunExampleEncryption.main(
                arrayOf(
                    "--inputDir", inputDir,
                    "--nballots", nballots.toString(),
                    "--plaintextBallotDir", "$outputDir/plaintext",
                    "--encryptBallotDir", outputDir,
                    "-device", "device42,device11",
                )
            )

        val count = verifyOutput(inputDir, "$outputDir", true)
        assertEquals(nballots, count)
    }

    @Test
    fun testExampleEncryptionWithNoChaining() {
        val inputDir = "src/test/data/encrypt/testBallotNoChain"
        val outputDir = "${Testing.testOut}/encrypt/testExampleEncryptionNoChaining"
        val nballots = 33

        removeAllFiles(Path.of("$outputDir/encrypted_ballots"))

        RunExampleEncryption.main(
            arrayOf(
                "--inputDir", inputDir,
                "--nballots", nballots.toString(),
                "--plaintextBallotDir", "$outputDir/plaintext",
                "-device", "device42,device11",
                "--encryptBallotDir", "$outputDir",
                "--noDeviceNameInDir",
            )
        )

        val count = verifyOutput(inputDir, "$outputDir", false)
        assertEquals(nballots, count)
    }

}

