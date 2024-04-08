package org.cryptobiotic.eg.cli

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.required
import org.cryptobiotic.eg.core.GroupContext
import org.cryptobiotic.eg.election.EncryptedBallot
import org.cryptobiotic.eg.encrypt.EncryptedBallotChain
import org.cryptobiotic.eg.encrypt.EncryptedBallotChain.Companion.makeCodeBaux
import org.cryptobiotic.eg.encrypt.EncryptedBallotChain.Companion.writeChain
import org.cryptobiotic.eg.encrypt.EncryptedBallotChain.Companion.terminateChain
import org.cryptobiotic.eg.encrypt.Encryptor
import org.cryptobiotic.eg.encrypt.submit
import org.cryptobiotic.eg.input.ManifestInputValidation
import org.cryptobiotic.eg.publish.Consumer
import org.cryptobiotic.eg.publish.EncryptedBallotSinkIF
import org.cryptobiotic.eg.publish.makeConsumer
import org.cryptobiotic.eg.publish.makePublisher
import org.cryptobiotic.util.ErrorMessages

/**
 * Reads a plaintext ballot from disk and writes its encryption to disk.
 * Note that this does not allow for benolah challenge, ie voter submits a ballot, gets a confirmation code
 * (with or without ballot chaining), then decide to challenge or cast.
 */
class RunEncryptBallot {

    companion object {
        private val logger = KotlinLogging.logger("RunEncryptBallot")

        @JvmStatic
        fun main(args: Array<String>) {
            val parser = ArgParser("RunEncryptBallot")
            val inputDir by parser.option(
                ArgType.String,
                shortName = "in",
                description = "Directory containing input election record"
            ).required()
            val device by parser.option(
                ArgType.String,
                shortName = "device",
                description = "voting device name"
            ).required()
            val ballotFilepath by parser.option(
                ArgType.String,
                shortName = "ballot",
                description = "Plaintext ballot filepath (or 'CLOSE')"
            ).required()
            val encryptBallotDir by parser.option(
                ArgType.String,
                shortName = "output",
                description = "Write encrypted ballot to this directory"
            ).required()
            parser.parse(args)

            logger.info {
                "starting\n inputDir= $inputDir\n ballotFilepath= $ballotFilepath\n encryptBallotDir = $encryptBallotDir\n device = $device"
            }

            val consumerIn = makeConsumer(inputDir)
            try {
                if (ballotFilepath == "CLOSE") {
                   close(consumerIn.group, device, encryptBallotDir)
                } else {
                    val retval = encryptBallot(
                        consumerIn,
                        ballotFilepath,
                        encryptBallotDir,
                        device,
                    )
                    if (retval != 0) {
                        logger.info { "encryptBallot retval=$retval" }
                        // exitProcess(retval)
                    }
                }

            } catch (t: Throwable) {
                logger.error(t) { "failed ${t.message}" }
                // exitProcess(10)
            }
        }

        fun encryptBallot(
            consumerIn: Consumer,
            ballotFilepath: String,
            encryptBallotDir: String,
            device: String,
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
                electionInit.jointPublicKey,
                electionInit.extendedBaseHash,
                device,
            )

            val result = consumerIn.readPlaintextBallot(ballotFilepath)
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
                val consumerChain = makeConsumer(encryptBallotDir, consumerIn.group)
                // this will read in an existing chain, and so recover from machine going down.
                val pair = makeCodeBaux(consumerChain, device, encryptBallotDir, configBaux0, electionInit.extendedBaseHash )
                codeBaux = pair.first
                currentChain = pair.second
            }
            if (codeBaux == null) {
                return 4
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

            try {
                val publisher = makePublisher(encryptBallotDir, false)
                val sink: EncryptedBallotSinkIF = publisher.encryptedBallotSink(null) // null means ignore device name
                val fileout = sink.writeEncryptedBallot(eballot)
                logger.info { "success encrypted ballot written to '$fileout' " }

                if (chaining) {
                    writeChain(
                        publisher,
                        encryptBallotDir,
                        ballot.ballotId,
                        eballot.confirmationCode,
                        currentChain!!
                    )
                }
            } catch (t: Throwable) {
                logger.error(t) { " error writing encrypted ballot ${t.message}" }
                return 6
            }
            return 0
        }

        fun close(
            group: GroupContext,
            device: String,
            encryptBallotDir: String,
        ): Int {
            val consumer = makeConsumer(encryptBallotDir, group)
            val chainResult = consumer.readEncryptedBallotChain(device, encryptBallotDir)
            val retval = if (chainResult is Ok) {
                val chain = chainResult.unwrap()
                val publisher = makePublisher(encryptBallotDir) // TODO not placing it into the device directory?
                val termval = terminateChain(publisher, device, encryptBallotDir, chain)
                if (termval != 0) {
                    logger.info { "Cant terminateBallotChain retval=$termval" }
                }
                termval

            } else {
                logger.error { "Cant readEncryptedBallotChain err=$chainResult" }
                11
            }
            return retval
        }
    }
}