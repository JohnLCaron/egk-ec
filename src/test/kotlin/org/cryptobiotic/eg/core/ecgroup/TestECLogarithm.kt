package org.cryptobiotic.eg.core.ecgroup

import kotlin.test.Test
import kotlin.test.assertEquals

class TestECLogarithm {

    @Test
    fun testLog() {
        val group = EcGroupContext("P-256")

        repeat(100) {
            val exp = group.uIntToElementModQ(it.toUInt())
            val test = group.gPowP(exp)
            val log = group.dLogG(test)
            print("$it log= $log p= ${test.toStringShort()}")
            val elem = test as EcElementModP
            println("  x,y size=${elem.ec.x.bitLength()} ${elem.ec.y.bitLength()}")
            assertEquals( it, log)
        }
    }

}