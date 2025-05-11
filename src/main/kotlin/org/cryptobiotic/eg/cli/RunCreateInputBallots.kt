package org.cryptobiotic.eg.cli

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import org.cryptobiotic.eg.election.PlaintextBallot
import org.cryptobiotic.eg.input.RandomBallotProvider
import org.cryptobiotic.eg.publish.makePublisher
import org.cryptobiotic.eg.publish.readAndCheckManifest
import kotlin.system.exitProcess

/** Run Create Input Ballots CLI. */
class RunCreateInputBallots {

    companion object {
        private val logger = KotlinLogging.logger("RunCreateInputBallots")

        @JvmStatic
        fun main(args: Array<String>) {
            val parser = ArgParser("RunCreateInputBallots")
            val manifestDirOrFile by parser.option(
                ArgType.String,
                shortName = "manifest",
                description = "Election manifest file or directory"
            ).required()
            val outputDir by parser.option(
                ArgType.String,
                shortName = "out",
                description = "Directory to write plaintext ballots"
            ).required()
            val nballots by parser.option(
                ArgType.Int,
                shortName = "n",
                description = "Number of ballots to generate"
            ).default(11)
            val noexit by parser.option(
                ArgType.Boolean,
                shortName = "noexit",
                description = "Dont call System.exit"
            ).default(false)

            parser.parse(args)

            println(
                "RunCreateInputBallots\n" +
                        "  electionManifest = '$manifestDirOrFile'\n" +
                        "  outputDir = '$outputDir'\n" +
                        "  nballots = '$nballots'\n"
            )

            try {
                val (_, manifest, _) = readAndCheckManifest(manifestDirOrFile)
                val publisher = makePublisher(outputDir, true)

                val ballots = mutableListOf<PlaintextBallot>()
                val ballotProvider = RandomBallotProvider(manifest)
                repeat(nballots) {
                    ballots.add(ballotProvider.makeBallot())
                }
                publisher.writePlaintextBallot(outputDir, ballots)

            } catch (t: Throwable) {
                logger.error { "Exception= ${t.message} ${t.stackTraceToString()}" }
                if (!noexit) exitProcess(-1)
            }
        }
    }
}