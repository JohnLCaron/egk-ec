package org.cryptobiotic.eg.core

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.core.spec.style.wordSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import io.kotest.property.forAll
import org.cryptobiotic.eg.core.ecgroup.EcGroupContext

import org.cryptobiotic.eg.core.productionGroup
import kotlin.test.assertEquals

private fun smallInts() = Arb.int(min = 0, max = 1000)

class ElGamalEncryptionTest : WordSpec({
    val intGroup = productionGroup()
    val ecGroup = EcGroupContext("P-256")

    include(testEncryption("integer group", intGroup))
    include(testEncryption("elliptic group", ecGroup))
})

private fun testEncryption(name: String, group: GroupContext) = wordSpec {
    name should {
        "throw exception on small keys" {
            runTest {
                shouldThrow<ArithmeticException> { elGamalKeyPairFromSecret(0.toElementModQ(group)) }
                shouldThrow<ArithmeticException> { elGamalKeyPairFromSecret(1.toElementModQ(group)) }
                shouldNotThrowAny { elGamalKeyPairFromSecret(2.toElementModQ(group)) }
            }
        }
    }
    name should {
        "match encrypt and decrypt message" {
            val message = 1
            val nonce = group.randomElementModQ()
            val keypair = elGamalKeyPairFromRandom(group)
            val encryption = message.encrypt(keypair, nonce)
            message shouldBe encryption.decrypt(keypair)
        }
    }

    name should {
        "match encrypt and decrypt random messages" {
            runTest {
                checkAll(
                    propTestFastConfig,
                    elGamalKeypairs(group),
                    elementsModQNoZero(group),
                    smallInts()
                ) { keypair, nonce, message ->
                    message shouldBe message.encrypt(keypair, nonce).decrypt(keypair)
                }
            }
        }
    }

    name should {
        "match encrypt and decrypt with random nonces" {
            runTest {
                checkAll(
                    propTestFastConfig,
                    elGamalKeypairs(group),
                    smallInts()
                ) { keypair, message ->
                    val encryption = message.encrypt(keypair)
                    val decryption = encryption.decrypt(keypair)
                    message shouldBe decryption
                }
            }
        }
    }

    name should {
        "match encrypt and decrypt extra nonce messages" {
            runTest {
                var count = 0
                checkAll(
                    propTestFastConfig,
                    elGamalKeypairs(group), smallInts()
                ) { keypair, message ->
                    val org = message.encrypt(keypair)
                    val extra: ElementModQ = group.randomElementModQ(minimum = 1)
                    val extraEncryption =
                        ElGamalCiphertext(org.pad * group.gPowP(extra), org.data * (keypair.publicKey powP extra))
                    val decryption = extraEncryption.decrypt(keypair)
                    assertEquals(message, decryption)
                    count++
                }
            }
        }
    }

    name should {
        "decrypt with nonce" {
            runTest {
                checkAll(
                    propTestFastConfig,
                    elGamalKeypairs(group),
                    elementsModQNoZero(group),
                    smallInts()
                )
                { keypair, nonce, message ->
                    val encryption = message.encrypt(keypair, nonce)
                    val decryption = encryption.decryptWithNonce(keypair.publicKey, nonce)
                    assertEquals(message, decryption)
                }
            }
        }
    }

    name should {
        "homomorphic sccumulation" {
            runTest {
                forAll(
                    propTestFastConfig,
                    elGamalKeypairs(group),
                    smallInts(),
                    smallInts(),
                    elementsModQNoZero(group),
                    elementsModQNoZero(group)
                ) { keypair, p1, p2, n1, n2 ->
                    val c1 = p1.encrypt(keypair, n1)
                    val c2 = p2.encrypt(keypair, n2)
                    val csum = c1 + c2
                    val d = csum.decrypt(keypair)
                    p1 + p2 == d
                }
            }
        }
    }

}
