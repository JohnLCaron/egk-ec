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

    fun decrypt(
        texts: List<ElementModP>,
    ): PartialDecryptions

    fun challenge(
        batchId: Int, // must match batchId from PartialDecryptions
        challenges: List<ElementModQ>,
    ): ChallengeResponses
}

/** These are the messages exchanged with the Decrypting Trustee's */

data class PartialDecryptions(val err: String?, val batchId : Int, val partial: List<PartialDecryption>)

data class PartialDecryption (
    val Mi: ElementModP, // Mi = A ^ P(i); spec 2.0.0, eq 66 or = C0 ^ P(i); eq 77
    //// these are needed for the proof
    val a: ElementModP,  // g^u
    val b: ElementModP,  // A^u
)

data class ChallengeResponses(val err: String?, val batchId : Int, val responses: List<ElementModQ>)