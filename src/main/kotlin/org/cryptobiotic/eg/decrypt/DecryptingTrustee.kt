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
    val publicKey: ElGamalPublicKey, // Must match the public record
    val keyShare: ElementModQ, // P(i) = (P1 (i) + P2 (i) + · · · + Pn (i)) eq 65
    ) : DecryptingTrusteeIF {

    val group = compatibleContextOrFail(publicKey.key, keyShare)
    init {
        require(xCoordinate > 0)
    }

    override fun id(): String = id
    override fun xCoordinate(): Int = xCoordinate
    override fun guardianPublicKey() = publicKey

    private val mutex = Mutex()
    private val nonceTracker = mutableMapOf<Int, ElementModQ>()

    override fun decrypt(
        texts: List<ElementModP>,
    ): PartialDecryptions {

        val seed = group.randomElementModQ() // random value u in Zq
        val batchId = randomInt()
        runBlocking {
            mutex.withLock {
                nonceTracker[batchId] = seed
            }
        }

        val nonces = Nonces(seed, id)
        val results: MutableList<PartialDecryption> = mutableListOf()
        val badTexts = mutableListOf<Int>()
        texts.forEachIndexed { idx, text ->
            if (!text.isValidElement()) {
                badTexts.add(idx)
            } else { // do not process if not valid
                val u = nonces.get(idx) // random value u in Zq
                val a = group.gPowP(u)  // (a,b) for the proof, spec 2.0.0, eq 69
                val b = text powP u
                val mi = text powP keyShare // Mi = A ^ P(i), spec 2.0.0, eq 66
                results.add(PartialDecryption(mi, a, b))
            }
        }
        val errs = if (badTexts.isEmpty()) null else "invalidResidues for $badTexts"
        return PartialDecryptions(errs, batchId, results)
    }

    override fun challenge(
        batchId: Int,
        challenges: List<ElementModQ>,
    ): ChallengeResponses {
        val seed: ElementModQ?
        runBlocking {
            mutex.withLock {
                seed = nonceTracker.remove(batchId)
            }
        }
        if (seed == null) {
            return ChallengeResponses("Unknown batchId in trustee '$id'", batchId, emptyList())
        }

        val nonces = Nonces(seed, id)
        val responses =  challenges.mapIndexed { idx, challenge ->
            val nonce = nonces.get(idx)
            nonce - challenge * keyShare // spec 2.0.0, eq 73
        }
        return ChallengeResponses(null, batchId, responses)
    }
}