package org.cryptobiotic.eg.encrypt

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.unwrap
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.eg.cli.RunEncryptBallot
import org.cryptobiotic.eg.cli.RunEncryptBallot.Companion
import kotlin.test.*

import org.cryptobiotic.eg.core.*
import org.cryptobiotic.eg.election.*
import org.cryptobiotic.eg.input.BallotInputValidation
import org.cryptobiotic.eg.input.RandomBallotProvider
import org.cryptobiotic.eg.publish.makeConsumer
import org.cryptobiotic.eg.publish.makePublisher
import org.cryptobiotic.eg.publish.readElectionRecord
import org.cryptobiotic.eg.verifier.VerifyEncryptedBallots
import org.cryptobiotic.util.ErrorMessages
import org.cryptobiotic.util.Stats
import org.cryptobiotic.util.Testing

class AddEncryptedBallotTest {
    val input = "src/test/data/workflow/allAvailableEc"
    val testDir = "${Testing.testOut}/encrypt/addEncryptedBallot/Plain"
    val nballots = 4
    private val logger = KotlinLogging.logger("AddEncryptedBallotTest")

    @Test
    fun testOneDevice() {
        val outputDir = "$testDir/testOneDevice"
        val device = "device0"

        val electionRecord = readElectionRecord(input)
        val electionInit = electionRecord.electionInit()!!
        val publisher = makePublisher(outputDir, true)
        publisher.writeElectionInitialized(electionInit)

        val encryptor = AddEncryptedBallot(
            electionRecord.manifest(),
            BallotInputValidation(electionRecord.manifest()),
            electionInit.config.chainConfirmationCodes,
            electionInit.config.configBaux0,
            electionInit.jointPublicKey,
            electionInit.extendedBaseHash,
            device,
            outputDir,
            "${outputDir}/invalidDir",
        )
        val ballotProvider = RandomBallotProvider(electionRecord.manifest())

        repeat(nballots) {
            val ballot = ballotProvider.makeBallot()
            val result = encryptor.encrypt(ballot, ErrorMessages("testOneDevice"))
            assertNotNull(result)
            encryptor.submit(result.confirmationCode, EncryptedBallot.BallotState.CAST)
        }
        encryptor.close()

        checkOutput(outputDir, nballots, electionInit.config.chainConfirmationCodes)
    }

    @Test
    fun testOneDeviceNotInDir() {
        val outputDir = "$testDir/testOneDeviceNotInDir"
        val device = "device0"

        val electionRecord = readElectionRecord(input)
        val electionInit = electionRecord.electionInit()!!
        val publisher = makePublisher(outputDir, true)
        publisher.writeElectionInitialized(electionInit)

        val encryptor = AddEncryptedBallot(
            electionRecord.manifest(),
            BallotInputValidation(electionRecord.manifest()),
            electionInit.config.chainConfirmationCodes,
            electionInit.config.configBaux0,
            electionInit.jointPublicKey,
            electionInit.extendedBaseHash,
            device,
            outputDir,
            "${outputDir}/invalidDir",
            noDeviceNameInDir = true
        )
        val ballotProvider = RandomBallotProvider(electionRecord.manifest())

        repeat(nballots) {
            val ballot = ballotProvider.makeBallot()
            val result = encryptor.encrypt(ballot, ErrorMessages("testOneDeviceNotInDir"))
            assertNotNull(result)
            encryptor.submit(result.confirmationCode, EncryptedBallot.BallotState.CAST)
        }
        encryptor.close()

        checkOutput(outputDir, nballots, electionInit.config.chainConfirmationCodes)
    }

    @Test
    fun testEncryptAndCast() {
        val outputDir = "$testDir/testEncryptAndCast"
        val device = "device0"

        val electionRecord = readElectionRecord(input)
        val electionInit = electionRecord.electionInit()!!
        val publisher = makePublisher(outputDir, true)
        publisher.writeElectionInitialized(electionInit)

        val encryptor = AddEncryptedBallot(
            electionRecord.manifest(),
            BallotInputValidation(electionRecord.manifest()),
            electionInit.config.chainConfirmationCodes,
            electionInit.config.configBaux0,
            electionInit.jointPublicKey,
            electionInit.extendedBaseHash,
            device,
            outputDir,
            "${outputDir}/invalidDir",
        )
        val ballotProvider = RandomBallotProvider(electionRecord.manifest())

        repeat(nballots) {
            val ballot = ballotProvider.makeBallot()
            val result = encryptor.encryptAndCast(ballot, ErrorMessages("testEncryptAndCast"))
            assertNotNull(result)
            // expect submitting again to fail
            val submitAgain = encryptor.submit(result.confirmationCode, EncryptedBallot.BallotState.CAST)
            assertTrue(submitAgain is Err)
            assertTrue(submitAgain.error.contains("unknown ballot ccode"))
        }
        encryptor.close()

        checkOutput(outputDir, nballots, electionInit.config.chainConfirmationCodes)
    }

    @Test
    fun testEncryptAndCastNoWrite() {
        val outputDir = "$testDir/testEncryptAndCastNoWrite"
        val device = "device0"

        val electionRecord = readElectionRecord(input)
        val electionInit = electionRecord.electionInit()!!
        val publisher = makePublisher(outputDir, true)
        publisher.writeElectionInitialized(electionInit)

        val encryptor = AddEncryptedBallot(
            electionRecord.manifest(),
            BallotInputValidation(electionRecord.manifest()),
            electionInit.config.chainConfirmationCodes,
            electionInit.config.configBaux0,
            electionInit.jointPublicKey,
            electionInit.extendedBaseHash,
            device,
            outputDir,
            "${outputDir}/invalidDir",
        )
        val ballotProvider = RandomBallotProvider(electionRecord.manifest())

        repeat(nballots) {
            val ballot = ballotProvider.makeBallot()
            val result = encryptor.encryptAndCast(ballot, ErrorMessages("testEncryptAndCastNoWrite"), false)
            assertNotNull(result)
            // expect submitting again to fail
            val submitAgain = encryptor.submit(result.confirmationCode, EncryptedBallot.BallotState.CAST)
            assertTrue(submitAgain is Err)
            assertTrue(submitAgain.error.contains("unknown ballot ccode"))
        }
        encryptor.close()

        // make sure no ballots were written
        val consumer = makeConsumer(outputDir)
        assertFalse(consumer.hasEncryptedBallots())
    }

    @Test
    fun testCallMultipleTimes() {
        val outputDir = "$testDir/testCallMultipleTimes"
        val device = "device1"

        val electionRecord = readElectionRecord(input)
        val electionInit = electionRecord.electionInit()!!
        val publisher = makePublisher(outputDir, true)
        publisher.writeElectionInitialized(electionInit)

        repeat(3) {
            val encryptor = AddEncryptedBallot(
                electionRecord.manifest(),
                BallotInputValidation(electionRecord.manifest()),
                electionInit.config.chainConfirmationCodes,
                electionInit.config.configBaux0,
                electionInit.jointPublicKey,
                electionInit.extendedBaseHash,
                device,
                outputDir,
                "outputDir/invalidDir",
            )
            val ballotProvider = RandomBallotProvider(electionRecord.manifest())

            repeat(nballots) {
                val ballot = ballotProvider.makeBallot()
                val result = encryptor.encrypt(ballot, ErrorMessages("testCallMultipleTimes"))
                assertNotNull(result)
                encryptor.submit(result.confirmationCode, EncryptedBallot.BallotState.CAST)
            }
            encryptor.close()
        }

        checkOutput(outputDir, 3 * nballots, electionInit.config.chainConfirmationCodes)
    }

    @Test
    fun testMultipleDevices() {
        val outputDir = "$testDir/testMultipleDevices"

        val electionRecord = readElectionRecord(input)
        val electionInit = electionRecord.electionInit()!!
        val publisher = makePublisher(outputDir, true)
        publisher.writeElectionInitialized(electionInit)

        repeat(3) { it ->
            val encryptor = AddEncryptedBallot(
                electionRecord.manifest(),
                BallotInputValidation(electionRecord.manifest()),
                electionInit.config.chainConfirmationCodes,
                electionInit.config.configBaux0,
                electionInit.jointPublicKey,
                electionInit.extendedBaseHash,
                "device$it",
                outputDir,
                "$outputDir/invalidDir",
            )
            val ballotProvider = RandomBallotProvider(electionRecord.manifest())

            repeat(nballots) {
                val ballot = ballotProvider.makeBallot()
                val result = encryptor.encrypt(ballot, ErrorMessages("testMultipleDevices"))
                assertNotNull(result)
                encryptor.submit(result.confirmationCode, EncryptedBallot.BallotState.CAST)
            }
            encryptor.close()
        }

        checkOutput(outputDir, 3 * nballots, electionInit.config.chainConfirmationCodes)
    }

    @Test
    fun testOneWithChain() {
        val outputDir = "$testDir/testOneWithChain"
        val device = "device0"

        val electionRecord = readElectionRecord(input)
        val configWithChaining = electionRecord.config().copy(chainConfirmationCodes = true)
        val electionInit = electionRecord.electionInit()!!.copy(config = configWithChaining)

        val publisher = makePublisher(outputDir, true)
        publisher.writeElectionInitialized(electionInit)

        val encryptor = AddEncryptedBallot(
            electionRecord.manifest(),
            BallotInputValidation(electionRecord.manifest()),
            electionInit.config.chainConfirmationCodes,
            electionInit.config.configBaux0,
            electionInit.jointPublicKey,
            electionInit.extendedBaseHash,
            device,
            outputDir,
            "${outputDir}/invalidDir",
        )
        val ballotProvider = RandomBallotProvider(electionRecord.manifest())

        repeat(nballots) {
            val ballot = ballotProvider.makeBallot()
            val result = encryptor.encrypt(ballot, ErrorMessages("testOneWithChain"))
            assertNotNull(result)
            encryptor.submit(result.confirmationCode, EncryptedBallot.BallotState.CAST)
        }
        encryptor.close()

        checkOutput(outputDir, nballots, true)
    }

    @Test
    fun testCallMultipleTimesChaining() {
        val outputDir = "$testDir/testCallMultipleTimesChaining"
        val device = "device1"

        val electionRecord = readElectionRecord(input)
        val configWithChaining = electionRecord.config().copy(chainConfirmationCodes = true)
        val electionInit = electionRecord.electionInit()!!.copy(config = configWithChaining)

        val publisher = makePublisher(outputDir, true)
        publisher.writeElectionInitialized(electionInit)

        repeat(4) {
            val encryptor = AddEncryptedBallot(
                electionRecord.manifest(),
                BallotInputValidation(electionRecord.manifest()),
                electionInit.config.chainConfirmationCodes,
                electionInit.config.configBaux0,
                electionInit.jointPublicKey,
                electionInit.extendedBaseHash,
                device,
                outputDir,
                "outputDir/invalidDir",
            )
            val ballotProvider = RandomBallotProvider(electionRecord.manifest())

            repeat(nballots) {
                val ballot = ballotProvider.makeBallot()
                val result = encryptor.encrypt(ballot, ErrorMessages("testCallMultipleTimesChaining"))
                assertNotNull(result)
                encryptor.submit(result.confirmationCode, EncryptedBallot.BallotState.CAST)
            }
            encryptor.close()
        }

        checkOutput(outputDir, 4 * nballots, true)
    }

    @Test
    fun testMultipleDevicesChaining() {
        val outputDir = "$testDir/testMultipleDevicesChaining"

        val electionRecord = readElectionRecord( input)
        val configWithChaining = electionRecord.config().copy(chainConfirmationCodes = true)
        val electionInit = electionRecord.electionInit()!!.copy(config = configWithChaining)

        val publisher = makePublisher(outputDir, true)
        publisher.writeElectionInitialized(electionInit)

        repeat(3) {
            val encryptor = AddEncryptedBallot(
                electionRecord.manifest(),
                BallotInputValidation(electionRecord.manifest()),
                electionInit.config.chainConfirmationCodes,
                electionInit.config.configBaux0,
                electionInit.jointPublicKey,
                electionInit.extendedBaseHash,
                "device$it",
                outputDir,
                "$outputDir/invalidDir",
            )
            val ballotProvider = RandomBallotProvider(electionRecord.manifest())

            repeat(nballots) {
                val ballot = ballotProvider.makeBallot()
                val result = encryptor.encrypt(ballot, ErrorMessages("testMultipleDevicesChaining"))
                assertNotNull(result)
                encryptor.submit(result.confirmationCode, EncryptedBallot.BallotState.CAST)
            }
            encryptor.close()
        }

        checkOutput(outputDir, 3 * nballots, true)
    }
}

fun checkOutput(outputDir: String, expectedCount: Int, chained: Boolean) {
    val consumer = makeConsumer(outputDir)
    var count = 0
    consumer.iterateAllEncryptedBallots { true }.forEach {
        count++
    }
    assertEquals(expectedCount, count)

    if (chained) {
        consumer.encryptingDevices().forEach { device ->
            val chain = consumer.readEncryptedBallotChain(device).unwrap()
            var lastConfirmationCode: UInt256? = null
            consumer.iterateEncryptedBallots(device, null).forEach { eballot ->
                assertTrue(chain.ballotIds.contains(eballot.ballotId))
                lastConfirmationCode = eballot.confirmationCode
            }
            assertEquals(lastConfirmationCode, chain.lastConfirmationCode)
        }
    }

    val record = readElectionRecord(consumer)
    val verifyEncryptions = VerifyEncryptedBallots(
        consumer.group, record.manifest(),
        record.jointPublicKey()!!,
        record.extendedBaseHash()!!,
        record.config(), 1
    )

    val stats = Stats()
    val errs = ErrorMessages("verifyBallots")
    verifyEncryptions.verifyBallots(record.encryptedAllBallots { true }, errs, stats)
    println(errs)
    assertFalse(errs.hasErrors())
    assertEquals(expectedCount, stats.count())

    if (chained) {
        val chainErrs = ErrorMessages("verifyConfirmationChain")
        verifyEncryptions.verifyConfirmationChain(consumer, chainErrs)
        println(chainErrs)
        assertFalse(chainErrs.hasErrors())
    }
}