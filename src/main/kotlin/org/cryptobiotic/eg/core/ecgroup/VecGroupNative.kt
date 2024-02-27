package org.cryptobiotic.eg.core.ecgroup

import com.verificatum.vecj.VEC
import org.cryptobiotic.eg.core.ElementModP
import org.cryptobiotic.eg.core.ElementModQ
import java.math.BigInteger

class VecGroupNative(
    curveName: String,
    a: BigInteger,
    b: BigInteger,
    primeModulus: BigInteger, // Prime primeModulus of the underlying field
    order: BigInteger,
    gx: BigInteger,
    gy: BigInteger,
    h: BigInteger
) : VecGroup(curveName, a, b, primeModulus, order, gx, gy, h) {

    /** Pointer to curve parameters in native space. */
    val nativePointer: ByteArray = VEC.getCurve("P-256")

    override fun makeVecModP(x: BigInteger, y: BigInteger, safe: Boolean) = VecElementPnative(this, x, y, safe)

    override fun sqrt(a: BigInteger): BigInteger {
       return VEC.sqrt(a, primeModulus)
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

    // i think this is prodPow, needed only? by the mixnet. Implemented in Java by
    //     public PPGroupElement expProd(final PGroupElement[] bases,
    //                                  final PRingElement[] exponents) {
    // TODO
    //     /**
    //     * Returns the product of all elements in <code>bases</code> to
    //     * the respective powers in <code>exponents</code>. This uses
    //     * simultaneous exponentiation and threading.
    //     *
    //     * @param bases Bases to be exponentiated.
    //     * @param exponents Powers to be taken.
    //     * @return Product of all bases to the powers of the given
    //     * exponents.
    //     */
    //    public abstract PGroupElement expProd(final PGroupElement[] bases,
    //                                          final PRingElement[] exponents);
    //
    //     // VECJ_BEGIN
    //    @Override
    //    public PGroupElement expProd(final PGroupElement[] bases,
    //                                 final LargeInteger[] integers,
    //                                 final int bitLength) {
    //
    //        if (bases.length != integers.length) {
    //            throw new ArithmError("Different lengths of inputs!");
    //        }
    //
    //        // We need to collect partial results from multiple threads in
    //        // a thread-safe way.
    //        final List<PGroupElement> parts =
    //            Collections.synchronizedList(new LinkedList<PGroupElement>());
    //
    //        final ECqPGroup pGroup = this;
    //
    //        final ArrayWorker worker =
    //            new ArrayWorker(bases.length) {
    //                @Override
    //                public boolean divide() {
    //                    return bases.length > expThreadThreshold;
    //                }
    //                @Override
    //                public void work(final int start, final int end) {
    //
    //                    int batchSize = end - start;
    //
    //                    byte[][] basesx = new byte[batchSize][];
    //                    byte[][] basesy = new byte[batchSize][];
    //                    byte[][] integerBytes = new byte[batchSize][];
    //
    //                    for (int i = 0, j = start; i < batchSize; i++, j++) {
    //
    //                        basesx[i] =
    //                            ((ECqPGroupElement) bases[j]).x.toByteArray();
    //                        basesy[i] =
    //                            ((ECqPGroupElement) bases[j]).y.toByteArray();
    //                        integerBytes[i] = integers[j].toByteArray();
    //                    }
    //
    //                    byte[][] res = VEC.smul(nativePointer,
    //                                            basesx,
    //                                            basesy,
    //                                            integerBytes);
    //
    //                    try {
    //                        PGroupElement part =
    //                            new ECqPGroupElement(pGroup,
    //                                                 new LargeInteger(res[0]),
    //                                                 new LargeInteger(res[1]));
    //                        parts.add(part);
    //
    //                    } catch (ArithmFormatException afe) {
    //                        throw new ArithmError("Unable to create point!", afe);
    //                    }
    //                }
    //            };
    //        worker.work();
    //
    //        PGroupElement res = getONE();
    //        for (final PGroupElement part : parts) {
    //            res = res.mul(part);
    //        }
    //        return res;
    //    }
    //
    //    //VECJ_END

}