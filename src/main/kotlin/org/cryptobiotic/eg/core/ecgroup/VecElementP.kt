package org.cryptobiotic.eg.core.ecgroup

import org.cryptobiotic.eg.core.normalize
import org.cryptobiotic.eg.core.toHex
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

    //     // VECJ_PURE_JAVA_BEGIN
    //
    //    /**
    //     * Compute the power of this element to the given exponent.
    //     *
    //     * @param exponent Exponent.
    //     * @return Power of this element to the given exponent.
    //     */
    //    public PGroupElement exp(final LargeInteger exponent) {
    //
    //        PGroupElement res = getPGroup().getONE();
    //
    //        for (int i = exponent.bitLength(); i >= 0; i--) {
    //            res = res.mul(res);
    //            if (exponent.testBit(i)) {
    //                res = mul(res);
    //            }
    //        }
    //        return res;
    //    }
    //    // VECJ_PURE_JAVA_END


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

    //
    //    // VECJ_BEGIN
    //
    //    /**
    //     * Compute the power of this element to the given exponent.
    //     *
    //     * @param exponent Exponent.
    //     * @return Power of this element to the given exponent.
    //     */
    //    public PGroupElement exp(final LargeInteger exponent) {
    //
    //        final byte[] exponenta = exponent.toByteArray();
    //
    //        final byte[] xa = x.toByteArray();
    //        final byte[] ya = y.toByteArray();
    //
    //        final ECqPGroup jECPGroup = (ECqPGroup) pGroup;
    //
    //        final byte[][] res =
    //            VEC.mul(jECPGroup.nativePointer, xa, ya, exponenta);
    //
    //        try {
    //            return new ECqPGroupElement(jECPGroup,
    //                                        new LargeInteger(res[0]),
    //                                        new LargeInteger(res[1]));
    //        } catch (ArithmFormatException afe) {
    //            throw new ArithmError("Unable to create elliptic curve point!",
    //                                  afe);
    //        }
    //    }
    //
    //    /** * Compute the powers of this element to the given positive * integer exponents. */
    //    public PGroupElement[] exp(final LargeInteger[] integers, final int bitLength) {
    //    ...
    //    }
    //    // VECJ_END

    fun toByteArray(): ByteArray {
        val byteLength = (pGroup.pbitLength + 7) / 8

        // We add one byte and use point compression.
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

    // "Bijective map from the set of elements to arrays of bytes. This is not intended to be used for storing elements."
    // apparently only used in hash function.
    // "store x0 and use the equation of the elliptic curve to solve for y0"
    // toByteTree() stores both x and y, so 512 bits.
    fun toByteArrayPointCompression(): ByteArray {
        val byteLength = (pGroup.pbitLength + 7) / 8

        // We add one byte and use point compression.
        val res = ByteArray(byteLength + 1)

        if (x == MINUS_ONE) {
            Arrays.fill(res, 0xFF.toByte())
        } else {
            val tmp = x.toByteArray()
            System.arraycopy(tmp, 0, res, res.size - tmp.size, tmp.size)
            if (y.negate().compareTo(y) < 0) {
                res[0] = 1 // sign bit
            }
        }
        return res
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
        return "VecElementModP(${x.toHex()}, ${y.toHex()})"
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