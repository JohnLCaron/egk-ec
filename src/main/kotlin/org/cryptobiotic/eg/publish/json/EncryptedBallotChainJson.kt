package org.cryptobiotic.eg.publish.json

import org.cryptobiotic.eg.core.*
import org.cryptobiotic.util.ErrorMessages
import kotlinx.serialization.Serializable
import org.cryptobiotic.eg.encrypt.EncryptedBallotChain

@Serializable
data class EncryptedBallotChainJson(
    val encrypting_device: String,
    val baux0: ByteArray,
    val election_base_hash: UInt256Json, // Hb
    val ballot_ids: List<String>,
    val last_confirmation_code: UInt256Json,
    val closing_hash: UInt256Json?,
    val metadata: Map<String, String> = emptyMap(),
)

fun EncryptedBallotChain.publishJson() = EncryptedBallotChainJson(
    this.encryptingDevice,
    this.baux0,
    this.extendedBaseHash.publishJson(),
    this.ballotIds,
    this.lastConfirmationCode.publishJson(),
    this.closingHash?.publishJson(),
    this.metadata,
)

fun EncryptedBallotChainJson.import(errs : ErrorMessages): EncryptedBallotChain? {
    val last = last_confirmation_code.import() ?: errs.addNull("malformed last_confirmation_code") as UInt256?
    val base_hash = election_base_hash.import() ?: errs.addNull("malformed election_base_hash") as UInt256?
    val closing_hash = if (closing_hash == null) null else closing_hash.import() ?: errs.addNull("malformed closing_hash") as UInt256?

    return if (errs.hasErrors()) null
    else EncryptedBallotChain(
        this.encrypting_device,
        this.baux0,
        base_hash!!,
        this.ballot_ids,
        last!!,
        closing_hash,
        this.metadata,
    )
}