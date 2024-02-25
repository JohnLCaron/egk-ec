package org.cryptobiotic.eg.core.ecgroup

import com.verificatum.vecj.VEC
import org.cryptobiotic.eg.core.ecgroup.VecGroup.Companion.MINUS_ONE
import java.math.BigInteger

class VecElementModPnative(
    pGroup: VecGroup,
    x: BigInteger,
    y: BigInteger,
    safe: Boolean = false
) : VecElementModP(pGroup, x, y, safe) {
    val vgNative = pGroup as VecGroupNative

    constructor(group: VecGroup, xs: String, ys: String): this(group, BigInteger(xs,16), BigInteger(ys, 16))

    // TODO needed??
    // vcr also has "Simple implementation of elliptic curve addition in affine coordinates."
    // public PGroupElement affineMul(final PGroupElement el)

    /** Compute the product of this element with other. */
    override fun mul(other: VecElementModP): VecElementModP {
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
    override fun exp(exponent: BigInteger): VecElementModP {
        val result: Array<BigInteger> = VEC.mul(vgNative.nativePointer, x, y, exponent)
        return pGroup.makeVecModP(result[0], result[1])
    }
}

// TODO
// Also have this from ECqPGroupElement, which is maybe acc exponetiations, but it seems to calculate the tables each time.
// Could be a problem with native memory leaks?
//
//     public PGroupElement[] exp(final LargeInteger[] integers,
//                               final int bitLength) {
//
//        final ECqPGroup jECPGroup = (ECqPGroup) pGroup;
//
//        // Perform precomputation.
//        final byte[] tablePtr =
//            VEC.fmul_precompute(jECPGroup.nativePointer,
//                                x.toByteArray(),
//                                y.toByteArray(),
//                                integers.length);
