package org.cryptobiotic.eg.decrypt

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.cryptobiotic.eg.core.*
import org.cryptobiotic.eg.core.randomInt

/**
 * A Trustee that has a share of the election private key, for the purpose of decryption.
 * DecryptingTrustee must stay private. Guardian is its public info in the election record.
 */
data class DecryptingTrustee(
    val id: String,
    val xCoordinate: Int,
    val publicKey: ElementModP, // Must match the public record
    val keyShare: ElementModQ, // P(i) = (P1 (i) + P2 (i) + · · · + Pn (i)) eq 65
    ) : DecryptingTrusteeIF {

    val group = compatibleContextOrFail(publicKey, keyShare)
    init {
        require(xCoordinate > 0)
    }

    override fun id(): String = id
    override fun xCoordinate(): Int = xCoordinate
    override fun guardianPublicKey(): ElementModP = publicKey

    ///////////
    // old way
    private val randomConstantNonce = group.randomElementModQ(2) // random value u in Zq

    override fun decrypt(
        group: GroupContext,
        texts: List<ElementModP>,
    ): List<PartialDecryption> {
        val results: MutableList<PartialDecryption> = mutableListOf()
        for (text: ElementModP in texts) {
            if (!text.isValidResidue()) {
                return emptyList()
            }
            val u = group.randomElementModQ(2) // random value u in Zq
            val a = group.gPowP(u)  // (a,b) for the proof, spec 2.0.0, eq 69
            val b = text powP u
            val mi = text powP keyShare // Mi = A ^ P(i), spec 2.0.0, eq 66
            results.add( PartialDecryption(id, mi, u + randomConstantNonce, a, b))
        }
        return results
    }

    override fun challenge(
        group: GroupContext,
        challenges: List<ChallengeRequest>,
    ): List<ChallengeResponse> {
        return challenges.map {
            ChallengeResponse(it.id, it.nonce - randomConstantNonce - it.challenge * keyShare) // spec 2.0.0, eq 73
        }
    }

    ////////////////////////////////
    // new way
    private val mutex = Mutex()
    private val nonceTracker = mutableMapOf<Int, ElementModQ>()

    override fun decrypt2(
        texts: List<ElementModP>,
    ): PartialDecryptions {

        val seed = group.randomElementModQ(2) // random value u in Zq
        val batchId = randomInt()
        runBlocking {
            mutex.withLock {
                nonceTracker[batchId] = seed
            }
        }

        val nonces = Nonces(seed, id)
        val results: MutableList<PartialDecryption> = mutableListOf()
        texts.forEachIndexed { idx, text ->
            if (!text.isValidResidue()) {
                return PartialDecryptions(this.id, batchId, emptyList()) // TODO return error code
            }
            val u = nonces.get(idx) // random value u in Zq
            val a = group.gPowP(u)  // (a,b) for the proof, spec 2.0.0, eq 69
            val b = text powP u
            val mi = text powP keyShare // Mi = A ^ P(i), spec 2.0.0, eq 66

            results.add( PartialDecryption(id, mi, u, a, b)) // TODO remove u
        }
        return PartialDecryptions(this.id, batchId, results)
    }

    override fun challenge2(
        batchId: Int,
        challenges: List<ElementModQ>,
    ): List<ElementModQ> {
        val seed: ElementModQ?
        runBlocking {
            mutex.withLock {
                seed = nonceTracker.remove(batchId)
            }
        }
        if (seed == null) {
            return emptyList() // return error code
        }
        val nonces = Nonces(seed, id)

        return challenges.mapIndexed { idx, challenge ->
            val nonce = nonces.get(idx)
            nonce - challenge * keyShare // spec 2.0.0, eq 73
        }
    }
}