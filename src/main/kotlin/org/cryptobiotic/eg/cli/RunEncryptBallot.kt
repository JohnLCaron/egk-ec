package org.cryptobiotic.eg.cli

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.unwrap
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import org.cryptobiotic.eg.core.*
import org.cryptobiotic.eg.core.Base64.fromBase64
import org.cryptobiotic.eg.election.EncryptedBallot
import org.cryptobiotic.eg.encrypt.EncryptedBallotChain
import org.cryptobiotic.eg.encrypt.EncryptedBallotChain.Companion.makeCodeBaux
import org.cryptobiotic.eg.encrypt.EncryptedBallotChain.Companion.storeChain
import org.cryptobiotic.eg.encrypt.EncryptedBallotChain.Companion.terminateChain
import org.cryptobiotic.eg.encrypt.Encryptor
import org.cryptobiotic.eg.encrypt.submit
import org.cryptobiotic.eg.input.ManifestInputValidation
import org.cryptobiotic.eg.publish.Consumer
import org.cryptobiotic.eg.publish.EncryptedBallotSinkIF
import org.cryptobiotic.eg.publish.makeConsumer
import org.cryptobiotic.eg.publish.makePublisher
import org.cryptobiotic.util.ErrorMessages
import kotlin.system.exitProcess

/** Reads a plaintext ballot from disk and writes its encryption to disk.
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
            val device by parser.option(
                ArgType.String,
                shortName = "device",
                description = "voting device name"
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
            val previousConfirmationCode by parser.option(
                ArgType.String,
                shortName = "previous",
                description = "previous confirmation code for chaining ballots"
            ).default("")
            parser.parse(args)

            logger.info {
                "starting\n configDir= $configDir\n ballotFilename= $ballotFilename\n encryptBallotDir = $encryptBallotDir\n" +
                        " device = $device\n previousConfirmationCode = $previousConfirmationCode"
            }

            try {
                val consumerIn = makeConsumer(configDir)

                if (ballotFilename == "CLOSE") {
                    val retval = terminateChain(device, encryptBallotDir, previousConfirmationCode, consumerIn)
                    if (retval != 0) {
                        logger.info { "terminateBallotChain retval=$retval" }
                        exitProcess(retval)
                    }
                } else {
                    val retval = encryptBallot(
                        consumerIn,
                        ballotFilename,
                        encryptBallotDir,
                        device,
                        previousConfirmationCode,
                    )
                    if (retval != 0) {
                        logger.info { "encryptBallot retval=$retval" }
                        exitProcess(retval)
                    }
                }

            } catch (t: Throwable) {
                logger.error(t) { "failed ${t.message}" }
                exitProcess(10)
            }
        }

        fun encryptBallot(
            consumerIn: Consumer,
            ballotFilename: String,
            encryptBallotDir: String,
            device: String,
            previousConfirmationCode: String,
        ): Int {
            val initResult = consumerIn.readElectionInitialized()
            if (initResult is Err) {
                logger.error { "readElectionInitialized error ${initResult.error}" }
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

            val chaining = electionInit.config.chainConfirmationCodes
            val configBaux0 = electionInit.config.configBaux0

            var currentChain: EncryptedBallotChain? = null
            val codeBaux: ByteArray?
            if (!chaining) {
                codeBaux = configBaux0
            } else {
                val pair = makeCodeBaux(device, encryptBallotDir, configBaux0, electionInit.extendedBaseHash, previousConfirmationCode, consumerIn)
                codeBaux = pair.first
                currentChain = pair.second
            }
            if (codeBaux == null) {
                return 4
            }

            /*
            val chaining = electionInit.config.chainConfirmationCodes
            val configBaux0 = electionInit.config.configBaux0
            val codeBaux = if (!chaining) {
                configBaux0
            } else if (previousConfirmationCode.isEmpty()) {
                val chainResult = consumerIn.readEncryptedBallotChain(device, encryptBallotDir)
                if (chainResult is Ok) {
                    val chain: EncryptedBallotChain = chainResult.unwrap()
                    chain.lastConfirmationCode.bytes + configBaux0
                } else {
                    // otherwise its the first one
                    // H0 = H(HE ; 0x24, Baux,0 ), eq (59)
                    hashFunction(electionInit.extendedBaseHash.bytes, 0x24.toByte(), configBaux0).bytes
                }
            } else { // caller is supplying the previousConfirmationCodeBytes
                val previousConfirmationCodeBytes = previousConfirmationCode.fromBase64()
                if (previousConfirmationCodeBytes == null) {
                    logger.error { "previousConfirmationCodeBytes '$previousConfirmationCode' invalid" }
                    return 4
                }
                previousConfirmationCodeBytes + configBaux0
            } */

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

            try {
                val publisher = makePublisher(encryptBallotDir, false)
                val sink: EncryptedBallotSinkIF = publisher.encryptedBallotSink(null) // null means ignore device name
                val fileout = sink.writeEncryptedBallot(eballot)
                logger.info { "success encrypted ballot written to '$fileout' " }

                if (chaining) {
                    val retval = storeChain(
                        device,
                        encryptBallotDir,
                        ballot.ballotId,
                        eballot.confirmationCode,
                        currentChain
                    )
                }
            } catch (t: Throwable) {
                logger.error(t) { " error writing encrypted ballot ${t.message}" }
                return 6
            }
            return 0
        }

        fun terminateBallotChain(
            consumerIn: Consumer,
            encryptBallotDir: String,
            device: String,
            finalConfirmationCode64: String,
        ): Int {
            val initResult = consumerIn.readElectionInitialized()
            if (initResult is Err) {
                logger.error { "readElectionInitialized error ${initResult.error}" }
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

            try {
                // TODO write ballotIds
                val chain = EncryptedBallotChain(device, emptyList(), finalConfirmationCode, hashFinal)
                val publisher = makePublisher(encryptBallotDir, false)
                publisher.writeEncryptedBallotChain(chain)
                logger.info { "success chain written to '$encryptBallotDir' " }
            } catch (t: Throwable) {
                logger.error(t) { "error writing chain ${t.message}" }
                return 6
            }
            return 0
        }
    }
}