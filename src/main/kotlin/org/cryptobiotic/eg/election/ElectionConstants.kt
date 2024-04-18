package org.cryptobiotic.eg.election

import org.cryptobiotic.eg.core.UInt256
import org.cryptobiotic.eg.core.hashFunction
import org.cryptobiotic.eg.core.normalize
import java.math.BigInteger

/**
 * Generalization of ElectionGuard 2.0 section 3.1 "Parameter requirements"
 * to also describe elliptic curve groups, as well as the ElectionGuard integer group.
 * Note that this class is just a container for named BigInteger parameters.
 */
enum class GroupType { IntegerGroup, EllipticCurve }

data class ElectionConstants(
    val name: String,
    val type: GroupType,
    val protocolVersion: String,
    val constants: Map<String, BigInteger> // 3.1.1 Standard baseline cryptographic parameters
) {
    val parameterBaseHash by lazy {
        parameterBaseHash(this).bytes
    }

    override fun toString(): String {
        return "ElectionConstants(name='$name', type=$type, protocolVersion='$protocolVersion', constants=$constants)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ElectionConstants

        if (name != other.name) return false
        if (type != other.type) return false
        if (protocolVersion != other.protocolVersion) return false
        if (constants != other.constants) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + protocolVersion.hashCode()
        result = 31 * result + constants.hashCode()
        return result
    }
}

/**
 *  ElectionGuard 2.0 section 3.1.2 Parameter base hash (Hp).
 *  Generalize to also allow EllipticCurve in the obvious way.
 */
fun parameterBaseHash(constants : ElectionConstants) : UInt256 {
    // spec 2.0.0 p 16, eq 4
    //   integer groups : HP = H(ver; 0x00, p, q, g)
    //      The symbol ver denotes the version byte array that encodes the used version of this specification.
    //      The array has length 32 and contains the UTF-8 encoding of the string “v2.0.0” followed by 0x00 bytes
    //      i.e. ver = 0x76322E302E30 ∥ b(0, 26)
    //      ver = org.cryptobiotic.eg.core.intgroup.GroupConstants.protocolVersion = "v2.0.0"

    //   elliptic groups: HP = H(ver; 0x00, elliptic parameters... )
    //      ver = org.cryptobiotic.eg.core.ecgroup.VecGroup.protocolVersion = "v2.1.0"

    val version = constants.protocolVersion.toByteArray()
    val HV = ByteArray(32) { if (it < version.size) version[it] else 0 }

    return if (constants.type == GroupType.EllipticCurve)
        hashFunction(
            HV,
            0x00.toByte(),
            constants.constants["a"]!!.normalize(32),
            constants.constants["b"]!!.normalize(32),
            constants.constants["primeModulus"]!!.normalize(32),
            constants.constants["g.x"]!!.normalize(32),
            constants.constants["g.y"]!!.normalize(32),
            constants.constants["h"]!!.normalize(32),
        )
    else hashFunction(
        HV,
        0x00.toByte(),
        constants.constants["largePrime"]!!.normalize(512),
        constants.constants["smallPrime"]!!.normalize(32),
        constants.constants["generator"]!!.normalize(512),
    )
}

fun BigInteger.normalize(len: Int): ByteArray = this.toByteArray().normalize(len)
