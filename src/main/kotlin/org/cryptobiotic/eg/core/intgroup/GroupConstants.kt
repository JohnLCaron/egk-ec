package org.cryptobiotic.eg.core.intgroup

import org.cryptobiotic.eg.core.Base16.toHex
import org.cryptobiotic.eg.core.UInt256
import org.cryptobiotic.eg.core.hashFunction
import org.cryptobiotic.eg.election.ElectionConstants
import org.cryptobiotic.eg.election.GroupType

const val protocolVersion = "v2.0.0"

/**
 * A public description of the mathematical group used for the encryption and processing of ballots.
 * The byte arrays are defined to be big-endian.
 */
data class GroupConstants(
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

    val constants by lazy {
        ElectionConstants(name, GroupType.IntegerGroup, protocolVersion,
            mapOf(
                "largePrime" to largePrime,
                "smallPrime" to smallPrime,
                "cofactor" to cofactor,
                "generator" to generator,
                )
            )
    }

    // override because of the byte arrays
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as GroupConstants

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