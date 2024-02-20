package org.cryptobiotic.eg.publish.json

import org.cryptobiotic.eg.core.*
import org.cryptobiotic.eg.election.*
import org.cryptobiotic.util.ErrorMessages
import kotlinx.serialization.Serializable
import org.cryptobiotic.eg.keyceremony.EncryptedKeyShare
import org.cryptobiotic.eg.keyceremony.KeyShare
import org.cryptobiotic.eg.keyceremony.PublicKeys

/** External representation of a PublicKeys, used in KeyCeremony */
@Serializable
data class PublicKeysJson(
    val guardianId: String,
    val guardianXCoordinate: Int,
    val coefficientProofs: List<SchnorrProofJson>,
)

fun PublicKeys.publishJson() = PublicKeysJson(
    this.guardianId,
    this.guardianXCoordinate,
    this.coefficientProofs.map { it.publishJson() }
)

fun PublicKeysJson.import(group: GroupContext, errs : ErrorMessages) : PublicKeys? {
    val coefficientProofs : List<SchnorrProof?> = this.coefficientProofs.mapIndexed { idx, it ->
        it.import(group, errs.nested("coefficientProof $idx"))
    }
    return if (errs.hasErrors()) null
    else PublicKeys(
            this.guardianId,
            this.guardianXCoordinate,
            coefficientProofs.filterNotNull(),
        )
}

/////////////////////////////////////////////////////
/** External representation of an EncryptedKeyShare */
@Serializable
data class EncryptedKeyShareJson(
    val ownerXcoord : Int,
    val polynomial_owner: String, // guardian j (owns the polynomial Pj)
    val secret_share_for: String, // guardian l
    val encrypted_coordinate: HashedElGamalCiphertextJson,
)

fun EncryptedKeyShare.publishJson() = EncryptedKeyShareJson(
    this.ownerXcoord,
    this.polynomialOwner,
    this.secretShareFor,
    this.encryptedCoordinate.publishJson(),
)

fun EncryptedKeyShareJson.import(group: GroupContext): EncryptedKeyShare? {
    val encryptedCoordinate = this.encrypted_coordinate.import(group)
    return if (encryptedCoordinate == null) null else
        EncryptedKeyShare(
            this.ownerXcoord,
            this.polynomial_owner,
            this.secret_share_for,
            encryptedCoordinate,
        )
}

/** External representation of a KeyShare LOOK */
@Serializable
data class KeyShareJson(
    val ownerXcoord : Int,
    val polynomial_owner: String, // guardian j (owns the polynomial Pj)
    val secret_share_for: String, // guardian l
    val coordinate: ElementModQJson,
)

fun KeyShare.publishJson() = KeyShareJson(
    this.ownerXcoord,
    this.polynomialOwner,
    this.secretShareFor,
    this.yCoordinate.publishJson(),
)

fun KeyShareJson.import(group: GroupContext): KeyShare? {
    val coordinate = this.coordinate.import(group)
    return if (coordinate == null) null else
        KeyShare(
            this.ownerXcoord,
            this.polynomial_owner,
            this.secret_share_for,
            coordinate,
        )
}
