package org.cryptobiotic.eg.cli

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import org.cryptobiotic.eg.input.ManifestInputValidation
import org.cryptobiotic.eg.publish.makePublisher

/** Create Test Manifest CLI. */
class RunCreateTestManifest {

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val parser = ArgParser("RunCreateTestManifest")
            val nstyles by parser.option(
                ArgType.Int,
                shortName = "nstyles",
                description = "number of ballot styles"
            ).default(3)
            val ncontests by parser.option(
                ArgType.Int,
                shortName = "ncontests",
                description = "number of contests"
            ).required()
            val nselections by parser.option(
                ArgType.Int,
                shortName = "nselections",
                description = "number of selections per contest"
            ).required()
            val outputType by parser.option(
                ArgType.String,
                shortName = "type",
                description = "JSON or PROTO"
            ).default("JSON")
            val outputDir by parser.option(
                ArgType.String,
                shortName = "out",
                description = "Directory to write test manifest file"
            ).required()
            parser.parse(args)

            println(
                "RunCreateTestManifest starting\n" +
                        "   nstyles= $nstyles\n" +
                        "   ncontests= $ncontests\n" +
                        "   nselections= $nselections\n" +
                        "   outputType= $outputType\n" +
                        "   output = $outputDir\n"
            )

            val manifest = if (nstyles == 1) buildTestManifest(ncontests, nselections)
                           else buildTestManifest(nstyles, ncontests, nselections)


            val validator = ManifestInputValidation(manifest)
            val errs = validator.validate()
            if (errs.hasErrors()) {
                println("failed $errs")
            } else {
                val publisher = makePublisher(outputDir, true)
                publisher.writeManifest(manifest)
                println("ManifestInputValidation succeeded")
            }
        }
    }
}