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

import com.verificatum.vecj.VEC
import org.cryptobiotic.eg.core.ElementModP
import org.cryptobiotic.eg.core.ElementModQ
import java.math.BigInteger

class VecGroupNative(
    curveName: String,
    a: BigInteger,
    b: BigInteger,
    primeModulus: BigInteger, // primeModulus of the underlying field
    order: BigInteger,
    gx: BigInteger,
    gy: BigInteger,
    h: BigInteger
) : VecGroup(curveName, a, b, primeModulus, order, gx, gy, h) {

    /** Pointer to curve parameters in native space. */
    val nativePointer: ByteArray = VEC.getCurve("P-256")

    override fun makeVecModP(x: BigInteger, y: BigInteger, safe: Boolean) = VecElementPnative(this, x, y, safe)

    override fun sqrt(x: BigInteger): BigInteger {
       return VEC.sqrt(x, primeModulus).toPositive()
    }

    override fun prodPowers(bases: List<ElementModP>, exps: List<ElementModQ>): VecElementP {
        val basesx = Array(bases.size) { (bases[it] as EcElementModP).ec.x.toByteArray() }
        val basesy = Array(bases.size) { (bases[it] as EcElementModP).ec.y.toByteArray() }
        val scalars = Array(exps.size) { (exps[it] as EcElementModQ).element.toByteArray() }

            //     public static native byte[][] smul(final byte[] curve_ptr,
            //                                       final byte[][] basesx,
            //                                       final byte[][] basesy,
            //                                       final byte[][] scalars);
        val result: Array<ByteArray> = VEC.smul(nativePointer, basesx, basesy, scalars)

        return makeVecModP( BigInteger(1, result[0]), BigInteger(1, result[1]))
    }
}