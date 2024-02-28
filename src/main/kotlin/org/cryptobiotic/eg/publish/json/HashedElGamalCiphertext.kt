package org.cryptobiotic.eg.publish.json

import org.cryptobiotic.eg.core.*
import kotlinx.serialization.Serializable
import org.cryptobiotic.eg.core.Base16.fromHex
import org.cryptobiotic.eg.core.Base16.toHex

@Serializable
data class HashedElGamalCiphertextJson(
    val c0: ElementModPJson,
    val c1: String, // ByteArray
    val c2: UInt256Json,
    val numBytes: Int // TODO needed?
)

fun HashedElGamalCiphertext.publishJson() =
    HashedElGamalCiphertextJson(this.c0.publishJson(), this.c1.toHex(), this.c2.publishJson(), this.numBytes)

fun HashedElGamalCiphertextJson.import(group : GroupContext) : HashedElGamalCiphertext? {
    val c0 = this.c0.import(group)
    val c1 = this.c1.fromHex()
    val c2 = this.c2.import()
    return if (c0 == null || c1 == null || c2 == null) null else HashedElGamalCiphertext(c0, c1, c2, numBytes)
}