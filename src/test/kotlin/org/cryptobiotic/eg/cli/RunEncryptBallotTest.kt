package org.cryptobiotic.eg.cli

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.unwrap
import org.cryptobiotic.eg.core.ElGamalPublicKey
import org.cryptobiotic.eg.input.RandomBallotProvider
import org.cryptobiotic.eg.publish.makeConsumer
import org.cryptobiotic.eg.publish.makePublisher
import org.cryptobiotic.eg.publish.readElectionRecord
import org.cryptobiotic.eg.verifier.VerifyEncryptedBallots
import org.cryptobiotic.util.ErrorMessages
import org.cryptobiotic.util.testOut
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Predicate
import kotlin.random.Random
import kotlin.test.*

class RunEncryptBallotTest {

    @Test
    fun testRunEncryptBallotNoChaining() {
        val inputDir = "src/test/data/encrypt/testBallotNoChain"
        val outputDir = "$testOut/encrypt/testRunEncryptBallotNoChaining"
        val ballotId = "3842034"

        val consumerIn = makeConsumer(inputDir)
        val record = readElectionRecord(consumerIn)
        val manifest = record.manifest()

        val ballotProvider = RandomBallotProvider(manifest)
        val ballot = ballotProvider.getFakeBallot(manifest, "ballotStyle", ballotId)

        val publisher = makePublisher(outputDir, true)
        publisher.writePlaintextBallot(outputDir, listOf(ballot))

        RunEncryptBallot.main(
            arrayOf(
                "--configDir", inputDir,
                "--ballotFilename", "$outputDir/pballot-$ballotId.json",
                "--encryptBallotDir", outputDir,
                "-device", "device42",
            )
        )

        assertTrue(Files.exists(Path.of("$outputDir/eballot-$ballotId.json")))

        verifyOutput(inputDir, outputDir, false)
    }

    @Test
    fun testRunEncryptBallotNoChainingBut() {
        val inputDir = "src/test/data/encrypt/testBallotNoChain"
        val outputDir = "$testOut/encrypt/testRunEncryptBallotNoChaining"
        val nballots = 10

        val consumerIn = makeConsumer(inputDir)
        val record = readElectionRecord(consumerIn)
        val manifest = record.manifest()
        val publisher = makePublisher(outputDir, true)

        val ballotProvider = RandomBallotProvider(manifest)
        var prevCC = ""
        repeat (nballots) {
            val ballotId = Random.nextInt().toString()
            val ballot = ballotProvider.getFakeBallot(manifest, "ballotStyle", ballotId)

            publisher.writePlaintextBallot(outputDir, listOf(ballot))

            val ballotFilename = "$outputDir/pballot-$ballotId.json"
            RunEncryptBallot.main(
                arrayOf(
                    "--configDir", inputDir,
                    "--ballotFilename", ballotFilename,
                    "--encryptBallotDir", outputDir,
                    "-device", "device42",
                    "-previous", prevCC,
                )
            )

            val result = consumerIn.readEncryptedBallot(outputDir, ballotId)
            val eballot = result.unwrap()
            prevCC = eballot.confirmationCode.toBase64()
        }
        verifyOutput(inputDir, outputDir, true)
    }


    @Test
    fun testRunEncryptBallotChaining() {
        val inputDir = "src/test/data/encrypt/testBallotChain"
        val outputDir = "$testOut/encrypt/testRunEncryptBallotChaining"
        val nballots = 10

        val consumerIn = makeConsumer(inputDir)
        val record = readElectionRecord(consumerIn)
        val manifest = record.manifest()
        val publisher = makePublisher(outputDir, true)

        val ballotProvider = RandomBallotProvider(manifest)
        var prevCC = ""
        repeat (nballots) {
            val ballotId = Random.nextInt().toString()
            val ballot = ballotProvider.getFakeBallot(manifest, "ballotStyle", ballotId)

            publisher.writePlaintextBallot(outputDir, listOf(ballot))

            val ballotFilename = "$outputDir/pballot-$ballotId.json"
            RunEncryptBallot.main(
                arrayOf(
                    "--configDir", inputDir,
                    "--ballotFilename", ballotFilename,
                    "--encryptBallotDir", outputDir,
                    "-device", "device42",
                    "-previous", prevCC,
                )
            )

            val result = consumerIn.readEncryptedBallot(outputDir, ballotId)
            val eballot = result.unwrap()
            prevCC = eballot.confirmationCode.toBase64()
        }
        verifyOutput(inputDir, outputDir, true)
    }

    fun verifyOutput(inputDir: String, ballotDir: String, chained: Boolean = false) {
        val consumer = makeConsumer(inputDir)
        val record = readElectionRecord(consumer)

        val verifier = VerifyEncryptedBallots(
            consumer.group,
            record.manifest(),
            ElGamalPublicKey(record.jointPublicKey()!!),
            record.extendedBaseHash()!!,
            record.config(), 1
        )

        val consumerBallots = makeConsumer(ballotDir, consumer.group)
        // TODO should this be standard filter?
        val pathFilter = Predicate<Path> {
            val name = it.getFileName().toString()
            name.startsWith("eballot")
        }
        val ballots = consumerBallots.iterateEncryptedBallotsFromDir(ballotDir, pathFilter, null )
        ballots.forEach {
            println("  ballot = ${it.ballotId}")
        }
        val errs = ErrorMessages("verifyBallots")
        val ok = verifier.verifyBallots(ballots, errs)
        println("  verifyEncryptedBallots: ok= $ok result= $errs")
        assertFalse(errs.hasErrors())

        if (chained) {
            val chainErrs = ErrorMessages("verifyConfirmationChain")
            val chainOk = verifier.verifyConfirmationChain2(consumerBallots, chainErrs)
            println("  verifyConfirmationChain: ok= $chainOk result= $errs")
            assertFalse(chainErrs.hasErrors())
        }
        println("Success")
    }


}

