package org.cryptobiotic.eg.cli

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.unwrap
import org.cryptobiotic.eg.election.EncryptedTally
import org.cryptobiotic.eg.election.TallyResult
import org.cryptobiotic.eg.core.GroupContext
import org.cryptobiotic.eg.core.getSystemDate
import org.cryptobiotic.eg.publish.makeConsumer
import org.cryptobiotic.eg.publish.makePublisher
import org.cryptobiotic.eg.tally.AccumulateTally
import org.cryptobiotic.util.ErrorMessages
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.required
import org.cryptobiotic.eg.core.intgroup.productionGroup
import org.cryptobiotic.util.Stopwatch

/**
 * Run tally accumulation CLI.
 * Read election record from inputDir, write to outputDir.
 */
class RunAccumulateTally {

    companion object {
        private val logger = KotlinLogging.logger("RunAccumulateTally")

        @JvmStatic
        fun main(args: Array<String>) {
            val parser = ArgParser("RunAccumulateTally")
            val inputDir by parser.option(
                ArgType.String,
                shortName = "in",
                description = "Directory containing input ElectionInitialized record and encrypted ballots"
            ).required()
            val outputDir by parser.option(
                ArgType.String,
                shortName = "out",
                description = "Directory to write output election record"
            ).required()
            val encryptDir by parser.option(
                ArgType.String,
                shortName = "eballots",
                description = "Read encrypted ballots here (optional)"
            )
            val name by parser.option(
                ArgType.String,
                shortName = "name",
                description = "Name of tally"
            )
            val createdBy by parser.option(
                ArgType.String,
                shortName = "createdBy",
                description = "who created"
            )
            parser.parse(args)

            println("RunAccumulateTally starting\n" +
                    "   input= $inputDir\n" +
                    "   outputDir = $outputDir\n" +
                    "   encryptDir = $encryptDir")

            val group = productionGroup()
            try {
                runAccumulateBallots(
                    group,
                    inputDir,
                    outputDir,
                    encryptDir,
                    name ?: "RunAccumulateTally",
                    createdBy ?: "RunAccumulateTally"
                )
            } catch (t: Throwable) {
                logger.error { "Exception= ${t.message} ${t.stackTraceToString()}" }
                t.printStackTrace()
            }
        }

        fun runAccumulateBallots(
            group: GroupContext,
            inputDir: String,
            outputDir: String,
            encryptDir: String?,
            name: String,
            createdBy: String
        ) {
            val stopwatch = Stopwatch()

            val consumerIn = makeConsumer(group, inputDir)
            val initResult = consumerIn.readElectionInitialized()
            if (initResult is Err) {
                println("readElectionInitialized error ${initResult.error}")
                return
            }
            val electionInit = initResult.unwrap()
            val manifest = consumerIn.makeManifest(electionInit.config.manifestBytes)

            var countBad = 0
            var countOk = 0
            val accumulator = AccumulateTally(group, manifest, name, electionInit.extendedBaseHash, electionInit.jointPublicKey())
            val encryptedBallots = if (encryptDir == null) consumerIn.iterateAllCastBallots() else consumerIn.iterateEncryptedBallotsFromDir(encryptDir, null)
            for (encryptedBallot in encryptedBallots) {
                val errs = ErrorMessages("RunAccumulateTally ballotId=${encryptedBallot.ballotId}")
                accumulator.addCastBallot(encryptedBallot, errs)
                if (errs.hasErrors()) {
                    println(errs)
                    logger.error{ errs.toString() }
                    countBad++
                } else {
                    countOk++
                }
            }
            val tally: EncryptedTally = accumulator.build()

            val publisher = makePublisher(outputDir, false, consumerIn.isJson())
            publisher.writeTallyResult(
                TallyResult(
                    electionInit, tally, listOf(name),
                    mapOf(
                        Pair("CreatedBy", createdBy),
                        Pair("CreatedOn", getSystemDate()),
                        Pair("CreatedFromDir", inputDir)
                    )
                )
            )

            println("AccumulateTally processed $countOk good ballots, $countBad bad ballots, ${stopwatch.tookPer(countOk, "good ballot")}")
        }
    }
}
