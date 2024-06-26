package org.cryptobiotic.eg.keyceremony

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.unwrap
import com.github.michaelbull.result.unwrapError
import io.github.oshai.kotlinlogging.KotlinLogging

import org.cryptobiotic.eg.core.*
import org.cryptobiotic.eg.election.*

private val logger = KotlinLogging.logger("keyCeremonyExchange")

/** Exchange PublicKeys and secret KeyShares among the trustees */
fun keyCeremonyExchange(trustees: List<KeyCeremonyTrusteeIF>, allowEncryptedFailure : Boolean = false): Result<KeyCeremonyResults, String> {
    // make sure trustee ids are all different
    val uniqueIds = trustees.map{it.id()}.toSet()
    if (uniqueIds.size != trustees.size) {
        return Err("keyCeremonyExchange trustees have non-unique ids = ${trustees.map{it.id()}}")
    }

    // make sure trustee xcoords are all different
    val uniqueCoords = trustees.map { it.xCoordinate() }.toSet()
    if (uniqueCoords.size != trustees.size) {
        return Err("keyCeremonyExchange trustees have non-unique xcoordinates = ${trustees.map{it.xCoordinate()}}")
    }

    // make sure trustee quorum are all the same
    val uniqueQuorum = trustees.map{it.coefficientCommitments().size}.toSet()
    if (uniqueQuorum.size != 1) {
        return Err("keyCeremonyExchange trustees have different quorums = ${trustees.map{it.coefficientCommitments().size}}")
    }

    // if the trustees are not trusted, we could do other verification tests here.

    // exchange PublicKeys
    val publicKeys: MutableList<PublicKeys> = mutableListOf()
    val publicKeyResults: MutableList<Result<Boolean, String>> = mutableListOf()
    trustees.forEach { t1 ->
        logger.debug{ " ${t1.id()} publicKeys()" }
        val t1Result = t1.publicKeys()
        if (t1Result is Err) {
            publicKeyResults.add(t1Result)
        } else {
            val t1Keys = t1Result.unwrap()
            publicKeys.add(t1Keys)
            trustees.filter { it.id() != t1.id() }.forEach { t2 ->
                logger.debug{ "  ${t2.id()} receivePublicKeys() from ${t1Keys.guardianId}"}
                publicKeyResults.add(t2.receivePublicKeys(t1Keys))
            }
        }
    }

    val errors = publicKeyResults.merge()
    if (errors is Err) {
        return Err("keyCeremonyExchange error exchanging public keys:\n ${errors.error}")
    }

    // exchange SecretKeyShares, and validate them
    val keyShareFailures: MutableList<KeyShareFailed> = mutableListOf()
    val encryptedKeyResults: MutableList<Result<Boolean, String>> = mutableListOf()
    trustees.forEach { owner ->
        trustees.filter { it.id() != owner.id() }.forEach { shareFor ->
            logger.debug{ " ${owner.id()} encryptedKeyShareFor() ${shareFor.id()}" }
            val encryptedKeyShareResult = owner.encryptedKeyShareFor(shareFor.id())
            if (encryptedKeyShareResult is Err) {
                encryptedKeyResults.add(Err(encryptedKeyShareResult.unwrapError()))
                keyShareFailures.add(KeyShareFailed(owner, shareFor))
            } else {
                val secretKeyShare = encryptedKeyShareResult.unwrap()
                logger.debug {
                    "  ${shareFor.id()} encryptedKeyShareFor() for ${owner.id()} " +
                            "(polynomialOwner ${secretKeyShare.polynomialOwner} secretShareFor ${secretKeyShare.secretShareFor})"
                }
                val receiveEncryptedKeyShareResult = shareFor.receiveEncryptedKeyShare(secretKeyShare)
                if (receiveEncryptedKeyShareResult is Err) {
                    encryptedKeyResults.add(receiveEncryptedKeyShareResult)
                    keyShareFailures.add(KeyShareFailed(owner, shareFor))
                }
            }
        }
    }

    // spec 2.0.0, p 24 "Share verification"
    // If the recipient guardian Gℓ reports not receiving a suitable value Pi (ℓ), it becomes incumbent on the
    // sending guardian Gi to publish this Pi (ℓ) together with the nonce ξi,ℓ it used to encrypt Pi (ℓ)
    // under the public key Kℓ of recipient guardian Gℓ . If guardian Gi fails to produce a suitable Pi (ℓ)
    // and nonce ξi,ℓ that match both the published encryption and the above equation, it should be
    // excluded from the election and the key generation process should be restarted with an alternate
    // guardian. If, however, the published Pi (ℓ) and ξi,ℓ satisfy both the published encryption and the
    // equation above, the claim of malfeasance is dismissed, and the key generation process continues undeterred.
    // footnote 28 It is also permissible to dismiss any guardian that makes a false claim of malfeasance. However, this is not
    // required as the sensitive information that is released as a result of the claim could have been released by the claimant
    // in any case.

    // Phase Two: if any secretKeyShares fail to validate, send and validate KeyShares
    val keyResults: MutableList<Result<Boolean, String>> = mutableListOf()
    keyShareFailures.forEach {
        logger.debug{ " ${it.polynomialOwner.id()} secretShareFor ${it.secretShareFor.id()}" }
        val keyShareResult = it.polynomialOwner.keyShareFor(it.secretShareFor.id())
        if (keyShareResult is Ok) {
            val keyShare = keyShareResult.unwrap()
            logger.debug{ " ${it.secretShareFor.id()} keyShareFor() ${keyShare.polynomialOwner}" }
            keyResults.add(it.secretShareFor.receiveKeyShare(keyShare))
        } else {
            keyResults.add(Err(keyShareResult.unwrapError()))
        }
    }

    // check that everyone is happy
    val happy = trustees.map { it.isComplete() }.reduce{ a, b -> a && b }
    if (!happy) {
        return Err("keyCeremonyExchange not complete")
    }

    if (allowEncryptedFailure) {
        val keyResultAll = keyResults.merge()
        return if (keyResultAll is Ok) {
            Ok(KeyCeremonyResults(publicKeys))
        } else {
            val all = (keyResults + encryptedKeyResults).merge()
            Err("keyCeremonyExchange had failures exchanging shares: ${all.unwrapError()}, allowed to continue")
        }
    } else {
        val all = (keyResults + encryptedKeyResults).merge()
        return if (all is Ok) {
            Ok(KeyCeremonyResults(publicKeys))
        } else {
            Err("keyCeremonyExchange had failures exchanging shares:\n ${all.unwrapError()}")
        }
    }
}

private data class KeyShareFailed(
    val polynomialOwner: KeyCeremonyTrusteeIF, // guardian j (owns the polynomial Pj)
    val secretShareFor: KeyCeremonyTrusteeIF, // guardian l with coordinate ℓ
)

/** An internal result class used during the key ceremony. */
data class KeyCeremonyResults(
    val publicKeys: List<PublicKeys>,
) {
    val publicKeysSorted = publicKeys.sortedBy { it.guardianXCoordinate }

    fun makeElectionInitialized(
        config: ElectionConfig,
        metadata: Map<String, String> = emptyMap(),
    ): ElectionInitialized {
        // spec 2.0.0 p.25, eq 8.
        val jointPublicKey: ElementModP =
            publicKeysSorted.map { it.publicKey }.reduce { a, b -> a * b }

        // He = H(HB ; 0x12, K) ; spec 2.0.0 p.25, eq 23.
        val extendedBaseHash = electionExtendedHash(config.electionBaseHash, jointPublicKey)

        val guardians: List<Guardian> = publicKeysSorted.map { makeGuardian(it) }

        val metadataAll = mutableMapOf(
            Pair("CreatedBy", "keyCeremonyExchange"),
            Pair("CreatedOn", getSystemDate()),
        )
        metadataAll.putAll(metadata)

        return ElectionInitialized(
            config,
            ElGamalPublicKey(jointPublicKey),
            extendedBaseHash,
            guardians,
            metadataAll,
        )
    }
}

private fun makeGuardian(publicKeys: PublicKeys): Guardian {
    return Guardian(
        publicKeys.guardianId,
        publicKeys.guardianXCoordinate,
        publicKeys.coefficientProofs,
    )
}