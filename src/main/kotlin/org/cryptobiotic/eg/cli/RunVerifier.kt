package org.cryptobiotic.eg.cli

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import org.cryptobiotic.eg.publish.Consumer
import org.cryptobiotic.eg.publish.readElectionRecord
import org.cryptobiotic.eg.verifier.Verifier
import org.cryptobiotic.util.Stats
import org.cryptobiotic.util.Stopwatch

/**
 * Run election record verification CLI.
 */
class RunVerifier {

    companion object {
        private val logger = KotlinLogging.logger("RunVerifier")

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
            logger.info { "RunVerifier input= $inputDir" }

            runVerifier(inputDir, nthreads, showTime)
        }

        fun runVerifier(inputDir: String, nthreads: Int, showTime: Boolean = false): Boolean {
            val stopwatch = Stopwatch() // start timing here

            val electionRecord = readElectionRecord(inputDir)
            val verifier = Verifier(electionRecord, nthreads)
            val stats = Stats()
            val allOk = verifier.verify(stats, showTime)
            if (showTime) {
                stats.show(logger)
            }

            logger.debug { "${stopwatch.took()}" }
            logger.info { "verified = ${allOk}" }
            return allOk
        }

        // RunVerifier.runVerifier(group, consumerIn, 11, true)
        fun runVerifier(consumer: Consumer, nthreads: Int, showTime: Boolean = false): Boolean {
            val stopwatch = Stopwatch() // start timing here

            val electionRecord = readElectionRecord(consumer)
            val verifier = Verifier(electionRecord, nthreads)
            val stats = Stats()
            val allOk = verifier.verify(stats, showTime)
            if (showTime) {
                stats.show(logger)
            }

            logger.info { "RunVerifier ${stopwatch.took()} OK = ${allOk}" }
            return allOk
        }

        fun verifyEncryptedBallots(inputDir: String, nthreads: Int) {
            val stopwatch = Stopwatch() // start timing here

            val electionRecord = readElectionRecord(inputDir)
            val verifier = Verifier(electionRecord, nthreads)

            val stats = Stats()
            val errs = verifier.verifyEncryptedBallots(stats)
            logger.info { errs }
            stats.show(logger)

            logger.info { "VerifyEncryptedBallots ${stopwatch.took()}" }
        }

        fun verifyDecryptedTally(inputDir: String) {
            val stopwatch = Stopwatch() // start timing here

            val electionRecord = readElectionRecord(inputDir)
            val verifier = Verifier(electionRecord, 1)

            val decryptedTally = electionRecord.decryptedTally() ?: throw IllegalStateException("no decryptedTally ")
            val stats = Stats()
            val errs = verifier.verifyDecryptedTally(decryptedTally, stats)
            logger.info { errs }
            stats.show(logger)

            logger.info { "verifyDecryptedTally ${stopwatch.took()} " }
        }

        fun verifyChallengedBallots(inputDir: String) {
            val stopwatch = Stopwatch() // start timing here

            val electionRecord = readElectionRecord(inputDir)
            val verifier = Verifier(electionRecord, 1)

            val stats = Stats()
            val errs = verifier.verifySpoiledBallotTallies(stats)
            logger.info { errs }
            stats.show(logger)

            logger.info { "verifyRecoveredShares ${stopwatch.took()}" }
        }

        fun verifyTallyBallotIds(inputDir: String) {
            val electionRecord = readElectionRecord(inputDir)
            println("$inputDir stage=${electionRecord.stage()} ncast_ballots=${electionRecord.encryptedTally()!!.castBallotIds.size}")

            val verifier = Verifier(electionRecord, 1)
            val errs = verifier.verifyTallyBallotIds()
            logger.info { errs }
        }
    }
}
