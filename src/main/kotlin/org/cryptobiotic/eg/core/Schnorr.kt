package org.cryptobiotic.eg.core

import com.github.michaelbull.result.*
import io.github.oshai.kotlinlogging.KotlinLogging
private val logger = KotlinLogging.logger("SchnorrProof")

/**
 * Proof that the prover knows the private key corresponding to the public commitment.
 * Spec 2.0.0, section 3.2.2, "NIZK Proof" (non-interactive zero knowledge proof).
 */
data class SchnorrProof(
    val publicCommitment: ElementModP, // K_ij, public commitment to the jth coefficient.
    val challenge: ElementModQ, // c_ij, p 22. eq 12
    val response: ElementModQ,  // v_ij = nonce - secretKey.key * cij.
    ) {

    init {
        compatibleContextOrFail(publicCommitment, challenge, response)
        require(publicCommitment.isValidElement()) // 2.A
    }

    // verification Box 2, p 23
    fun validate(guardianXCoord: Int, coeff: Int, loggit: Boolean = true): Result<Boolean, String> {
        val group = compatibleContextOrFail(publicCommitment, challenge, response)

        val gPowV = group.gPowP(response)
        val h = gPowV * (publicCommitment powP challenge) // h_ij (2.1)
        // h = g^v * K^c = g^(u - c*s) * g^s*c = g^(u - c*s + c*s) = g^u
        val c = hashFunction(group.constants.parameterBaseHash, 0x10.toByte(), guardianXCoord, coeff, publicCommitment, h).toElementModQ(group) // 2.C
        // c wouldnt agree unless h = g^u
        // therefore, whoever generated v knows s

        val inBoundsK = publicCommitment.isValidElement() // 2.A
        val inBoundsU = response.isValidElement() // 2.B
        val validChallenge = c == challenge // 2.C
        val success = inBoundsK && inBoundsU && validChallenge

        if (!success) {
            val resultMap =
                mapOf(
                    "inBoundsU" to inBoundsU,
                    "validChallenge" to validChallenge,
                    "proof" to this
                )
            if (loggit) logger.warn { "found an invalid Schnorr proof: $resultMap" }
            return Err("inBoundsU=$inBoundsU validChallenge=$validChallenge")
        }

        return Ok(true)
    }
}

/**
 * Given an ElGamal keypair (public and private key) for i,j, generate a ZNP (zero knowledge proof)
 * that the author of the proof knows the private key, without revealing it.
 */
fun ElGamalKeypair.schnorrProof(
    guardianXCoord: Int, // i
    coeff: Int, // j
    nonce: ElementModQ = context.randomElementModQ() // u_ij
): SchnorrProof {
    val context = compatibleContextOrFail(publicKey.key, secretKey.key, nonce)
    val h = context.gPowP(nonce) // h = g ^ u; spec 2.0.0,  eq 11
    val c = hashFunction(context.constants.parameterBaseHash, 0x10.toByte(), guardianXCoord, coeff, publicKey, h).toElementModQ(context) // eq 12
    val v = nonce - secretKey.key * c

    return SchnorrProof(publicKey.key, c, v)
}