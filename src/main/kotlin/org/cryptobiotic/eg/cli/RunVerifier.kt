package org.cryptobiotic.eg.cli

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import kotlin.math.roundToInt

import org.cryptobiotic.eg.core.*
import org.cryptobiotic.eg.core.Base16.toHex
import org.cryptobiotic.eg.core.intgroup.productionGroup
import org.cryptobiotic.eg.decrypt.DecryptingTrusteeIF
import org.cryptobiotic.eg.election.*
import org.cryptobiotic.eg.publish.Consumer
import org.cryptobiotic.eg.publish.ElectionRecord
import org.cryptobiotic.eg.publish.makeConsumer
import org.cryptobiotic.eg.publish.readElectionRecord
import org.cryptobiotic.eg.verifier.Verifier
import org.cryptobiotic.util.ErrorMessages
import org.cryptobiotic.util.Stats
import org.cryptobiotic.util.Stopwatch

/**
 * Run election record verification CLI.
 */
class RunVerifier {

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val parser = ArgParser("RunVerifier")
            val inputDir by parser.option(
                ArgType.String,
                shortName = "in",
                description = "Directory containing input election record"
            ).required()
            val nthreads by parser.option(
                ArgType.Int,
                shortName = "nthreads",
                description = "Number of parallel threads to use"
            ).default(11)
            val showTime by parser.option(
                ArgType.Boolean,
                shortName = "time",
                description = "Show timing"
            ).default(false)
            parser.parse(args)
            println("RunVerifier starting\n   input= $inputDir")

            runVerifier(productionGroup(), inputDir, nthreads, showTime)
        }

        fun runVerifier(group: GroupContext, inputDir: String, nthreads: Int, showTime: Boolean = false): Boolean {
            val stopwatch = Stopwatch() // start timing here

            val electionRecord = readElectionRecord(group, inputDir)
            val verifier = Verifier(electionRecord, nthreads)
            val stats = Stats()
            val allOk = verifier.verify(stats, showTime)
            if (showTime) {
                stats.show()
            }

            println("RunVerifier ${stopwatch.took()} OK = ${allOk}")
            return allOk
        }

        // RunVerifier.runVerifier(group, consumerIn, 11, true)
        fun runVerifier(group: GroupContext, consumer: Consumer, nthreads: Int, showTime: Boolean = false): Boolean {
            val stopwatch = Stopwatch() // start timing here

            val electionRecord = readElectionRecord(consumer)
            val verifier = Verifier(electionRecord, nthreads)
            val stats = Stats()
            val allOk = verifier.verify(stats, showTime)
            if (showTime) {
                stats.show()
            }

            println("RunVerifier ${stopwatch.took()} OK = ${allOk}")
            return allOk
        }

        fun verifyEncryptedBallots(group: GroupContext, inputDir: String, nthreads: Int) {
            val stopwatch = Stopwatch() // start timing here

            val electionRecord = readElectionRecord(group, inputDir)
            val verifier = Verifier(electionRecord, nthreads)

            val stats = Stats()
            val errs = verifier.verifyEncryptedBallots(stats)
            println(errs)
            stats.show()

            println("VerifyEncryptedBallots ${stopwatch.took()}")
        }

        fun verifyDecryptedTally(group: GroupContext, inputDir: String) {
            val stopwatch = Stopwatch() // start timing here

            val electionRecord = readElectionRecord(group, inputDir)
            val verifier = Verifier(electionRecord, 1)

            val decryptedTally = electionRecord.decryptedTally() ?: throw IllegalStateException("no decryptedTally ")
            val stats = Stats()
            val errs = verifier.verifyDecryptedTally(decryptedTally, stats)
            println(errs)
            stats.show()

            println("verifyDecryptedTally ${stopwatch.took()} ")
        }

        fun verifyChallengedBallots(group: GroupContext, inputDir: String) {
            val stopwatch = Stopwatch() // start timing here

            val electionRecord = readElectionRecord(group, inputDir)
            val verifier = Verifier(electionRecord, 1)

            val stats = Stats()
            val errs = verifier.verifySpoiledBallotTallies(stats)
            stats.show()
            println(errs)

            println("verifyRecoveredShares ${stopwatch.took()}")
        }

        fun verifyTallyBallotIds(group: GroupContext, inputDir: String) {
            val electionRecord = readElectionRecord(group, inputDir)
            println("$inputDir stage=${electionRecord.stage()} ncast_ballots=${electionRecord.encryptedTally()!!.castBallotIds.size}")

            val verifier = Verifier(electionRecord, 1)
            val errs = verifier.verifyTallyBallotIds()
            println(errs)
        }
    }
}
