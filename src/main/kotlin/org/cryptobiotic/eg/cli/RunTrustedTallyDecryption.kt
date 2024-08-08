package org.cryptobiotic.eg.cli

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.partition
import com.github.michaelbull.result.unwrap
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required

import org.cryptobiotic.eg.core.*
import org.cryptobiotic.eg.decrypt.DecryptingTrusteeIF
import org.cryptobiotic.eg.decrypt.Guardians
import org.cryptobiotic.eg.decrypt.TallyDecryptor
import org.cryptobiotic.eg.election.*
import org.cryptobiotic.eg.publish.*
import org.cryptobiotic.util.ErrorMessages
import org.cryptobiotic.util.Stopwatch
import org.cryptobiotic.util.mergeErrorMessages
import kotlin.system.exitProcess

/**
 * Run Trusted Tally Decryption CLI.
 * Read election record from inputDir, write to outputDir.
 * This has access to all the trustees, so is only used for testing, or in a use case of trust.
 * A version of this where each Trustee is in its own process space is implemented in egk-webapps.
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
            val encryptedTallyFile by parser.option(
                ArgType.String,
                shortName = "encryptedTally",
                description = "encryptedTally file (if different from the one in the election record)"
            )
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
            val noexit by parser.option(
                ArgType.Boolean,
                shortName = "noexit",
                description = "Dont call System.exit"
            ).default(false)

            parser.parse(args)

            val startupInfo = "starting\n   input= $inputDir\n   trustees= $trusteeDir\n   output = $outputDir"
            val extraInfo = if (encryptedTallyFile != null) "\n   encryptedTallyFile = $encryptedTallyFile" else ""
            logger.info { startupInfo + extraInfo}

            try {
                val retval = runDecryptTally(
                    inputDir,
                    outputDir,
                    readDecryptingTrustees(inputDir, trusteeDir, missing),
                    createdBy,
                    encryptedTallyFile,
                )
                if (!noexit && retval != 0) exitProcess(retval)
            } catch (t: Throwable) {
                logger.error { "Exception= ${t.message} ${t.stackTraceToString()}" }
                if (!noexit) exitProcess(-1)
            }
        }

        fun readDecryptingTrustees(
            inputDir: String,
            trusteeDir: String,
            missing: String? = null,
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
            createdBy: String? = null,
            encryptedTallyFile: String? = null,
        ): Int {
            val consumerIn = makeConsumer(inputDir)
            val resultInit = consumerIn.readElectionInitialized()
            if (resultInit is Err) {
                logger.error { "readElectionInitialized error ${resultInit.error}" }
                return 1
            }
            val electionInit = resultInit.unwrap()

            val trusteeNames = decryptingTrustees.map { it.id() }.toSet()
            val missingGuardians =
                electionInit.guardians.filter { !trusteeNames.contains(it.guardianId) }.map { it.guardianId }
            logger.debug { "runDecryptTally ${outputDir} present = $trusteeNames missing = $missingGuardians" }
            val quorum = electionInit.config.quorum
            if (decryptingTrustees.size < quorum) {
                logger.error { " encryptedTally.decrypt $inputDir does not have a quorum=${quorum}, only ${electionInit.config.quorum} guardians" }
                return 2
            }

            val guardians = Guardians(consumerIn.group, electionInit.guardians)
            val decryptor = TallyDecryptor(
                consumerIn.group,
                electionInit.extendedBaseHash,
                electionInit.jointPublicKey,
                guardians,
                decryptingTrustees,
            )

            var tallyResult: TallyResult? = null
            val encryptedTally : EncryptedTally = if (encryptedTallyFile == null) {
                val result = consumerIn.readTallyResult()
                if (result is Err) {
                    logger.error { "readTallyResult error ${result.error}" }
                    return 3
                }
                tallyResult = result.unwrap()
                tallyResult.encryptedTally
            } else {
                val result = consumerIn.readEncryptedTallyFromFile(encryptedTallyFile)
                if (result is Err) {
                    logger.error { " Cant read readEncryptedTallyFromFile $encryptedTallyFile err = $result" }
                    return 4
                }
                result.unwrap()
            }
            val stopwatch = Stopwatch() // start timing here

            val errs = ErrorMessages("RunTrustedTallyDecryption")
            val decryptedTally = decryptor.decrypt(encryptedTally, errs)
            if (decryptedTally == null) {
                logger.error { " encryptedTally.decrypt $inputDir has error=${errs}" }
                return 5
            }
            val countEncryptions = countEncryptions(decryptedTally)
            println( "runDecryptTally ${stopwatch.tookPer(countEncryptions, "encryptions")}")

            val publisher = makePublisher(outputDir, false)
            if (tallyResult != null) {
                publisher.writeDecryptionResult(
                    DecryptionResult(
                        tallyResult,
                        decryptedTally,
                        mapOf(
                            Pair("CreatedBy", createdBy ?: "RunTrustedTallyDecryption"),
                            Pair("CreatedOn", getSystemDate()),
                            Pair("CreatedFromDir", inputDir),
                        ),
                    ),
                )
                logger.info{ "writeDecryptionResult to output directory $outputDir "}
            } else {
                publisher.writeDecryptedTally(decryptedTally)
                logger.info{ "writeDecryptedTally to output directory $outputDir "}
            }
            logger.info { "success" }
            return 0
        }
    }
}


fun countEncryptions(tally: DecryptedTallyOrBallot): Int {
    return tally.contests.map { contest ->
        1 + contest.selections.size
    }.sum()
}
