package org.cryptobiotic.eg.core

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlin.test.Test
import kotlin.test.assertTrue
import org.cryptobiotic.eg.intgroup.productionGroup


class SchnorrTest {
    val useGroup = productionGroup()

    @Test
    fun testCorruption() {
        runTest {
            checkAll(
                propTestFastConfig,
                elGamalKeypairs(useGroup),
                Arb.int(1, 11),
                Arb.int(0, 10),
                elementsModQ(useGroup),
                validResiduesOfP(useGroup),
                elementsModQ(useGroup)
            ) { kp, i, j, nonce, fakeElementModP, fakeElementModQ ->
                val goodProof = kp.schnorrProof(i, j, nonce)
                // hp : UInt256, guardianXCoord: Int, coeff: Int
                assertTrue(goodProof.validate(i, j) is Ok)

                val badProof1 = goodProof.copy(challenge = fakeElementModQ)
                val badProof2 = goodProof.copy(response = fakeElementModQ)

                // The generator might have generated replacement values equal to the
                // originals, so we need to be a little bit careful here.

                assertTrue(goodProof.challenge == fakeElementModQ || badProof1.validate(i, j, false) is Err)
                assertTrue(goodProof.response == fakeElementModQ || badProof2.validate(i, j, false) is Err)
                assertTrue(kp.publicKey.key == fakeElementModP || badProof2.validate(i, j, false) is Err)
            }
        }
    }
}