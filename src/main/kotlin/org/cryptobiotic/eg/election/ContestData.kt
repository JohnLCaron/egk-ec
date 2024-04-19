package org.cryptobiotic.eg.election

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import org.cryptobiotic.eg.core.*
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.eg.publish.decodeToContestData
import org.cryptobiotic.eg.publish.encodeToByteArray
import kotlin.math.max

enum class ContestDataStatus {
    normal, null_vote, over_vote, under_vote
}

/**
 * This information consists of any text written into one or more write-in text fields, information about overvotes,
 * undervotes, and null votes, and possibly other data about voter selections.
 */
data class ContestData(
    val overvotes: List<Int>,
    val writeIns: List<String>,
    val status: ContestDataStatus = if (overvotes.isNotEmpty()) ContestDataStatus.over_vote else ContestDataStatus.normal,
) {
    // Make sure that the HashedElGamalCiphertext message is exactly (votesAllowed + 1) * BLOCK_SIZE
    // If too large, remove extra writeIns, add "*" to list to indicate some were removed
    // If still too large, truncate writeIns to CHOP_WRITE_INS characters, append "*" to string to indicate truncated
    // If still too large, truncate overVote to (votesAllowed + 1), append "-1" to list to indicate some were removed
    // If now too small, add a filler string to make it exactly (votesAllowed + 1) * BLOCK_SIZE
    fun encrypt(
        publicKey: ElGamalPublicKey, // aka K
        extendedBaseHash: UInt256, // aka He
        contestId: String, // aka Λ
        contestIndex: Int, // ind_c(Λ)
        ballotNonce: UInt256,
        contestLimit: Int
    ): HashedElGamalCiphertext {

        val messageSize = (1 + contestLimit) * BLOCK_SIZE

        var trialContestData = this
        var trialContestDataBA = trialContestData.encodeToByteArray()
        var trialSize = trialContestDataBA.size
        val trialSizes = mutableListOf<Int>()
        trialSizes.add(trialSize)

        // remove extra write_ins, append a "*"
        if ((trialSize > messageSize) && trialContestData.writeIns.size > contestLimit) {
            val truncateWriteIns = trialContestData.writeIns.subList(0, contestLimit)
                .toMutableList()
            truncateWriteIns.add("*")
            trialContestData = trialContestData.copy(
                writeIns = truncateWriteIns,
            )
            trialContestDataBA = trialContestData.encodeToByteArray()
            trialSize = trialContestDataBA.size
            trialSizes.add(trialSize)
        }

        // chop write-in vote strings
        if ((trialSize > messageSize) && trialContestData.writeIns.isNotEmpty()) {
            val chop = max(CHOP_WRITE_INS, (messageSize - trialSize + contestLimit - 1) / contestLimit)
            val truncateWriteIns = trialContestData.writeIns.map {
                if (it.length <= CHOP_WRITE_INS) it else it.substring(0, chop) + "*"
            }
            trialContestData = trialContestData.copy(writeIns = truncateWriteIns)
            trialContestDataBA = trialContestData.encodeToByteArray()
            trialSize = trialContestDataBA.size
            trialSizes.add(trialSize)
        }

        // chop overvote list
        while (trialSize > messageSize && (trialContestData.overvotes.size > contestLimit + 1)) {
            val chopList = trialContestData.overvotes.subList(0, contestLimit + 1) + (-1)
            trialContestData = trialContestData.copy(overvotes = chopList)
            trialContestDataBA = trialContestData.encodeToByteArray()
            trialSize = trialContestDataBA.size
            trialSizes.add(trialSize)
        }

        // now fill it up so its a uniform message length, if needed
        if (trialSize < messageSize) {
            val filler = StringBuilder().apply {
                repeat(messageSize - trialSize - 2) { append("*") }
            }
            trialContestDataBA = trialContestData.encodeToByteArray(filler.toString())
            trialSize = trialContestDataBA.size
            trialSizes.add(trialSize)
        }
        logger.debug { "encodedData = $trialContestData trialSizes = $trialSizes" }

        return trialContestDataBA.encryptContestData(publicKey, extendedBaseHash, contestId, contestIndex, ballotNonce)
    }

    companion object {
        val logger = KotlinLogging.logger("ContestData")
        private const val BLOCK_SIZE: Int = 32
        private const val CHOP_WRITE_INS: Int = 30

        const val label = "share_enc_keys"
        const val contestDataLabel = "contest_data"
    }
}

fun ByteArray.encryptContestData(
    publicKey: ElGamalPublicKey, // aka K
    extendedBaseHash: UInt256, // aka He
    contestId: String, // aka Λ
    contestIndex: Int, // ind_c(Λ)
    ballotNonce: UInt256
): HashedElGamalCiphertext {

    val group = compatibleContextOrFail(publicKey.key)
    val contestDataNonce =
        hashFunction(extendedBaseHash.bytes, 0x20.toByte(), ballotNonce, contestIndex, ContestData.contestDataLabel)

    return this.encryptToHashedElGamal(
        group,
        publicKey,
        extendedBaseHash,
        0x22.toByte(),
        ContestData.label,
        "${ContestData.contestDataLabel}$contestId",
        contestDataNonce.toElementModQ(group),
    )
}

fun makeContestData(
    contestLimit: Int,
    optionLimit: Int,
    selections: List<PlaintextBallot.Selection>,
    writeIns: List<String>
): Pair<ContestData, Int> {
    // count the number of votes
    val votedFor = mutableListOf<Int>()
    var selectionOvervote = false
    selections.forEach { selection ->
        if (selection.vote > 0) {
            votedFor.add(selection.sequenceOrder)
            if (selection.vote > optionLimit) {
                selectionOvervote = true
            }
        }
    }

    // Compute the contest status
    val totalVotedFor = votedFor.size + writeIns.size
    val status = if (totalVotedFor == 0) ContestDataStatus.null_vote
        else if (selectionOvervote || totalVotedFor > contestLimit)  ContestDataStatus.over_vote
        else if (totalVotedFor < contestLimit)  ContestDataStatus.under_vote
        else ContestDataStatus.normal

    val votes = if (status == ContestDataStatus.over_vote) 0 else totalVotedFor

    val contestData = ContestData(
        if (status == ContestDataStatus.over_vote) votedFor else emptyList(),
        writeIns,
        status
    )

    return Pair(contestData, votes)
}

fun HashedElGamalCiphertext.decryptWithBetaToContestData(
    publicKey: ElGamalPublicKey, // aka K
    extendedBaseHash: UInt256, // aka He
    contestId: String, // aka Λ
    beta: ElementModP
): Result<ContestData, String> {

    val ba: ByteArray = this.decryptContestData(publicKey, extendedBaseHash, contestId, c0, beta)
        ?: return Err("decryptWithBetaToContestData did not succeed")
    return ba.decodeToContestData()
}

fun HashedElGamalCiphertext.decryptWithNonceToContestData(
    publicKey: ElGamalPublicKey, // aka K
    extendedBaseHash: UInt256, // aka He
    contestId: String, // aka Λ
    contestIndex: Int,
    ballotNonce: UInt256
): Result<ContestData, String> {

    val group = compatibleContextOrFail(publicKey.key)
    val contestDataNonce =
        hashFunction(extendedBaseHash.bytes, 0x20.toByte(), ballotNonce, contestIndex, ContestData.contestDataLabel)
    val (alpha, beta) = 0.encrypt(publicKey, contestDataNonce.toElementModQ(group))
    val ba: ByteArray = this.decryptContestData(publicKey, extendedBaseHash, contestId, alpha, beta)
        ?: return Err("decryptWithNonceToContestData did not succeed")
    return ba.decodeToContestData()
}

fun HashedElGamalCiphertext.decryptWithSecretKey(
    publicKey: ElGamalPublicKey, // aka K
    extendedBaseHash: UInt256, // aka He
    contestId: String, // aka Λ
    secretKey: ElGamalSecretKey
): ByteArray? = decryptContestData(publicKey, extendedBaseHash, contestId, c0, c0 powP secretKey.key)

fun HashedElGamalCiphertext.decryptContestData(
    publicKey: ElGamalPublicKey, // aka K
    extendedBaseHash: UInt256, // aka He
    contestId: String, // aka Λ
    alpha: ElementModP,
    beta: ElementModP
): ByteArray? {

    return this.decryptToByteArray(
        publicKey,
        extendedBaseHash,
        0x22.toByte(),
        ContestData.label,
        "${ContestData.contestDataLabel}$contestId",
        alpha,
        beta,
    )
}
