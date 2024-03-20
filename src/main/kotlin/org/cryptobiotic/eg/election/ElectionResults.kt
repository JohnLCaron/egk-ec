package org.cryptobiotic.eg.election

/** Results of tallying some collection of ballots, namely an EncryptedTally. */
data class TallyResult(
    val electionInitialized: ElectionInitialized,
    val encryptedTally: EncryptedTally,
    val tallyIds: List<String>,
    val metadata: Map<String, String> = emptyMap(),
) {
    fun jointPublicKey() = electionInitialized.jointPublicKey

    fun extendedBaseHash() = electionInitialized.extendedBaseHash

    fun numberOfGuardians() = electionInitialized.config.numberOfGuardians

    fun quorum() = electionInitialized.config.quorum
}

/** Results of decrypting an EncryptedTally. */
data class DecryptionResult(
    val tallyResult: TallyResult,
    val decryptedTally: DecryptedTallyOrBallot,
    val metadata: Map<String, String> = emptyMap(),
)