package org.cryptobiotic.eg.cli

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.unwrap
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import org.cryptobiotic.eg.input.ManifestInputValidation
import org.cryptobiotic.eg.input.RandomBallotProvider
import org.cryptobiotic.eg.publish.makeConsumer
import org.cryptobiotic.eg.publish.makePublisher
import kotlin.random.Random
import kotlin.system.exitProcess

/**
 * Simulates using RunEncryptBallot one ballot at a time.
 * Note that chaining is controlled by config.chainConfirmationCodes, and handled by RunEncryptBallot.
 * Note that RunExampleEncryption does not allow for benolah challenge, ie voter submits a ballot, gets a confirmation code
 * (with or without ballot chaining), then decide to challenge or cast. So all ballots are cast.
 */
class RunExampleEncryption {

    companion object {
        private val logger = KotlinLogging.logger("RunExampleEncryption")

        @JvmStatic
        fun main(args: Array<String>) {
            val parser = ArgParser("RunExampleEncryption")
            val inputDir by parser.option(
                ArgType.String,
                shortName = "in",
                description = "Directory containing input election record"
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
            val deviceNames by parser.option(
                ArgType.String,
                shortName = "device",
                description = "voting device name(s), comma delimited"
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

            val devices = deviceNames.split(",")
            logger.info {
                "starting\n inputDir= $inputDir\n  nballots= $nballots\n  plaintextBallotDir = $plaintextBallotDir\n" +
                        "  outputDir = $outputDir\n  devices = $devices\n  noDeviceNameInDir= $noDeviceNameInDir"
            }

            try {
                val consumerIn = makeConsumer(inputDir)
                val initResult = consumerIn.readElectionInitialized()
                if (initResult is Err) {
                    logger.error { "readElectionInitialized error ${initResult.error}" }
                    if (!noexit) exitProcess(1) else return
                }
                val electionInit = initResult.unwrap()
                val manifest = consumerIn.makeManifest(electionInit.config.manifestBytes)
                val errors = ManifestInputValidation(manifest).validate()
                if (ManifestInputValidation(manifest).validate().hasErrors()) {
                    logger.error { "ManifestInputValidation error ${errors}" }
                    if (!noexit) exitProcess(2) else return
                }
                val chaining = electionInit.config.chainConfirmationCodes
                val publisher = makePublisher(plaintextBallotDir)
                var allOk = true

                val ballotProvider = RandomBallotProvider(manifest)
                repeat(nballots) {
                    val pballot = ballotProvider.getFakeBallot(manifest, null, "ballot$it")
                    publisher.writePlaintextBallot(plaintextBallotDir, listOf(pballot))
                    val pballotFilename = "$plaintextBallotDir/pballot-${pballot.ballotId}.json"
                    val deviceIdx = if (devices.size == 1) 0 else Random.nextInt(devices.size)
                    val device = devices[deviceIdx]
                    // val eballotDir = if (chaining || !noDeviceNameInDir) "$encryptBallotDir/$device" else encryptBallotDir
                    // createDirectories(eballotDir)

                    val retval = RunEncryptBallot.encryptBallot(
                        consumerIn,
                        pballotFilename,
                        outputDir,
                        device,
                        noDeviceNameInDir,
                    )
                    if (retval != 0) allOk = false
                }
                if (chaining) {
                    devices.forEach { device ->
                        RunEncryptBallot.encryptBallot(
                            consumerIn,
                            "CLOSE",
                            outputDir,
                            device,
                            noDeviceNameInDir,
                        )
                    }
                }

                if (allOk) {
                    logger.info { "success" }
                } else {
                    if (!noexit) exitProcess(3)
                }
            } catch (t: Throwable) {
                logger.error { "Exception= ${t.message} ${t.stackTraceToString()}" }
                if (!noexit) exitProcess(-1)
            }
        }
    }
}