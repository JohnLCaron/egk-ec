package org.cryptobiotic.eg.cli

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import org.cryptobiotic.eg.input.ManifestInputValidation
import org.cryptobiotic.eg.publish.makePublisher
import kotlin.system.exitProcess

/** Create Test Manifest CLI. */
class RunCreateTestManifest {

    companion object {
        private val logger = KotlinLogging.logger("RunCreateTestManifest")

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
                description = "Directory to write test election manifest file"
            ).required()
            val noexit by parser.option(
                ArgType.Boolean,
                shortName = "noexit",
                description = "Dont call System.exit"
            ).default(false)

            parser.parse(args)

            println(
                "RunCreateTestManifest starting\n" +
                        "   nstyles= $nstyles\n" +
                        "   ncontests= $ncontests\n" +
                        "   nselections= $nselections\n" +
                        "   outputType= $outputType\n" +
                        "   output = $outputDir\n"
            )

            try {
                val manifest = if (nstyles == 1) buildTestManifest(ncontests, nselections)
                else buildTestManifest(nstyles, ncontests, nselections)

                val validator = ManifestInputValidation(manifest)
                val errs = validator.validate()
                if (errs.hasErrors()) {
                    logger.error{"failed $errs"}
                    if (!noexit) exitProcess(1)
                } else {
                    val publisher = makePublisher(outputDir, true)
                    publisher.writeManifest(manifest)
                    logger.info("ManifestInputValidation succeeded")
                }
            } catch (t: Throwable) {
                logger.error { "Exception= ${t.message} ${t.stackTraceToString()}" }
                if (!noexit) exitProcess(-1)
            }
        }
    }
}