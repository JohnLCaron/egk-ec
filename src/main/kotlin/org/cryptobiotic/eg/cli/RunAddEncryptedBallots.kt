package org.cryptobiotic.eg.cli

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import org.cryptobiotic.eg.encrypt.AddEncryptedBallot
import org.cryptobiotic.eg.input.BallotInputValidation
import org.cryptobiotic.eg.input.ManifestInputValidation
import org.cryptobiotic.eg.publish.readElectionRecord
import org.cryptobiotic.util.ErrorMessages
import kotlin.random.Random

/**
 *  This reads plaintext ballots from ballotDir and writes their encryptions into the specified election record.
 */
class RunAddEncryptedBallots {

    companion object {
        private val logger = KotlinLogging.logger("RunAddEncryptedBallots")

        @JvmStatic
        fun main(args: Array<String>) {
            val parser = ArgParser("RunAddEncryptedBallots")
            val inputDir by parser.option(
                ArgType.String,
                shortName = "in",
                description = "Directory containing input election record"
            ).required()
            val ballotDir by parser.option(
                ArgType.String,
                shortName = "ballots",
                description = "Directory to read Plaintext ballots from"
            ).required()
            val device by parser.option(
                ArgType.String,
                shortName = "device",
                description = "voting device name"
            ).required()
            val outputDir by parser.option(
                ArgType.String,
                shortName = "out",
                description = "Directory to write output election record"
            ).required()
            val challengePct by parser.option(
                ArgType.Int,
                shortName = "challenge",
                description = "Challenge percent of ballots"
            ).default(0)

            parser.parse(args)

            logger.info {
                "starting\n inputDir= $inputDir\n  ballotDir = $ballotDir\n device = $device\n" +
                        "  outputDir = $outputDir\n  challengePct = $challengePct"
            }

            val electionRecord = readElectionRecord(inputDir)
            val electionInit = electionRecord.electionInit()!!
            val consumerIn = electionRecord.consumer()

            val manifest = consumerIn.makeManifest(electionInit.config.manifestBytes)
            val errors = ManifestInputValidation(manifest).validate()
            if (ManifestInputValidation(manifest).validate().hasErrors()) {
                logger.error { "ManifestInputValidation error ${errors}" }
                throw RuntimeException("ManifestInputValidation error $errors")
            }
            val chaining = electionInit.config.chainConfirmationCodes

            var allOk = true

            val encryptor = AddEncryptedBallot(
                electionRecord.manifest(),
                BallotInputValidation(electionRecord.manifest()),
                electionInit.config.chainConfirmationCodes,
                electionInit.config.configBaux0,
                electionInit.jointPublicKey,
                electionInit.extendedBaseHash,
                device,
                outputDir,
                "${outputDir}/invalidDir",
                noDeviceNameInDir = !chaining
            )

            var countChallenge = 0
            consumerIn.iteratePlaintextBallots(ballotDir, null).forEach { pballot ->
                val errs = ErrorMessages("AddEncryptedBallot ${pballot.ballotId}")
                val encrypted = encryptor.encrypt(pballot, errs)
                if (encrypted == null) {
                    logger.error{ "failed errors = $errs"}
                    allOk = false
                } else {
                    val challengeThisOne = (challengePct != 0) && (Random.nextInt(100) > (100 - challengePct))
                    if (challengeThisOne) {
                        encryptor.challenge(encrypted.confirmationCode)
                        countChallenge++
                    } else {
                        encryptor.cast(encrypted.confirmationCode)
                    }
                }
            }
            encryptor.close()

            if (allOk) {
                logger.info { "success" }
            } else {
                logger.error { "failure" }
            }
        }
    }
}