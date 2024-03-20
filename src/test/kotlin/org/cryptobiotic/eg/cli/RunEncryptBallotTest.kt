package org.cryptobiotic.eg.cli

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import org.cryptobiotic.eg.core.ElGamalPublicKey
import org.cryptobiotic.eg.core.removeAllFiles
import org.cryptobiotic.eg.input.RandomBallotProvider
import org.cryptobiotic.eg.publish.makeConsumer
import org.cryptobiotic.eg.publish.makePublisher
import org.cryptobiotic.eg.publish.readElectionRecord
import org.cryptobiotic.eg.verifier.VerifyEncryptedBallots
import org.cryptobiotic.util.ErrorMessages
import org.cryptobiotic.util.testOut
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Predicate
import kotlin.random.Random
import kotlin.test.*

class RunEncryptBallotTest {

    @Test
    fun testRunEncryptOneBallotNoChaining() {
        val inputDir = "src/test/data/encrypt/testBallotNoChain"
        val outputDir = "$testOut/encrypt/testRunEncryptBallotNoChaining"
        val ballotId = "3842034"

        val consumerIn = makeConsumer(inputDir)
        val record = readElectionRecord(consumerIn)
        val manifest = record.manifest()

        val ballotProvider = RandomBallotProvider(manifest)
        val ballot = ballotProvider.getFakeBallot(manifest, null, ballotId)

        removeAllFiles(Path.of(outputDir))
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

        val count = verifyOutput(inputDir, outputDir, false)
        assertEquals(1, count)
    }

    @Test
    fun testRunEncryptBallotNoChainingBut() {
        val inputDir = "src/test/data/encrypt/testBallotNoChain"
        val outputDir = "$testOut/encrypt/testRunEncryptBallotNoChainingBut"
        val nballots = 10

        val consumerIn = makeConsumer(inputDir)
        val record = readElectionRecord(consumerIn)
        val manifest = record.manifest()

        removeAllFiles(Path.of(outputDir))
        val publisher = makePublisher(outputDir, true)

        val ballotProvider = RandomBallotProvider(manifest)
        repeat(nballots) {
            val ballotId = Random.nextInt().toString()
            val ballot = ballotProvider.getFakeBallot(manifest, null, ballotId)

            publisher.writePlaintextBallot(outputDir, listOf(ballot))

            val ballotFilename = "$outputDir/pballot-$ballotId.json"
            RunEncryptBallot.main(
                arrayOf(
                    "--configDir", inputDir,
                    "--ballotFilename", ballotFilename,
                    "--encryptBallotDir", outputDir,
                    "-device", "device42",
                )
            )

            val result = consumerIn.readEncryptedBallot(outputDir, ballotId)
            if (result is Err) {
                println(result)
                fail()
            }
        }
        val count = verifyOutput(inputDir, outputDir, true)
        assertEquals(nballots, count)
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
        repeat(nballots) {
            val ballotId = Random.nextInt().toString()
            val ballot = ballotProvider.getFakeBallot(manifest, null, ballotId)

            publisher.writePlaintextBallot(outputDir, listOf(ballot))

            val ballotFilename = "$outputDir/pballot-$ballotId.json"
            RunEncryptBallot.main(
                arrayOf(
                    "--configDir", inputDir,
                    "--ballotFilename", ballotFilename,
                    "--encryptBallotDir", outputDir,
                    "-device", "device42",
                )
            )

            val result = consumerIn.readEncryptedBallot(outputDir, ballotId)
            assertTrue( result is Ok)
        }
        val count = verifyOutput(inputDir, outputDir, true)
        assertEquals(nballots, count)
    }
}

fun verifyOutput(inputDir: String, ballotDir: String, chained: Boolean = false): Int {
    val consumer = makeConsumer(inputDir)
    val record = readElectionRecord(consumer)

    val verifier = VerifyEncryptedBallots(
        consumer.group,
        record.manifest(),
        record.jointPublicKey()!!,
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
    val errs = ErrorMessages("verifyBallots")
    val (ok, count) = verifier.verifyBallots(ballots, errs)
    println("  verifyEncryptedBallots $ballotDir: ok= $ok result= $errs")
    assertFalse(errs.hasErrors())

    if (chained) {
        val chainErrs = ErrorMessages("verifyConfirmationChain2")
        val chainOk = verifier.verifyConfirmationChain2(consumerBallots, chainErrs)
        println("  verifyConfirmationChain2 $ballotDir: ok= $chainOk result= $errs")
        if (chainErrs.hasErrors()) {
            println(chainErrs)
        }
        assertFalse(chainErrs.hasErrors())
    }
    println("Success")
    return count
}


