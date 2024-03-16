package org.cryptobiotic.eg.decrypt

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.eg.core.*
import org.cryptobiotic.eg.election.*
import org.cryptobiotic.util.ErrorMessages
import org.cryptobiotic.util.Stats

// TODO TallyDecryptor.doVerifierSelectionProof

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

    // kTOnly = dont compute log
    fun decrypt(texts: List<ElGamalCiphertext>, errs : ErrorMessages, kTOnly: Boolean): List<DecryptionAndProof>? {
        // get the PartialDecryptions from each of the trustees
        val partialDecryptions = decryptingTrustees.map { // partialDecryptions are in the order of the decryptingTrustees
            it.getPartialDecryptionsFromTrustee(texts, errs)
        }
        if (errs.hasErrors()) {
            logger.error { "partial decryptions failed = ${errs}" }
            return null
        }

        // Do the decryption for each text
        val decryptions = texts.mapIndexed { idx, text ->

            // TODO could use the shares
            // lagrange weighted product of the shares, M = Prod(M_i^w_i) mod p; spec 2.0.0, eq 68
            val weightedProduct = with(group) {
                // for this idx, run over all the trustees
                partialDecryptions.mapIndexed { tidx, pds ->
                    val trustee = decryptingTrustees[tidx]
                    val lagrange = lagrangeCoordinates[trustee.id()]
                    val coeff = if (lagrange == null) { // TODO check to make sure this cant happen
                        errs.add("missing lagrangeCoordinate for ${trustee.id()}")
                        group.ONE_MOD_Q
                    } else {
                        lagrange.lagrangeCoefficient
                    }
                    pds.partial[idx].Mi powP coeff
                }.multP()
            }
            val T = text.data / weightedProduct
            val tally = if (kTOnly) null else publicKey.dLog(T, maxDlog) ?: errs.addNull("dLog not found on $idx") as Int?

            // compute the collective challenge, needed for the collective proof; spec 2.0.0 eq 70
            val shares: List<PartialDecryption> = partialDecryptions.map { it.partial[idx] } // for this text, one from each trustee
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

            Decryption(text, shares, weightedProduct, T, tally, collectiveChallenge)
        }
        if (errs.hasErrors()) {
            logger.error { "decrypt failed = ${errs}" }
            return null
        }

        // now that we have the collective challenges, gather the individual challenges/responses to construct the proofs.
        val challengeResponses: List<ChallengeResponses> = decryptingTrustees.mapIndexed { trusteeIdx, trustee ->
            val batchId = partialDecryptions[trusteeIdx].batchId
            trustee.getResponsesFromTrustee(batchId, decryptions, errs.nested("trusteeChallengeResponses"))
        }
        if (errs.hasErrors()) {
            logger.error { "decrypt failed = ${errs}" }
            return null
        }

        // After gathering the challenge responses from the available trustees, we can create the proof
        return makeDecryptionAndProofs( decryptions, challengeResponses, errs)
    }

    /**
     * Get trustee decryptions, aka a 'partial decryption', for all the texts.
     * @param trustee: The trustee who will partially decrypt the tally
     * @param texts: decrypt these
     */
    private fun DecryptingTrusteeIF.getPartialDecryptionsFromTrustee(texts: List<ElGamalCiphertext>, errs : ErrorMessages) : PartialDecryptions {
        // Only need the pads
        val pads = texts.map { it.pad }

        val pds = this.decrypt(pads)
        if (pds.err != null) {
            errs.add(pds.err)
        }
        return pds
    }

    // send all challenges for a ballot / tally to one trustee, get all its responses
    fun DecryptingTrusteeIF.getResponsesFromTrustee(batchId: Int, decryptions: List<Decryption>, errs : ErrorMessages) : ChallengeResponses {
        val wi = lagrangeCoordinates[this.id()]!!.lagrangeCoefficient
        // Create all the challenges from each Decryption for this trustee
        val requests: MutableList<ElementModQ> = mutableListOf()
        decryptions.forEach { decryption ->
            requests.add(wi * decryption.collectiveChallenge.toElementModQ(group)) // spec 2.0.0, eq 72
        }
        // get all the responses at once from the trustee
        val responses = this.challenge(batchId, requests)
        if (responses.err != null) {
            errs.add(responses.err)
        }
        return responses
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
        challengeResponses: List<ElementModQ>, // across trustees for this decryption
    ): DecryptionAndProof {
        // v = Sum(v_i mod q); spec 2.0.0 eq 76
        val response: ElementModQ = with(group) { challengeResponses.map { it }.addQ() }
        // finally we can create the proof
        val proof = ChaumPedersenProof(decryption.collectiveChallenge.toElementModQ(group), response)

        val a = group.gPowP(proof.r) * (publicKey powP proof.c) // 9.2
        val b = (decryption.ciphertext.pad powP proof.r) * (decryption.M powP proof.c) // 9.3

        // TODO vertify proof ??
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

// one decryption
class Decryption(
    val ciphertext: ElGamalCiphertext, // text to decrypt
    val shares: List<PartialDecryption>, // for each trustee
    val M: ElementModP, // lagrange weighted product of the shares, M = Prod(M_i^w_i) mod p; spec 2.0, eq 68
    val T: ElementModP, // K^tally
    val tally: Int? = null, // the decrypted tally, may be null
    val collectiveChallenge: UInt256, // spec 2.0, eq 71
)

// the return value of the decrypt
data class DecryptionAndProof(val decryption: Decryption, val proof: ChaumPedersenProof)