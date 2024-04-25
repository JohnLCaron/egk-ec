package org.cryptobiotic.eg.cli

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import org.cryptobiotic.eg.core.pathExists
import org.cryptobiotic.eg.election.EncryptedBallot
import org.cryptobiotic.eg.encrypt.EncryptedBallotChain
import org.cryptobiotic.eg.encrypt.Encryptor
import org.cryptobiotic.eg.encrypt.submit
import org.cryptobiotic.eg.input.ManifestInputValidation
import org.cryptobiotic.eg.publish.*
import org.cryptobiotic.util.ErrorMessages
import kotlin.system.exitProcess

/**
 * Reads a plaintext ballot from disk and writes its encryption to disk.
 * Note that this does not allow for benolah challenge.
 * It does allow ballot chaining; if chaining, then make sure device is in encryptBallotDir,
 * and close the chain by calling with ballotFilepath="CLOSE".
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
            val outputDir by parser.option(
                ArgType.String,
                shortName = "out",
                description = "Directory to write output election record"
            ).required()
            val noDeviceNameInDir by parser.option(
                ArgType.Boolean,
                shortName = "deviceDir",
                description = "Dont add device name to encrypted ballots directory"
            ).default(false)
            val noexit by parser.option(
                ArgType.Boolean,
                shortName = "noexit",
                description = "Dont call System.exit"
            ).default(false)
            parser.parse(args)

            logger.info {
                "starting\n inputDir= $inputDir\n ballotFilepath= $ballotFilepath\n outputDir = $outputDir\n" +
                        " device = $device\n noDeviceNameInDir = $noDeviceNameInDir"
            }

            try {
                val retval = encryptBallot(
                    makeConsumer(inputDir),
                    ballotFilepath,
                    outputDir,
                    device,
                    noDeviceNameInDir,
                )
                if (retval != 0) {
                    logger.error { "failed retval=$retval" }
                    if (!noexit) exitProcess(retval)
                }

            } catch (t: Throwable) {
                logger.error(t) { "failed ${t.message}" }
                if (!noexit) exitProcess(-1)
            }
        }

        fun encryptBallot(
            consumerIn: Consumer,
            ballotFilepath: String,
            encryptBallotDir: String,
            device: String,
            noDeviceNameInDir: Boolean = false
        ): Int {
            if (!pathExists(encryptBallotDir)) {
                logger.error { "output Directory '$encryptBallotDir' must already exist" }
                return 4
            }

            val initResult = consumerIn.readElectionInitialized()
            if (initResult is Err) {
                logger.error { "readElectionInitialized error ${initResult.error}" }
                return 1
            }
            val electionInit = initResult.unwrap()
            val chaining = electionInit.config.chainConfirmationCodes

            val publisher = makePublisher(encryptBallotDir, false)
            val sink: EncryptedBallotSinkIF = publisher.encryptedBallotSink( if (noDeviceNameInDir) null else device)

            if (ballotFilepath == "CLOSE") {
                return close(makeConsumer(encryptBallotDir, consumerIn.group), device, publisher)
            }

            val configBaux0 = electionInit.config.configBaux0
            var currentChain: EncryptedBallotChain? = null
            val codeBaux =
                if (!chaining) {
                    configBaux0
                } else {
                    val consumerChain = makeConsumer(encryptBallotDir, consumerIn.group)
                    // this reads in an existing chain, or starts one.
                    val pair = EncryptedBallotChain.makeCodeBaux(
                        consumerChain,
                        device,
                        configBaux0,
                        electionInit.extendedBaseHash
                    )
                    currentChain = pair.second
                    pair.first
                }

            // validate manifest
            val manifest = consumerIn.makeManifest(electionInit.config.manifestBytes)
            val errors = ManifestInputValidation(manifest).validate()
            if (ManifestInputValidation(manifest).validate().hasErrors()) {
                logger.error { "ManifestInputValidation error $errors" }
                return 2
            }

            // read and validate ballot
            val result = consumerIn.readPlaintextBallot(ballotFilepath)
            if (result is Err) {
                logger.error { "readPlaintextBallot $result" }
                return 3
            }
            val ballot = result.unwrap()

            // encryption
            val encryptor = Encryptor(
                consumerIn.group,
                manifest,
                electionInit.jointPublicKey,
                electionInit.extendedBaseHash,
                device,
            )
            val errs = ErrorMessages("Encrypt ${ballot.ballotId}")
            val cballot = encryptor.encrypt(
                ballot,
                codeBaux,
                errs,
            )
            if (errs.hasErrors()) {
                logger.error { errs.toString() }
                return 6
            }

            val eballot = cballot!!.submit(EncryptedBallot.BallotState.CAST)

            try {
                val fileout = sink.writeEncryptedBallot(eballot)
                logger.info { "success encrypted ballot written to '$fileout' " }

                if (chaining) {
                    EncryptedBallotChain.writeChain(
                        publisher,
                        ballot.ballotId,
                        eballot.confirmationCode,
                        currentChain!!
                    )
                }
            } catch (t: Throwable) {
                logger.error(t) { " error writing encrypted ballot ${t.message}" }
                return 7
            }
            return 0
        }

        fun close(
            consumer: Consumer,
            device: String,
            publisher: Publisher,
        ): Int {
            val chainResult = consumer.readEncryptedBallotChain(device)
            val retval = if (chainResult is Ok) {
                val chain = chainResult.unwrap()
                val termval = EncryptedBallotChain.terminateChain(publisher, chain)
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