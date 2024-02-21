package org.cryptobiotic.eg.core

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.WordSpec
import io.kotest.core.spec.style.wordSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import org.cryptobiotic.eg.core.ecgroup.EcGroupContext
import org.cryptobiotic.eg.core.productionGroup

class SchnorrTest : WordSpec({
    val intGroup = productionGroup()
    val ecGroup = EcGroupContext("P-256")

    include(testSchnorrProof("integer group", intGroup))
    include(testSchnorrProof("elliptic group", ecGroup))
})

private fun testSchnorrProof(name: String, group: GroupContext) = wordSpec {
    name should {
        "create valid SchnoorProofs" {
            runTest {
                checkAll(
                    propTestFastConfig,
                    elGamalKeypairs(group),
                    Arb.int(1, 11),
                    Arb.int(0, 10),
                    elementsModQ(group),
                    validResiduesOfP(group),
                    elementsModQ(group)
                ) { kp, i, j, nonce, fakeElementModP, fakeElementModQ ->
                    val goodProof = kp.schnorrProof(i, j, nonce)
                    (goodProof.validate(i, j) is Ok) shouldBe true
                }
            }
        }
    }

    name should {
        "not validate bad SchnoorProofs" {
            runTest {
                checkAll(
                    propTestFastConfig,
                    elGamalKeypairs(group),
                    Arb.int(1, 11),
                    Arb.int(0, 10),
                    elementsModQ(group),
                    validResiduesOfP(group),
                    elementsModQ(group)
                ) { kp, i, j, nonce, fakeElementModP, fakeElementModQ ->
                    val goodProof = kp.schnorrProof(i, j, nonce)
                    val badProof1 = goodProof.copy(challenge = fakeElementModQ)
                    val badProof2 = goodProof.copy(response = fakeElementModQ)

                    // The generator might have generated replacement values equal to the
                    // originals, so we need to be a little bit careful here.
                    (badProof1.validate(i, j, false) is Err) shouldBe true
                    (badProof2.validate(i, j, false) is Err) shouldBe true
                }
            }
        }
    }
}