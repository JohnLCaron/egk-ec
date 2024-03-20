package org.cryptobiotic.eg.encrypt

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.unwrap
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
import org.cryptobiotic.util.testOut

class AddEncryptedBallotTest {
    val input = "src/test/data/workflow/allAvailableEc"
    val outputDir = "$testOut/encrypt/addEncryptedBallot"
    val nballots = 4

    @Test
    fun testJustOne() {
        val outputDir = "$outputDir/testJustOne"
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
            isJson = publisher.isJson(),
        )
        val ballotProvider = RandomBallotProvider(electionRecord.manifest())

        repeat(nballots) {
            val ballot = ballotProvider.makeBallot()
            val result = encryptor.encrypt(ballot, ErrorMessages("testJustOne"))
            assertNotNull(result)
            encryptor.submit(result.confirmationCode, EncryptedBallot.BallotState.CAST)
        }
        encryptor.close()

        checkOutput(outputDir, nballots, electionInit.config.chainConfirmationCodes)
    }

    @Test
    fun testEncryptAndCast() {
        val outputDir = "$outputDir/testEncryptAndCast"
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
            isJson = publisher.isJson(),
        )
        val ballotProvider = RandomBallotProvider(electionRecord.manifest())

        repeat(nballots) {
            val ballot = ballotProvider.makeBallot()
            val result = encryptor.encryptAndCast(ballot, ErrorMessages("testEncryptAndCast"))
            assertNotNull(result)
            assertTrue(encryptor.submit(result.confirmationCode, EncryptedBallot.BallotState.CAST) is Err)
        }
        encryptor.close()

        checkOutput(outputDir, nballots, electionInit.config.chainConfirmationCodes)
    }

    @Test
    fun testEncryptAndCastNoWrite() {
        val outputDir = "$outputDir/testEncryptAndCastNoWrite"
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
            isJson = publisher.isJson(),
        )
        val ballotProvider = RandomBallotProvider(electionRecord.manifest())

        repeat(nballots) {
            val ballot = ballotProvider.makeBallot()
            val result = encryptor.encryptAndCast(ballot, ErrorMessages("testEncryptAndCastNoWrite"), false)
            assertNotNull(result)
            assertTrue(encryptor.submit(result.confirmationCode, EncryptedBallot.BallotState.CAST) is Err)
        }
        encryptor.close()

        // TODO make sure no ballots were written
    }

    @Test
    fun testCallMultipleTimes() {
        val outputDir = "$outputDir/testCallMultipleTimes"
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
                isJson = publisher.isJson(),
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
        val outputDir = "$outputDir/testMultipleDevices"

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
                isJson = publisher.isJson(),
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
        val outputDir = "$outputDir/testOneWithChain"
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
            isJson = publisher.isJson(),
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
        val outputDir = "$outputDir/testCallMultipleTimesChaining"
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
                isJson = publisher.isJson(),
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
        val outputDir = "$outputDir/testMultipleDevicesChaining"

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
                isJson = publisher.isJson(),
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
        verifyEncryptions.verifyConfirmationChain(record, chainErrs)
        println(chainErrs)
        assertFalse(chainErrs.hasErrors())
    }
}