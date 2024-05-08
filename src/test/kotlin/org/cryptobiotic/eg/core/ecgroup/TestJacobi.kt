package org.cryptobiotic.eg.core.ecgroup

import org.cryptobiotic.eg.core.ecgroup.VecGroup.Companion.jacobiSymbol
import org.cryptobiotic.eg.core.randomBytes
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals

class TestJacobi {

    @Test
    fun testJacobiJava() {
        val group = EcGroupContext("P-256", false)
        testJacobi(group.vecGroup)
    }

    @Test
    fun testJacobiNative() {
        if (!VecGroups.hasNativeLibrary()) return

        val group = EcGroupContext("P-256", true)
        testJacobi(group.vecGroup)
    }

    fun testJacobi(vgroup: VecGroup) {
        val pM1over2= (vgroup.primeModulus - BigInteger.ONE) / BigInteger.TWO

        val ntrials = 1000
        var countTrue = 0
        repeat(ntrials) {
            val x = BigInteger(1, randomBytes(vgroup.pbyteLength))
            val fx = vgroup.equationf(x)
            val isJacobiOne = jacobiSymbol(fx, vgroup.primeModulus) == 1
            if (isJacobiOne) countTrue++

            // presumably its equivalent to y^((p-1)/2) == 1 as described in \cite{Haines20}
            val testHaines = fx.modPow(pM1over2, vgroup.primeModulus) == BigInteger.ONE
            // println("isJacobiOne = $isJacobiOne testHaines=$testHaines")
            assertEquals(isJacobiOne, testHaines)
        }
        println(" ${vgroup.javaClass} isJacobiOne= $countTrue / $ntrials")
    }
}