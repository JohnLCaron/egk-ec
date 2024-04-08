package org.cryptobiotic.util

import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals

class SigfigTest {

    @Test
    fun testSigfig() {
        assertEquals(".0909090", (1.0 / 11).sigfig(6))
        assertEquals(".090909", (1.0 / 11).sigfig(5))
        assertEquals(".09090", (1.0 / 11).sigfig(4))
        assertEquals(".0909", (1.0 / 11).sigfig(3))
        assertEquals(".090", (1.0 / 11).sigfig(2))
        assertEquals(".09", (1.0 / 11).sigfig(1))
        assertEquals(".0", (1.0 / 11).sigfig(0))
    }

    @Test
    fun testSigfig2() {
        assertEquals("9.09090", (100.0 / 11).sigfig(6))
        assertEquals("100.090", (100.0 + 1.0 / 11).sigfig(6))
    }

    @Test
    fun compareSigfig() {
        compareSigfig(".0909090", (1.0 / 11),6)
        compareSigfig(".090909", (1.0 / 11), 5)
        compareSigfig(".09090", (1.0 / 11), 4)
        compareSigfig(".0909", (1.0 / 11), 3)
        compareSigfig(".090", (1.0 / 11), 2)
        compareSigfig(".09", (1.0 / 11), 1)
        compareSigfig(".0", (1.0 / 11), 0)
    }

    @Test
    fun compareSigfigGreaterThanOne() {
        compareSigfig("9.09090", (100.0 / 11), 6)
        compareSigfig("100.090", (100.0 + 1.0 / 11), 6)
    }


    fun compareSigfig(expect: String, d : Double, fixed: Int) {
        val df = " %.${fixed}G".format(d)
        val sigfig = d.sigfig(fixed)
        println("$fixed expect = $expect format = $df, sigfig = $sigfig")
    }

}


/**
 * Format a double value to have a minimum significant figures.
 *
 * @param minSigfigs minimum significant figures
 * @return double formatted as a string
 */
fun Double.sigfig(minSigfigs: Int = 4): String {
    val s: String = this.toString()

    // extract the sign
    val sign: String
    val unsigned: String
    if (s.startsWith("-") || s.startsWith("+")) {
        sign = s.substring(0, 1)
        unsigned = s.substring(1)
    } else {
        sign = ""
        unsigned = s
    }

    // deal with exponential notation
    val mantissa: String
    val exponent: String
    var eInd = unsigned.indexOf('E')
    if (eInd == -1) {
        eInd = unsigned.indexOf('e')
    }
    if (eInd == -1) {
        mantissa = unsigned
        exponent = ""
    } else {
        mantissa = unsigned.substring(0, eInd)
        exponent = unsigned.substring(eInd)
    }

    // deal with decimal point
    var number: StringBuilder
    val fraction: StringBuilder
    val dotInd = mantissa.indexOf('.')
    if (dotInd == -1) {
        number = StringBuilder(mantissa)
        fraction = StringBuilder()
    } else {
        number = StringBuilder(mantissa.substring(0, dotInd))
        fraction = StringBuilder(mantissa.substring(dotInd + 1))
    }

    // number of significant figures
    var numFigs = number.length
    var fracFigs = fraction.length

    // Don't count leading zeros in the fraction, if no number
    if (numFigs == 0 || number.toString() == "0" && fracFigs > 0) {
        numFigs = 0
        number = StringBuilder()
        for (element in fraction) {
            if (element != '0') {
                break
            }
            --fracFigs
        }
    }
    // Don't count trailing zeroes in the number if no fraction
    if (fracFigs == 0 && numFigs > 0) {
        for (i in number.length - 1 downTo 1) {
            if (number[i] != '0') {
                break
            }
            --numFigs
        }
    }
    // deal with min sig figures
    val sigFigs = numFigs + fracFigs
    if (sigFigs > minSigfigs) {
        // Want fewer figures in the fraction; chop (should round? )
        val chop: Int = min(sigFigs - minSigfigs, fracFigs)
        fraction.setLength(fraction.length - chop)
    }

    return if (fraction.isEmpty()) {
        "$sign$number$exponent"
    } else {
        "$sign$number.$fraction$exponent"
    }
}