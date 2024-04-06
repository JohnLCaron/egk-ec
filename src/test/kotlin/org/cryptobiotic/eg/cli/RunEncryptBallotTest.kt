package org.cryptobiotic.eg.cli

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
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
                "--inputDir", inputDir,
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
    fun testRunEncryptBallotsNoChaining() {
        val inputDir = "src/test/data/encrypt/testBallotNoChain"
        val outputDir = "$testOut/encrypt/testRunEncryptBallotsNoChaining"
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
                    "--inputDir", inputDir,
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
    fun testRunEncryptBallotsChaining() {
        val inputDir = "src/test/data/encrypt/testBallotChain"
        val device = "device42"
        val outputDeviceDir = "$testOut/encrypt/testRunEncryptBallotsChaining/$device"
        val nballots = 10

        val consumerIn = makeConsumer(inputDir)
        val record = readElectionRecord(consumerIn)
        val manifest = record.manifest()
        val publisher = makePublisher(outputDeviceDir, true)

        val ballotProvider = RandomBallotProvider(manifest)
        repeat(nballots) {
            val ballotId = Random.nextInt().toString()
            val ballot = ballotProvider.getFakeBallot(manifest, null, ballotId)

            publisher.writePlaintextBallot(outputDeviceDir, listOf(ballot))

            val ballotFilename = "$outputDeviceDir/pballot-$ballotId.json"
            RunEncryptBallot.main(
                arrayOf(
                    "--inputDir", inputDir,
                    "--ballotFilename", ballotFilename,
                    "--encryptBallotDir", outputDeviceDir,
                    "-device", "device42",
                )
            )

            val result = consumerIn.readEncryptedBallot(outputDeviceDir, ballotId)
            if (result is Err) {
                println("Error = $result")
            }
            assertTrue( result is Ok)
        }
        val count = verifyOutput(inputDir, outputDeviceDir, true)
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
    val ballots = consumerBallots.iterateEncryptedBallotsFromDir(ballotDir, null )
    val errs = ErrorMessages("verifyBallots")
    val (ok, count) = verifier.verifyBallots(ballots, errs)
    println("  verifyEncryptedBallots $ballotDir: ok= $ok result= $errs")
    assertFalse(errs.hasErrors())

    if (chained) {
        val chainErrs = ErrorMessages("verifyConfirmationChain2")
        val chainOk = verifier.assembleAndVerifyChains(consumerBallots, chainErrs)
        println("  verifyConfirmationChain2 $ballotDir: ok= $chainOk result= $errs")
        if (chainErrs.hasErrors()) {
            println(chainErrs)
        }
        assertFalse(chainErrs.hasErrors())
    }
    println("Success")
    return count
}


