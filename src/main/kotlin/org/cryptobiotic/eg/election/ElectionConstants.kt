package org.cryptobiotic.eg.election

import org.cryptobiotic.eg.core.UInt256
import org.cryptobiotic.eg.core.hashFunction

enum class GroupType { IntegerGroup, EllipticCurve }

data class ElectionConstants(
    val name: String,
    val type: GroupType,
    val protocolVersion: String,
    val constants: Map<String, ByteArray>
) {
    val parameterBaseHash by lazy {
        parameterBaseHash(this).bytes
    }
}

fun parameterBaseHash(constants : ElectionConstants) : UInt256 {
    // HP = H(ver; 0x00, p, q, g) ; spec 2.0.0 p 16, eq 4
    // The symbol ver denotes the version byte array that encodes the used version of this specification.
    // The array has length 32 and contains the UTF-8 encoding of the string “v2.0.0” followed by 0x00-
    // bytes, i.e. ver = 0x76322E302E30 ∥ b(0, 27). FIX should be b(0, 26)
    val version = constants.constants["protocolVersion"]!!
    val HV = ByteArray(32) { if (it < version.size) version[it] else 0 }

    return if (constants.type == GroupType.EllipticCurve)
        hashFunction(
            HV,
            0x00.toByte(),
            constants.constants["a"]!!,
            constants.constants["b"]!!,
            constants.constants["primeModulus"]!!,
            constants.constants["g"]!!,
            constants.constants["h"]!!,
        )
    else hashFunction(
        HV,
        0x00.toByte(),
        constants.constants["largePrime"]!!,
        constants.constants["smallPrime"]!!,
        constants.constants["generator"]!!,
    )
}
