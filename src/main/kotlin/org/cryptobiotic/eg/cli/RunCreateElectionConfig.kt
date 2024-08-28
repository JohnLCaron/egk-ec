package org.cryptobiotic.eg.cli

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import org.cryptobiotic.eg.core.getSystemDate
import org.cryptobiotic.eg.core.productionGroup
import org.cryptobiotic.eg.election.makeElectionConfig
import org.cryptobiotic.eg.publish.makePublisher
import org.cryptobiotic.eg.publish.readAndCheckManifest
import kotlin.system.exitProcess

/** Run Create Election Configuration CLI. */
class RunCreateElectionConfig {

    companion object {
        private val logger = KotlinLogging.logger("RunCreateElectionConfig")

        @JvmStatic
        fun main(args: Array<String>) {
            val parser = ArgParser("RunCreateElectionConfig")
            val electionManifest by parser.option(
                ArgType.String,
                shortName = "manifest",
                description = "Election manifest file or directory"
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
            )
            val chainCodes by parser.option(
                ArgType.Boolean,
                shortName = "chainCodes",
                description = "chain confirmation codes"
            ).default(false)
            val noexit by parser.option(
                ArgType.Boolean,
                shortName = "noexit",
                description = "Dont call System.exit"
            ).default(false)
            parser.parse(args)

            val startupInfo = "starting" +
                        "\n   manifest= $electionManifest" +
                        "\n   groupName= $groupName" +
                        "\n   nguardians= $nguardians" +
                        "\n   quorum= $quorum" +
                        "\n   output = $outputDir" +
                        "\n   createdBy = $createdBy" +
                        "\n   baux0 = $baux0" +
                        "\n   chainCodes = $chainCodes"
            logger.info { startupInfo }

            try {
                val group = productionGroup(groupName)

                val (_, _, manifestBytes) = readAndCheckManifest(electionManifest)

                // As input, either specify the election record directory,
                // OR the election manifest, nguardians and quorum.
                val config =
                    makeElectionConfig(
                        group.constants,
                        nguardians,
                        quorum,
                        manifestBytes,
                        chainCodes,
                        baux0?.encodeToByteArray() ?: ByteArray(0), // use empty ByteArray if not specified
                        mapOf(
                            Pair("CreatedBy", createdBy),
                            Pair("CreatedOn", getSystemDate()),
                        ),
                    )

                val publisher = makePublisher(outputDir, true)
                publisher.writeElectionConfig(config)

                logger.info { "success" }
            } catch (t: Throwable) {
                if (!noexit) exitProcess(-1)
            }
        }
    }
}
