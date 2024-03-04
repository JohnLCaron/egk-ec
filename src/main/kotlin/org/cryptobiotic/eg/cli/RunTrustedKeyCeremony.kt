package org.cryptobiotic.eg.cli

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.unwrap
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required

import org.cryptobiotic.eg.core.*
import org.cryptobiotic.eg.election.*
import org.cryptobiotic.eg.keyceremony.KeyCeremonyTrustee
import org.cryptobiotic.eg.keyceremony.keyCeremonyExchange
import org.cryptobiotic.eg.publish.*
import org.cryptobiotic.util.Stopwatch

/**
 * Run KeyCeremony CLI.
 * This has access to all the trustees, so is only used for testing, or in a use case of trust.
 * A version of this where each Trustee is in its own process space is implemented in the webapps modules.
 */
class RunTrustedKeyCeremony {

    companion object {
        private val logger = KotlinLogging.logger("RunTrustedKeyCeremony")

        @JvmStatic
        fun main(args: Array<String>) {
            val parser = ArgParser("RunTrustedKeyCeremony")
            val inputDir by parser.option(
                ArgType.String,
                shortName = "in",
                description = "Directory containing input election record"
            ).required()
            val trusteeDir by parser.option(
                ArgType.String,
                shortName = "trustees",
                description = "Directory to write private trustees"
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
            ).default("RunTrustedKeyCeremony")
            parser.parse(args)

            val startupInfo = "starting\n   input= $inputDir\n   trustees= $trusteeDir\n   output = $outputDir"
            logger.info { startupInfo }

            val consumerIn = makeConsumer(inputDir)
            val configResult = consumerIn.readElectionConfig()
            if (configResult is Err) {
                logger.error {"readElectionConfig error ${configResult.error}"}
                return
            }
            val config = configResult.unwrap()

            try {
                val result = runKeyCeremony(
                    consumerIn.group,
                    inputDir,
                    config,
                    outputDir,
                    trusteeDir,
                    consumerIn.isJson(),
                    createdBy
                )
                logger.info {"result = $result"}
                require(result is Ok)

            } catch (t: Throwable) {
                logger.error{ "Exception= ${t.message} ${t.stackTraceToString()}" }
            }
        }

        fun runKeyCeremony(
            group: GroupContext,
            createdFrom: String,
            config: ElectionConfig,
            outputDir: String,
            trusteeDir: String,
            isJson: Boolean,
            createdBy: String?
        ): Result<Boolean, String> {
            // Generate all KeyCeremonyTrustees here, which means this is a trusted situation.
            val trustees: List<KeyCeremonyTrustee> = List(config.numberOfGuardians) {
                val seq = it + 1
                KeyCeremonyTrustee(group, "trustee$seq", seq, nguardians = config.numberOfGuardians, quorum = config.quorum )
            }

            val exchangeResult = keyCeremonyExchange(trustees)
            if (exchangeResult is Err) {
                return exchangeResult
            }
            val keyCeremonyResults = exchangeResult.unwrap()
            val electionInitialized = keyCeremonyResults.makeElectionInitialized(
                config,
                mapOf(
                    Pair("CreatedBy", createdBy ?: "runKeyCeremony"),
                    Pair("CreatedFrom", createdFrom),
                )
            )

            val publisher = makePublisher(outputDir, false)
            publisher.writeElectionInitialized(electionInitialized)

            // store the trustees in some private place.
            val trusteePublisher : Publisher = makePublisher(trusteeDir, false)
            trustees.forEach { trusteePublisher.writeTrustee(trusteeDir, it) }

            return Ok(true)
        }
    }
}
