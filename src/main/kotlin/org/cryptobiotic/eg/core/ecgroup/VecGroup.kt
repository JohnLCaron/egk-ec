/*
 * Copyright 2024 John Caron
 *
 * Derived work from:
 * Copyright 2008-2019 Douglas Wikstrom
 *
 * This file is part of Verificatum Core Routines (VCR).
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use, copy,
 * modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS
 * BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN
 * ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.cryptobiotic.eg.core.ecgroup

import org.cryptobiotic.eg.core.ElementModP
import org.cryptobiotic.eg.core.ElementModQ
import org.cryptobiotic.eg.election.ElectionConstants
import org.cryptobiotic.eg.election.GroupType
import java.math.BigInteger

/**
 * Elliptic curve f(x) = x^3 + ax + b
 * @param curveName Curve name.
 * @param a x-coefficient
 * @param b constant
 * @param primeModulus Field over which to perform calculations.
 * @param order Order of the group.
 * @param gx x-coordinate of the generator.
 * @param gy y-coordinate of the generator.
 * @throws RuntimeException If the input parameters are * inconsistent
 * @throws RuntimeException If the input parameters are * inconsistentaqrt
 */

// So far, I havent seen any use for order, buts its lurking underneath VCR ECqPGroup. ugh.
// the order of G is the smallest positive number n such that ng = O (the point at infinity of the curve, and the identity element)
open class VecGroup(
    val curveName: String,
    val a: BigInteger,
    val b: BigInteger,
    val primeModulus: BigInteger, // Prime primeModulus of the underlying field
    val order: BigInteger,
    gx: BigInteger,
    gy: BigInteger,
    val h: BigInteger
) {
    /** Standard group generator. */
    val g: VecElementP = makeVecModP(gx, gy).acceleratePow()

    /** Group unit element. */
    val ONE: VecElementP = makeVecModP(MINUS_ONE, MINUS_ONE)

    val pbitLength: Int = primeModulus.bitLength()
    val pbyteLength = (pbitLength + 7) / 8
    val qbitLength: Int = order.bitLength()
    val qbyteLength = (qbitLength + 7) / 8

    val constants by lazy {
        ElectionConstants(curveName, GroupType.EllipticCurve, "v2.1.0",
            mapOf(
                "a" to a,
                "b" to b,
                "primeModulus" to primeModulus,
                "order" to order,
                "g.x" to g.x,
                "g.y" to g.y,
                "h" to h,
            )
        )
    }

    open fun makeVecModP(x: BigInteger, y: BigInteger, safe: Boolean = false) = VecElementP(this, x, y, safe)

    val ffbyte: Byte = (-1).toByte()
    fun elementFromByteArray(ba: ByteArray): VecElementP? {
        if (ba.size != 2*pbyteLength) return null
        val allff = ba.fold( true) { a, b -> a && (b == ffbyte) }
        if (allff) return ONE
        val x = BigInteger(1, ByteArray(pbyteLength) { ba[it] })
        val y = BigInteger(1, ByteArray(pbyteLength) { ba[pbyteLength+it] })
        return makeVecModP(x, y)
    }

    fun elementFromByteArray1(ba: ByteArray): VecElementP? {
        if (ba.size != 2*pbyteLength) return null
        val allff = ba.fold( true) { a, b -> a && (b == ffbyte) }
        if (allff) return ONE
        val x = BigInteger(1, ByteArray(pbyteLength) { ba[it] })
        val y = calcYfromX(x)
        return makeVecModP(x, y)
    }

    fun randomElement(): VecElementP {
        val r = java.util.Random()
        for (j in 0 until 1000) { // limited in case theres a bug
            try {
                val x = BigInteger(pbitLength, r)
                val fx = equationf(x)

                if (jacobiSymbol(fx, primeModulus) == 1) {
                    val y2 = sqrt(fx)
                    return makeVecModP(x, y2, true)
                }
            } catch (e: RuntimeException) {
                throw RuntimeException("Unexpected format exception", e)
            }
        }
        throw RuntimeException("Failed to randomize a ro element")
    }

    // note this isnt == BigInteger.sqrt(). This is EC sqrt(), but we only need the x coordinate.
    open fun sqrt(x: BigInteger): BigInteger {
        val p: BigInteger = primeModulus

        val a: BigInteger = x.mod(p)
        if (a.equals(BigInteger.ZERO)) {
            return BigInteger.ZERO
        }
        if (p.equals(BigInteger.TWO)) {
            return a
        }

        // p = 3 mod 4
        if (p.testBit(0) && p.testBit(1)) {
            // v = p + 1
            var v = p.add(BigInteger.ONE)

            // v = v / 4
            v = v.shiftRight(2)

            // return a^v mod p
            // return --> a^((p + 1) / 4) mod p
            return a.modPow(v, p)
        }

        // Compute k and s, where p = 2^s (2k+1) +1

        // k = p - 1
        var k: BigInteger = p.subtract(BigInteger.ONE)
        var s = 0

        // while k is even
        while (!k.testBit(0)) {
            s++
            // k = k / 2
            k = k.shiftRight(1)
        }

        // k = (k - 1) / 2
        k = k.subtract(BigInteger.ONE)
        k = k.shiftRight(1)

        // r = a^k mod p
        var r: BigInteger = a.modPow(k, p)

        // n = r^2 mod p
        var n: BigInteger = r.multiply(r).mod(p)

        // n = n * a mod p
        n = n.multiply(a).mod(p)

        // r = r * a modp
        r = r.multiply(a).mod(p)

        if (n.equals(BigInteger.ONE)) {
            return r
        }

        // non-quadratic residue
        var z: BigInteger = BigInteger.TWO // z = 2

        // while z quadratic residue
        while (jacobiSymbol(z, p) == 1) {
            z = z.add(BigInteger.ONE)
        }

        // v = 2k
        var v = k.multiply(BigInteger.TWO)

        // v = 2k + 1
        v = v.add(BigInteger.ONE)

        // c = z^v mod p
        var c: BigInteger = z.modPow(v, p)

        while (n.compareTo(BigInteger.ONE) > 0) {
            k = n
            var t = s
            s = 0

            // k != 1
            while (!k.equals(BigInteger.ONE)) {
                // k = k^2 mod p
                k = k.multiply(k).mod(p)
                // s = s + 1
                s++
            }

            // t = t - s
            t -= s

            // v = 2^(t-1)
            v = BigInteger.ONE
            v = v.shiftLeft(t - 1)

            // c = c^v mod p
            c = c.modPow(v, p)

            // r = r * c mod p
            r = r.multiply(c).mod(p)

            // c = c^2 mod p
            c = c.multiply(c).mod(p)

            // n = n * c mod p
            n = n.multiply(c).mod(p)
        }
        return r
    }

    // TODO test if we should use (JVM) simultateous exponentiaton
    open fun prodPowers(bases: List<ElementModP>, exps: List<ElementModQ>): VecElementP {
        // val pows = List( exps.size) { bases[it] powP exps[it] }
        val pows = List( exps.size) {
            val base = (bases[it] as EcElementModP).ec
            val exp: BigInteger = (exps[it] as EcElementModQ).element
            base.exp(exp)
        }
        if (pows.count() == 1) {
            return pows[0]
        }
        return pows.reduce { a, b -> (a.mul(b)) }
    }

    // Checks whether the point (x,y) is on the curve as defined by this group.
    // 4 mult, 3 add, 6 mod
    fun isPointOnCurve(x: BigInteger, y: BigInteger): Boolean {
        if (isUnity(x, y)) {
            return true
        }
        if (x.compareTo(BigInteger.ZERO) < 0 || y.compareTo(BigInteger.ZERO) < 0) {
            return false
        }
        if (x.compareTo(primeModulus) >= 0 || y.compareTo(primeModulus) >= 0) {
            return false
        }
        val right = equationf(x)

        val left = y.multiply(y).mod(primeModulus)
        return right == left
    }

    fun calcYfromX(x: BigInteger): BigInteger {
        val fx = equationf(x)
        return sqrt(fx)
    }

    // Applies the curve's formula f(x) = x^3 + ax + b on the given parameter.
    // 3 mult, 2 add, 5 mod
    fun equationf(x: BigInteger): BigInteger {
        var right = x.multiply(x).mod(primeModulus) // TODO  can skip primeModulus on some intermediate terms ?
        right = right.multiply(x).mod(primeModulus)
        val aterm = x.multiply(a).mod(primeModulus)
        right = right.add(aterm).mod(primeModulus) // maybe skip on the add ?
        right = right.add(b).mod(primeModulus)
        return right
    }

    override fun toString(): String {
        return "ECqPGroup($curveName)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as VecGroup

        return curveName == other.curveName
    }

    override fun hashCode(): Int {
        return curveName.hashCode()
    }

    companion object {
        /** Will be used for the "infinity" element of the group.  */
        val MINUS_ONE = BigInteger((-1).toString(16), 16)

        /** Checks whether the input represents the unit element in the group. */
        fun isUnity(x: BigInteger, y: BigInteger): Boolean {
            return x == MINUS_ONE && y == MINUS_ONE
        }

        fun jacobiSymbol(value: BigInteger, modulus: BigInteger): Int {
            var a = value
            var n = modulus

            var sa = 1
            while (true) {
                if (a == BigInteger.ZERO) {
                    return 0
                } else if (a == BigInteger.ONE) {
                    return sa
                } else {
                    val e = a.lowestSetBit
                    a = a.shiftRight(e)
                    val intn = n.toInt() and 0x000000FF
                    var s = 1
                    if (e % 2 != 0 && (intn % 8 == 3 || intn % 8 == 5)) {
                        s = -1
                    }

                    val inta = a.toInt() and 0x000000FF
                    if (intn % 4 == 3 && inta % 4 == 3) {
                        s = -s
                    }

                    sa *= s
                    if (a == BigInteger.ONE) {
                        return sa
                    }
                    n = n.mod(a)
                    val t = a
                    a = n
                    n = t
                }
            }
        }
    }
}