package org.cryptobiotic.eg.core.ecgroup

import com.verificatum.vecj.VEC
import org.cryptobiotic.eg.core.ecgroup.VecGroup.Companion.MINUS_ONE
import java.math.BigInteger

open class VecElementPnative(
    pGroup: VecGroup,
    x: BigInteger,
    y: BigInteger,
    safe: Boolean = false
) : VecElementP(pGroup, x, y, safe) {
    val vgNative = pGroup as VecGroupNative

    constructor(group: VecGroup, xs: String, ys: String): this(group, BigInteger(xs,16), BigInteger(ys, 16))

    override fun acceleratePow(): VecElementP {
        return VecElementPnativeAcc(pGroup, x, y, true)
    }

    /** Compute the product of this element with other. */
    override fun mul(other: VecElementP): VecElementP {
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

        val result: Array<BigInteger> = VEC.add(vgNative.nativePointer, x, y, other.x, other.y)
        return pGroup.makeVecModP(result[0], result[1])
    }

    /** Compute the power of this element to the given exponent. */
    override fun exp(exponent: BigInteger): VecElementP {
        val result: Array<BigInteger> = VEC.mul(vgNative.nativePointer, x, y, exponent)
        return pGroup.makeVecModP(result[0], result[1])
    }
}

// precompute tables so exp goes faster
class VecElementPnativeAcc(
    pGroup: VecGroup,
    x: BigInteger,
    y: BigInteger,
    safe: Boolean = false
) : VecElementPnative(pGroup, x, y, safe) {
    val tablePtr: ByteArray by lazy { // TODO free the native memory....

        /**
         * Performs pre-computation for fixed-basis multiplications.
         * @param curve_ptr Native pointer to curve.
         * @param basisx x coordinate of basis point.
         * @param basisy y coordinate of basis point.
         * @param size Expected number of fixed-basis multiplications to be done. seems to be the bitLength
         * @return Native pointer to pre-computed values represented as an array of bytes.
         */
        // public static byte[] fmul_precompute(final byte[] curve_ptr,
        //                                         final BigInteger basisx,
        //                                         final BigInteger basisy,
        //                                         final int size) {
        VEC.fmul_precompute(vgNative.nativePointer, x, y, vgNative.qbitLength)
    }

    override fun exp(exponent: BigInteger): VecElementP {
        /**
         * Computes a fixed-basis multiplication using the
         * pre-computed table and non-negative scalar smaller than the
         * order of the curve.
         *
         * @param curve_ptr Native pointer to curve.
         * @param table_ptr Native pointer to fixed-basis multiplication table.
         * @param scalar Scalar used to multiply.
         * @return Resulting point.
         */
        // public static BigInteger[] fmul(final byte[] curve_ptr, final byte[] table_ptr, final BigInteger scalar) {
        val result: Array<BigInteger> = VEC.fmul(vgNative.nativePointer, tablePtr, exponent)
        return pGroup.makeVecModP(result[0], result[1])
    }
}