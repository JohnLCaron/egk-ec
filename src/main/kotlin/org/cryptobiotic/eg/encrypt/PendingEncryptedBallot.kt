package org.cryptobiotic.eg.encrypt

import org.cryptobiotic.eg.core.*
import org.cryptobiotic.eg.election.EncryptedBallot

/**
 * Intermediate stage while encrypting. Does not have the extra Pre-encryption info, nor the state.
 * Contains nonces which are discarded when converted to EncryptedBallot. */
data class PendingEncryptedBallot(
    val ballotId: String,
    val ballotStyleId: String,
    val encryptingDevice: String,
    val timestamp: Long,
    val codeBaux: ByteArray, // Baux in spec 2.0.0, eq 58
    val confirmationCode: UInt256, // tracking code, H(B), eq 58
    val electionId : UInt256, // aka extendedBaseHash
    val contests: List<Contest>,
    val ballotNonce: UInt256,
    val encryptedSN: ElGamalCiphertext?,
    val isPreEncrypt: Boolean = false,
) {
    data class Contest(
        val contestId: String, // matches ContestDescription.contestIdd
        val sequenceOrder: Int, // matches ContestDescription.sequenceOrder
        val contestHash: UInt256, // eq 57
        val selections: List<Selection>,
        val proof: ChaumPedersenRangeProofKnownNonce,
        val contestData: HashedElGamalCiphertext,
    )

    data class Selection(
        val selectionId: String, // matches SelectionDescription.selectionId
        val sequenceOrder: Int, // matches SelectionDescription.sequenceOrder
        val ciphertext: ElGamalCiphertext, //  the encrypted vote
        val proof: ChaumPedersenRangeProofKnownNonce,
        val selectionNonce: ElementModQ,
    )
}

fun PendingEncryptedBallot.cast(): EncryptedBallot {
    return this.submit(EncryptedBallot.BallotState.CAST)
}

fun PendingEncryptedBallot.spoil(): EncryptedBallot {
    return this.submit(EncryptedBallot.BallotState.SPOILED)
}

fun PendingEncryptedBallot.submit(state: EncryptedBallot.BallotState): EncryptedBallot {
    return EncryptedBallot(
        this.ballotId,
        this.ballotStyleId,
        this.encryptingDevice,
        this.timestamp,
        this.codeBaux,
        this.confirmationCode,
        this.electionId,
        this.contests.map { it.submit() },
        state,
        this.encryptedSN,
        this.isPreEncrypt,
    )
}

fun PendingEncryptedBallot.Contest.submit(): EncryptedBallot.Contest {
    return EncryptedBallot.Contest(
        this.contestId,
        this.sequenceOrder,
        this.contestHash,
        this.selections.map { it.submit() },
        this.proof,
        this.contestData,
    )
}

fun PendingEncryptedBallot.Selection.submit(): EncryptedBallot.Selection {
    return EncryptedBallot.Selection(
        this.selectionId,
        this.sequenceOrder,
        this.ciphertext,
        this.proof,
    )
}