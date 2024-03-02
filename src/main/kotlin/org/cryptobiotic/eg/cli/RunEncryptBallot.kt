package org.cryptobiotic.eg.cli

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import org.cryptobiotic.eg.core.*
import org.cryptobiotic.eg.core.Base64.fromBase64
import org.cryptobiotic.eg.election.EncryptedBallot
import org.cryptobiotic.eg.election.PlaintextBallot
import org.cryptobiotic.eg.encrypt.AddEncryptedBallot
import org.cryptobiotic.eg.encrypt.Encryptor
import org.cryptobiotic.eg.encrypt.submit
import org.cryptobiotic.eg.input.ManifestInputValidation
import org.cryptobiotic.eg.input.RandomBallotProvider
import org.cryptobiotic.eg.publish.EncryptedBallotSinkIF
import org.cryptobiotic.eg.publish.makeConsumer
import org.cryptobiotic.eg.publish.makePublisher
import org.cryptobiotic.eg.publish.readElectionRecord
import org.cryptobiotic.eg.verifier.VerifyEncryptedBallots
import org.cryptobiotic.util.ErrorMessages
import kotlin.random.Random

class RunEncryptBallot {

    companion object {
        private val logger = KotlinLogging.logger("RunEncryptBallot")

        @JvmStatic
        fun main(args: Array<String>) {
            val parser = ArgParser("RunEncryptBallot")
            val configDir by parser.option(
                ArgType.String,
                shortName = "config",
                description = "Directory containing election configuration"
            ).required()
            val ballotFilename by parser.option(
                ArgType.String,
                shortName = "ballot",
                description = "Plaintext ballot filename (input)"
            ).required()
            val encryptBallotDir by parser.option(
                ArgType.String,
                shortName = "output",
                description = "Write encrypted ballot to this directory"
            ).required()
            val device by parser.option(
                ArgType.String,
                shortName = "device",
                description = "voting device name"
            ).required()
            val previousConfirmationCode by parser.option(
                ArgType.String,
                shortName = "previous",
                description = "previous confirmation code when chaining ballots"
            )
            parser.parse(args)

            logger.info {
                "RunEncryptBallot configDir= $configDir ballotFilename= $ballotFilename encryptBallotDir = $encryptBallotDir" +
                        " device = $device previousConfirmationCode = $previousConfirmationCode"
            }

            val retval = encryptBallot(
                configDir,
                ballotFilename,
                encryptBallotDir,
                device,
                previousConfirmationCode?: "",
            )
            println("RunEncryptBallot returns $retval")
        }

        fun encryptBallot(
            configDir: String,
            ballotFilename: String,
            encryptBallotDir: String,
            device: String,
            previousConfirmationCode: String,
            ): Int {
            val consumerIn = makeConsumer(configDir)
            val initResult = consumerIn.readElectionInitialized()
            if (initResult is Err) {
                logger.error {"readElectionInitialized error ${initResult.error}"}
                return 1
            }
            val electionInit = initResult.unwrap()
            val manifest = consumerIn.makeManifest(electionInit.config.manifestBytes)
            val errors = ManifestInputValidation(manifest).validate()
            if (ManifestInputValidation(manifest).validate().hasErrors()) {
                logger.error { "ManifestInputValidation error $errors" }
                return 2
            }

            val encryptor = Encryptor(
                consumerIn.group,
                manifest,
                electionInit.jointPublicKey(),
                electionInit.extendedBaseHash,
                device,
            )

            val result = consumerIn.readPlaintextBallot(ballotFilename)
            if (result is Err) {
                logger.error { "readPlaintextBallot $result" }
                return 3
            }
            val ballot = result.unwrap()

            val configBaux0 = electionInit.config.configBaux0
            val codeBaux = if (previousConfirmationCode.isEmpty()) { // TODO true when not chaining ??
                // H0 = H(HE ; 0x24, Baux,0 ), eq (59)
                hashFunction(electionInit.extendedBaseHash.bytes, 0x24.toByte(), configBaux0).bytes
            } else {
                val previousConfirmationCodeBytes = previousConfirmationCode.fromBase64()
                if (previousConfirmationCodeBytes == null) {
                    logger.error { "previousConfirmationCodeBytes invalid" }
                    return 4
                }
                // Baux,j = Hj−1 ∥ Baux,0 eq (60)
                hashFunction(electionInit.extendedBaseHash.bytes, 0x24.toByte(), previousConfirmationCodeBytes, configBaux0).bytes
            }

            val errs = ErrorMessages("Encrypt ${ballot.ballotId}")
            val cballot = encryptor.encrypt(
                ballot,
                codeBaux,
                errs,
            )
            if (errs.hasErrors()) {
                logger.error { errs.toString() }
                return 5
            }

            val eballot = cballot!!.submit(EncryptedBallot.BallotState.CAST)

            // TODO possible errors here
            val publisher = makePublisher(encryptBallotDir, false)
            val sink: EncryptedBallotSinkIF = publisher.encryptedBallotSink(null) // null means ignore device name
            val fileout = sink.writeEncryptedBallot(eballot)

            // success
            logger.info {"Success encrypted ballot written to '$fileout' " }
            return 0
        }
    }
}