package org.cryptobiotic.eg.cli

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import org.cryptobiotic.eg.election.PlaintextBallot
import org.cryptobiotic.eg.input.RandomBallotProvider
import org.cryptobiotic.eg.publish.makePublisher
import org.cryptobiotic.eg.publish.readAndCheckManifest

/** Run Create Election Configuration CLI. */
class RunCreateInputBallots {

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val parser = ArgParser("RunCreateInputBallots")
            val manifestDirOrFile by parser.option(
                ArgType.String,
                shortName = "manifest",
                description = "Manifest file or directory (json or protobuf)"
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
            val isJson by parser.option(
                ArgType.Boolean,
                shortName = "json",
                description = "Generate Json ballots (default to manifest type)"
            )
            parser.parse(args)

            println(
                "RunCreateInputBallots\n" +
                        "  electionManifest = '$manifestDirOrFile'\n" +
                        "  outputDir = '$outputDir'\n" +
                        "  nballots = '$nballots'\n" +
                        "  isJson = '$isJson'\n"
            )

            val (manifestIsJson, manifest, _) = readAndCheckManifest(manifestDirOrFile)
            val useJson = isJson ?: manifestIsJson
            val publisher = makePublisher(outputDir, true)

            val ballots = mutableListOf<PlaintextBallot>()
            val ballotProvider = RandomBallotProvider(manifest)
            repeat(nballots) {
                ballots.add(ballotProvider.makeBallot())
            }

            publisher.writePlaintextBallot(outputDir, ballots)
        }
    }
}