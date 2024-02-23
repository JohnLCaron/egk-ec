package org.cryptobiotic.gmp

import com.verificatum.vecj.VEC
import org.cryptobiotic.eg.core.ElementModP
import org.cryptobiotic.eg.core.GroupContext
import org.cryptobiotic.eg.core.ecgroup.*
import org.cryptobiotic.eg.core.productionGroup
import org.cryptobiotic.util.Stopwatch
import org.junit.jupiter.api.Assertions.assertTrue
import java.math.BigInteger
import kotlin.test.Test

class VecTest {
    @Test
    fun testProperties() {
        System.getProperties().forEach {
            println(it)
        }
    }

    @Test
    fun testIfLoaded() {
        val names = VEC.getCurveNames()
        names.forEach{
            println(it)
        }
    }

    @Test
    fun testVecParams() {
        val names = arrayOf("modulus","a","b<","gx","gy","n")
        val curve_ptr: ByteArray = VEC.getCurve("P-256")
        val params : Array<BigInteger> = VEC.getCurveParams(curve_ptr)
        params.forEachIndexed{ idx, it ->
            println("${names[idx]} = ${it.toString(16)}")
        }
    }

    @Test
    // time java ec exp
    fun timeVecJavaExp() {
        val group = productionGroup("P-256", false)
        timeVecJavaExp(group, 100)
        timeVecJavaExp(group, 100)
    }

    fun timeVecJavaExp(group: GroupContext, n:Int) {
        val nonces = List(n) { group.randomElementModQ() }
        val h = group.gPowP(group.randomElementModQ())

        var stopwatch = Stopwatch()
        repeat(n) { h powP nonces[it] }
        println("timeVecJavaExp ${stopwatch.tookPer(n, "exps")}")
    }

    @Test
    // time ec exp
    fun timeVecExp() {
        val group = productionGroup("P-256", true)
        timeVecExp(group, 1000)
        timeVecExp(group, 1000)
    }

    fun timeVecExp(group: GroupContext, n:Int) {
        val nonces = List(n) { group.randomElementModQ() }
        val h = group.gPowP(group.randomElementModQ())

        var stopwatch = Stopwatch()
        repeat(n) { h powP nonces[it] }
        println("timeVecExp ${stopwatch.tookPer(n, "exps")}")
    }

    @Test
    // time using VEC directly
    fun testVecDirectExp() {
        val group = productionGroup("P-256") as EcGroupContext
        val namedCurve: ByteArray = VEC.getCurve("P-256")
        testVecDirectExp(group, namedCurve, 100)
        testVecDirectExp(group, namedCurve, 100)
    }

    fun testVecDirectExp(group: EcGroupContext, curvePtr: ByteArray, n:Int) {
        val nonces = List(n) { group.randomElementModQ() }
        val h = group.gPowP(group.randomElementModQ()) as EcElementModP
        val hx = h.ec.x
        val hy = h.ec.y

        //     public static BigInteger[] mul(final byte[] curve_ptr,
        //                                   final BigInteger x,
        //                                   final BigInteger y,
        //                                   final BigInteger scalar) {
        var stopwatch = Stopwatch()
        nonces.forEach {
            val scalar = (it as EcElementModQ).element
            val result: Array<BigInteger> = VEC.mul(curvePtr, hx, hy, scalar)
            val resultElem = EcElementModP(group, VecElementModP(group.ecGroup, result[0], result[1]))
        }
        println("testVecDirectExp ${stopwatch.tookPer(n, "VEC.mul")}")
    }

    @Test
    // time java ec exp
    fun compareVecExp() {
        val group = productionGroup("P-256", false)
        val groupN = productionGroup("P-256", true)
        val n = 100
        val nonces = List(n) { group.randomElementModQ() }
        val rexp = group.randomElementModQ()
        val h = group.gPowP(rexp)
        val hn = groupN.gPowP(rexp)

        assertTrue(h is EcElementModP)
        assertTrue(hn is EcElementModP)
        assertTrue((h as EcElementModP).ec is VecElementModP)
        assertTrue((hn as EcElementModP).ec is VecElementModPnative)

        val prodpow : ElementModP = nonces.map { h powP it }.reduce{ a, b -> a * b }
        val prodpowN : ElementModP = nonces.map { hn powP it }.reduce{ a, b -> a * b }
        assertTrue (prodpow.byteArray().contentEquals(prodpowN.byteArray()))
    }
}