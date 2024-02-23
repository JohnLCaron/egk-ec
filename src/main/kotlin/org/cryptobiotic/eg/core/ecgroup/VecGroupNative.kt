package org.cryptobiotic.eg.core.ecgroup

import com.verificatum.vecj.VEC
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

    /** Pointer to field order in native space. LOOK */
    val fieldOrdera: ByteArray = order.toByteArray()

    /** Pointer to curve parameters in native space. */
    val nativePointer: ByteArray = VEC.getCurve("P-256")

    override fun makeVecModP(x: BigInteger, y: BigInteger, safe: Boolean) = VecElementModPnative(this, x, y, safe)

    /*
    override fun sqrt(a: BigInteger): BigInteger {
        val root: ByteArray = VEC.sqrt(a.toByteArray(), fieldOrdera)
        return BigInteger(1, root)
    }

     */

    // TODO
    //     // VECJ_BEGIN
    //
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