package org.cryptobiotic.eg.cli

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.unwrap
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.required
import org.cryptobiotic.eg.core.*
import org.cryptobiotic.eg.core.Base64.fromBase64
import org.cryptobiotic.eg.election.EncryptedBallot
import org.cryptobiotic.eg.election.EncryptedBallotChain
import org.cryptobiotic.eg.encrypt.Encryptor
import org.cryptobiotic.eg.encrypt.submit
import org.cryptobiotic.eg.input.ManifestInputValidation
import org.cryptobiotic.eg.publish.EncryptedBallotSinkIF
import org.cryptobiotic.eg.publish.makeConsumer
import org.cryptobiotic.eg.publish.makePublisher
import org.cryptobiotic.util.ErrorMessages

/** Reads a plaintext ballot from disk and writes its encryption to disk.
 * Note that the plaintext ballot must have the sn field set if you want the encrypted sn in the encrypted ballot.
 * TODO optionally manage previousConfirmationCode here
 */
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
                description = "Plaintext ballot filename (or 'CLOSE')"
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
                "starting\n configDir= $configDir\n ballotFilename= $ballotFilename\n encryptBallotDir = $encryptBallotDir\n" +
                        " device = $device\n previousConfirmationCode = $previousConfirmationCode"
            }

            if (ballotFilename == "CLOSE") {
                terminateBallotChain(configDir, encryptBallotDir, device, previousConfirmationCode!!)
                logger.info { "ballot chain closed" }
            } else {
                val retval = encryptBallot(
                    configDir,
                    ballotFilename,
                    encryptBallotDir,
                    device,
                    previousConfirmationCode ?: "",
                )
                logger.info { "RunEncryptBallot returns $retval" }
            }
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

            // TODO do we have same behavior in AddEncryptedBallot ?
            val chaining = electionInit.config.chainConfirmationCodes
            val configBaux0 = electionInit.config.configBaux0
            val codeBaux = if (!chaining) {
                configBaux0
            } else if (previousConfirmationCode.isEmpty()) { // then its the first one
                // H0 = H(HE ; 0x24, Baux,0 ), eq (59)
                hashFunction(electionInit.extendedBaseHash.bytes, 0x24.toByte(), configBaux0).bytes
            } else {
                val previousConfirmationCodeBytes = previousConfirmationCode.fromBase64()
                if (previousConfirmationCodeBytes == null) {
                    logger.error { "previousConfirmationCodeBytes '$previousConfirmationCode' invalid" }
                    return 4
                }
                previousConfirmationCodeBytes + configBaux0
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

        fun terminateBallotChain(
            configDir: String,
            encryptBallotDir: String,
            device: String,
            finalConfirmationCode64: String,
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

            // The chain should be closed at the end of an election by forming and publishing
            //    H = H(HE ; 0x24, Baux ) (61)
            // using Baux = H(Bℓ ) ∥ Baux,0 ∥ b(“CLOSE”, 5), where H(Bℓ ) is the final confirmation code in the chain.

            val configBaux0 = electionInit.config.configBaux0
            val finalConfirmationCodeBytes = finalConfirmationCode64.fromBase64()
            if (finalConfirmationCodeBytes == null) {
                logger.error { "previousConfirmationCodeBytes '$finalConfirmationCode64' invalid" }
                return 4
            }
            val finalConfirmationCode = finalConfirmationCodeBytes.toUInt256()!!
            val bauxFinal = finalConfirmationCodeBytes + configBaux0 + "CLOSE".encodeToByteArray()
            val hashFinal = hashFunction(electionInit.extendedBaseHash.bytes, 0x24.toByte(), bauxFinal)

            // TODO write ballotIds
            val closing =
                EncryptedBallotChain(device, emptyList(), finalConfirmationCode, hashFinal)

            val publisher = makePublisher(encryptBallotDir, false)
            publisher.writeEncryptedBallotChain(closing)

            // success
            logger.info {"Success terminateBallotChain written to '$encryptBallotDir' " }
            return 0
        }
    }
}