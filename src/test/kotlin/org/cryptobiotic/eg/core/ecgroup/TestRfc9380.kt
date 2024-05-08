package org.cryptobiotic.eg.core.ecgroup

import org.cryptobiotic.eg.core.Base16.fromHex
import org.cryptobiotic.eg.core.UInt256
import org.cryptobiotic.eg.core.elGamalKeyPairFromRandom
import org.cryptobiotic.eg.core.hashFunction
import kotlin.test.Test
import kotlin.test.assertEquals

// https://www.rfc-editor.org/rfc/rfc9380.pdf Appendix J-1
class TestRfc9380 {

    @Test
    fun testRfc() {
        val group = EcGroupContext("P-256")
        val dst = "QUUX-V01-CS02-with-P256_XMD:SHA-256_SSWU_RO_"
        val test = RFC9380(group, dst.toByteArray(), 16)

        testRfc(test, ByteArray(0))
        testRfc(test, "abcdef0123456789".fromHex()!!)
        testRfc(test, "abcdef0123456789".toByteArray())
    }

    @Test
    fun testRfcWithHash() {
        val group = EcGroupContext("P-256")
        val dst = "QUUX-V01-CS02-with-P256_XMD:SHA-256_SSWU_RO_"
        val test = RFC9380(group, dst.toByteArray(), 16)

        val keypair = elGamalKeyPairFromRandom(group)
        val extendedBaseHash = UInt256.random()
        val h = hashFunction(extendedBaseHash.bytes, 0x42.toByte(), keypair.publicKey, keypair.secretKey.key)
        testRfc(test, h.bytes)
    }

    fun testRfc(rfc: RFC9380, msg: ByteArray) {
        val peat = rfc.hash_to_field(msg)
        val repeat = rfc.hash_to_field(msg)

        println("q = ${peat}")
        assertEquals(peat, repeat)
    }
}