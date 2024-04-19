package org.cryptobiotic.eg.decrypt

import com.github.michaelbull.result.unwrap
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.eg.core.*
import org.cryptobiotic.eg.core.Base16.toHex
import org.cryptobiotic.eg.election.*
import org.cryptobiotic.util.ErrorMessages

/** Orchestrates the decryption of List<ElGamalCiphertext> or List<HashedElGamalCiphertext> using DecryptingTrustees. */
class CipherDecryptor(
    val group: GroupContext,
    val extendedBaseHash: UInt256,
    val publicKey: ElGamalPublicKey,
    guardians: Guardians, // all guardians
    private val decryptingTrustees: List<DecryptingTrusteeIF>, // the trustees available to decrypt
) {
    val lagrangeCoordinates: Map<String, LagrangeCoordinate> = guardians.buildLagrangeCoordinates(decryptingTrustees)

    fun decrypt(texts: List<Cipher>, errs : ErrorMessages): List<CipherDecryptionAndProof>? {
        if (texts.isEmpty()) return emptyList()

        // get the PartialDecryptions from each of the trustees
        val partialDecryptions = decryptingTrustees.map { trustee -> // partialDecryptions are in order of the decryptingTrustees
            trustee.getPartialDecryptionsFromTrustee(texts, errs)
        }
        if (errs.hasErrors()) {
            logger.error { "partial decryptions failed = $errs" }
            return null
        }

        // Do the decryption for each text
        val decryptions = texts.mapIndexed { idx, text ->

            // lagrange weighted product of the shares, M = Prod(M_i^w_i) mod p; spec 2.0.0, eq 68
            val weightedProduct = group.multP(
                // for this idx, run over all the trustees
                partialDecryptions.mapIndexed { tidx, pds ->
                    val trustee = decryptingTrustees[tidx]
                    val lagrange = lagrangeCoordinates[trustee.id()]!! // buildLagrangeCoordinates() guarentees exists
                    pds.partial[idx].Mi powP lagrange.lagrangeCoefficient
                }
            )

            // compute the collective challenge, needed for the collective proof; spec 2.0.0 eq 70
            val shares: List<PartialDecryption> = partialDecryptions.map { it.partial[idx] } // for this text, one from each trustee
            val a: ElementModP = group.multP(shares.map { it.a }) // Prod(ai)
            val b: ElementModP = group.multP(shares.map { it.b }) // Prod(bi)

            val collectiveChallenge = text.collectiveChallenge(extendedBaseHash, publicKey, a, b, weightedProduct)

            CipherDecryption(text, weightedProduct, collectiveChallenge)
        }
        if (errs.hasErrors()) {
            logger.error { "decrypt failed = $errs" }
            return null
        }

        // now that we have the collective challenges, gather the individual challenges/responses to construct the proofs.
        val challengeResponses: List<ChallengeResponses> = decryptingTrustees.mapIndexed { trusteeIdx, trustee ->
            val batchId = partialDecryptions[trusteeIdx].batchId
            trustee.getResponsesFromTrustee(batchId, decryptions, errs.nested("trusteeChallengeResponses"))
        }
        if (errs.hasErrors()) {
            logger.error { "decrypt failed = $errs" }
            return null
        }

        // After gathering the challenge responses from the available trustees, we can create the proof
        return makeHDecryptionAndProofs( decryptions, challengeResponses)
    }

    private fun DecryptingTrusteeIF.getPartialDecryptionsFromTrustee(texts: List<Cipher>, errs : ErrorMessages) : PartialDecryptions {
        // Only need the pads
        val pads = texts.map { it.pad() }

        val pds = this.decrypt(pads)
        if (pds.err != null) {
            errs.add(pds.err)
        }
        return pds
    }

    // send all challenges for a ballot / tally to one trustee, get all its responses, in one call
    fun DecryptingTrusteeIF.getResponsesFromTrustee(batchId: Int, decryptions: List<CipherDecryption>, errs : ErrorMessages) : ChallengeResponses {
        val wi = lagrangeCoordinates[this.id()]!!.lagrangeCoefficient // buildLagrangeCoordinates() guarentees exists
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
    fun makeHDecryptionAndProofs(
        decryptions: List<CipherDecryption>, // for each text
        challengeResponses: List<ChallengeResponses>, // for each trustee, list of responses for each text
    ): List<CipherDecryptionAndProof> {
        val ds = mutableListOf<CipherDecryptionAndProof>()
        decryptions.forEachIndexed { idx, decryption ->
            val responsesForIdx = challengeResponses.map { it.responses[idx] }
            ds.add( makeHDecryptionAndProof( decryption, responsesForIdx) )
        }
        return ds // one for each text
    }

    private fun makeHDecryptionAndProof(
        decryption: CipherDecryption,
        challengeResponses: List<ElementModQ>, // across trustees for this decryption
    ): CipherDecryptionAndProof {
        // v = Sum(v_i mod q); spec 2.0.0 eq 76
        val response: ElementModQ = group.addQ(challengeResponses)
        val proof = ChaumPedersenProof(decryption.collectiveChallenge.toElementModQ(group), response)
        return CipherDecryptionAndProof(decryption, proof)
    }

    companion object {
        private val logger = KotlinLogging.logger("Decryptor")
    }
}

// one decryption
class CipherDecryption(
    val cipher: Cipher, // text that was decrypted
    val beta: ElementModP, // lagrange weighted product of the shares, M = Prod(M_i^w_i) mod p; spec 2.0, eq 68
    val collectiveChallenge: UInt256, // spec 2.0, eq 71
) {

    fun decryptCiphertext(publicKey: ElGamalPublicKey, ktOnly: Boolean = false): Pair<ElementModP, Int?> {
        require (cipher is Ciphertext)
        val ciphertext = cipher.delegate
        val Kt = ciphertext.data / beta // K^tally
        val tally = if (ktOnly) null else publicKey.dLog(Kt) // may not be able to take the log
        return Pair(Kt, tally)
    }

    fun decryptHashedCiphertext(publicKey: ElGamalPublicKey, extendedBaseHash: UInt256, contestId: String, proof: ChaumPedersenProof): DecryptedTallyOrBallot.DecryptedContestData {
        require (cipher is HashedCiphertext)
        val hashedCiphertext = cipher.delegate

        val contestData = hashedCiphertext.decryptWithBetaToContestData(
                publicKey,
                extendedBaseHash,
                contestId,
                beta
            )

        return DecryptedTallyOrBallot.DecryptedContestData(
            contestData.unwrap(),
            hashedCiphertext,
            proof,
            beta,
        )
    }
}

// the return value of the decryption
data class CipherDecryptionAndProof(val decryption: CipherDecryption, val proof: ChaumPedersenProof)

// abstraction so we can work with either ElGamalCiphertext or HashedElGamalCiphertext
interface Cipher {
    fun pad() : ElementModP
    fun collectiveChallenge(extendedBaseHash:UInt256, publicKey: ElGamalPublicKey, a: ElementModP, b: ElementModP, beta: ElementModP): UInt256
}

data class Ciphertext(val delegate: ElGamalCiphertext): Cipher {
    override fun pad() = delegate.pad

    override fun collectiveChallenge(extendedBaseHash:UInt256, publicKey: ElGamalPublicKey, a: ElementModP, b: ElementModP, beta: ElementModP) =
        // "collective challenge" c = H(HE ; 0x30, K, A, B, a, b, M ) ; spec 2.0.0 eq 71
        hashFunction(
            extendedBaseHash.bytes,
            0x30.toByte(),
            publicKey,
            delegate.pad,
            delegate.data,
            a, b, beta)
}

data class HashedCiphertext(val delegate: HashedElGamalCiphertext): Cipher {
    override fun pad() = delegate.c0

    override fun collectiveChallenge(extendedBaseHash:UInt256, publicKey: ElGamalPublicKey, a: ElementModP, b: ElementModP, beta: ElementModP) =
        // "joint challenge" c = H(HE ; 0x31, K, C0 , C1 , C2 , a, b, β) ; 2.0, eq 81
        hashFunction(extendedBaseHash.bytes, 0x31.toByte(), publicKey,
            delegate.c0,
            delegate.c1.toHex(),
            delegate.c2,
            a, b, beta)
}