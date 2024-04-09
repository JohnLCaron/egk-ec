package org.cryptobiotic.eg.core.ecgroup

import org.cryptobiotic.eg.core.ElementModP
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestAgainstNative {

    @Test
    fun testSqrt3() {
        if (VecGroups.hasNativeLibrary()) {
            val group = EcGroupContext("P-256", false)
            val vecGroup = group.vecGroup
            val vecGroupN = EcGroupContext("P-256", true).vecGroup
            assertTrue(vecGroupN is VecGroupNative)

            // randomElementModP seems to always be the case when p = 3 mod 4
            repeat(100) {
                val elemP = group.randomElementModP(2).ec
                val elemPy2 = vecGroupN.equationf(elemP.x)

                val elemPy = vecGroup.sqrt(elemPy2)
                assertEquals(elemP.y, elemPy)

                val elemPyt = vecGroupN.sqrt(elemPy2)
                assertEquals(elemP.y, elemPyt)

                assertEquals(elemPy, elemPyt)
            }
        }
    }

    // use P-224 because pIs3mod4 = false
    @Ignore("pIs3mod4 = false not working")
    fun testSqrt() {
        val group = EcGroupContext("P-224", false)
        val vecGroup = group.vecGroup
        val vecGroupN = EcGroupContext("P-224", true).vecGroup
        assertTrue(vecGroupN is VecGroupNative)

        // seems to be the case when p = 3 mod 4
        repeat(100) {
            val elemP = group.randomElementModP(2).ec
            val elemPy = elemP.y

            val sqrtPy = vecGroup.sqrt(elemPy)
            assertEquals(elemP.y, elemPy)

            val sqrtPyN = vecGroupN.sqrt(elemPy)

            assertEquals(sqrtPy, sqrtPyN)
        }
    }

    @Test
    fun testProdPowers() {
        if (VecGroups.hasNativeLibrary()) {
            val group = EcGroupContext("P-256", false)
            val groupN = EcGroupContext("P-256", true)
            assertTrue(groupN.vecGroup is VecGroupNative)

            val n = 100
            val bases = List(n) { group.randomElementModP() }
            val nonces = List(n) { group.randomElementModQ() }
            val prodpow: ElementModP = group.prodPowers(bases, nonces)
            val prodpowN: ElementModP = groupN.prodPowers(bases, nonces)
            assertEquals(prodpow, prodpowN)
        }
    }

}