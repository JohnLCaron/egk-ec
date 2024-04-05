package org.cryptobiotic.eg.cli

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.unwrap
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import org.cryptobiotic.eg.core.createDirectories
import org.cryptobiotic.eg.input.ManifestInputValidation
import org.cryptobiotic.eg.input.RandomBallotProvider
import org.cryptobiotic.eg.publish.makeConsumer
import org.cryptobiotic.eg.publish.makePublisher
import kotlin.random.Random
import kotlin.system.exitProcess

/**
 * Simulates using RunEncryptBallot one ballot at a time.
 * Note that chaining is controlled by config.chainConfirmationCodes, and handled by RunEncryptBallot.
 * Note that this does not allow for benolah challenge, ie voter submits a ballot, gets a confirmation code
 * (with or without ballot chaining), then decide to challenge or cast. So all ballots are cast. */
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
            val encryptBallotDir by parser.option(
                ArgType.String,
                shortName = "eballotDir",
                description = "Write encrypted ballots to this directory"
            ).required()
            val addDeviceNameToDir by parser.option(
                ArgType.Boolean,
                shortName = "deviceDir",
                description = "Add device name to encrypted ballots directory"
            ).default(true)
            parser.parse(args)

            val devices = deviceNames.split(",")
            logger.info {
                "starting\n inputDir= $inputDir\n  nballots= $nballots\n  plaintextBallotDir = $plaintextBallotDir\n" +
                        "  encryptBallotDir = $encryptBallotDir\n  devices = $devices\n  addDeviceNameToDir= $addDeviceNameToDir"
            }

            val consumerIn = makeConsumer(inputDir)
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
            val chaining = electionInit.config.chainConfirmationCodes
            val publisher = makePublisher(plaintextBallotDir)
            var allOk = true

            val ballotProvider = RandomBallotProvider(manifest)
            repeat(nballots) {
                val pballot = ballotProvider.getFakeBallot(manifest, null, "ballot$it")
                publisher.writePlaintextBallot(plaintextBallotDir, listOf(pballot))
                val pballotFilename = "$plaintextBallotDir/pballot-${pballot.ballotId}.json"
                val deviceIdx = if(devices.size == 1) 0 else Random.nextInt(devices.size)
                val device = devices[deviceIdx]
                val eballotDir = if (chaining || addDeviceNameToDir) "$encryptBallotDir/$device" else encryptBallotDir
                createDirectories(eballotDir)

                val retval = RunEncryptBallot.encryptBallot(
                    consumerIn,
                    pballotFilename,
                    eballotDir,
                    device,
                )
                if (retval != 0) allOk = false
            }
            if (chaining) {
                devices.forEach { device ->
                    val eballotDir = "$encryptBallotDir/$device"
                    if (RunEncryptBallot.close(consumerIn.group, device, eballotDir) != 0) allOk = false
                }
            }

            if (allOk) {
                logger.info { "success" }
            } else {
                logger.error { "failure" }
                exitProcess(10)
            }
        }
    }
}