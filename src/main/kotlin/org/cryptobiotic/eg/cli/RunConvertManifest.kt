package org.cryptobiotic.eg.cli

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.required
import org.cryptobiotic.eg.publish.makePublisher
import org.cryptobiotic.eg.publish.readAndCheckManifest

/** Convert Manifest CLI. */
class RunConvertManifest {

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val parser = ArgParser("RunConvertManifest")
            val electionManifest by parser.option(
                ArgType.String,
                shortName = "manifest",
                description = "Input manifest file or directory (json or protobuf)"
            ).required()
            val outputDir by parser.option(
                ArgType.String,
                shortName = "out",
                description = "Directory to write output Manifest"
            ).required()
            parser.parse(args)

            println(
                "RunConvertManifest starting\n" +
                        "   manifest= $electionManifest\n" +
                        "   output = $outputDir\n"
            )

            val (isJson, manifest, _) = readAndCheckManifest(electionManifest)

            val publisher = makePublisher(outputDir, true)

            publisher.writeManifest(manifest)

            println("RunConvertManifest success, outputType = ${if (!isJson) "JSON" else "PROTO"}")
        }
    }
}