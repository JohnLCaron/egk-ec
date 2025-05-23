package org.cryptobiotic.eg.workflow

import org.cryptobiotic.eg.core.ElGamalKeypair
import org.cryptobiotic.eg.core.ElGamalPublicKey
import org.cryptobiotic.eg.core.ElGamalSecretKey
import org.cryptobiotic.eg.core.ElementModP
import org.cryptobiotic.eg.core.elGamalKeyPairFromRandom
import org.cryptobiotic.eg.core.encrypt
import org.cryptobiotic.eg.core.encryptedSum
import org.cryptobiotic.eg.core.productionGroup
import kotlinx.coroutines.test.runTest
import org.cryptobiotic.eg.core.toElementModQ
import kotlin.test.Test
import kotlin.test.assertEquals

/** test basic workflow: encrypt, accumulate, decrypt */
class TestWorkflowEncryptDecrypt {
    @Test
    fun singleTrusteeZero() {
        runTest {
            val group = productionGroup()
            val secret = group.randomElementModQ()
            val publicKey = ElGamalPublicKey(group.gPowP(secret))
            val keypair = ElGamalKeypair(ElGamalSecretKey(secret), publicKey)
            val nonce = group.randomElementModQ()

            // accumulate random sequence of 1 and 0
            val vote = 0
            val evote = vote.encrypt(publicKey, nonce)
            assertEquals(group.gPowP(nonce), evote.pad)
            val oneOrG = group.gPowP(vote.toElementModQ(group))
            val expectedData = oneOrG * publicKey.key powP nonce
            assertEquals(expectedData, evote.data)

            //decrypt
            val partialDecryption = evote.computeShare(keypair.secretKey)
            val decryptedValue: ElementModP = evote.data / partialDecryption
            val m = publicKey.dLog(decryptedValue)
            assertEquals(0, m)
        }
    }

    @Test
    fun singleTrusteeOne() {
        runTest {
            val group = productionGroup()
            val secret = group.randomElementModQ()
            val publicKey = ElGamalPublicKey(group.gPowP(secret))
            val keypair = ElGamalKeypair(ElGamalSecretKey(secret), publicKey)
            val nonce = group.randomElementModQ()

            // acumulate random sequence of 1 and 0
            val vote = 1
            val evote = vote.encrypt(publicKey, nonce)
            assertEquals(group.gPowP(nonce), evote.pad)
            val expectedData = publicKey.key powP (nonce + vote.toElementModQ(group))
            assertEquals(expectedData, evote.data)

            //decrypt
            val partialDecryption = evote.computeShare(keypair.secretKey)
            val decryptedValue: ElementModP = evote.data / partialDecryption
            val m = publicKey.dLog(decryptedValue)
            assertEquals(1, m)
        }
    }

    @Test
    fun singleTrusteeTally() {
        runTest {
            val group = productionGroup()
            val secret = group.randomElementModQ()
            val publicKey = ElGamalPublicKey(group.gPowP(secret))
            assertEquals(group.gPowP(secret), publicKey.key)
            val keypair = ElGamalKeypair(ElGamalSecretKey(secret), publicKey)

            val vote = 1
            val evote1 = vote.encrypt(publicKey, group.randomElementModQ())
            val evote2 = vote.encrypt(publicKey, group.randomElementModQ())
            val evote3 = vote.encrypt(publicKey, group.randomElementModQ())

            val accum = listOf(evote1, evote2, evote3)
            val eAccum = accum.encryptedSum()?: 0.encrypt(publicKey)

            //decrypt
            val partialDecryption = eAccum.computeShare(keypair.secretKey)
            val decryptedValue: ElementModP = eAccum.data / partialDecryption
            val m = publicKey.dLog(decryptedValue)
            assertEquals(3, m)
        }
    }

    @Test
    fun multipleTrustees() {
        runTest {
            val group = productionGroup()
            val trustees = listOf(
                elGamalKeyPairFromRandom(group),
                elGamalKeyPairFromRandom(group),
                elGamalKeyPairFromRandom(group),
            )
            val pkeys: Iterable<ElementModP> = trustees.map { it.publicKey.key}
            val publicKey = ElGamalPublicKey( group.multP(pkeys) )

            val vote = 1
            val evote1 = vote.encrypt(publicKey, group.randomElementModQ())
            val evote2 = vote.encrypt(publicKey, group.randomElementModQ())
            val evote3 = vote.encrypt(publicKey, group.randomElementModQ())

            // tally
            val accum = listOf(evote1, evote2, evote3)
            val eAccum = accum.encryptedSum()?: 0.encrypt(publicKey)

            //decrypt
            val shares = trustees.map { eAccum.pad powP it.secretKey.key }
            val allSharesProductM: ElementModP = group.multP(shares)
            val decryptedValue: ElementModP = eAccum.data / allSharesProductM
            val dlogM: Int = publicKey.dLog(decryptedValue)?: throw RuntimeException("dlog error")
            assertEquals(3, dlogM)

            //decrypt2
            val dlogM2 = eAccum.decryptWithShares(publicKey, shares)
            assertEquals(3, dlogM2)
        }
    }
}