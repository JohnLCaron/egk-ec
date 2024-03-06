package org.cryptobiotic.eg.encrypt

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.eg.core.Base64.fromBase64
import org.cryptobiotic.eg.core.UInt256
import org.cryptobiotic.eg.core.hashFunction
import org.cryptobiotic.eg.core.plus
import org.cryptobiotic.eg.core.toUInt256
import org.cryptobiotic.eg.election.EncryptedBallot
import org.cryptobiotic.eg.publish.Consumer
import org.cryptobiotic.eg.publish.makePublisher
import org.cryptobiotic.util.ErrorMessages

// TODO auto add device name do dont have to have config for each device
// TODO error if ballotChain is already closed ??

data class EncryptedBallotChain(
    val encryptingDevice: String,
    val ballotIds: List<String>,
    val lastConfirmationCode: UInt256,
    val closingHash: UInt256?,
    val metadata: Map<String, String> = emptyMap(),
) {

    companion object {
        private val logger = KotlinLogging.logger("EncryptedBallotChain")
        private val showChain = false

        // calculate codeBaux from previousConfirmationCode to be used in the current confirmationCode
        fun makeCodeBaux(
            device: String,
            encryptBallotDir: String?,
            configBaux0: ByteArray,
            extendedBaseHash: UInt256,
            previousConfirmationCode: String,
            consumer: Consumer
        ): Pair<ByteArray?, EncryptedBallotChain?> {
            var chain: EncryptedBallotChain? = null

            val codeBaux = if (previousConfirmationCode.isEmpty()) {
                val chainResult = consumer.readEncryptedBallotChain(device, encryptBallotDir)
                if (chainResult is Ok) {
                    chain = chainResult.unwrap()
                    chain.lastConfirmationCode.bytes + configBaux0
                } else {
                    // otherwise this is the first one in the chain
                    // H0 = H(HE ; 0x24, Baux,0 ), eq (59)
                    hashFunction(extendedBaseHash.bytes, 0x24.toByte(), configBaux0).bytes
                }
            } else { // caller is supplying the previousConfirmationCode
                val previousConfirmationCodeBytes = previousConfirmationCode.fromBase64()
                if (previousConfirmationCodeBytes == null) {
                    logger.error { "previousConfirmationCodeBytes '$previousConfirmationCode' invalid" }
                    null // empty byte array ??
                } else {
                    previousConfirmationCodeBytes + configBaux0
                }
            }
            return Pair(codeBaux, chain)
        }

        // append ballotId, confirmationCode to the ballotChain
        fun storeChain(
            device: String,
            encryptBallotDir: String,
            ballotId: String,
            confirmationCode: UInt256,
            currentChain: EncryptedBallotChain?
        ): Int {
            val newChain = if (currentChain != null) {
                // add to previous
                val ids = currentChain.ballotIds + ballotId
                currentChain.copy(ballotIds = ids, lastConfirmationCode = confirmationCode)
            } else {
                // first time
                EncryptedBallotChain(device, listOf(ballotId), confirmationCode, null)
            }

            try {
                val publisher = makePublisher(encryptBallotDir, false)
                publisher.writeEncryptedBallotChain(newChain, encryptBallotDir)
            } catch (t: Throwable) {
                logger.error(t) { "error writing chain ${t.message}" }
                return 6
            }
            return 0
        }

        // append finalConfirmationCode and close the ballotChain
        fun terminateChain(
            device: String,
            encryptBallotDir: String,
            finalConfirmationCode: String,
            consumer: Consumer
        ): Int {
            val initResult = consumer.readElectionInitialized()
            if (initResult is Err) {
                logger.error { "readElectionInitialized error ${initResult.error}" }
                return 1
            }
            val electionInit = initResult.unwrap()
            val configBaux0: ByteArray = electionInit.config.configBaux0
            val extendedBaseHash: UInt256 = electionInit.extendedBaseHash

            // The chain should be closed at the end of an election by forming and publishing
            //    H = H(HE ; 0x24, Baux ) (61)
            // using Baux = H(Bℓ ) ∥ Baux,0 ∥ b(“CLOSE”, 5), where H(Bℓ ) is the final confirmation code in the chain.

            val ballotChain = if (finalConfirmationCode.isEmpty()) {
                val chainResult = consumer.readEncryptedBallotChain(device, encryptBallotDir)
                if (chainResult is Err) {
                    logger.error { "terminateChain failed on readEncryptedBallotChain err = $chainResult" }
                    return 4
                }
                val chain = chainResult.unwrap()

                val finalConfirmationCode = chain.lastConfirmationCode
                val bauxFinal = finalConfirmationCode.bytes + configBaux0 + "CLOSE".encodeToByteArray()
                val hashFinal = hashFunction(extendedBaseHash.bytes, 0x24.toByte(), bauxFinal)
                chain.copy(closingHash = hashFinal)

            } else { // caller is supplying the finalConfirmationCode64
                val finalConfirmationCodeBytes = finalConfirmationCode.fromBase64()
                if (finalConfirmationCodeBytes == null) {
                    logger.error { "previousConfirmationCodeBytes '$finalConfirmationCode' invalid" }
                    return 4
                }
                val finalConfirmationCode = finalConfirmationCodeBytes.toUInt256()!!
                val bauxFinal = finalConfirmationCodeBytes + configBaux0 + "CLOSE".encodeToByteArray()
                val hashFinal = hashFunction(extendedBaseHash.bytes, 0x24.toByte(), bauxFinal)
                // we dont have the ballot ids
                EncryptedBallotChain(device, emptyList(), finalConfirmationCode, hashFinal)
            }

            try {
                val publisher = makePublisher(encryptBallotDir, false)
                publisher.writeEncryptedBallotChain(ballotChain)
            } catch (t: Throwable) {
                logger.error(t) { "error writing chain ${t.message}" }
                return 6
            }
            return 0
        }

        // read encrypted ballots and (re)create the chain
        fun assembleChain(
            device: String,
            encryptBallotDir: String?, // TODO
            configBaux0: ByteArray,
            extendedBaseHash: UInt256,
            consumer: Consumer,
            errs: ErrorMessages
        ): EncryptedBallotChain? {
            // 7.D The initial hash code H0 satisfies H0 = H(HE ; 0x24, Baux,0 )
            val baux0 = hashFunction(extendedBaseHash.bytes, 0x24.toByte(), configBaux0).bytes
            val start = BallotChainEntry("START", 0)

            val encryptedBallots = consumer.iterateEncryptedBallots(device) { true }
            val bauxMap = mutableMapOf<Int, BallotChainEntry>()
            bauxMap[baux0.contentHashCode()] = start
            encryptedBallots.map { eballot ->
                // 7.E the additional input byte array used to compute Hj = H(Bj) is equal to Baux,j = H(Bj−1) ∥ Baux,0 .
                val bauxj: ByteArray = eballot.confirmationCode.bytes + configBaux0
                bauxMap[bauxj.contentHashCode()] =
                    BallotChainEntry(eballot.ballotId, eballot.codeBaux.contentHashCode())
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
                    } else {
                        prev.nextBallot = BallotInfo(eballot)
                    }
                } else {
                    errs.add("    VerifyChain ${eballot.ballotId} cant find ${eballot.codeBaux.contentHashCode()}")
                }
            }

            if (showChain) {
                println("ballotChainAfter")
                bauxMap.values.forEach { println("  $it") }
            }

            val chainMap: Map<String, BallotChainEntry> = bauxMap.values.associateBy { it.ballotId }
            val ballotIdList = mutableListOf<String>()
            var lastCC: UInt256? = null
            var chain = start
            while (chain.nextBallot != null) {
                val nextBallot = chain.nextBallot!!
                ballotIdList.add(nextBallot.ballotId)
                lastCC = nextBallot.code
                chain = chainMap[nextBallot.ballotId]!!
            }
            if (lastCC == null) {
                errs.add("    empty chain")
                return null
            }

            // 7.F The final additional input byte array is equal to Baux = H(Bℓ ) ∥ Baux,0 ∥ b(“CLOSE”, 5) where
            //      H(Bℓ ) is the final confirmation code on this device.
            val bauxFinal = lastCC.bytes + configBaux0 + "CLOSE".encodeToByteArray()
            val hashFinal = hashFunction(extendedBaseHash.bytes, 0x24.toByte(), bauxFinal)

            return EncryptedBallotChain(device, ballotIdList, lastCC, hashFinal)
        }

    }

    private class BallotChainEntry(val ballotId: String, val bauxHash: Int, var nextBallot: BallotInfo? = null) {
        override fun toString(): String {
            return "BallotChainEntry(ballotId='$ballotId', bauxHash = $bauxHash, nextBallot=${nextBallot})"
        }
    }

    private data class BallotInfo(val ballotId: String, val code: UInt256, val codeBaux: ByteArray) {
        constructor(ballot: EncryptedBallot) : this(ballot.ballotId, ballot.confirmationCode, ballot.codeBaux)

        override fun toString(): String {
            return "$ballotId"
        }
    }
}
