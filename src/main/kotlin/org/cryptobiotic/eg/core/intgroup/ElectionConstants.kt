package org.cryptobiotic.eg.core.intgroup

import org.cryptobiotic.eg.core.Base16.toHex
import org.cryptobiotic.eg.core.UInt256
import org.cryptobiotic.eg.core.hashFunction

const val protocolVersion = "v2.0.0"

/**
 * A public description of the mathematical group used for the encryption and processing of ballots.
 * The byte arrays are defined to be big-endian.
 */
data class ElectionConstants(
    /** name of the constants defining the Group*/
    val name: String,
    /** large prime or P. */
    val largePrime: ByteArray,
    /** small prime or Q. */
    val smallPrime: ByteArray,
    /** cofactor or R. */
    val cofactor: ByteArray,
    /** generator or G. */
    val generator: ByteArray,
) {
    val hp = parameterBaseHash(this)

    // override because of the byte arrays
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as ElectionConstants

        if (name != other.name) return false
        if (!largePrime.contentEquals(other.largePrime)) return false
        if (!smallPrime.contentEquals(other.smallPrime)) return false
        if (!cofactor.contentEquals(other.cofactor)) return false
        if (!generator.contentEquals(other.generator)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + largePrime.contentHashCode()
        result = 31 * result + smallPrime.contentHashCode()
        result = 31 * result + cofactor.contentHashCode()
        result = 31 * result + generator.contentHashCode()
        return result
    }

    override fun toString(): String {
        return "name = ${this.name}\n" +
                "largePrime = ${this.largePrime.toHex()}\n" +
                "smallPrime = ${this.smallPrime.toHex()}\n" +
                "  cofactor = ${this.cofactor.toHex()}\n" +
                " generator = ${this.generator.toHex()}"
    }
}

fun parameterBaseHash(primes : ElectionConstants) : UInt256 {
    // HP = H(ver; 0x00, p, q, g) ; spec 2.0.0 p 16, eq 4
    // The symbol ver denotes the version byte array that encodes the used version of this specification.
    // The array has length 32 and contains the UTF-8 encoding of the string “v2.0.0” followed by 0x00-
    // bytes, i.e. ver = 0x76322E302E30 ∥ b(0, 27). FIX should be b(0, 26)
    val version = protocolVersion.encodeToByteArray()
    val HV = ByteArray(32) { if (it < version.size) version[it] else 0 }

    return hashFunction(
        HV,
        0x00.toByte(),
        primes.largePrime,
        primes.smallPrime,
        primes.generator,
    )
}