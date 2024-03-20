package org.cryptobiotic.eg.election

import org.cryptobiotic.eg.core.*

/** Interface used in the crypto routines for easy mocking. */
interface EncryptedBallotIF {
    val ballotId: String
    val encryptedSn: ElGamalCiphertext?
    val encryptedStyle: ElGamalCiphertext? // TODO reconsider, kludge
    val electionId : UInt256
    val contests: List<Contest>
    val state: EncryptedBallot.BallotState

    interface Contest {
        val contestId: String
        val sequenceOrder: Int
        val selections: List<Selection>
        val contestData: HashedElGamalCiphertext?
    }

    interface Selection {
        val selectionId: String
        val sequenceOrder: Int
        val encryptedVote: ElGamalCiphertext
    }

}