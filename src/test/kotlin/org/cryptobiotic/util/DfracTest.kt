package org.cryptobiotic.util

import kotlin.test.Test

// LOOK can use println("SimpleBallot %.2f encryptions / sec".format(numBallots / encryptionTime)) instead of dfrac

class DfracTest {


    @Test
    fun compareDfrac() {
        compareDfrac("0.0909", (1.0 / 11), 4)
        compareDfrac("0.090", (1.0 / 11), 3) // should be 0.091 ?
        compareDfrac("0.09", (1.0 / 11), 2)
        compareDfrac("0.0", (1.0 / 11), 1) // should be 0.1 ?
    }

    fun compareDfrac(expect: String, d : Double, fixed: Int) {
        val df = " %.${fixed}f".format(d)
        val dfrac = d.dfrac(fixed)
        println("expect = $expect format = $df, dfrac = $dfrac")
    }

    /**
     * Format a double value to have a fixed number of decimal places.
     *
     * @param fixedDecimals number of fixed decimals
     * @return double formatted as a string
     */
    fun Double.dfrac(fixedDecimals: Int = 2): String {
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
        val number: StringBuilder
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
        val fracFigs = fraction.length

        if (fixedDecimals == 0) {
            fraction.setLength(0)
        } else if (fixedDecimals > fracFigs) {
            val want = fixedDecimals - fracFigs
            for (i in 0 until want) {
                fraction.append("0")
            }
        } else if (fixedDecimals < fracFigs) {
            val chop = fracFigs - fixedDecimals // TODO should round !!
            fraction.setLength(fraction.length - chop)
        }

        return if (fraction.isEmpty()) {
            "$sign$number$exponent"
        } else {
            "$sign$number.$fraction$exponent"
        }
    }
}