package org.cryptobiotic.eg.publish.json

import org.cryptobiotic.eg.core.*
import org.cryptobiotic.util.ErrorMessages
import kotlinx.serialization.Serializable
import org.cryptobiotic.eg.decrypt.DecryptingTrustee
import org.cryptobiotic.eg.keyceremony.KeyCeremonyTrustee

/** These must stay private, not in the election record. */
@Serializable
data class TrusteeJson(
    val id: String,
    val x_coordinate: Int,
    val polynomial_coefficients: List<ElementModQJson>,
    val key_share: ElementModQJson,
)

fun KeyCeremonyTrustee.publishJson(): TrusteeJson {
    return TrusteeJson(
        this.id,
        this.xCoordinate,
        this.polynomial.coefficients.map { it.publishJson() },
        this.computeSecretKeyShare().publishJson(),
    )
}

fun TrusteeJson.importDecryptingTrustee(group: GroupContext, errs : ErrorMessages): DecryptingTrustee? {
    val privateKey = this.polynomial_coefficients[0].import(group) ?: errs.addNull("malformed privateKey") as ElementModQ?
    val keyShare = this.key_share.import(group) ?: errs.addNull("malformed keyShare") as ElementModQ?
    return if (errs.hasErrors()) null
    else DecryptingTrustee(
        this.id,
        this.x_coordinate,
        ElGamalPublicKey(group.gPowP(privateKey!!)),
        keyShare!!,
        )
}