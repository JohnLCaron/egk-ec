package org.cryptobiotic.eg.core.intgroup

import org.cryptobiotic.eg.core.*
import java.math.BigInteger

internal val tinyGroupContext =
    TinyGroupContext(
        p = intTestP.toUInt(),
        q = intTestQ.toUInt(),
        r = intTestR.toUInt(),
        g = intTestG.toUInt(),
        name = "Integer group, 32-bit (test only)",
    )

/**
 * The "tiny" group is built around having p and q values that fit inside unsigned 32-bit integers.
 * The purpose of this group is to make the unit tests run (radically) faster by avoiding the
 * overheads associated with bignum arithmetic on thousands of bits. The values of p and q are big
 * enough to make it unlikely that, for example, false hash collisions might crop up in texting.
 *
 * Needless to say, THIS GROUP SHOULD NOT BE USED IN PRODUCTION CODE! And, in fact, this is why this
 * group isn't exported as part of the "main" code of our library but is only visible to test code.
 */
fun tinyGroup(): GroupContext = tinyGroupContext

internal fun Element.getCompat(other: GroupContext): UInt {
    group.assertCompatible(other)
    return when (this) {
        is TinyElementModP -> this.element
        is TinyElementModQ -> this.element
        else -> throw NotImplementedError("should only be two kinds of elements")
    }
}

internal class TinyGroupContext(
    val p: UInt,
    val q: UInt,
    val g: UInt,
    val r: UInt,
    val name: String,
) : GroupContext {
    val oneModP: ElementModP
    val gModP: ElementModP
    val gInvModP by lazy { gPowP(qMinus1Q) }
    val gSquaredModP: ElementModP
    val zeroModQ: ElementModQ
    val oneModQ: ElementModQ
    val twoModQ: ElementModQ
    val dlogger: DLogarithm
    val qMinus1Q: ElementModQ
    val pm1overq = TinyElementModQ ((p - 1U).div(q), this) // (p-1)/q

    init {
        oneModP = TinyElementModP(1U, this)
        gModP = TinyElementModP(g, this).acceleratePow()
        gSquaredModP = TinyElementModP((g * g) % p, this)
        zeroModQ = TinyElementModQ(0U, this)
        oneModQ = TinyElementModQ(1U, this)
        twoModQ = TinyElementModQ(2U, this)
        qMinus1Q = zeroModQ - oneModQ
        dlogger = DLogarithm(gModP)
    }

    val groupConstants = IntGroupConstants(name,
        BigInteger(1, p.toByteArray()),
        BigInteger(1, q.toByteArray()),
        BigInteger(1, r.toByteArray()),
        BigInteger(1, g.toByteArray()),
    )
    override val constants = groupConstants.constants

    override fun toString(): String = name

    override val ONE_MOD_P: ElementModP
        get() = oneModP
    override val G_MOD_P: ElementModP
        get() = gModP
    val GINV_MOD_P: ElementModP
        get() = gInvModP
    val G_SQUARED_MOD_P: ElementModP
        get() = gSquaredModP
    override val ZERO_MOD_Q: ElementModQ
        get() = zeroModQ
    override val ONE_MOD_Q: ElementModQ
        get() = oneModQ
    override val TWO_MOD_Q: ElementModQ
        get() = twoModQ
    override val MAX_BYTES_P: Int
        get() = 2
    override val MAX_BYTES_Q: Int
        get() = 2
    override val NUM_P_BITS: Int
        get() = 31

    override fun isCompatible(ctx: GroupContext): Boolean = ctx is TinyGroupContext

    /**
     * Convert a ByteArray, of arbitrary size, to a UInt, mod the given modulus. If the ByteArray
     * happens to be of size eight or less, the result is exactly the same as treating the ByteArray
     * as a big-endian integer and computing the modulus afterward. For anything longer, this method
     * uses the smallest 64 bits (i.e., the final eight bytes of the array) and ignores the rest.
     *
     * If the modulus is zero, it's ignored, and the intermediate value is truncated to a UInt and
     * returned.
     */
    internal fun ByteArray.toUIntMod(modulus: UInt): UInt {
        val preModulus = this.fold(0UL) { prev, next -> ((prev shl 8) or next.toUByte().toULong()) }
        return if (modulus == 0U) {
            preModulus.toUInt()
        } else {
            (preModulus % modulus).toUInt()
        }
    }

    override fun hashToElementModQ(hash: UInt256): ElementModQ = binaryToElementModQ(hash.bytes)

    override fun randomElementModQ() : ElementModQ {
        val b = randomBytes(MAX_BYTES_Q)
        val u32 = b.toUIntMod(q)
        val result = if (u32 < 2U) u32 + 2U else u32
        return uIntToElementModQ(result)
    }

    override fun randomElementModP(): ElementModP {
        val tmp = binaryToElementModP(randomBytes(MAX_BYTES_P)) as TinyElementModP
        val modp = if (tmp.element < 2U) uIntToElementModP(tmp.element + 2U) else tmp
        return modp powP pm1overq // by magic this makes it into a group element
    }

    override fun binaryToElementModP(b: ByteArray): ElementModP {
        val u32: UInt = b.toUIntMod(p)
        return uIntToElementModP(u32)
    }

    override fun binaryToElementModQ(b: ByteArray): ElementModQ {
        val u32: UInt = b.toUIntMod(q)
        return uIntToElementModQ(u32)
    }

    override fun uIntToElementModQ(i: UInt): ElementModQ =
        if (i >= q)
            throw ArithmeticException("out of bounds for TestElementModQ: $i")
        else
            TinyElementModQ(i, this)

    override fun uLongToElementModQ(i: ULong): ElementModQ =
        if (i >= q)
            throw ArithmeticException("out of bounds for TestElementModQ: $i")
        else
            TinyElementModQ(i.toUInt(), this) // hmmm, could we use a different group here?

    fun uIntToElementModP(i: UInt): ElementModP =
        if (i >= p)
            throw ArithmeticException("out of bounds for TestElementModP: $i")
        else
            TinyElementModP(i, this)

    override fun addQ(cues: Iterable<ElementModQ>): ElementModQ =
        uIntToElementModQ(cues.fold(0U) { a, b -> (a + b.getCompat(this@TinyGroupContext)) % q })

    override fun multP(pees: Iterable<ElementModP>): ElementModP =
        uIntToElementModP(
            pees.fold(1UL) { a, b -> (a * b.getCompat(this@TinyGroupContext).toULong()) % p }.toUInt()
        )

    override fun gPowP(exp: ElementModQ): ElementModP = gModP powP exp

    override fun dLogG(p: ElementModP, maxResult: Int): Int? = dlogger.dLog(p, maxResult)

    override fun getAndClearOpCounts() = emptyMap<String, Int>()
}

internal class TinyElementModP(val element: UInt, val groupContext: TinyGroupContext) : ElementModP {
    fun UInt.modWrap(): ElementModP = (this % groupContext.p).wrap()
    fun ULong.modWrap(): ElementModP = (this % groupContext.p).wrap()
    fun UInt.wrap(): ElementModP = TinyElementModP(this, groupContext)
    fun ULong.wrap(): ElementModP = toUInt().wrap()

    override fun isValidElement(): Boolean {
        val inBounds = element < groupContext.p
        val residue = this powP TinyElementModQ(groupContext.q, groupContext)
        return inBounds && (residue == groupContext.ONE_MOD_P)
    }

    override fun powP(exp: ElementModQ): ElementModP {
        var result: ULong = 1U
        var base: ULong = element.toULong()
        val expc: UInt = exp.getCompat(groupContext)

        // we know that all the bits above this are zero because q < 2^28
        (0..28)
            .forEach { bit ->
                val eBitSet = ((expc shr bit) and 1U) == 1U

                // We're doing arithmetic in the larger 64-bit space, but we'll never overflow
                // because the internal values are always mod p or q, and thus fit in 32 bits.
                if (eBitSet) result = (result * base) % groupContext.p
                base = (base * base) % groupContext.p
            }
        return result.wrap()
    }

    override fun times(other: ElementModP): ElementModP =
        (this.element.toULong() * other.getCompat(groupContext).toULong()).modWrap()

    override fun multInv(): ElementModP = this powP groupContext.qMinus1Q

    override fun div(denominator: ElementModP): ElementModP = this * denominator.multInv()

    // we could try to use the whole powradix thing, but it's overkill for 16-bit numbers
    override fun acceleratePow(): ElementModP = this

    override fun compareTo(other: ElementModP): Int =
        element.compareTo(other.getCompat(groupContext))

    override val group: GroupContext
        get() = groupContext

    // fun inBounds(): Boolean = element < groupContext.p

    override fun byteArray(): ByteArray = element.toByteArray()

    override fun equals(other: Any?): Boolean = other is TinyElementModP && element == other.element

    override fun hashCode(): Int = element.hashCode()

    override fun toString(): String = "ElementModP($element)"

    fun toMontgomeryElementModP(): MontgomeryElementModP =
        TinyMontgomeryElementModP(
            ((element.toULong() shl intTestPBits) % groupContext.p).toUInt(),
            groupContext)
}

internal class TinyElementModQ(val element: UInt, val groupContext: TinyGroupContext) : ElementModQ {
    fun ULong.modWrap(): ElementModQ = (this % groupContext.q).wrap()
    fun UInt.modWrap(): ElementModQ = (this % groupContext.q).wrap()
    fun ULong.wrap(): ElementModQ = toUInt().wrap()
    fun UInt.wrap(): ElementModQ = TinyElementModQ(this, groupContext)

    override fun plus(other: ElementModQ): ElementModQ =
        (this.element + other.getCompat(groupContext)).modWrap()

    override fun minus(other: ElementModQ): ElementModQ =
        (this.element + (-other).getCompat(groupContext)).modWrap()

    override fun times(other: ElementModQ): ElementModQ =
        (this.element.toULong() * other.getCompat(groupContext).toULong()).modWrap()

    override operator fun unaryMinus(): ElementModQ =
        if (this == groupContext.zeroModQ) this else (groupContext.q - element).wrap()

    override fun multInv(): ElementModQ {
        // https://en.wikipedia.org/wiki/Extended_Euclidean_algorithm

        // This implementation is functional and lazy, which is kinda fun, although it's
        // going to allocate a bunch of memory and otherwise be much less efficient than
        // writing it as a vanilla while-loop. This function is rarely used in real code,
        // so efficient doesn't matter as much as correctness.

        // We're using Long, rather than UInt, to ensure that we never experience over-
        // or under-flow. This allows the normalization of the result, which checks for
        // finalState.t < 0, to work correctly.

        data class State(val t: Long, val newT: Long, val r: Long, val newR: Long)
        val seq =
            generateSequence(State(0, 1, groupContext.q.toLong(), element.toLong()))
                { (t, newT, r, newR) ->
                    val quotient = r / newR
                    State(newT, t - quotient * newT, newR, r - quotient * newR)
                }

        val finalState = seq.find { it.newR == 0L } ?: throw Error("should never happen")

        if (finalState.r > 1) {
            throw ArithmeticException("element $element is not invertible")
        }

        return TinyElementModQ(
            (if (finalState.t < 0) finalState.t + groupContext.q.toLong() else finalState.t)
                .toUInt(),
            groupContext
        )
    }

    override fun div(denominator: ElementModQ): ElementModQ = this * denominator.multInv()

    override fun compareTo(other: ElementModQ): Int =
        element.compareTo(other.getCompat(groupContext))

    override val group: GroupContext
        get() = groupContext

    override fun isValidElement(): Boolean = element < groupContext.q

    override fun isZero(): Boolean = element == 0U

    override fun byteArray(): ByteArray = element.toByteArray()

    override fun equals(other: Any?): Boolean = other is TinyElementModQ && element == other.element

    override fun hashCode(): Int = element.hashCode()

    override fun toString(): String = "ElementModQ($element)"
}

internal data class TinyMontgomeryElementModP(val element: UInt, val groupContext: TinyGroupContext):
    MontgomeryElementModP {
    private fun MontgomeryElementModP.getCompat(other: GroupContext): UInt {
        context.assertCompatible(other)
        if (this is TinyMontgomeryElementModP) {
            return this.element
        } else {
            throw NotImplementedError("unexpected MontgomeryElementModP type")
        }
    }

    internal fun ULong.modI(): ULong = this and intTestMontgomeryIMinus1.toULong()

    internal fun ULong.divI(): UInt = (this shr intTestPBits).toUInt()

    override fun times(other: MontgomeryElementModP): MontgomeryElementModP {
        val w: ULong = this.element.toULong() * other.getCompat(this.context).toULong()

        // Z = ((((W mod I)⋅p^' )  mod I)⋅p+W)/I
        val z = ((w.modI() * intTestMontgomeryPPrime).modI() * groupContext.p + w).divI()

        return TinyMontgomeryElementModP(
            if (z >= groupContext.p) z - groupContext.p else z,
            groupContext)
    }

    override fun toElementModP(): ElementModP =
        TinyElementModP((element.toULong() * intTestMontgomeryIPrime.toULong()).mod(groupContext.p), groupContext)

    override val context: GroupContext
        get() = groupContext
}
