package org.cryptobiotic.eg.cli

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.unwrap
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.required
import org.cryptobiotic.eg.input.ManifestInputValidation
import org.cryptobiotic.eg.input.RandomBallotProvider
import org.cryptobiotic.eg.publish.makeConsumer
import org.cryptobiotic.eg.publish.makePublisher

/**
 * Simulates using RunEncryptBallot one ballot at a time.
 * Note that chaining is controlled by config.chainConfirmationCodes.
 */
class RunExampleEncryption {

    companion object {
        private val logger = KotlinLogging.logger("RunExampleEncryption")

        @JvmStatic
        fun main(args: Array<String>) {
            val parser = ArgParser("RunExampleEncryption")
            val configDir by parser.option(
                ArgType.String,
                shortName = "config",
                description = "Directory containing election configuration"
            ).required()
            val nballots by parser.option(
                ArgType.Int,
                shortName = "nballots",
                description = "Number of test ballots to generate"
            ).required()
            val plaintextBallotDir by parser.option(
                ArgType.String,
                shortName = "pballotDir",
                description = "Write plaintext ballots to this directory"
            ).required()
            val encryptBallotDir by parser.option(
                ArgType.String,
                shortName = "eballotDir",
                description = "Write encrypted ballots to this directory"
            ).required()
            val device by parser.option(
                ArgType.String,
                shortName = "device",
                description = "voting device name"
            ).required()
            parser.parse(args)

            logger.info {
                "starting\n configDir= $configDir\n  nballots= $nballots\n  plaintextBallotDir = $plaintextBallotDir\n" +
                        "  encryptBallotDir = $encryptBallotDir\n  device = $device"
            }

            val consumerIn = makeConsumer(configDir)
            val initResult = consumerIn.readElectionInitialized()
            if (initResult is Err) {
                logger.error { "readElectionInitialized error ${initResult.error}" }
                return
            }
            val electionInit = initResult.unwrap()
            val manifest = consumerIn.makeManifest(electionInit.config.manifestBytes)
            val errors = ManifestInputValidation(manifest).validate()
            if (ManifestInputValidation(manifest).validate().hasErrors()) {
                logger.error { "ManifestInputValidation error ${errors}" }
                throw RuntimeException("ManifestInputValidation error $errors")
            }
            val ballotChaining = electionInit.config.chainConfirmationCodes
            val publisher = makePublisher(plaintextBallotDir)
            var previousConfirmationCode = ""

            val ballotProvider = RandomBallotProvider(manifest)
            repeat(nballots) {
                val pballot = ballotProvider.getFakeBallot(manifest, "ballotStyle", "ballot$it")
                publisher.writePlaintextBallot(plaintextBallotDir, listOf(pballot))
                val pballotFilename = "$plaintextBallotDir/pballot-${pballot.ballotId}.json"

                RunEncryptBallot.encryptBallot(
                    configDir,
                    pballotFilename,
                    encryptBallotDir,
                    device,
                    previousConfirmationCode,
                )

                if (ballotChaining) {
                    // read the encrypted ballot back in to get its confirmationCode
                    val result = consumerIn.readEncryptedBallot(encryptBallotDir, pballot.ballotId)
                    if (result is Err) {
                        logger.warn { "readEncryptedBallot error ${result}" }
                    } else {
                        val eballot = result.unwrap()
                        previousConfirmationCode = eballot.confirmationCode.toBase64()
                    }
                }
            }

            if (ballotChaining) {
                RunEncryptBallot.encryptBallot(
                    configDir,
                    "CLOSE",
                    encryptBallotDir,
                    device,
                    previousConfirmationCode,
                )
            }

            logger.info { "success" }
        }
    }
}