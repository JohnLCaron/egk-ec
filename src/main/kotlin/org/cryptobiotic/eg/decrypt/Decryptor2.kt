package org.cryptobiotic.eg.decrypt

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.eg.core.*
import org.cryptobiotic.eg.election.*
import org.cryptobiotic.util.ErrorMessages
import org.cryptobiotic.util.Stats

private const val maxDlog: Int = 1000

/** Orchestrates the decryption of List<ElGamalCiphertext> using DecryptingTrustees. */
class Decryptor2(
    val group: GroupContext,
    val extendedBaseHash: UInt256,
    val publicKey: ElGamalPublicKey,
    val guardians: Guardians, // all guardians
    private val decryptingTrustees: List<DecryptingTrusteeIF>, // the trustees available to decrypt
) {
    val lagrangeCoordinates: Map<String, LagrangeCoordinate>
    val stats = Stats()
    val nguardians = guardians.guardians.size // number of guardinas
    val quorum = guardians.guardians[0].coefficientCommitments().size

    init {
        // check that the DecryptingTrustee's match their public key
        val badTrustees = mutableListOf<String>()
        for (trustee in decryptingTrustees) {
            val guardian = guardians.guardianMap[trustee.id()]
            if (guardian == null) {
                badTrustees.add(trustee.id())
            } else {
                if (trustee.guardianPublicKey() != guardian.publicKey()) {
                    badTrustees.add(trustee.id())
                    logger.error { "trustee public key = ${trustee.guardianPublicKey()} not equal guardian = ${guardian.publicKey()}" }
                }
            }
        }
        if (badTrustees.isNotEmpty()) {
            throw RuntimeException("DecryptingTrustee(s) ${badTrustees.joinToString(",")} do not match the public record")
        }

        // build the lagrangeCoordinates once and for all
        val dguardians = mutableListOf<LagrangeCoordinate>()
        for (trustee in decryptingTrustees) {
            val present: List<Int> = // available trustees minus me
                decryptingTrustees.filter { it.id() != trustee.id() }.map { it.xCoordinate() }
            val coeff: ElementModQ = group.computeLagrangeCoefficient(trustee.xCoordinate(), present)
            dguardians.add(LagrangeCoordinate(trustee.id(), trustee.xCoordinate(), coeff))
        }
        this.lagrangeCoordinates = dguardians.associateBy { it.guardianId }
    }

    var first = false

    // kTOnly = dont compute log
    fun decrypt(texts: List<ElGamalCiphertext>, errs : ErrorMessages, kTOnly: Boolean): List<DecryptionAndProof> {
        // must get the PartialDecryptions from all trustees before we can do the challenges
        val partialDecryptions = mutableListOf<PartialDecryptions>()
        for (decryptingTrustee in decryptingTrustees) { // could be parallel
            partialDecryptions.add( decryptingTrustee.getPartialDecryptionsFromTrustee(texts))
        }

        val decryptions = texts.mapIndexed { idx, text ->
            val shares = partialDecryptions.map { it.partial[idx] }

            // lagrange weighted product of the shares, M = Prod(M_i^w_i) mod p; spec 2.0.0, eq 68
            val weightedProduct = with(group) {
                // for this idx, run over all the trustees
                partialDecryptions.map { (trusteeId, pdlist) ->
                    val lagrange = lagrangeCoordinates[trusteeId]
                    val coeff = if (lagrange == null) { // check make sure this cant happen
                        errs.add("missing lagrangeCoordinate for $trusteeId")
                        group.ONE_MOD_Q
                    } else {
                        lagrange.lagrangeCoefficient
                    }
                    pdlist[idx].Mi powP coeff
                }.multP()
            }
            val T = text.data / weightedProduct
            val tally = if (kTOnly) null else publicKey.dLog(T, maxDlog) ?: errs.addNull("dLog not found on $idx") as Int?

            // compute the collective challenge, needed for the collective proof; spec 2.0.0 eq 70
            val a: ElementModP = with(group) { shares.map { it.a }.multP() } // Prod(ai)
            val b: ElementModP = with(group) { shares.map { it.b }.multP() } // Prod(bi)
            // "collective challenge" c = H(HE ; 0x30, K, A, B, a, b, M ) ; spec 2.0.0 eq 71
            val collectiveChallenge = hashFunction(
                extendedBaseHash.bytes,
                0x30.toByte(),
                publicKey.key,
                text.pad,
                text.data,
                a, b, weightedProduct)

            if (first) {
                println("decrypt $text")
                println("  M = $weightedProduct")
                println("  bOverM= $T")
                println("  a= $a")
                println("  b= $b")
            }
            first = false

            Decryption(text, shares, weightedProduct, T, tally, collectiveChallenge)
        }

        /* compute M for each DecryptionResults over all the shares from available guardians
        for ((selectionKey, dresults) in allDecryptions.shares) {
            // TODO if nguardians = 1, can set weightedProduct = Mi.
            // lagrange weighted product of the shares, M = Prod(M_i^w_i) mod p; spec 2.0.0, eq 68
            val weightedProduct = with(group) {
                dresults.shares.map { (guardianId, value) ->
                    val lagrange = lagrangeCoordinates[guardianId]
                    val coeff = if (lagrange == null) {
                            errs.add("missing lagrangeCoordinate for $guardianId")
                            group.ONE_MOD_Q
                        } else {
                            lagrange.lagrangeCoefficient
                        }
                    value.Mi powP coeff
                }.multP()
            }

            // T = B · M−1 mod p; spec 2.0.0, eq 64
            val T = dresults.ciphertext.data / weightedProduct
            // T = K^t mod p, take log to get t = tally.
            dresults.tally = if (kTOnly) 0 else jointPublicKey.dLog(T, maxDlog) ?: errs.addNull("dLog not found on $selectionKey") as Int?
            dresults.M = weightedProduct

            val tidx = Integer.parseInt(selectionKey)
            val vote = votes[tidx].toElementModQ(group)
            if (T != jointPublicKey powP vote) {
                println("HEY")
            }

            // compute the collective challenge, needed for the collective proof; spec 2.0.0 eq 70
            val a: ElementModP = with(group) { dresults.shares.values.map { it.a }.multP() } // Prod(ai)
            val b: ElementModP = with(group) { dresults.shares.values.map { it.b }.multP() } // Prod(bi)
            // "collective challenge" c = H(HE ; 0x30, K, A, B, a, b, M ) ; spec 2.0.0 eq 71
            dresults.collectiveChallenge = hashFunction(
                extendedBaseHash.bytes,
                0x30.toByte(),
                jointPublicKey.key,
                dresults.ciphertext.pad,
                dresults.ciphertext.data,
                a, b, weightedProduct)
        } */

        // now that we have the collective challenges, gather the individual challenges to construct the proofs.
        val challengeResponses: List<ChallengeResponses> = decryptingTrustees.mapIndexed { trusteeIdx, trustee ->
            trustee.getResponsesFromTrustee(trusteeIdx, decryptions, errs.nested("trusteeChallengeResponses"))
        }

        // After gathering the challenge responses from the available trustees, we can create the proof
        return makeDecryptionAndProofs( decryptions, challengeResponses, errs)
    }

    /**
     * Get trustee decryptions, aka a 'partial decryption', for all the texts.
     * @param trustee: The trustee who will partially decrypt the tally
     * @param texts: decrypt these
     */
    private fun DecryptingTrusteeIF.getPartialDecryptionsFromTrustee(texts: List<ElGamalCiphertext>) : PartialDecryptions {
        // Only need the pads
        val pads = texts.map { it.pad }

        // decrypt all of them at once
        val results: List<PartialDecryption> = this.decrypt(group, pads)

        return PartialDecryptions(this.id(), results)
    }

    // send all challenges for a ballot / tally to one trustee, get all its reponses
    fun DecryptingTrusteeIF.getResponsesFromTrustee(trusteeIdx: Int, decryptions: List<Decryption>, errs : ErrorMessages) : ChallengeResponses {
        val wi = lagrangeCoordinates[this.id()]!!.lagrangeCoefficient
        // Create all the challenges from each Decryption for this trustee
        val requests: MutableList<ChallengeRequest> = mutableListOf()
        decryptions.forEach { decryption ->
            val share = decryption.shares[trusteeIdx]
            // spec 2.0.0, eq 72
            val ci = wi * decryption.collectiveChallenge.toElementModQ(group)
            requests.add(ChallengeRequest("selectionKey", ci, share.u))
        }
        /* for ((selectionKey, results) in decryptions.shares) {
            val result = results.shares[this.id()]
            if (result == null) {
                errs.add("missing share ${this.id()}")
            } else {
                // spec 2.0.0, eq 72
                val ci = wi * results.collectiveChallenge!!.toElementModQ(group)
                requests.add(ChallengeRequest(selectionKey, ci, result.u))
            }
        } */

        // ask for all of them at once from the trustee
        val results: List<ChallengeResponse> = this.challenge(group, requests)
        return ChallengeResponses(this.id(), results)
    }

    /** Called after gathering the shares and challenge responses for all available trustees. */
    fun makeDecryptionAndProofs(
        decryptions: List<Decryption>, // for each text
        challengeResponses: List<ChallengeResponses>, // for each trustee, list of responses for each text
        errs : ErrorMessages,
    ): List<DecryptionAndProof> {
        val ds = mutableListOf<DecryptionAndProof>()
        decryptions.forEachIndexed { idx, decryption ->
            val responsesForIdx = challengeResponses.map { it.responses[idx] }
            ds.add( makeDecryptionAndProof( decryption, responsesForIdx) )
        }
        return ds // for each text
    }

    var firstProof = false
    private fun makeDecryptionAndProof(
        decryption: Decryption,
        challengeResponses: List<ChallengeResponse>, // across trustees for this decryption
    ): DecryptionAndProof {
        // v = Sum(v_i mod q); spec 2.0.0 eq 76
        val response: ElementModQ = with(group) { challengeResponses.map { it.response }.addQ() }
        // finally we can create the proof
        val proof = ChaumPedersenProof(decryption.collectiveChallenge.toElementModQ(group), response)

        val a = group.gPowP(proof.r) * (publicKey powP proof.c) // 9.2
        val b = (decryption.ciphertext.pad powP proof.r) * (decryption.M powP proof.c) // 9.3

        if (firstProof) {
            println("makeProof ${decryption.ciphertext}")
            println("  M = ${decryption.M}")
            println("  challenge = ${proof.c}")
            println("  response = ${proof.r}")
            println("  a= $a")
            println("  b= $b")
        }
        firstProof = false

        return DecryptionAndProof(decryption, proof)
    }

    companion object {
        private val logger = KotlinLogging.logger("Decryptor")
    }
}

data class PartialDecryptions(val trusteeId : String, val partial: List<PartialDecryption>)

// one decryption
class Decryption(
    val ciphertext: ElGamalCiphertext, // text to decrypt
    val shares: List<PartialDecryption>, // for each trustee
    val M: ElementModP, // lagrange weighted product of the shares, M = Prod(M_i^w_i) mod p; spec 2.0, eq 68
    val T: ElementModP, // K^tally
    val tally: Int? = null, // the decrypted tally, may be null
    val collectiveChallenge: UInt256, // spec 2.0, eq 71
)

// for one trustee, the responses for all the texts
data class ChallengeResponses(val trusteeId : String, val responses: List<ChallengeResponse>)

// the return value of the decrypt
data class DecryptionAndProof(val decryption: Decryption, val proof: ChaumPedersenProof)