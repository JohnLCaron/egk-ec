@file:OptIn(ExperimentalKotest::class)

package org.cryptobiotic.eg.core

import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.ShrinkingMode
import io.kotest.property.arbitrary.*

/** Generate an arbitrary ElementModP for the given group context. */
fun elementsModP(ctx: GroupContext): Arb<ElementModP> {
    return arbitrary { ctx.randomElementModP() }
}

//    Arb.byteArray(Arb.constant(ctx.MAX_BYTES_P), Arb.byte())
//        .map { _ -> ctx.randomElementModP() }

/** Generate an arbitrary ElementModP in [1, P) for the given group context. */
// fun elementsModPNoZero(ctx: GroupContext) = elementsModP(ctx)

/** Generate an arbitrary ElementModQ in [minimum, Q) for the given group context. */
fun elementsModQ(ctx: GroupContext): Arb<ElementModQ> = arbitrary{ ctx.randomElementModQ() }

/** Generate an arbitrary ElementModQ in [1, Q) for the given group context. */
fun elementsModQNoZero(ctx: GroupContext) = elementsModQ(ctx)

/**
 * Generates a valid element of the subgroup of ElementModP where there exists an e in Q such that v
 * = g^e. aka "the set of r-th-residues in Z∗_p"
 */
fun validResiduesOfP(ctx: GroupContext): Arb<ElementModP> =
    elementsModQ(ctx).map { e -> ctx.gPowP(e) }

/**
 * Generates a random ElGamal keypair. Modular exponentiation with the public key will be
 * accelerated using the default PowRadixOption in the GroupContext.
 */
fun elGamalKeypairs(ctx: GroupContext): Arb<ElGamalKeypair> =
    elementsModQ(ctx).map { elGamalKeyPairFromSecret(it) }

/** Generates arbitrary UInt256 values. */
fun uint256s(): Arb<UInt256> = Arb.byteArray(Arb.constant(32), Arb.byte()).map { UInt256(it) }

/** Generates arbitrary ByteArray of length len. */
fun byteArrays(len: Int): Arb<ByteArray> = Arb.byteArray(Arb.constant(len), Arb.byte())

/**
 * Property-based testing can run slowly. This will speed things up by turning off shrinking and
 * using fewer iterations. Typical usage:
 * ```
 * forAll(propTestFastConfig, Arb.x(), Arb.y()) { x, y -> ... }
 * ```
 */
val propTestFastConfig =
    PropTestConfig(maxFailure = 1, shrinkingMode = ShrinkingMode.Off, iterations = 10)

/**
 * If we know we can afford more effort to run a property test, this will spend extra time
 * trying more inputs and will put more effort into shrinking any counterexamples. Typical usage:
 * ```
 * forAll(propTestSlowConfig, Arb.x(), Arb.y()) { x, y -> ... }
 * ```
 */
val propTestSlowConfig =
    PropTestConfig(iterations = 1000)