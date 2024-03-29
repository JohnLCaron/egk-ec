package org.cryptobiotic.eg.publish.json

import org.cryptobiotic.eg.core.ElGamalPublicKey
import org.cryptobiotic.eg.publish.makeConsumer
import org.cryptobiotic.eg.publish.readElectionRecord
import org.cryptobiotic.eg.verifier.VerifyEncryptedBallots
import org.cryptobiotic.util.ErrorMessages
import java.nio.file.Path
import java.util.function.Predicate
import kotlin.test.assertFalse

class ReadIteratorsTest {

    fun verifyOutput(inputDir: String, ballotDir: String, chained: Boolean = false) {
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