package org.cryptobiotic.eg.cli

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.unwrap
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
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
import kotlinx.cli.default
import org.cryptobiotic.eg.cli.RunTrustedTallyDecryption.Companion.readDecryptingTrustees

import org.cryptobiotic.eg.core.*
import org.cryptobiotic.eg.decrypt.*
import org.cryptobiotic.eg.election.*
import org.cryptobiotic.eg.publish.*
import org.cryptobiotic.util.ErrorMessages
import org.cryptobiotic.util.Stopwatch
import kotlin.system.exitProcess

/**
 * Decrypt challenged ballots with local trustees CLI.
 * Read election record from inputDir, write to outputDir.
 * This has access to all the trustees, so is only used for testing, or in a use case of trust.
 * A version of this where each Trustee is in its own process space is implemented in the webapps modules.
 */
class RunTrustedBallotDecryption {

    companion object {
        private val logger = KotlinLogging.logger("RunTrustedBallotDecryption")
        private const val debug = false

        @JvmStatic
        fun main(args: Array<String>) {
            val parser = ArgParser("RunTrustedBallotDecryption")
            val inputDir by parser.option(
                ArgType.String,
                shortName = "in",
                description = "Directory containing input election record"
            ).required()
            val trusteeDir by parser.option(
                ArgType.String,
                shortName = "trustees",
                description = "Directory to read private trustees"
            ).required()
            val outputDir by parser.option(
                ArgType.String,
                shortName = "out",
                description = "Directory to write output election record"
            ).required()
            val decryptChallenged by parser.option(
                ArgType.String,
                shortName = "challenged",
                description = "decrypt challenged ballots"
            )
            val nthreads by parser.option(
                ArgType.Int,
                shortName = "nthreads",
                description = "Number of parallel threads to use"
            )
            val noexit by parser.option(
                ArgType.Boolean,
                shortName = "noexit",
                description = "Dont call System.exit"
            ).default(false)

            parser.parse(args)

            val startupInfo = "RunTrustedBallotDecryption starting   input= $inputDir   trustees= $trusteeDir" +
                        "   decryptChallenged = $decryptChallenged   output = $outputDir"
            logger.info { startupInfo }

            try {
                val (retval, _) = runDecryptBallots(
                    inputDir, outputDir, readDecryptingTrustees(inputDir, trusteeDir),
                    decryptChallenged, nthreads ?: 11
                )
                if (!noexit && retval != 0) exitProcess(retval)

            } catch (t : Throwable) {
                logger.error{"Exception= ${t.message} ${t.stackTraceToString()}"}
                if (!noexit) exitProcess(-1)
            }
        }

        fun runDecryptBallots(
            inputDir: String,
            outputDir: String,
            decryptingTrustees: List<DecryptingTrusteeIF>,
            decryptChallenged: String?, // comma delimited, no spaces
            nthreads: Int,
        ): Pair<Int,Int> {
            val stopwatch = Stopwatch() // start timing here

            val consumerIn = makeConsumer(inputDir)
            val result: Result<ElectionInitialized, ErrorMessages> = consumerIn.readElectionInitialized()
            if (result is Err) {
                logger.error { result.error.toString() }
                return Pair(1,0)
            }
            val electionInitialized = result.unwrap()
            val guardians = Guardians(consumerIn.group, electionInitialized.guardians)

            val decryptor = BallotDecryptor(
                consumerIn.group,
                electionInitialized.extendedBaseHash,
                electionInitialized.jointPublicKey,
                guardians,
                decryptingTrustees,
            )

            val publisher = makePublisher(outputDir, false)
            val sink: DecryptedBallotSinkIF = publisher.decryptedBallotSink()

            val ballotIter: Iterable<EncryptedBallot> =
                when {
                    (decryptChallenged == null) || (decryptChallenged == "challenged") -> {
                        logger.info {" use all challenged" }
                        consumerIn.iterateAllChallengedBallots()
                    }

                    (decryptChallenged.trim().lowercase() == "all") -> {
                        logger.info { " use all" }
                        consumerIn.iterateAllEncryptedBallots { true }
                    }

                    pathExists(decryptChallenged) -> {
                        logger.info {" use ballots in file '$decryptChallenged'"}
                        val wanted: List<String> = fileReadLines(decryptChallenged)
                        val wantedTrim: List<String> = wanted.map { it.trim() }
                        consumerIn.iterateAllEncryptedBallots { wantedTrim.contains(it.ballotId) }
                    }

                    else -> {
                        logger.info {" use ballots in list '${decryptChallenged}'"}
                        val wanted: List<String> = decryptChallenged.split(",")
                        consumerIn.iterateAllEncryptedBallots {
                            wanted.contains(it.ballotId)
                        }
                    }
                }

            try {
                runBlocking {
                    val outputChannel = Channel<DecryptedTallyOrBallot>()
                    val decryptorJobs = mutableListOf<Job>()
                    val ballotProducer = produceBallots(ballotIter)
                    repeat(nthreads) {
                        decryptorJobs.add(
                            launchDecryptor(
                                it,
                                ballotProducer,
                                decryptor,
                                outputChannel
                            )
                        )
                    }
                    launchSink(outputChannel, sink)

                    // wait for all decryptions to be done, then close everything
                    joinAll(*decryptorJobs.toTypedArray())
                    outputChannel.close()
                }
            } finally {
                sink.close()
            }

            decryptor.stats.show(5)
            val count = decryptor.stats.count()

            logger.info {" decrypt ballots ${stopwatch.tookPer(count, "ballots")}" }

            return Pair(0, count)
        }

        // parallelize over ballots
        // place the ballot reading into its own coroutine
        @OptIn(ExperimentalCoroutinesApi::class)
        private fun CoroutineScope.produceBallots(producer: Iterable<EncryptedBallot>): ReceiveChannel<EncryptedBallot> =
            produce {
                for (ballot in producer) {
                    logger.debug { "Producer sending ballot ${ballot.ballotId}" }
                    send(ballot)
                    yield()
                }
                channel.close()
            }

        private fun CoroutineScope.launchDecryptor(
            id: Int,
            input: ReceiveChannel<EncryptedBallot>,
            decryptor: BallotDecryptor,
            output: SendChannel<DecryptedTallyOrBallot>,
        ) = launch(Dispatchers.Default) {
            for (ballot in input) {
                val errs = ErrorMessages("RunTrustedBallotDecryption ballot=${ballot.ballotId}")
                try {
                    val decrypted = decryptor.decrypt(ballot, errs)
                    if (decrypted != null) {
                        logger.debug { " Decryptor #$id sending DecryptedTallyOrBallot ${decrypted.id}" }
                        if (debug) println(" Decryptor #$id sending DecryptedTallyOrBallot ${decrypted.id}")
                        output.send(decrypted)
                    } else {
                        logger.error { " decryptBallot error on ${ballot.ballotId} error=${errs}" }
                    }
                } catch (t : Throwable) {
                    errs.add("Exception= ${t.message} ${t.stackTraceToString()}")
                    logger.error { errs }
                }
                yield()
            }
            logger.debug { "Decryptor #$id done" }
        }

        // place the output writing into its own coroutine
        private fun CoroutineScope.launchSink(
            input: Channel<DecryptedTallyOrBallot>, sink: DecryptedBallotSinkIF,
        ) = launch {
            for (tally in input) {
                sink.writeDecryptedBallot(tally)
            }
        }
    }
}
