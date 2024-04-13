package org.cryptobiotic.eg.cli

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import org.cryptobiotic.eg.core.createDirectories
import org.cryptobiotic.eg.core.removeAllFiles
import org.cryptobiotic.eg.input.RandomBallotProvider
import org.cryptobiotic.eg.publish.makeConsumer
import org.cryptobiotic.eg.publish.makePublisher
import org.cryptobiotic.eg.publish.readElectionRecord
import org.cryptobiotic.eg.verifier.VerifyEncryptedBallots
import org.cryptobiotic.util.ErrorMessages
import org.cryptobiotic.util.Testing
import java.nio.file.Files
import java.nio.file.Path
import kotlin.random.Random
import kotlin.test.*

class RunEncryptBallotTest {

    @Test
    fun testRunEncryptBadPlaintextBallot() {
        val inputDir = "src/test/data/encrypt/testBallotNoChain"
        val outputDir = "${Testing.testOut}/encrypt/testRunEncryptBadPlaintextBallot"
        val consumerIn = makeConsumer(inputDir)

        val retval = RunEncryptBallot.encryptBallot(
            consumerIn,
            "$outputDir/pballot-bad.json",
            "$outputDir/encrypted_ballots",
            "device42",
        )
        assertEquals(3, retval)
    }

    @Test
    fun testRunEncryptBadOutputDir() {
        val inputDir = "src/test/data/encrypt/testBallotChain"
        val outputDir = "${Testing.testOut}/encrypt/testRunEncryptBadOutputDir"
        val ballotId = "3842034"

        val consumerIn = makeConsumer(inputDir)
        val record = readElectionRecord(consumerIn)
        val manifest = record.manifest()

        val ballotProvider = RandomBallotProvider(manifest)
        val ballot = ballotProvider.getFakeBallot(manifest, null, ballotId)

        removeAllFiles(Path.of(outputDir))
        val publisher = makePublisher(outputDir, true)
        publisher.writePlaintextBallot(outputDir, listOf(ballot))

        val retval = RunEncryptBallot.encryptBallot(
            consumerIn,
            "$outputDir/pballot-$ballotId.json",
            "nosuchdir",
            "device42",
        )
        assertEquals(4, retval)
    }

    @Test
    fun testRunEncryptOneBallotNoChaining() {
        val inputDir = "src/test/data/encrypt/testBallotNoChain"
        val outputDir = "${Testing.testOut}/encrypt/testRunEncryptOneBallotNoChaining"
        val ballotId = "3842034"

        val consumerIn = makeConsumer(inputDir)
        val record = readElectionRecord(consumerIn)
        val manifest = record.manifest()

        val ballotProvider = RandomBallotProvider(manifest)
        val ballot = ballotProvider.getFakeBallot(manifest, null, ballotId)

        removeAllFiles(Path.of(outputDir))
        val publisher = makePublisher(outputDir, true)
        publisher.writePlaintextBallot(outputDir, listOf(ballot))
        createDirectories("$outputDir/encrypted_ballots")

        RunEncryptBallot.main(
            arrayOf(
                "--inputDir", inputDir,
                "--ballotFilepath", "$outputDir/pballot-$ballotId.json",
                "--encryptBallotDir", "$outputDir/encrypted_ballots",
                "-device", "device42",
            )
        )

        assertTrue(Files.exists(Path.of("$outputDir/encrypted_ballots/eballot-$ballotId.json")))

        val count = verifyOutput(inputDir, outputDir, false)
        assertEquals(1, count)
    }

    @Test
    fun testRunEncryptBallotsNoChaining() {
        val inputDir = "src/test/data/encrypt/testBallotNoChain"
        val outputDir = "${Testing.testOut}/encrypt/testRunEncryptBallotsNoChaining"
        val nballots = 10
        val device = "device42"

        val consumerIn = makeConsumer(inputDir)
        val record = readElectionRecord(consumerIn)
        val manifest = record.manifest()

        removeAllFiles(Path.of(outputDir))
        val publisher = makePublisher(outputDir, true)
        val consumerOut = makeConsumer(outputDir, consumerIn.group)
        createDirectories("$outputDir/encrypted_ballots")

        val ballotProvider = RandomBallotProvider(manifest)
        repeat(nballots) {
            val ballotId = Random.nextInt().toString()
            val ballot = ballotProvider.getFakeBallot(manifest, null, ballotId)

            publisher.writePlaintextBallot(outputDir, listOf(ballot))

            val ballotFilename = "$outputDir/pballot-$ballotId.json"
            RunEncryptBallot.main(
                arrayOf(
                    "--inputDir", inputDir,
                    "--ballotFilepath", ballotFilename,
                    "--encryptBallotDir", "$outputDir/encrypted_ballots",
                    "-device", device,
                )
            )

            assertTrue(Files.exists(Path.of("$outputDir/encrypted_ballots/eballot-$ballotId.json")))
            val result = consumerOut.readEncryptedBallot("", ballotId)
            if (result is Err) {
                println(result)
                fail()
            }
        }

        val count = verifyOutput(inputDir, outputDir, false)
        assertEquals(nballots, count)
    }

    @Test
    fun testRunEncryptBallotsChaining() {
        val inputDir = "src/test/data/encrypt/testBallotChain"
        val device = "device42"
        val outputDir = "${Testing.testOut}/encrypt/testRunEncryptBallotsChaining"
        val outputDeviceDir = "$outputDir/encrypted_ballots/$device"
        val nballots = 10

        val consumerIn = makeConsumer(inputDir)
        val record = readElectionRecord(consumerIn)
        val manifest = record.manifest()
        val publisher = makePublisher(outputDeviceDir, true)
        val consumerOut = makeConsumer(outputDir, consumerIn.group)
        createDirectories("$outputDeviceDir")

        val ballotProvider = RandomBallotProvider(manifest)
        repeat(nballots) {
            val ballotId = Random.nextInt().toString()
            val ballot = ballotProvider.getFakeBallot(manifest, null, ballotId)

            publisher.writePlaintextBallot(outputDeviceDir, listOf(ballot))

            val ballotFilename = "$outputDeviceDir/pballot-$ballotId.json"
            RunEncryptBallot.main(
                arrayOf(
                    "--inputDir", inputDir,
                    "--ballotFilepath", ballotFilename,
                    "--encryptBallotDir", outputDeviceDir,
                    "-device", device,
                )
            )

            val result = consumerOut.readEncryptedBallot(device, ballotId)
            if (result is Err) {
                println("Error = $result")
            }
            assertTrue( result is Ok)
        }

        RunEncryptBallot.main(
            arrayOf(
                "--inputDir", inputDir,
                "--ballotFilepath", "CLOSE",
                "--encryptBallotDir", outputDeviceDir,
                "-device", device,
            )
        )

        val count = verifyOutput(inputDir, outputDir, true)
        assertEquals(nballots, count)
    }

    @Test
    fun testRunEncryptBallotsChainingNotClosed() {
        val inputDir = "src/test/data/encrypt/testBallotChain"
        val device = "device42"
        val outputDir = "${Testing.testOut}/encrypt/testRunEncryptBallotsChainingNotClosed"
        val outputDeviceDir = "$outputDir/encrypted_ballots/$device"
        val nballots = 10

        val consumerIn = makeConsumer(inputDir)
        val record = readElectionRecord(consumerIn)
        val manifest = record.manifest()
        val publisher = makePublisher(outputDeviceDir, true)
        val consumerOut = makeConsumer(outputDir, consumerIn.group)

        val ballotProvider = RandomBallotProvider(manifest)
        repeat(nballots) {
            val ballotId = Random.nextInt().toString()
            val ballot = ballotProvider.getFakeBallot(manifest, null, ballotId)

            publisher.writePlaintextBallot(outputDeviceDir, listOf(ballot))

            val ballotFilename = "$outputDeviceDir/pballot-$ballotId.json"
            RunEncryptBallot.main(
                arrayOf(
                    "--inputDir", inputDir,
                    "--ballotFilepath", ballotFilename,
                    "--encryptBallotDir", outputDeviceDir,
                    "-device", device,
                )
            )

            val result = consumerOut.readEncryptedBallot(device, ballotId)
            if (result is Err) {
                println("Error = $result")
            }
            assertTrue( result is Ok)
        }

        val verifier = VerifyEncryptedBallots(
            consumerIn.group,
            record.manifest(),
            record.jointPublicKey()!!,
            record.extendedBaseHash()!!,
            record.config(), 1
        )

        val consumerBallots = makeConsumer(outputDir, consumerIn.group)
        val chainErrs = ErrorMessages("verifyConfirmationChain")
        verifier.verifyConfirmationChain(consumerBallots, chainErrs)
        if (chainErrs.hasErrors()) {
            println(chainErrs)
        }
        assertTrue(chainErrs.hasErrors())
        assertTrue(chainErrs.toString().contains("7.G. The closing hash is not equal to H"))
    }
}

fun verifyOutput(inputDir: String, outputDir: String, chained: Boolean = false): Int {
    val consumer = makeConsumer(inputDir)
    val record = readElectionRecord(consumer)

    val verifier = VerifyEncryptedBallots(
        consumer.group,
        record.manifest(),
        record.jointPublicKey()!!,
        record.extendedBaseHash()!!,
        record.config(), 1
    )

    val consumerBallots = makeConsumer(outputDir, consumer.group)
    val ballots = consumerBallots.iterateAllEncryptedBallots( null )
    val errs = ErrorMessages("verifyBallots")
    val (ok, count) = verifier.verifyBallots(ballots, errs)
    println("  verifyEncryptedBallots $outputDir: ok= $ok result= $errs")
    assertFalse(errs.hasErrors())

    if (chained) {
        val chainErrs = ErrorMessages("verifyConfirmationChain")
        verifier.verifyConfirmationChain(consumerBallots, chainErrs)
        if (chainErrs.hasErrors()) {
            println(chainErrs)
        }
        assertFalse(chainErrs.hasErrors())
    }

    if (chained) {
        val chain2Errs = ErrorMessages("assembleAndVerifyChains")
        verifier.assembleAndVerifyChains(consumerBallots, chain2Errs)
        if (chain2Errs.hasErrors()) {
            println(chain2Errs)
        }
        assertFalse(chain2Errs.hasErrors())
    }
    println("Success")
    return count
}


