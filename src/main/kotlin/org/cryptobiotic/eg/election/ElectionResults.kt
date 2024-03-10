package org.cryptobiotic.eg.election

import org.cryptobiotic.eg.core.ElGamalPublicKey
import org.cryptobiotic.eg.core.UInt256

/** Results of tallying some collection of ballots, namely an EncryptedTally. */
data class TallyResult(
    val electionInitialized: ElectionInitialized,
    val encryptedTally: EncryptedTally,
    val tallyIds: List<String>,
    val metadata: Map<String, String> = emptyMap(),
) {
    fun jointPublicKey(): ElGamalPublicKey {
        return ElGamalPublicKey(electionInitialized.jointPublicKey)
    }
    fun extendedBaseHash(): UInt256 {
        return electionInitialized.extendedBaseHash
    }
    fun numberOfGuardians(): Int {
        return electionInitialized.config.numberOfGuardians
    }
    fun quorum(): Int {
        return electionInitialized.config.quorum
    }
}

/** Results of decrypting an EncryptedTally. */
data class DecryptionResult(
    val tallyResult: TallyResult,
    val decryptedTally: DecryptedTallyOrBallot,
    val metadata: Map<String, String> = emptyMap(),
)