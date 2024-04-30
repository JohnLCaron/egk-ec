@file:OptIn(ExperimentalCoroutinesApi::class)

package org.cryptobiotic.eg.cli

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.unwrap
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import io.github.oshai.kotlinlogging.KotlinLogging

import org.cryptobiotic.eg.core.*
import org.cryptobiotic.eg.decrypt.DecryptBallotWithNonce
import org.cryptobiotic.eg.election.*
import org.cryptobiotic.eg.encrypt.Encryptor
import org.cryptobiotic.eg.encrypt.submit
import org.cryptobiotic.eg.input.BallotInputValidation
import org.cryptobiotic.eg.input.ManifestInputValidation
import org.cryptobiotic.eg.publish.EncryptedBallotSinkIF
import org.cryptobiotic.eg.publish.makeConsumer
import org.cryptobiotic.eg.publish.makePublisher
import org.cryptobiotic.eg.verifier.VerifyEncryptedBallots
import org.cryptobiotic.util.ErrorMessages
import org.cryptobiotic.util.Stats
import org.cryptobiotic.util.Stopwatch
import kotlin.system.exitProcess

/**
 * Run ballot encryption in multithreaded batch mode CLI.
 * Read ElectionConfig from inputDir, write electionInit to outputDir.
 * Read plaintext ballots from ballotDir.
 * All ballots will be cast.
 * Ballot chaining cannot be used, since ordering is indeterminate.
 */
class RunBatchEncryption {

    companion object {
        private val logger = KotlinLogging.logger("RunBatchEncryption")

        @JvmStatic
        fun main(args: Array<String>) {
            val parser = ArgParser("RunBatchEncryption")
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
            val outputDir by parser.option(
                ArgType.String,
                shortName = "out",
                description = "Directory to write output election record"
            )
            val encryptDir by parser.option(
                ArgType.String,
                shortName = "eballots",
                description = "Write encrypted ballots here"
            )
            val invalidDir by parser.option(
                ArgType.String,
                shortName = "invalid",
                description = "Directory to write invalid input ballots to"
            )
            val check by parser.option(
                ArgType.Choice<CheckType>(),
                shortName = "check",
                description = "Check encryption"
            ).default(CheckType.None)
            val nthreads by parser.option(
                ArgType.Int,
                shortName = "nthreads",
                description = "Number of parallel threads to use"
            ).default(11)
            val createdBy by parser.option(
                ArgType.String,
                shortName = "createdBy",
                description = "who created"
            )
            val device by parser.option(
                ArgType.String,
                shortName = "device",
                description = "voting device name"
            ).required()
            val cleanOutput by parser.option(
                ArgType.Boolean,
                shortName = "clean",
                description = "clean output dir"
            ).default(false)
            val anonymize by parser.option(
                ArgType.Boolean,
                shortName = "anon",
                description = "anonymize ballot"
            ).default(false)
            val noexit by parser.option(
                ArgType.Boolean,
                shortName = "noexit",
                description = "Dont call System.exit"
            ).default(false)

            parser.parse(args)
            val startupInfo =
                "input= $inputDir\n   ballots = $ballotDir\n   device = $device" +
                        "\n   outputDir = $outputDir" +
                        "\n   encryptDir = $encryptDir" +
                        "\n   nthreads = $nthreads" +
                        "\n   check = $check" +
                        "\n   anonymize = $anonymize"
            logger.info { startupInfo }
            println(startupInfo)

            if (outputDir == null && encryptDir == null) {
                logger.error { "Must specify outputDir or encryptDir" }
                if (!noexit) exitProcess(1)
                else throw RuntimeException("Must specify outputDir or encryptDir")
            }

            try {
                val retval = batchEncryption(
                    inputDir,
                    ballotDir,
                    device = device,
                    outputDir,
                    encryptDir,
                    invalidDir,
                    nthreads,
                    createdBy,
                    check,
                    cleanOutput,
                    anonymize,
                )
                if (retval == 0) {
                    logger.info { "success" }
                } else {
                    if (!noexit) exitProcess(retval)
                }

            } catch (t: Throwable) {
                logger.error { "Exception= ${t.message} ${t.stackTraceToString()}" }
                if (!noexit) exitProcess(-1)
            }
        }

        enum class CheckType { None, Verify, EncryptTwice, DecryptNonce }

        // encrypt ballots in ballotDir
        fun batchEncryption(
            inputDir: String,
            ballotDir: String,
            device: String,
            outputDir: String?,
            encryptDir: String?,
            invalidDir: String?,
            nthreads: Int,
            createdBy: String?,
            check: CheckType = CheckType.None,
            cleanOutput: Boolean = false,
            anonymize: Boolean = false,
        ): Int {
            // ballots can be in either format
            val consumer = makeConsumer(inputDir)

            return batchEncryption(
                inputDir,
                consumer.iteratePlaintextBallots(ballotDir, null),
                device = device,
                outputDir, encryptDir, invalidDir,
                nthreads, createdBy, check, cleanOutput, anonymize
            )
        }

        // encrypt the ballots in Iterable<PlaintextBallot>
        fun batchEncryption(
            inputDir: String,
            ballots: Iterable<PlaintextBallot>,
            device: String,
            outputDir: String?,
            encryptDir: String?,
            invalidDir: String?,
            nthreads: Int = 11,
            createdBy: String?,
            check: CheckType = CheckType.None,
            cleanOutput: Boolean = false,
            anonymize: Boolean = false,
        ): Int {
            count = 0 // start over each batch
            val consumerIn = makeConsumer(inputDir)
            val initResult = consumerIn.readElectionInitialized()
            if (initResult is Err) {
                logger.error { "readElectionInitialized error ${initResult.error}" }
                return 2
            }
            val electionInit = initResult.unwrap()
            val manifest = consumerIn.makeManifest(electionInit.config.manifestBytes)

            // ManifestInputValidation
            val manifestValidator = ManifestInputValidation(manifest)
            val errors = manifestValidator.validate()
            if (errors.hasErrors()) {
                logger.error { "ManifestInputValidation error on election record in $inputDir errs=$errors}" }
                return 3
            }
            // debugging
            // Map<BallotStyle: String, selectionCount: Int>
            val styleCount = manifestValidator.countEncryptions()

            // BallotInputValidation
            var countEncryptions = 0
            val invalidBallots = ArrayList<PlaintextBallot>()
            val ballotValidator = BallotInputValidation(manifest)
            val validate: ((PlaintextBallot) -> Boolean) = {
                val mess = ballotValidator.validate(it)
                if (mess.hasErrors()) {
                    logger.warn { "*** BallotInputValidation error on ballot ${it.ballotId}: $mess" }
                    invalidBallots.add(PlaintextBallot(it, mess.toString()))
                    false
                } else {
                    countEncryptions += styleCount[it.ballotStyle] ?: 0
                    true
                }
            }
            val stopwatch = Stopwatch() // start timing here

            val encryptor = Encryptor(
                consumerIn.group,
                manifest,
                electionInit.jointPublicKey,
                electionInit.extendedBaseHash,
                device,
            )
            val runEncryption = EncryptionRunner(
                consumerIn.group, encryptor, manifest, electionInit.config,
                electionInit.jointPublicKey, electionInit.extendedBaseHash, check
            )

            // encryptDir is the exact encrypted ballot directory, outputDir is the election record topdir
            val publisher = makePublisher(encryptDir ?: outputDir!!, cleanOutput)
            val sink: EncryptedBallotSinkIF = publisher.encryptedBallotSink(device)

            try {
                runBlocking {
                    val outputChannel = Channel<EncryptedBallot>()
                    val encryptorJobs = mutableListOf<Job>()
                    val ballotProducer = produceBallots(ballots.filter { validate(it) })
                    repeat(nthreads) {
                        encryptorJobs.add(
                            launchEncryptor(
                                it,
                                ballotProducer,
                                outputChannel
                            ) { ballot -> runEncryption.encrypt(ballot) }
                        )
                    }
                    launchSink(outputChannel, sink, anonymize)

                    // wait for all encryptions to be done, then close everything
                    joinAll(*encryptorJobs.toTypedArray())
                    outputChannel.close()
                }
            } finally {
                sink.close()
            }

            // only copy the ElectionInitialized record if outputDir (not encryptDir) is used
            if (encryptDir == null) {
                publisher.writeElectionInitialized(
                    electionInit.addMetadataToCopy(
                        Pair("Used", createdBy ?: "RunBatchEncryption"),
                        Pair("UsedOn", getSystemDate()),
                        Pair("CreatedFromDir", inputDir)
                    )
                )
            }
            // Must save invalid ballots
            if (invalidBallots.isNotEmpty()) {
                val useInvalidDir =
                    if (invalidDir != null) invalidDir else if (outputDir != null) "$outputDir/invalid" else "$encryptDir/invalid"
                createDirectories(useInvalidDir)
                publisher.writePlaintextBallot(useInvalidDir, invalidBallots)
                logger.info { " wrote ${invalidBallots.size} invalid ballots to $useInvalidDir" }
            }

            logger.debug { "Encryption with nthreads = $nthreads ${stopwatch.tookPer(count, "ballot")}" }
            val encryptionPerBallot = if (count == 0) 0 else (countEncryptions / count)
            logger.debug {
                "  $countEncryptions total encryptions = $encryptionPerBallot per ballot ${
                    stopwatch.tookPer(
                        countEncryptions,
                        "encryptions"
                    )
                }"
            }
            return 0
        }

        private var codeBaux = ByteArray(0)

        // orchestrates the encryption
        private class EncryptionRunner(
            val group: GroupContext,
            val encryptor: Encryptor,
            manifest: ManifestIF,
            val config: ElectionConfig,
            val publicKey: ElGamalPublicKey,
            val extendedBaseHash: UInt256,
            val check: CheckType,
        ) {

            val verifier: VerifyEncryptedBallots? =
                if (check == CheckType.Verify)
                    VerifyEncryptedBallots(group, manifest, publicKey, extendedBaseHash, config, 1)
                else null

            fun encrypt(ballot: PlaintextBallot): EncryptedBallot? {
                val errs = ErrorMessages("Ballot ${ballot.ballotId}")
                val ciphertextBallotMaybe = encryptor.encrypt(ballot, config.configBaux0, errs)
                if (errs.hasErrors()) {
                    logger.error { errs.toString() }
                    return null
                }
                val ciphertextBallot = ciphertextBallotMaybe!!

                // experiments in testing the encryption
                val errs2 = ErrorMessages("Ballot ${ballot.ballotId}")
                if (check == CheckType.EncryptTwice) {
                    val encrypted2 =
                        encryptor.encrypt(ballot, config.configBaux0, errs2, ciphertextBallot.ballotNonce)!!
                    if (encrypted2.confirmationCode != ciphertextBallot.confirmationCode) {
                        logger.warn { "CheckType.EncryptTwice: encrypted.confirmationCode doesnt match" }
                    }
                    if (encrypted2 != ciphertextBallot) {
                        logger.warn { "CheckType.EncryptTwice: encrypted doesnt match" }
                    }
                } else if (check == CheckType.Verify && verifier != null) {
                    // VerifyEncryptedBallots may be doing more work than actually needed
                    val submitted = ciphertextBallot.submit(EncryptedBallot.BallotState.CAST)
                    val verifyOk = verifier.verifyEncryptedBallot(submitted, errs2, Stats())
                    if (!verifyOk) {
                        logger.warn { "CheckType.Verify: encrypted doesnt verify = $errs2" }
                    }
                } else if (check == CheckType.DecryptNonce) {
                    // Decrypt with Nonce to ensure encryption worked
                    val primaryNonce = ciphertextBallot.ballotNonce
                    val encryptedBallot = ciphertextBallot.submit(EncryptedBallot.BallotState.CAST)

                    val decryptionWithPrimaryNonce = DecryptBallotWithNonce(group, publicKey, extendedBaseHash)
                    val decryptResult = with(decryptionWithPrimaryNonce) { encryptedBallot.decrypt(primaryNonce) }
                    if (decryptResult is Err) {
                        logger.warn { "CheckType.DecryptNonce: encrypted ballot fails decryption = $decryptResult" }
                    }
                }

                codeBaux = ciphertextBallot.confirmationCode.bytes
                return ciphertextBallot.submit(EncryptedBallot.BallotState.CAST)
            }
        }

        // the ballot reading is in its own coroutine
        @ExperimentalCoroutinesApi
        private fun CoroutineScope.produceBallots(producer: Iterable<PlaintextBallot>): ReceiveChannel<PlaintextBallot> =
            produce {
                for (ballot in producer) {
                    logger.debug { "Producer sending PlaintextBallot ${ballot.ballotId}" }
                    send(ballot)
                    yield()
                }
                channel.close()
            }

        // coroutines allow parallel encryption at the ballot level
        private fun CoroutineScope.launchEncryptor(
            id: Int,
            input: ReceiveChannel<PlaintextBallot>,
            output: SendChannel<EncryptedBallot>,
            encrypt: (PlaintextBallot) -> EncryptedBallot?,
        ) = launch(Dispatchers.Default) {
            for (ballot in input) {
                val encrypted = encrypt(ballot)
                if (encrypted != null) {
                    logger.debug { " Encryptor #$id sending CiphertextBallot ${encrypted.ballotId}" }
                    output.send(encrypted)
                }
                yield()
            }
            logger.debug { "Encryptor #$id done" }
        }

        // the encrypted ballot writing is in its own coroutine
        private var count = 0
        private fun CoroutineScope.launchSink(
            input: Channel<EncryptedBallot>, sink: EncryptedBallotSinkIF, anonymize: Boolean,
        ) = launch {
            for (ballot in input) {
                val useBallot = if (!anonymize) ballot else ballot.copy(ballotId = (count + 1).toString())
                sink.writeEncryptedBallot(useBallot)
                logger.debug { " Sink wrote $count submitted ballot ${useBallot.ballotId}" }
                count++
            }
        }
    }
}