package org.cryptobiotic.eg.cli

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import org.cryptobiotic.eg.core.getSystemDate
import org.cryptobiotic.eg.core.productionGroup
import org.cryptobiotic.eg.election.makeElectionConfig
import org.cryptobiotic.eg.publish.makePublisher
import org.cryptobiotic.eg.publish.readAndCheckManifest

/** Run Create Election Configuration CLI. */
class RunCreateElectionConfig {

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val parser = ArgParser("RunCreateElectionConfig")
            val electionManifest by parser.option(
                ArgType.String,
                shortName = "manifest",
                description = "Manifest file or directory (json or protobuf)"
            ).required()
            val groupName by parser.option(
                ArgType.String,
                shortName = "group",
                description = "Group name ('P-256' or 'Integer')"
            ).required()
            val nguardians by parser.option(
                ArgType.Int,
                shortName = "nguardians",
                description = "number of guardians"
            ).required()
            val quorum by parser.option(
                ArgType.Int,
                shortName = "quorum",
                description = "quorum size"
            ).required()
            val outputDir by parser.option(
                ArgType.String,
                shortName = "out",
                description = "Directory to write output ElectionInitialized record"
            ).required()
            val createdBy by parser.option(
                ArgType.String,
                shortName = "createdBy",
                description = "who created"
            ).default("RunCreateElectionConfig")
            val baux0 by parser.option(
                ArgType.String,
                shortName = "device",
                description = "device information, used for B_aux,0 from eq 58-60"
            ).default("device")
            val chainCodes by parser.option(
                ArgType.Boolean,
                shortName = "chainCodes",
                description = "chain confirmation codes"
            ).default(false)
            parser.parse(args)

            println(
                "RunCreateElectionConfig starting\n" +
                        "   manifest= $electionManifest\n" +
                        "   groupName= $groupName\n" +
                        "   nguardians= $nguardians\n" +
                        "   quorum= $quorum\n" +
                        "   output = $outputDir\n" +
                        "   createdBy = $createdBy\n" +
                        "   baux0 = $baux0\n" +
                        "   chainCodes = $chainCodes"
            )

            val group = productionGroup(groupName)

            val (isJson, _, manifestBytes) = readAndCheckManifest(electionManifest)

            // As input, either specify the input directory that contains electionConfig.protobuf file,
            // OR the election manifest, nguardians and quorum.
            val config =
                makeElectionConfig(
                    group.constants,
                    nguardians,
                    quorum,
                    manifestBytes,
                    chainCodes,
                    baux0.encodeToByteArray(),
                    mapOf(
                        Pair("CreatedBy", createdBy),
                        Pair("CreatedOn", getSystemDate()),
                    ),
                )

            val publisher = makePublisher(outputDir, true)
            publisher.writeElectionConfig(config)

            println("RunCreateElectionConfig success, outputType = ${if (isJson) "JSON" else "PROTO"}")
        }
    }
}
