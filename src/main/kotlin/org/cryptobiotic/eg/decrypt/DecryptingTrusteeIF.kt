package org.cryptobiotic.eg.decrypt

import org.cryptobiotic.eg.core.*

/** The interface to a DecryptingTrustee. */
interface DecryptingTrusteeIF {
    /** Guardian id.  */
    fun id(): String

    /** Guardian x coordinate  */
    fun xCoordinate(): Int

    /** The guardian's public key = K_i.  */
    fun guardianPublicKey(): ElementModP

    /**
     * Compute partial decryptions of elgamal encryptions.
     *
     * @param texts list of ElementModP (ciphertext.pad or A) to be partially decrypted
     * @return a list of partial decryptions, in the same order as the texts
     */
    fun decrypt(
        group: GroupContext,
        texts: List<ElementModP>,
    ): List<PartialDecryption>

    /**
     * Compute responses to Chaum-Pedersen challenges
     * @param challenges list of Chaum-Pedersen challenges
     * @return a list of responses, in the same order as the challenges
     */
    fun challenge(
        group: GroupContext,
        challenges: List<ChallengeRequest>,
    ): List<ChallengeResponse>

    ///////////////////////////////////

    fun decrypt2(
        texts: List<ElementModP>,
    ): PartialDecryptions

    fun challenge2(
        batchId: Int, // must match batchId from PartialDecryptions
        challenges: List<ElementModQ>,
    ): List<ElementModQ>
}

/** These are the messages exchanged with the Decrypting Trustee's */

data class PartialDecryptions(val err: String?, val batchId : Int, val partial: List<PartialDecryption>)

data class PartialDecryption(
    val guardianId: String,  // guardian i // TODO needed ? error ?
    val Mi: ElementModP, // Mi = A ^ P(i); spec 2.0.0, eq 66 or = C0 ^ P(i); eq 77
    //// these are needed for the proof
    val u: ElementModQ,  // opaque, just pass back to the trustee TODO remove
    val a: ElementModP,  // g^u
    val b: ElementModP,  // A^u
)

data class ChallengeResponses(val err: String?, val batchId : Int, val responses: List<ElementModQ>)