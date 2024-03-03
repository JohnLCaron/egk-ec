package org.cryptobiotic.eg.cli

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.partition
import com.github.michaelbull.result.unwrap
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.required

import org.cryptobiotic.eg.core.*
import org.cryptobiotic.eg.decrypt.DecryptingTrusteeIF
import org.cryptobiotic.eg.decrypt.Decryptor
import org.cryptobiotic.eg.decrypt.Guardians
import org.cryptobiotic.eg.election.*
import org.cryptobiotic.eg.publish.*
import org.cryptobiotic.util.ErrorMessages
import org.cryptobiotic.util.Stopwatch
import org.cryptobiotic.util.mergeErrorMessages

/**
 * Run Trusted Tally Decryption CLI.
 * Read election record from inputDir, write to outputDir.
 * This has access to all the trustees, so is only used for testing, or in a use case of trust.
 * A version of this where each Trustee is in its own process space is implemented in the webapps modules.
 */
class RunTrustedTallyDecryption {

    companion object {
        private val logger = KotlinLogging.logger("RunTrustedTallyDecryption")

        @JvmStatic
        fun main(args: Array<String>) {
            val parser = ArgParser("RunTrustedTallyDecryption")
            val inputDir by parser.option(
                ArgType.String,
                shortName = "in",
                description = "Directory containing input election record"
            ).required()
            val trusteeDir by parser.option(
                ArgType.String,
                shortName = "trustees",
                description = "Directory to read private trustees"
            ).required()
            val outputDir by parser.option(
                ArgType.String,
                shortName = "out",
                description = "Directory to write output election record"
            ).required()
            val createdBy by parser.option(
                ArgType.String,
                shortName = "createdBy",
                description = "who created"
            )
            val missing by parser.option(
                ArgType.String,
                shortName = "missing",
                description = "missing guardians' xcoord, comma separated, eg '2,4'"
            )
            parser.parse(args)
            val startupInfo = "RunTrustedTallyDecryption starting   input= $inputDir   trustees= $trusteeDir   output = $outputDir"
            logger.info { startupInfo }

            try {
                runDecryptTally(
                    inputDir,
                    outputDir,
                    readDecryptingTrustees(inputDir, trusteeDir, missing),
                    createdBy
                )
            } catch (t: Throwable) {
                logger.error { "Exception= ${t.message} ${t.stackTraceToString()}" }
            }
        }

        fun readDecryptingTrustees(
            inputDir: String,
            trusteeDir: String,
            missing: String? = null
        ): List<DecryptingTrusteeIF> {
            val trusteeSource = makeConsumer(inputDir)
            val initResult = trusteeSource.readElectionInitialized()
            if (initResult is Err) {
                logger.error { initResult.error.toString() }
                return emptyList()
            }
            val init = initResult.unwrap()
            val readTrusteeResults : List<Result<DecryptingTrusteeIF, ErrorMessages>> = init.guardians.map { trusteeSource.readTrustee(trusteeDir, it.guardianId) }
            val (allTrustees, allErrors) = readTrusteeResults.partition()
            if (allErrors.isNotEmpty()) {
                logger.error { mergeErrorMessages("readDecryptingTrustees", allErrors).toString() }
                return emptyList()
            }
            if (missing.isNullOrEmpty()) {
                return allTrustees
            }
            // remove missing guardians
            val missingX = missing.split(",").map { it.toInt() }
            return allTrustees.filter { !missingX.contains(it.xCoordinate()) }
        }

        fun runDecryptTally(
            inputDir: String,
            outputDir: String,
            decryptingTrustees: List<DecryptingTrusteeIF>,
            createdBy: String?
        ) {
            val stopwatch = Stopwatch()

            val consumerIn = makeConsumer(inputDir)
            val result = consumerIn.readTallyResult()
            if (result is Err) {
                logger.error { "readTallyResult error ${result.error}" }
                return
            }
            val tallyResult = result.unwrap()
            val electionInit = tallyResult.electionInitialized

            val trusteeNames = decryptingTrustees.map { it.id() }.toSet()
            val missingGuardians =
                electionInit.guardians.filter { !trusteeNames.contains(it.guardianId) }.map { it.guardianId }
            logger.debug { "runDecryptTally ${outputDir} present = $trusteeNames missing = $missingGuardians" }
            val quorum = electionInit.config.quorum
            if (decryptingTrustees.size < quorum) {
                logger.error { " encryptedTally.decrypt $inputDir does not have a quorum=${quorum}, only ${electionInit.config.quorum} guardians" }
                return
            }

            val guardians = Guardians(consumerIn.group, electionInit.guardians)
            val decryptor = Decryptor(
                consumerIn.group,
                electionInit.extendedBaseHash,
                electionInit.jointPublicKey(),
                guardians,
                decryptingTrustees,
            )

            val errs = ErrorMessages("RunTrustedTallyDecryption")
            try {
                val decryptedTally = with(decryptor) { tallyResult.encryptedTally.decrypt(errs) }
                if (decryptedTally == null) {
                    logger.error { " encryptedTally.decrypt $inputDir has error=${errs}" }
                    return
                }
                val publisher = makePublisher(outputDir, false)
                publisher.writeDecryptionResult(
                    DecryptionResult(
                        tallyResult,
                        decryptedTally,
                        mapOf(
                            Pair("CreatedBy", createdBy ?: "RunTrustedTallyDecryption"),
                            Pair("CreatedOn", getSystemDate()),
                            Pair("CreatedFromDir", inputDir)
                        )
                    )
                )
            } catch (t: Throwable) {
                errs.add("Exception= ${t.message} ${t.stackTraceToString()}")
                logger.error { errs }
            }

            logger.info { "DecryptTally ${stopwatch.took()}" }
        }
    }
}
