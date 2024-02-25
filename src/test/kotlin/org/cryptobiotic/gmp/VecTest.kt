package org.cryptobiotic.gmp

import com.verificatum.vecj.VEC
import org.cryptobiotic.eg.core.ElementModP
import org.cryptobiotic.eg.core.GroupContext
import org.cryptobiotic.eg.core.ecgroup.*
import org.cryptobiotic.eg.core.productionGroup
import org.cryptobiotic.util.Stopwatch
import org.junit.jupiter.api.Assertions.assertEquals
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
            val resultElem = EcElementModP(group, VecElementModP(group.vecGroup, result[0], result[1]))
        }
        println("testVecDirectExp ${stopwatch.tookPer(n, "VEC.mul")}")
    }

    @Test
    // test java vs native agreement
    fun testAgreePowp() {
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

    @Test
    fun testProdPowers() {
        val groupN = productionGroup("P-256", false) as EcGroupContext
        val n = 100
        val bases = List(n) { groupN.randomElementModP() }
        val nonces = List(n) { groupN.randomElementModQ() }

        var stopwatch = Stopwatch()
        val prodpow : ElementModP = groupN.prodPowers(bases, nonces)
        println("testProdPowers ${stopwatch.tookPer(n, "exps")}")
    }

    @Test
    fun testProdPowersN() {
        val groupN = productionGroup("P-256", true) as EcGroupContext
        val n = 100
        val bases = List(n) { groupN.randomElementModP() }
        val nonces = List(n) { groupN.randomElementModQ() }

        var stopwatch = Stopwatch()
        val prodPowers : ElementModP = groupN.prodPowers(bases, nonces)
        val prodPowerTime = stopwatch.stop()
        //println("prodPowerTime ${Stopwatch.perRow(prodPowerTime, n)}")

        // call for each exp separately but use native
        stopwatch.start()
        val pows = List( nonces.size) { bases[it].powP(nonces[it]) }
        val prodPow = pows.reduce { a, b -> (a * b) }
        val prodPowTime = stopwatch.stop()
        //println("prodPowTime ${Stopwatch.perRow(prodPowTime, n)}")

        println("testProdPowersN ${Stopwatch.ratioAndPer(prodPowTime, prodPowerTime, n)}")

        assertEquals(prodPowers, prodPow)

        // prodPowerTime took 6 ms for 100 nrows, .06 ms per nrows
        // prodPowTime took 22 ms for 100 nrows, .22 ms per nrows
        // testProdPowersN 22 / 6 ms =  3.228;  .2252 / .06976 ms per row

        // outlier? warmup?
        // testProdPowN 30 / 14 ms =  2.182;  .3089 / .1415 ms per row
    }
}