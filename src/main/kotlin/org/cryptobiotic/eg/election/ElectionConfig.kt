package org.cryptobiotic.eg.election

import org.cryptobiotic.eg.core.UInt256
import org.cryptobiotic.eg.core.hashFunction

const val currentConfigVersion = "2.1.0"

/** Configuration input. */
data class ElectionConfig(
    val configVersion: String,
    val constants: ElectionConstants,

    /** The number of guardians necessary to generate the public key. */
    val numberOfGuardians: Int,
    /** The quorum of guardians necessary to decrypt an election. Must be <= numberOfGuardians. */
    val quorum: Int,

    val parameterBaseHash : UInt256, // Hp
    val manifestHash : UInt256, // Hm
    val electionBaseHash : UInt256,  // Hb
    // the raw bytes of the election manifest. You must regenerate the manifest from this.
    val manifestBytes: ByteArray,

    val chainConfirmationCodes: Boolean = false,
    val configBaux0: ByteArray, // B_aux,0 from eq 59,60 may be empty

    /** arbitrary key/value metadata. */
    val metadata: Map<String, String> = emptyMap(),
) {
    init {
        require(numberOfGuardians > 0) { "numberOfGuardians ${numberOfGuardians} <= 0" }
        require(numberOfGuardians >= quorum) { "numberOfGuardians ${numberOfGuardians} != $quorum" }
    }

    fun show(): String = buildString {
        appendLine("ElectionConfig '${configVersion}' numberOfGuardians=$numberOfGuardians, quorum=$quorum chainConfirmationCodes=$chainConfirmationCodes")
        appendLine("  parameterBaseHash ${parameterBaseHash}")
        appendLine("  electionBaseHash ${electionBaseHash}")
        appendLine("  ElectionConstants ${constants}")
    }

    // override because of the byte arrays
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ElectionConfig

        if (configVersion != other.configVersion) return false
        if (constants != other.constants) return false
        if (numberOfGuardians != other.numberOfGuardians) return false
        if (quorum != other.quorum) return false
        if (parameterBaseHash != other.parameterBaseHash) return false
        if (manifestHash != other.manifestHash) return false
        if (electionBaseHash != other.electionBaseHash) return false
        if (!manifestBytes.contentEquals(other.manifestBytes)) return false
        if (chainConfirmationCodes != other.chainConfirmationCodes) return false
        if (!configBaux0.contentEquals(other.configBaux0)) return false
        if (metadata != other.metadata) return false

        return true
    }

    override fun hashCode(): Int {
        var result = configVersion.hashCode()
        result = 31 * result + constants.hashCode()
        result = 31 * result + numberOfGuardians
        result = 31 * result + quorum
        result = 31 * result + parameterBaseHash.hashCode()
        result = 31 * result + manifestHash.hashCode()
        result = 31 * result + electionBaseHash.hashCode()
        result = 31 * result + manifestBytes.contentHashCode()
        result = 31 * result + chainConfirmationCodes.hashCode()
        result = 31 * result + configBaux0.contentHashCode()
        result = 31 * result + metadata.hashCode()
        return result
    }
}

fun manifestHash(Hp: UInt256, manifestBytes : ByteArray) : UInt256 {
    // HM = H(HP ; 0x01, manifest). spec 2.0.0 p 19, eq 6
    // B0 = HP
    // B1 = 0x01 ∥ b(len(manifest), 4) ∥ b(manifest, len(manifest))
    // len(B1 ) = 5 + len(manifest)
    return hashFunction(
        Hp.bytes,
        0x01.toByte(),
        manifestBytes.size, // b(len(file), 4) ∥ b(file, len(file)) , section 5.1.5
        manifestBytes,
    )
}

fun electionBaseHash(Hp: UInt256, HM: UInt256, n : Int, k : Int) : UInt256 {
    // HB = H(HP ; 0x02, HM , n, k). spec 2.0.0 p 19, eq 7
    //  B0 = HP
    //  B1 = 0x02 ∥ HM ∥ b(n, 4) ∥ b(k, 4)
    //  len(B1 ) = 41
    return hashFunction(
        Hp.bytes,
        0x02.toByte(),
        HM,
        n,
        k,
    )
}

/** Make ElectionConfig, calculating Hp, Hm and Hb. */
fun makeElectionConfig(
    constants: ElectionConstants,
    numberOfGuardians: Int,
    quorum: Int,
    manifestBytes: ByteArray,
    chainConfirmationCodes: Boolean,
    baux0: ByteArray, // B_aux,0 from eq 58-60
    metadata: Map<String, String> = emptyMap(),
): ElectionConfig {

    val parameterBaseHash = parameterBaseHash(constants)
    val manifestHash = manifestHash(parameterBaseHash, manifestBytes)
    val electionBaseHash = electionBaseHash(parameterBaseHash, manifestHash, numberOfGuardians, quorum)

    return ElectionConfig(
        currentConfigVersion,
        constants,
        numberOfGuardians,
        quorum,
        parameterBaseHash,
        manifestHash,
        electionBaseHash,
        manifestBytes,
        chainConfirmationCodes,
        baux0,
        metadata,
    )
}