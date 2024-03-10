package org.cryptobiotic.eg.publish.json

import kotlinx.serialization.Serializable
import org.cryptobiotic.eg.core.Base64.fromBase64
import org.cryptobiotic.eg.core.Base64.toBase64

import org.cryptobiotic.eg.election.*
import org.cryptobiotic.util.ErrorMessages
import org.cryptobiotic.eg.core.safeEnumValueOf
import java.math.BigInteger

@Serializable
data class ElectionConfigJson(
    val config_version: String,
    val number_of_guardians: Int,
    val quorum: Int,

    val parameter_base_hash: UInt256Json, // Hp
    val manifest_hash: UInt256Json, // Hm
    val election_base_hash: UInt256Json, // Hb

    val chain_confirmation_codes: Boolean,
    val baux0: String, // // base64 encoded ByteArray, B_aux,0 from eq 59,60
    val metadata: Map<String, String> = emptyMap(), // arbitrary key, value pairs
)

fun ElectionConfig.publishJson() = ElectionConfigJson(
    this.configVersion,
    this.numberOfGuardians,
    this.quorum,
    this.parameterBaseHash.publishJson(),
    this.manifestHash.publishJson(),
    this.electionBaseHash.publishJson(),
    this.chainConfirmationCodes,
    this.configBaux0.toBase64(),
    this.metadata,
)

fun ElectionConfigJson.import(constants: ElectionConstants?, manifestBytes: ByteArray?, errs:ErrorMessages) : ElectionConfig? {
    if (this.parameter_base_hash.import() == null) errs.add("malformed parameter_base_hash")
    if (this.manifest_hash.import() == null) errs.add("malformed manifest_hash")
    if (this.election_base_hash.import() == null) errs.add("malformed election_base_hash")
    if (this.baux0.fromBase64() == null) errs.add("malformed baux0")

    return if (errs.hasErrors() || constants == null || manifestBytes == null) null
    else ElectionConfig(
                this.config_version,
                constants,
                this.number_of_guardians,
                this.quorum,
                this.parameter_base_hash.import()!!,
                this.manifest_hash.import()!!,
                this.election_base_hash.import()!!,
                manifestBytes,
                this.chain_confirmation_codes,
                this.baux0.fromBase64()!!,
                this.metadata,
            )
}

@Serializable
data class ElectionConstantsJson(
    val name: String,
    val type: String,
    val protocolVersion: String,
    val constants : Map<String, String>
)

fun ElectionConstants.publishJson() = ElectionConstantsJson(
    this.name,
    this.type.toString(),
    this.protocolVersion,
    this.constants.mapValues { it.value.toString(16) }
)

fun ElectionConstantsJson.import(errs: ErrorMessages) : ElectionConstants? {
    val gtype = safeEnumValueOf(this.type) ?: GroupType.IntegerGroup
    this.constants.entries.forEach() { (key, value) ->
        try {
            BigInteger(value, 16)
        } catch (t: Throwable) {
            errs.add("constant '$key' has invalid value '$value, should be BigInteger in hex")
        }
    }
    if (errs.hasErrors()) return null

    return ElectionConstants(
        this.name,
        gtype,
        this.protocolVersion,
        this.constants.mapValues { BigInteger(it.value, 16) }
    )
}
