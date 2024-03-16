package org.cryptobiotic.eg.publish.json

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import org.cryptobiotic.eg.core.*
import org.cryptobiotic.eg.decrypt.*

// stuff used by the webapps - easiest to have it here as a common dependency
// TODO: are empty lists allowed?

@Serializable
data class SetMissingRequestJson(
    val lagrange_coeff: ElementModQJson,
    val missing: List<String>
)

data class SetMissingRequest(
    val lagrangeCoeff: ElementModQ,
    val missing: List<String>
)

fun SetMissingRequest.publishJson() = SetMissingRequestJson(
    this.lagrangeCoeff.publishJson(),
    this.missing
)

fun SetMissingRequestJson.import(group: GroupContext): SetMissingRequest? {
    val coeff = this.lagrange_coeff.import(group)
    return if (coeff == null) null else
        SetMissingRequest(
            coeff,
            this.missing
        )
}

///////////////////////////////////////////

@Serializable
@SerialName("ChallengeResponses")
data class ChallengeResponsesJson(
    val err: String?,
    val batchId: Int,
    val responses: List<ElementModQJson>
)

fun ChallengeResponses.publishJson() = ChallengeResponsesJson(
    this.err,
    this.batchId,
    this.responses.map { it.publishJson() }
)

fun ChallengeResponsesJson.import(group: GroupContext): Result<ChallengeResponses, String> {
    val responses = this.responses.map { it.import(group) }
    val allgood = responses.map { it != null }.reduce { a, b -> a && b }

    return if (allgood) Ok( ChallengeResponses(this.err, this.batchId, this.responses.map { it.import(group)!! }))
    else Err("importChallengeResponse error")
}

///////////////////////////////////////////

@Serializable
@SerialName("PartialDecryptions")
data class PartialDecryptionsJson(
    val err: String?,
    val batchId: Int,
    val responses: List<PartialDecryptionJson>
)

fun PartialDecryptions.publishJson() = PartialDecryptionsJson(
    this.err,
    this.batchId,
    this.partial.map { it.publishJson() }
)

fun PartialDecryptionsJson.import(group: GroupContext): Result<PartialDecryptions, String> {
    val responses = this.responses.map { it.import(group) }
    val allgood = responses.map { it != null }.reduce { a, b -> a && b }

    return if (allgood) Ok( PartialDecryptions(this.err, this.batchId, this.responses.map { it.import(group)!! }))
    else Err("importChallengeResponse error")
}

@Serializable
@SerialName("PartialDecryption")
data class PartialDecryptionJson(
    val mbari: ElementModPJson,
    val a: ElementModPJson,
    val b: ElementModPJson,
)

fun PartialDecryption.publishJson() = PartialDecryptionJson(
    this.Mi.publishJson(),
    this.a.publishJson(),
    this.b.publishJson(),
)

fun PartialDecryptionJson.import(group: GroupContext): PartialDecryption? {
    val mbari = this.mbari.import(group)
    val a = this.a.import(group)
    val b = this.b.import(group)
    return if (mbari == null || a == null || b == null) null
    else PartialDecryption(mbari, a, b)
}
