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

import org.cryptobiotic.eg.core.Base64.toBase64
import org.cryptobiotic.eg.core.normalize
import org.cryptobiotic.eg.core.ecgroup.VecGroup.Companion.MINUS_ONE
import java.math.BigInteger
import java.util.*

open class VecElementP(
    val pGroup: VecGroup,
    val x: BigInteger,
    val y: BigInteger,
    safe: Boolean = false // eg randomElement() knows its safe
) {
    val modulus = pGroup.primeModulus

    constructor(group: VecGroup, xs: String, ys: String): this(group, BigInteger(xs,16), BigInteger(ys, 16))

    init {
        if (!safe && !pGroup.isPointOnCurve(x, y)) {
            pGroup.isPointOnCurve(x, y)
            throw RuntimeException("Given point is not on the described curve")
        }
    }

    open fun acceleratePow(): VecElementP {
        return this
    }

    // For elliptic curve group operations, we use the well-known formulae in Jacobian projective coordinates:
    // point doubling in projective coordinates costs 5 field squarings, 3 field multiplication, and 12 linear
    // operations (additions, subtractions, scalar multiplications),
    // while point addition costs 4 squarings, 12 multiplications and 7 linear operations.

    /** Compute the product of this element with other. */
    open fun mul(other: VecElementP): VecElementP {
         if (pGroup != other.pGroup) {
            throw RuntimeException("Distinct groups!")
        }

        // If this instance is the unit element, then we return the input.
        if (x == MINUS_ONE) {
            return other
        }

        // If the other is the unit element, then we return this instance.
        if (other.x == MINUS_ONE) {
            return this
        }

        // If the other is the inverse of this element, then we return the unit element.
        if (x == other.x && y.add(other.y) == modulus) {
            return pGroup.ONE
        }

        // If the input is equal to this element, then we square this instance.
        if (this == other) {
            return square()
        }

        // Otherwise we perform multiplication of two points in general position.
        // s = (y-e.y)/(x-e.x)

        val s = y.subtract(other.y).multiply(x.subtract(other.x).modInverse(modulus).mod(modulus));

        // rx = s^2 - (x + e.x)
        val rx = s.multiply(s).subtract(this.x).subtract(other.x).mod(modulus)

        // ry = -y - s(rx - x)
        val ry = y.negate().subtract(s.multiply(rx.subtract(this.x))).mod(modulus)

        return pGroup.makeVecModP(rx, ry)
    }

    /** Compute the inverse of this element. */
    fun inv(): VecElementP {
        // If this is the unit element, then we return this element.
        if (x == MINUS_ONE) {
            return this
        }

        // If this element equals its inverse, then we return this element.
        if (y == BigInteger.ZERO) {
            return this
        }

        // Otherwise we mirror along the y-axis.
        return pGroup.makeVecModP(
            x,
            y.negate().mod(modulus)
        )
    }

    /** Compute the power of this element to the given exponent. */
    open fun exp(exponent: BigInteger): VecElementP {
        var res: VecElementP = pGroup.ONE

        for (i in exponent.bitLength() downTo 0) {
            res = res.mul(res) // why not square ??
            if (exponent.testBit(i)) {
                res = mul(res)
            }
        }
        return res
    }

    fun toByteArray() = toByteArray1()

        // store both values
    fun toByteArray2(): ByteArray {
        val byteLength = pGroup.pbyteLength

        // store both values
        val result = ByteArray(2 * byteLength)

        if (x == MINUS_ONE) {
            Arrays.fill(result, 0xFF.toByte())
        } else {
            val xbytes = x.toByteArray().normalize(byteLength)
            xbytes.forEachIndexed { idx, it -> result[idx] = it }
            val ybytes = y.toByteArray().normalize(byteLength)
            ybytes.forEachIndexed { idx, it -> result[byteLength+idx] = it }
        }
        return result
    }

    fun toByteArray1(): ByteArray {
        val byteLength = pGroup.pbyteLength

        // We add one byte and use "point compression", store just x and signe of y
        val result = ByteArray(byteLength + 1)

        if (x == MINUS_ONE) {
            Arrays.fill(result, 0xFF.toByte())
        } else {
            // the compressed format is one octet 02 or 03 designating whether Y is even or odd, which
            // corresponds to y > P/2 (odd) or not (even). This distinguishes y and -y, since one will be > P/2
            // and one will not.
            result[0] = if (y.testBit(0)) 3 else 2
            val xbytes = x.toByteArray().normalize(byteLength)
            xbytes.forEachIndexed { idx, it -> result[idx+1] = it }
        }
        return result
    }

    // point doubling in projective coordinates costs 5 field squarings, 3 field multiplication, and 12 linear
    // operations (additions, subtractions, scalar multiplications),
    /**
     * Doubling of a point on the curve. Since we are using
     * multiplicative notation throughout this is called squaring.
     *
     * @return Square of this element.
     */
    fun square(): VecElementP {
        // If this element is the unit element, then we return the unit element.
        if (x == MINUS_ONE) {
            return pGroup.ONE
        }

        // If this element equals its inverse then we return the unit element.
        if (y == BigInteger.ZERO) {
            return pGroup.ONE
        }

        // s = (3x^2 + a) / 2y
        val three = BigInteger.TWO.add(BigInteger.ONE)
        var s = x.multiply(x).mod(modulus)    // square, mod
        s = three.multiply(s).mod(modulus)
        s = s.add(pGroup.a).mod(modulus)

        val tmp = y.add(y).modInverse(modulus)
        s = s.multiply(tmp).mod(modulus)

        // rx = s^2 - 2x
        var rx = s.multiply(s).mod(modulus)    // square, mod
        rx = rx.subtract(x.add(x)).mod(modulus)

        // ry = s(x - rx) - y
        val ry = s.multiply(x.subtract(rx)).subtract(y).mod(modulus)

        return pGroup.makeVecModP(rx, ry)
    }

    fun compareTo(el: VecElementP): Int {
        if (pGroup == el.pGroup) {
            val cmp = x.compareTo(el.x)
            return if (cmp == 0) {
                y.compareTo(el.y)
            } else {
                cmp
            }
        } else {
            throw RuntimeException("Distinct groups!")
        }
    }

    override fun toString(): String {
        return "VecElementP(${x.toByteArray().toBase64()}, ${y.toByteArray().toBase64()})"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VecElementP) return false

        if (x != other.x) return false
        if (y != other.y) return false

        return true
    }

    override fun hashCode(): Int {
        var result = x.hashCode()
        result = 31 * result + y.hashCode()
        return result
    }

}