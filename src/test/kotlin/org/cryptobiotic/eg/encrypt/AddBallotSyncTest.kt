package org.cryptobiotic.eg.encrypt

import org.cryptobiotic.eg.election.*
import org.cryptobiotic.eg.input.RandomBallotProvider
import org.cryptobiotic.eg.publish.makePublisher
import org.cryptobiotic.eg.publish.readElectionRecord
import org.cryptobiotic.util.ErrorMessages
import kotlin.test.*

class AddBallotSyncTest {
    val inputJson = "src/test/data/workflow/allAvailableEc"
    val outputDirTop = "testOut/encrypt/AddBallotSyncTest"
    val nballots = 4

    @Test
    fun testJsonSyncNoChain() {
        val outputDir = "$outputDirTop/testJsonSyncNoChain"
        val device = "device1"

        val electionRecord = readElectionRecord(inputJson)
        val electionInit = electionRecord.electionInit()!!
        val publisher = makePublisher(outputDir, true)
        publisher.writeElectionInitialized(electionInit)

        val encryptor = AddEncryptedBallot(
            electionRecord.manifest(),
            electionInit.config.chainConfirmationCodes,
            electionInit.config.configBaux0,
            electionInit.jointPublicKey(),
            electionInit.extendedBaseHash,
            device,
            outputDir,
            "outputDir/invalidDir",
            isJson = publisher.isJson(),
        )
        val ballotProvider = RandomBallotProvider(electionRecord.manifest())

        repeat(3) {
            repeat(nballots) {
                val ballot = ballotProvider.makeBallot()
                val result = encryptor.encrypt(ballot, ErrorMessages("testJsonSyncNoChain"))
                assertNotNull(result)
                encryptor.submit(result.confirmationCode, EncryptedBallot.BallotState.CAST)
            }
            encryptor.sync()
        }
        encryptor.close()

        checkOutput(outputDir, 3 * nballots, false)

        // should fail once closed
        val ballot = ballotProvider.makeBallot()
        val errs = ErrorMessages("")
        encryptor.encrypt(ballot, errs)
        assertTrue(errs.hasErrors())
        assertContains(errs.toString(), "Trying to add ballot after chain has been closed")
    }

    @Test
    fun testJsonSyncChain() {
        val outputDir = "$outputDirTop/testJsonSyncChain"
        val device = "device1"

        val electionRecord = readElectionRecord(inputJson)
        val configWithChaining = electionRecord.config().copy(chainConfirmationCodes = true)
        val electionInit = electionRecord.electionInit()!!.copy(config = configWithChaining)

        val publisher = makePublisher(outputDir, true)
        publisher.writeElectionInitialized(electionInit)

        val encryptor = AddEncryptedBallot(
            electionRecord.manifest(),
            electionInit.config.chainConfirmationCodes,
            electionInit.config.configBaux0,
            electionInit.jointPublicKey(),
            electionInit.extendedBaseHash,
            device,
            outputDir,
            "outputDir/invalidDir",
            isJson = publisher.isJson(),
        )
        val ballotProvider = RandomBallotProvider(electionRecord.manifest())

        repeat(3) {
            repeat(nballots) {
                val ballot = ballotProvider.makeBallot()
                val result = encryptor.encrypt(ballot, ErrorMessages("testJsonSyncChain"))
                assertNotNull(result)
                encryptor.submit(result.confirmationCode, EncryptedBallot.BallotState.CAST)
            }
            encryptor.sync()
        }
        encryptor.close()

        checkOutput(outputDir, 3 * nballots, true)

        // should fail once closed
        val ballot = ballotProvider.makeBallot()
        val errs = ErrorMessages("")
        encryptor.encrypt(ballot, errs)
        assertTrue(errs.hasErrors())
        assertContains(errs.toString(), "Trying to add ballot after chain has been closed")
    }

}