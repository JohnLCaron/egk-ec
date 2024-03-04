package org.cryptobiotic.eg.verifier

import org.cryptobiotic.eg.core.*
import org.cryptobiotic.eg.election.*
import org.cryptobiotic.util.ErrorMessages
import org.cryptobiotic.util.Stats

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.unwrap
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield
import org.cryptobiotic.eg.publish.Consumer
import org.cryptobiotic.eg.publish.ElectionRecord
import org.cryptobiotic.util.Stopwatch

private const val debugBallots = false

/** Can be multithreaded. */
@OptIn(ExperimentalCoroutinesApi::class)
class VerifyEncryptedBallots(
    val group: GroupContext,
    val manifest: ManifestIF,
    val jointPublicKey: ElGamalPublicKey,
    val extendedBaseHash: UInt256, // He
    val config: ElectionConfig,
    private val nthreads: Int,
) {
    val aggregator = SelectionAggregator() // for Verification 8 (Correctness of ballot aggregation)

    fun verifyBallots(
        ballots: Iterable<EncryptedBallot>,
        errs: ErrorMessages,
        stats: Stats = Stats(),
    ): Boolean {
        val stopwatch = Stopwatch()

        runBlocking {
            val verifierJobs = mutableListOf<Job>()
            val ballotProducer = produceBallots(ballots)
            repeat(nthreads) {
                verifierJobs.add(
                    launchVerifier(
                        it,
                        ballotProducer,
                        aggregator
                    ) { ballot -> verifyEncryptedBallot(ballot, errs.nested("ballot ${ballot.ballotId}"), stats) })
            }

            // wait for all verifications to be done
            joinAll(*verifierJobs.toTypedArray())
        }

        // check duplicate confirmation codes (7.C): LOOK what if there are multiple records for the election?
        // LOOK what about checking for duplicate ballot ids?
        val checkDuplicates = mutableMapOf<UInt256, String>()
        confirmationCodes.forEach {
            if (checkDuplicates[it.code] != null) {
                errs.add("    7.C, 17.D. Duplicate confirmation code ${it.code} for ballot ids=${it.ballotId},${checkDuplicates[it.code]}")
            }
            checkDuplicates[it.code] = it.ballotId
        }

        logger.info { "verified $count ballots ${stopwatch.tookPer(count, "ballots")}" }
        return !errs.hasErrors()
    }

    fun verifyEncryptedBallot(
        ballot: EncryptedBallot,
        errs: ErrorMessages,
        stats: Stats
    ) : Boolean {
        val stopwatch = Stopwatch()

        if (ballot.electionId != extendedBaseHash) {
            errs.add("Encrypted Ballot ${ballot.ballotId} has wrong electionId = ${ballot.electionId}; skipping")
            return false
        }

        var ncontests = 0
        var nselections = 0
        for (contest in ballot.contests) {
            ncontests++
            nselections += contest.selections.size
            verifyEncryptedContest(contest, ballot.isPreencrypt, errs.nested("Selection ${contest.contestId}"))
        }

        if (!ballot.isPreencrypt) {
            // The ballot confirmation code H(B) = H(HE ; 0x24, χ1 , χ2 , . . . , χmB , Baux ) ; 7.B
            val contestHashes = ballot.contests.map { it.contestHash }
            val confirmationCode = hashFunction(extendedBaseHash.bytes, 0x24.toByte(), contestHashes, ballot.codeBaux)
            if (confirmationCode != ballot.confirmationCode) {
                errs.add("    7.B. Incorrect ballot confirmation code for ballot ${ballot.ballotId} ")
            }
        } else {
            verifyPreencryptedCode(ballot, errs)
        }

        stats.of("verifyEncryptions", "selection").accum(stopwatch.stop(), nselections)
        if (debugBallots) println(" Ballot '${ballot.ballotId}' ncontests = $ncontests nselections = $nselections")

        return !errs.hasErrors()
    }

    fun verifyEncryptedContest(
        contest: EncryptedBallot.Contest,
        isPreencrypt: Boolean,
        errs: ErrorMessages
    ) {
        contest.selections.forEach {
            verifySelection( it, manifest.optionLimit(contest.contestId), errs.nested("Selection ${it.selectionId}"))
        }

        // Verification 6 (Adherence to vote limits)
        val texts: List<ElGamalCiphertext> = contest.selections.map { it.encryptedVote }
        val ciphertextAccumulation: ElGamalCiphertext = texts.encryptedSum()?: 0.encrypt(jointPublicKey)
        val cvalid = contest.proof.verify(
            ciphertextAccumulation,
            this.jointPublicKey,
            this.extendedBaseHash,
            manifest.contestLimit(contest.contestId)
        )
        if (cvalid is Err) {
            errs.add("    6. ChaumPedersenProof validation error = ${cvalid.error} ")
        }

        // χl = H(HE ; 0x23, l, K, α1 , β1 , α2 , β2 . . . , αm , βm ) 7.A
        val ciphers = mutableListOf<ElementModP>()
        texts.forEach {
            ciphers.add(it.pad)
            ciphers.add(it.data)
        }
        val contestHash =
            hashFunction(extendedBaseHash.bytes, 0x23.toByte(), contest.sequenceOrder, jointPublicKey, ciphers)
        if (contestHash != contest.contestHash) {
            errs.add("    7.A. Incorrect contest hash")
        }

        if (isPreencrypt) {
            verifyPreencryptionShortCodes(contest, errs)
        }
    }

    // Verification 5 (Well-formedness of selection encryptions)
    private fun verifySelection(
        selection: EncryptedBallot.Selection,
        optionLimit: Int,
        errs: ErrorMessages
    ) {
        val svalid = selection.proof.verify(
            selection.encryptedVote,
            this.jointPublicKey,
            this.extendedBaseHash,
            optionLimit,
        )
        if (svalid is Err) {
            errs.add("    5. ChaumPedersenProof validation error = ${svalid.error} ")
        }
    }

    //////////////////////////////////////////////////////////////////////////////
    // ballot chaining, section 7

    fun verifyConfirmationChain(consumer: ElectionRecord, errs: ErrorMessages): Boolean {
        var ccount = 0
        consumer.encryptingDevices().forEach { device ->
            // println("verifyConfirmationChain device=$device")
            val ballotChainResult = consumer.readEncryptedBallotChain(device)
            if (ballotChainResult is Err) {
                errs.add(ballotChainResult.toString())
            } else {
                val ballotChain: EncryptedBallotChain = ballotChainResult.unwrap()
                val ballots = consumer.encryptedBallots(device) { true }

                // 7.D The initial hash code H0 satisfies H0 = H(HE ; 0x24, Baux,0 )
                // "and Baux,0 contains the unique voting device information". TODO ambiguous, change spec wording
                val H0 = hashFunction(extendedBaseHash.bytes, 0x24.toByte(), config.configBaux0).bytes

                // (7.E) For all 1 ≤ j ≤ ℓ, the additional input byte array used to compute Hj = H(Bj) is equal to
                //       Baux,j = H(Bj−1) ∥ Baux,0 .
                var prevCC = H0
                var first = true
                ballots.forEach { ballot ->
                    val expectedBaux = if (first) H0 else prevCC + config.configBaux0  // eq 7.D and 7.E
                    first = false
                    if (!expectedBaux.contentEquals(ballot.codeBaux)) {
                        errs.add("    7.E. additional input byte array Baux != H(Bj−1 ) ∥ Baux,0 for ballot=${ballot.ballotId}")
                    }
                    prevCC = ballot.confirmationCode.bytes
                }
                // 7.F The final additional input byte array is equal to Baux = H(Bℓ ) ∥ Baux,0 ∥ b(“CLOSE”, 5) and
                //      H(Bℓ ) is the final confirmation code on this device.
                val bauxFinal = prevCC + config.configBaux0 + "CLOSE".encodeToByteArray()

                // 7.G The closing hash is correctly computed as H = H(HE ; 0x24, Baux )
                val expectedClosingHash = hashFunction(extendedBaseHash.bytes, 0x24.toByte(), bauxFinal)
                if (expectedClosingHash != ballotChain.closingHash) {
                    errs.add("    7.G. The closing hash is not equal to H = H(HE ; 24, bauxFinal ) for encrypting device=$device")
                }
                ccount++
            }
        }

        logger.info { "verified $ccount chained ballots" }
        return !errs.hasErrors()
    }

    //////////////////////////////////////////////////////////////////////////////
    // ballot chaining without EncryptedBallotChain
    // where does closing hash get stored?
    // is there a separate config for every device ?? barf

    private val showChain = false
    fun verifyConfirmationChain2(consumer: Consumer, errs: ErrorMessages): Boolean {
        var allOk = true
        consumer.encryptingDevices().forEach { device ->
            val ballotChainResult = consumer.readEncryptedBallotChain(device)
            if (ballotChainResult is Err) {
                errs.add(ballotChainResult.toString())
            } else {
                val ballotChain: EncryptedBallotChain = ballotChainResult.unwrap()
                if (showChain) {
                    println("ballotChainOld for $device")
                    ballotChain.ballotIds.forEach { println("  $it") }
                }

                val ok = !verifyOneChain(device, ballotChain, consumer.iterateEncryptedBallots(device){ true }, errs )
                if (showChain) println(" device '$device' is ok = $ok")
                if (!ok) allOk = false
            }
        }
        return allOk
    }

    fun verifyOneChain(device: String, ballotChain: EncryptedBallotChain?, encryptedBallots: Iterable<EncryptedBallot>, errs: ErrorMessages): Boolean {
        var err = false
        val bauxMap = mutableMapOf<Int, BallotChainEntry>()
        val baux0 = hashFunction(extendedBaseHash.bytes, 0x24.toByte(), config.configBaux0).bytes
        val start = BallotChainEntry("START", 0)
        bauxMap[baux0.contentHashCode()] = start
        encryptedBallots.map { eballot ->
            val bauxj: ByteArray = eballot.confirmationCode.bytes + config.configBaux0
            bauxMap[bauxj.contentHashCode()] = BallotChainEntry(eballot.ballotId, eballot.codeBaux.contentHashCode())
        }

        if (showChain) {
            println("ballotChainNew")
            bauxMap.forEach { println("  $it") }
        }

        encryptedBallots.forEach { eballot ->
            val prev = bauxMap[eballot.codeBaux.contentHashCode()]
            if (prev != null) {
                if (prev.nextBallot != null) {
                    errs.add("    VerifyChain ${prev.ballotId} already has next")
                    err = true
                } else {
                    prev.nextBallot = ConfirmationCode(eballot)
                }
            } else {
                errs.add("    VerifyChain ${eballot.ballotId} cant find ${eballot.codeBaux.contentHashCode()}")
                err = true
            }
        }

        if (showChain) {
            println("ballotChainAfter")
            bauxMap.values.forEach { println("  $it") }
        }

        // 7.D The initial hash code H0 satisfies H0 = H(HE ; 0x24, Baux,0 )
        val H0 = hashFunction(extendedBaseHash.bytes, 0x24.toByte(), config.configBaux0).bytes
        var prevCC = H0
        var first = true

        val chainMap: Map<String, BallotChainEntry> = bauxMap.values.associateBy { it.ballotId }
        var ccount = 0
        var chain = start
        while (chain.nextBallot != null) {
            val nextBallot = chain.nextBallot!!
            if (ballotChain != null) {
                val check = (ballotChain.ballotIds[ccount] == nextBallot.ballotId)
                if (!check) err = true
            }

            // (7.E) For all 1 ≤ j ≤ ℓ, the additional input byte array used to compute Hj = H(Bj) is equal to
            //       Baux,j = H(Bj−1) ∥ Baux,0 .
            val expectedBaux = if (first) H0 else prevCC + config.configBaux0  // eq 7.D and 7.E
            first = false
            if (!expectedBaux.contentEquals(nextBallot.codeBaux)) {
                errs.add("    7.E. additional input byte array Baux != H(Bj−1 ) ∥ Baux,0 for ballot=${nextBallot.ballotId}")
            }
            prevCC = nextBallot.code.bytes

            chain = chainMap[nextBallot.ballotId]!!
            ccount++
        }
        // 7.F The final additional input byte array is equal to Baux = H(Bℓ ) ∥ Baux,0 ∥ b(“CLOSE”, 5) and
        //      H(Bℓ ) is the final confirmation code on this device.
        val bauxFinal = prevCC + config.configBaux0 + "CLOSE".encodeToByteArray()

        // 7.G The closing hash is correctly computed as H = H(HE ; 0x24, Baux )
        // TODO where does the closing hash get stored ?? Must be by device??
        val expectedClosingHash = hashFunction(extendedBaseHash.bytes, 0x24.toByte(), bauxFinal)
        if (ballotChain != null && expectedClosingHash != ballotChain.closingHash) {
            errs.add("    7.G. The closing hash is not equal to H = H(HE ; 24, bauxFinal ) for encrypting device=$device")
        }

        logger.info { "verified $ccount chained ballots for device $device" }
        return err || errs.hasErrors()
    }

    class BallotChainEntry(val ballotId: String, val bauxHash: Int, var nextBallot: ConfirmationCode? = null) {
        override fun toString(): String {
            return "BallotChainEntry(ballotId='$ballotId', bauxHash = $bauxHash, nextBallot=${nextBallot})"
        }
    }

    data class ConfirmationCode(val ballotId: String, val code: UInt256, val codeBaux: ByteArray) {
        constructor(ballot: EncryptedBallot): this(ballot.ballotId, ballot.confirmationCode, ballot.codeBaux)

        override fun toString(): String {
            return "$ballotId"
        }
    }

    //////////////////////////////////////////////////////////////
    // coroutines
    private var count = 0
    private fun CoroutineScope.produceBallots(producer: Iterable<EncryptedBallot>): ReceiveChannel<EncryptedBallot> =
        produce {
            for (ballot in producer) {
                send(ballot)
                yield()
                count++
            }
            channel.close()
        }

    private val confirmationCodes = mutableListOf<ConfirmationCode>()
    private val mutex = Mutex()

    private fun CoroutineScope.launchVerifier(
        id: Int,
        input: ReceiveChannel<EncryptedBallot>,
        aggregator: SelectionAggregator,
        verify: (EncryptedBallot) -> Boolean,
    ) = launch(Dispatchers.Default) {
        for (ballot in input) {
            if (debugBallots) println("$id channel working on ${ballot.ballotId}")
            val result = verify(ballot)
            mutex.withLock {
                if (result) {
                    aggregator.add(ballot) // this slows down the ballot parallelism: nselections * (2 (modP multiplication))
                    confirmationCodes.add( ConfirmationCode(ballot))
                }
            }
            yield()
        }
    }

    companion object {
        private val logger = KotlinLogging.logger("VerifyEncryptedBallots")
    }
}