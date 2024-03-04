package org.cryptobiotic.eg.cli

import org.cryptobiotic.eg.core.ElGamalPublicKey
import org.cryptobiotic.eg.publish.makeConsumer
import org.cryptobiotic.eg.publish.readElectionRecord
import org.cryptobiotic.eg.verifier.VerifyEncryptedBallots
import org.cryptobiotic.util.ErrorMessages
import org.cryptobiotic.util.testOut
import java.nio.file.Path
import java.util.function.Predicate
import kotlin.test.*

class RunExampleEncryptionTest {

    @Test
    fun testExampleEncryptionWithChaining() {
        val inputDir = "src/test/data/encrypt/testBallotChain"
        val outputDir = "$testOut/encrypt/testExampleEncryptionWithChaining"
        val nballots = 10

        RunExampleEncryption.main(
                arrayOf(
                    "--configDir", inputDir,
                    "--nballots", nballots.toString(),
                    "--plaintextBallotDir", "$outputDir/plaintext",
                    "--encryptBallotDir", "$outputDir/encrypted",
                    "-device", "device42",
                )
            )

        verifyOutput(inputDir, "$outputDir/encrypted", true)
    }

    fun verifyOutput(inputDir: String, eballotDir: String, chained: Boolean = false) {
        val consumer = makeConsumer(inputDir)
        val record = readElectionRecord(consumer)

        val verifier = VerifyEncryptedBallots(
            consumer.group,
            record.manifest(),
            ElGamalPublicKey(record.jointPublicKey()!!),
            record.extendedBaseHash()!!,
            record.config(), 1
        )

        val consumerBallots = makeConsumer(eballotDir, consumer.group)
        // TODO should this be standard filter?
        val pathFilter = Predicate<Path> {
            val name = it.getFileName().toString()
            name.startsWith("eballot")
        }
        val eballots = consumerBallots.iterateEncryptedBallotsFromDir(eballotDir, pathFilter, null )
        val errs = ErrorMessages("verifyBallots")
        val ok = verifier.verifyBallots(eballots, errs)
        println("  verifyEncryptedBallots: ok= $ok result= $errs")
        assertFalse(errs.hasErrors())

        if (chained) {
            val chainErrs = ErrorMessages("verifyConfirmationChain")
            val chainOk =  verifier.verifyOneChain("device42", null, eballots, chainErrs)
            println("  verifyConfirmationChain: ok= $chainOk result= $errs")
            if (chainErrs.hasErrors()) {
                println(chainErrs)
            }
            assertFalse(chainErrs.hasErrors())
        }
        println("Success")
    }


}

