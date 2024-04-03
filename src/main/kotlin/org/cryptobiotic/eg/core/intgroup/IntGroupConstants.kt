package org.cryptobiotic.eg.core.intgroup

import org.cryptobiotic.eg.election.ElectionConstants
import org.cryptobiotic.eg.election.GroupType
import java.math.BigInteger

/**
 * A public description of the mathematical group used for the encryption and processing of ballots.
 * The byte arrays are defined to be big-endian.
 */
data class IntGroupConstants(
    /** name of the constants defining the Group*/
    val name: String,
    /** large prime or P. */
    val largePrime: BigInteger,
    /** small prime or Q. */
    val smallPrime: BigInteger,
    /** cofactor or R. */
    val cofactor: BigInteger,
    /** generator or G. */
    val generator: BigInteger,
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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as IntGroupConstants

        if (name != other.name) return false
        if (largePrime != other.largePrime) return false
        if (smallPrime != other.smallPrime) return false
        if (cofactor != other.cofactor) return false
        if (generator != other.generator) return false

        return true
    }

    fun isCompatible(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as IntGroupConstants

        if (largePrime != other.largePrime) return false
        if (smallPrime != other.smallPrime) return false
        if (cofactor != other.cofactor) return false
        if (generator != other.generator) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + largePrime.hashCode()
        result = 31 * result + smallPrime.hashCode()
        result = 31 * result + cofactor.hashCode()
        result = 31 * result + generator.hashCode()
        return result
    }

    override fun toString(): String {
        return "GroupConstants(name='$name', largePrime=$largePrime, smallPrime=$smallPrime, cofactor=$cofactor, generator=$generator)"
    }

    companion object {
        const val protocolVersion = "v2.0.0"
    }
}