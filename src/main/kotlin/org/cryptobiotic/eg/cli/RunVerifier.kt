package org.cryptobiotic.eg.cli

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import org.cryptobiotic.eg.publish.readElectionRecord
import org.cryptobiotic.eg.verifier.Verifier
import org.cryptobiotic.util.ErrorMessages
import org.cryptobiotic.util.Stats
import org.cryptobiotic.util.Stopwatch
import kotlin.system.exitProcess

/** Run election record verification CLI. */
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
            val noexit by parser.option(
                ArgType.Boolean,
                shortName = "noexit",
                description = "Dont call System.exit"
            ).default(false)

            parser.parse(args)

            logger.info { "RunVerifier input= $inputDir" }

            try {
                val retval = runVerifier(inputDir, nthreads, showTime)
                if (!noexit && retval != 0) exitProcess(retval)
            } catch (t: Throwable) {
                logger.error { "Exception= ${t.message} ${t.stackTraceToString()}" }
                if (!noexit) exitProcess(-1)
            }
        }

        fun runVerifier(inputDir: String, nthreads: Int, showTime: Boolean = false): Int {
            val stopwatch = Stopwatch() // start timing here

            val electionRecord = readElectionRecord(inputDir)
            val verifier = Verifier(electionRecord, nthreads)
            val stats = Stats()
            val allOk = verifier.verify(stats, showTime)
            if (showTime) {
                stats.show(logger)
            }

            logger.debug { "${stopwatch.took()}" }
            logger.info { "$inputDir verified = ${allOk}" }
            return if (allOk) 0 else 1
        }

        fun verifyEncryptedBallots(inputDir: String, nthreads: Int): Result<Boolean, ErrorMessages> {
            val stopwatch = Stopwatch() // start timing here

            val electionRecord = readElectionRecord(inputDir)
            val verifier = Verifier(electionRecord, nthreads)

            val stats = Stats()
            val errs = verifier.verifyEncryptedBallots(stats)
            logger.info { errs }
            stats.show(logger)

            logger.info { "VerifyEncryptedBallots ${stopwatch.took()}" }
            return if (errs.hasErrors()) Err(errs) else Ok(true)
        }

        fun verifyDecryptedTally(inputDir: String): Result<Boolean, ErrorMessages> {
            val stopwatch = Stopwatch() // start timing here

            val electionRecord = readElectionRecord(inputDir)
            val verifier = Verifier(electionRecord, 1)

            val decryptedTally = electionRecord.decryptedTally() ?: throw IllegalStateException("no decryptedTally ")
            val stats = Stats()
            val errs = verifier.verifyDecryptedTally(decryptedTally, stats)
            logger.info { errs }
            stats.show(logger)

            logger.info { "verifyDecryptedTally ${stopwatch.took()} " }
            return if (errs.hasErrors()) Err(errs) else Ok(true)
        }

        fun verifyChallengedBallots(inputDir: String): Result<Boolean, ErrorMessages> {
            val stopwatch = Stopwatch() // start timing here

            val electionRecord = readElectionRecord(inputDir)
            val verifier = Verifier(electionRecord, 1)

            val stats = Stats()
            val errs = verifier.verifyChallengedBallots(stats)
            logger.info { errs }
            stats.show(logger)

            logger.info { "verifyRecoveredShares ${stopwatch.took()}" }
            return if (errs.hasErrors()) Err(errs) else Ok(true)
        }

        fun verifyTallyBallotIds(inputDir: String): Result<Boolean, ErrorMessages> {
            val electionRecord = readElectionRecord(inputDir)
            // println("$inputDir stage=${electionRecord.stage()} ncast_ballots=${electionRecord.encryptedTally()!!.castBallotIds.size}")

            val verifier = Verifier(electionRecord, 1)
            val errs = verifier.verifyTallyBallotIds()
            logger.info { errs }
            return if (errs.hasErrors()) Err(errs) else Ok(true)
        }
    }
}
