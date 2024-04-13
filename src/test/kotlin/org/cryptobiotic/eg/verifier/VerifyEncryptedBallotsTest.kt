package org.cryptobiotic.eg.verifier

import org.cryptobiotic.eg.core.UInt256
import org.cryptobiotic.eg.publish.makeConsumer
import org.cryptobiotic.eg.publish.readElectionRecord
import org.cryptobiotic.util.ErrorMessages
import org.cryptobiotic.util.Stats
import kotlin.test.Test
import kotlin.test.assertTrue

class VerifyEncryptedBallotsTest {

    @Test
    fun testVerifyEncryptedBallotBadElectionId() {
        val inputDir = "src/test/data/encrypt/testBallotChain"
        val wrongDir = "src/test/data/workflow/allAvailableEc"

        val consumerIn = makeConsumer(inputDir)
        val record = readElectionRecord(consumerIn)

        val verifier = VerifyEncryptedBallots(
            consumerIn.group,
            record.manifest(),
            record.jointPublicKey()!!,
            record.extendedBaseHash()!!,
            record.config(), 1
        )

        val wrongConsumer = makeConsumer(wrongDir, consumerIn.group)
        val errs = ErrorMessages("wrongBallot")
        val wrongBallot = wrongConsumer.iterateAllEncryptedBallots { true }.iterator().next()

        verifier.verifyEncryptedBallot(wrongBallot, errs, Stats())
        println(errs)
        assertTrue(errs.hasErrors())
        assertTrue(errs.toString().contains("has wrong electionId"))
    }

    @Test
    fun testVerifyEncryptedBallotBadConfirmationCode() {
        val inputDir = "src/test/data/encrypt/testBallotChain"

        val consumerIn = makeConsumer(inputDir)
        val record = readElectionRecord(consumerIn)

        val verifier = VerifyEncryptedBallots(
            consumerIn.group,
            record.manifest(),
            record.jointPublicKey()!!,
            record.extendedBaseHash()!!,
            record.config(), 1
        )
        val eballot = consumerIn.iterateAllEncryptedBallots { true }.iterator().next()
        val badBallot = eballot.copy(confirmationCode = UInt256.random())
        val errs = ErrorMessages("wrongCC")
        verifier.verifyEncryptedBallot(badBallot, errs, Stats())
        println(errs)
        assertTrue(errs.hasErrors())
        assertTrue(errs.toString().contains("7.B. Incorrect ballot confirmation code"))
    }

    @Test
    fun testBadConfirmationChain() {
        val inputDir = "src/test/data/encrypt/testBallotChain"
        val wrongDir = "src/test/data/encrypt/testBallotNoChain"

        val consumerIn = makeConsumer(inputDir)
        val record = readElectionRecord(consumerIn)

        val verifier = VerifyEncryptedBallots(
            consumerIn.group,
            record.manifest(),
            record.jointPublicKey()!!,
            record.extendedBaseHash()!!,
            record.config(), 1
        )

        val wrongConsumer = makeConsumer(wrongDir, consumerIn.group)
        val errs = ErrorMessages("testBadConfirmationChain")
        verifier.verifyConfirmationChain(wrongConsumer, errs)
        println(errs)
        assertTrue(errs.hasErrors())
        assertTrue(errs.toString().contains("file does not exist"))
    }
}