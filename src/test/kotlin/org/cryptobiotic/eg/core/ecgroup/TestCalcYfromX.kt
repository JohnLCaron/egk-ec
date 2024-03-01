package org.cryptobiotic.eg.core.ecgroup

import org.cryptobiotic.eg.core.Base16.toHex
import org.cryptobiotic.eg.core.Base64.fromBase64
import org.cryptobiotic.eg.core.GroupContext
import org.cryptobiotic.eg.core.productionGroup
import org.junit.jupiter.api.Assertions.assertEquals
import java.math.BigInteger

import kotlin.test.Test

class TestCalcYfromX {

    @Test
    fun testRoundtrip() {
        val vecGroup = VecGroups.getEcGroup("P-256", false)
        testRoundtrip(vecGroup, "Rpk9roh5iY5NMuUASaDsinWovp2gxLI8bOXrVMqxzj4=", "NYl5WxfLXBDASVSA2iXEFVVkw91oeB9feqren6BeIME=")
        testRoundtrip(vecGroup, "Rpk9roh5iY5NMuUASaDsinWovp2gxLI8bOXrVMqxzj4=", "AMp2hqPoNKPwP7arfyXaO+qqmzwjl4fgoIVVIWBfod8+")
        // Expected :VecElementP(Rpk9roh5iY5NMuUASaDsinWovp2gxLI8bOXrVMqxzj4=, NYl5WxfLXBDASVSA2iXEFVVkw91oeB9feqren6BeIME=)
        // Actual   :VecElementP(Rpk9roh5iY5NMuUASaDsinWovp2gxLI8bOXrVMqxzj4=, AMp2hqPoNKPwP7arfyXaO+qqmzwjl4fgoIVVIWBfod8+)

        testRoundtrip(vecGroup, "JBMVm3j1N43jtmP0U2gSgaEfOvsalw3RFJYK98W7vJQ=", "AIvNYmlbu9YrWsSrg/TuQzipiOfBvt4SDva2ymUYKC8z")
        testRoundtrip(vecGroup, "JBMVm3j1N43jtmP0U2gSgaEfOvsalw3RFJYK98W7vJQ=", "dDKdlaREKdWlO1R8CxG8x1Z3GD9BIe3xCUk1mufX0Mw=")
        // Expected :VecElementP(JBMVm3j1N43jtmP0U2gSgaEfOvsalw3RFJYK98W7vJQ=, AIvNYmlbu9YrWsSrg/TuQzipiOfBvt4SDva2ymUYKC8z)
        //Actual   :VecElementP(JBMVm3j1N43jtmP0U2gSgaEfOvsalw3RFJYK98W7vJQ=, dDKdlaREKdWlO1R8CxG8x1Z3GD9BIe3xCUk1mufX0Mw=)
    }

    fun testRoundtrip(vecGroup: VecGroup, xs: String, ys: String) {
        val xs16 = xs.fromBase64()!!.toHex()
        val ys16 = ys.fromBase64()!!.toHex()
        val vecp = VecElementP(vecGroup, xs16, ys16)
        check("org", vecGroup, vecp)

        val vecr = vecGroup.elementFromByteArray1(vecp.toByteArray1())!!
        check("vecr", vecGroup, vecr)

        println()
    }

    fun check(name: String, vecGroup: VecGroup, vec: VecElementP) {
        println("check $name $vec")
        val isYOdd = checkIfOdd("  vec.y", vec.y)

        // how does it pass this
        val fx = vecGroup.equationf(vec.x)
        val y2 = vec.y.multiply(vec.y).mod(vecGroup.primeModulus)
        println( "  isPointOnCurve = ${fx == y2}" )
      //  println( " jacobiSymbol = ${VecGroup.jacobiSymbol(fx, vecGroup.primeModulus) == 1}" )

        val fxsqrt =  vecGroup.sqrt(fx)
        println( "  y == calcYfromX = ${vec.y == fxsqrt}" )
        val isSqrtOdd = checkIfOdd("  fxsqrt", fxsqrt)

        println(" y == fxsqrtnmod = ${vec.y == fxsqrt.negate().mod(vecGroup.primeModulus)} should flip = ${isYOdd != isSqrtOdd}")
        val fxsqrtElem = vecGroup.makeVecModP(vec.x, fxsqrt)
        if (isYOdd != isSqrtOdd) {
            assertEquals(vec, fxsqrtElem.inv())
        } else {
            assertEquals(vec, fxsqrtElem)
        }
    }

    fun checkIfOdd(what: String, b: BigInteger): Boolean {
        val isOdd0 = b.testBit(0)
        val isOdd1 = b.testBit(b.bitLength() - 1)
        val bbytes = b.toByteArray()
        val firstByte: Int = bbytes[0].toUByte().toInt()
        val lastByte: Int = bbytes[bbytes.size-1].toUByte().toInt()
        val isOdd2 = (lastByte and 1) == 1
        assertEquals(isOdd0, isOdd2) // so testBit is little endian

        println(" $what isOdd = $isOdd0, $isOdd1, $isOdd2 firstLast= $firstByte, $lastByte")
        return isOdd2
    }

    @Test
    fun testDiff() {
        val group = productionGroup("P-256")
        testDiff(group, "zf3s36vzNltIQHCE8LsghGfojN+rXSq2EtDzZ5BH3lAB", "HFZYAcGDmxEvKb4/LBRtjaN7oXayiuSqTy8z20DmH74=",
            "AM397N+r8zZbSEBwhPC7IIRn6Izfq10qthLQ82eQR95Q",
            "APRGXjr26y7xc0/MlAGg2jTYE2VvCpB/z6hBRvM1RUMF",
            "C7mhxAkU0Q+MsDNr/l8lyyfsmpH1b4AwV765DMq6vPo=")
        // Expected :VecElementP(AM397N+r8zZbSEBwhPC7IIRn6Izfq10qthLQ82eQR95Q, APRGXjr26y7xc0/MlAGg2jTYE2VvCpB/z6hBRvM1RUMF)
        //Actual   :VecElementP(AM397N+r8zZbSEBwhPC7IIRn6Izfq10qthLQ82eQR95Q, C7mhxAkU0Q+MsDNr/l8lyyfsmpH1b4AwV765DMq6vPo=)
    }

    fun testDiff(group: GroupContext, pks: String, coeffs: String, expectX: String, expectY1: String, expectY2: String) {
        val vecGroup = (group as EcGroupContext).vecGroup

        val coeff = group.binaryToElementModQ(coeffs.fromBase64()!!)!!
        val pkcalc = (group.gPowP(coeff) as EcElementModP).ec
        check("pkcalc", vecGroup, pkcalc)

        val pk = vecGroup.elementFromByteArray1(pks.fromBase64()!!)!!
        check("pk", vecGroup, pk)

        val xs16 = expectX.fromBase64()!!.toHex()
        val ys116 = expectY1.fromBase64()!!.toHex()
        val ys216 = expectY2.fromBase64()!!.toHex()

        val vec1 = VecElementP(vecGroup, xs16, ys116)
        check("vec1", vecGroup, vec1)

        val vec2 = VecElementP(vecGroup, xs16, ys216)
        check("vec2", vecGroup, vec2)

        println(" y1 == y2.neg.mod = ${ vec1.y == vec2.y.negate().mod(vecGroup.primeModulus)}")
        println(" y1 == y2.mod = ${ vec1.y == vec2.y.mod(vecGroup.primeModulus)}")
        println(" y1 == y2.neg = ${ vec1.y == vec2.y.negate()}")

    }

}

