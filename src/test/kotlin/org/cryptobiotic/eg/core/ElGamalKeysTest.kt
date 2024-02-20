package org.cryptobiotic.eg.core

import io.kotest.core.spec.style.WordSpec
import io.kotest.core.spec.style.wordSpec
import io.kotest.matchers.shouldBe
import org.cryptobiotic.eg.ecgroup.EcGroupContext
import org.cryptobiotic.eg.intgroup.productionGroup

class ElGamalKeysTest : WordSpec({
    val intGroup = productionGroup()
    val ecGroup = EcGroupContext("P-256")

    include(testKeys("integer group", intGroup))
    include(testKeys("elliptic group", ecGroup))
})

private fun testKeys(name: String, group: GroupContext) = wordSpec {
    name should {
        "have correct public key using elGamalKeyPairFromSecret" {
            val secret = group.randomElementModQ(2)
            val keypair = elGamalKeyPairFromSecret(secret)
            keypair.secretKey shouldBe secret
            keypair.publicKey shouldBe group.gPowP(secret)
        }
    }
    name should {
        "have correct public key using elGamalKeyPairFromRandom" {
            val keypair = elGamalKeyPairFromRandom(group)
            keypair.publicKey shouldBe group.gPowP(keypair.secretKey.key)
        }
    }
    name should {
        "have correct inverses" {
            val keypair = elGamalKeyPairFromRandom(group)
            keypair.publicKey shouldBe keypair.publicKey.inverseKey.multInv()
            keypair.secretKey shouldBe -keypair.secretKey.negativeKey
        }
    }
    name should {
        "test first n logarithms" {
            repeat(100) {
                val exp = group.uIntToElementModQ(it.toUInt())
                val test = group.gPowP(exp)
                val log = group.dLogG(test)
                it shouldBe log
            }
        }
    }
}