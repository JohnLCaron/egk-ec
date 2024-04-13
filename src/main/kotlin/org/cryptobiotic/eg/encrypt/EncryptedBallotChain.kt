package org.cryptobiotic.eg.encrypt

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
import org.cryptobiotic.eg.publish.Publisher
import org.cryptobiotic.util.ErrorMessages

// TODO error if ballotChain is already closed ??

// Let Baux,0 = "Baux,0 must contain at least a unique voting device identifier and possibly other voting device
// information as described above and as specified in the election manifest file." p 36.
//
// Then:
//  H0 = H(HE ; 0x24, Baux,0) (59)
//  Baux,1 = H0 ∥ Baux,0
//  H(B1) = H(HE ; 0x24, χ1 , χ2 , . . . , χmB , Baux,1 ).
//  Baux,j = Hj−1 ∥ Baux,0     (60)
//  H(Bj) = H(HE ; 0x24, χ1 , χ2 , . . . , χmB , Baux,j ).

data class EncryptedBallotChain(
    val encryptingDevice: String,
    val baux0: ByteArray,  // has device name if configBaux0 is empty
    val extendedBaseHash: UInt256,
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
            consumer: Consumer,
            device: String,
            ballotChainOverrideDir: String?,
            configBaux0: ByteArray,
            extendedBaseHash: UInt256,
        ): Pair<ByteArray?, EncryptedBallotChain> {
            var chain: EncryptedBallotChain? = null

            // If chainCodes is true, and configBaux0 is empty, then the device name UTF-8 bytes will be used when creating the
            // confirmation codes during encryption. This allows the configuration file to be used across multiple devices,
            // and still have the device name as part of the ballot chaining as required in the spec.
            val baux0 = if (configBaux0.isEmpty()) device.encodeToByteArray() else configBaux0

            val chainResult = consumer.readEncryptedBallotChain(device, ballotChainOverrideDir)
            val codeBaux = if (chainResult is Ok) {
                if (showChain) print(" next ")
                chain = chainResult.unwrap()
                // Baux,j = Hj−1 ∥ Baux,0
                chain.lastConfirmationCode.bytes + baux0
            } else {
                if (showChain) print(" first ")
                // otherwise this is the first one in the chain
                // H0 = H(HE ; 0x24, Baux,0 ), eq (59)
                // Baux,1 = H0 ∥ Baux,0
                hashFunction(extendedBaseHash.bytes, 0x24.toByte(), baux0).bytes + baux0
            }

            if (showChain) println( " makeCodeBaux ${codeBaux.contentToString()}")

            if (chain == null)
                chain = EncryptedBallotChain(device, baux0, extendedBaseHash, emptyList(), UInt256.ZERO, null)

            return Pair(codeBaux, chain)
        }

        // append ballotId, confirmationCode to the ballotChain
        fun writeChain(
            publisher: Publisher,
            ballotChainOverrideDir: String?,
            ballotId: String,
            confirmationCode: UInt256,
            currentChain: EncryptedBallotChain
        ): EncryptedBallotChain? {
            val ids = currentChain.ballotIds + ballotId
            val newChain = currentChain.copy(ballotIds = ids, lastConfirmationCode = confirmationCode)

            try {
                publisher.writeEncryptedBallotChain(newChain, ballotChainOverrideDir)
            } catch (t: Throwable) {
                logger.error(t) { "error writing chain ${t.message}" }
                return null
            }
            return newChain
        }

        // append finalConfirmationCode and close the ballotChain
        fun terminateChain(
            publisher: Publisher,
            device: String,
            ballotChainOverrideDir: String?,
            currentChain: EncryptedBallotChain,
        ): Int {
            // The chain should be closed at the end of an election by forming and publishing
            //    H = H(HE ; 0x24, Baux ) (61) "closing hash"
            // using Baux = H(Bℓ ) ∥ Baux,0 ∥ b(“CLOSE”, 5), where H(Bℓ ) is the final confirmation code in the chain.

            val extendedBaseHash: UInt256 = currentChain.extendedBaseHash
            val finalConfirmationCode = currentChain.lastConfirmationCode
            val bauxFinal = finalConfirmationCode.bytes + currentChain.baux0 + "CLOSE".encodeToByteArray()
            val hashFinal = hashFunction(extendedBaseHash.bytes, 0x24.toByte(), bauxFinal)
            val ballotChain =  currentChain.copy(closingHash = hashFinal)

            try {
                publisher.writeEncryptedBallotChain(ballotChain, ballotChainOverrideDir)
            } catch (t: Throwable) {
                logger.error(t) { "error writing chain ${t.message}" }
                return 6
            }
            return 0
        }

        // read encrypted ballots and (re)create the chain from the eballot.codeBaux
        fun assembleChain(
            consumer: Consumer,
            device: String,
            ballotChainOverrideDir: String?,
            configBaux0: ByteArray,
            extendedBaseHash: UInt256,
            errs: ErrorMessages
        ): EncryptedBallotChain? {
            val baux0 = if (configBaux0.isEmpty()) device.encodeToByteArray() else configBaux0

            // 7.D The initial hash code H0 satisfies H0 = H(HE ; 0x24, Baux,0 )
            val H0 = hashFunction(extendedBaseHash.bytes, 0x24.toByte(), baux0).bytes + baux0
            val start = BallotChainEntry("START", 0)

            val encryptedBallots = consumer.iterateEncryptedBallots(device) { true } // TODO ballotChainOverrideDir ??
            val bauxMap = mutableMapOf<Int, BallotChainEntry>()
            bauxMap[H0.contentHashCode()] = start
            encryptedBallots.map { eballot ->
                // 7.E the additional input byte array used to compute Hj = H(Bj) is equal to Baux,j = H(Bj−1) ∥ Baux,0 .
                val bauxj: ByteArray = eballot.confirmationCode.bytes + baux0
                bauxMap[bauxj.contentHashCode()] =
                    BallotChainEntry(eballot.ballotId, eballot.codeBaux.contentHashCode())
            }

            if (showChain) {
                println("assembleChain")
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
            val bauxFinal = lastCC.bytes + baux0 + "CLOSE".encodeToByteArray()
            val hashFinal = hashFunction(extendedBaseHash.bytes, 0x24.toByte(), bauxFinal)

            return EncryptedBallotChain(device, baux0, extendedBaseHash, ballotIdList, lastCC, hashFinal)
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
