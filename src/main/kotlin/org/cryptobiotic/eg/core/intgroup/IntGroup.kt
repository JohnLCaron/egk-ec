package org.cryptobiotic.eg.core.intgroup

import org.cryptobiotic.eg.core.*
import org.cryptobiotic.eg.core.Base64.toBase64
import java.math.BigInteger
import java.util.concurrent.atomic.AtomicInteger

internal fun UInt.toBigInteger() = BigInteger.valueOf(this.toLong())
internal fun ULong.toBigInteger() = BigInteger.valueOf(this.toLong())

/** Convert an array of bytes, in big-endian format, to a BigInteger */
internal fun ByteArray.toBigInteger() = BigInteger(1, this)

class ProductionGroupContext(
    pBytes: ByteArray,
    qBytes: ByteArray,
    gBytes: ByteArray,
    rBytes: ByteArray,
    montIMinus1Bytes: ByteArray,
    montIPrimeBytes: ByteArray,
    montPPrimeBytes: ByteArray,
    val name: String,
    val powRadixOption: PowRadixOption,
    val productionMode: ProductionMode,
    val numPBits: Int
) : GroupContext {
    val p: BigInteger
    val q: BigInteger
    val g: BigInteger
    val r: BigInteger
    val oneModP: ProductionElementModP
    val gModP: ProductionElementModP
    val gInvModP by lazy { gPowP(qMinus1Q) }
    val gSquaredModP: ProductionElementModP
    val zeroModQ: ProductionElementModQ
    val oneModQ: ProductionElementModQ
    val twoModQ: ProductionElementModQ
    val dlogger: DLogarithm
    val qMinus1Q: ProductionElementModQ
    val montgomeryIMinusOne: BigInteger
    val montgomeryIPrime: BigInteger
    val montgomeryPPrime: BigInteger

    init {
        p = pBytes.toBigInteger()
        q = qBytes.toBigInteger()
        g = gBytes.toBigInteger()
        r = rBytes.toBigInteger()
        oneModP = ProductionElementModP(1U.toBigInteger(), this)
        gModP = ProductionElementModP(g, this).acceleratePow() as ProductionElementModP
        gSquaredModP = ProductionElementModP((g * g).mod(p), this)
        zeroModQ = ProductionElementModQ(0U.toBigInteger(), this)
        oneModQ = ProductionElementModQ(1U.toBigInteger(), this)
        twoModQ = ProductionElementModQ(2U.toBigInteger(), this)
        dlogger = DLogarithm(gModP)
        qMinus1Q = (zeroModQ - oneModQ) as ProductionElementModQ
        montgomeryIMinusOne = montIMinus1Bytes.toBigInteger()
        montgomeryIPrime = montIPrimeBytes.toBigInteger()
        montgomeryPPrime = montPPrimeBytes.toBigInteger()
    }

    val groupConstants = IntGroupConstants(name, p, q, r, g)
    override val constants = groupConstants.constants

    override fun isProductionStrength() = true
    override fun toString() : String = name

    override val ONE_MOD_P
        get() = oneModP

    override val G_MOD_P
        get() = gModP

    override val GINV_MOD_P
        get() = gInvModP

    override val G_SQUARED_MOD_P
        get() = gSquaredModP

    override val ZERO_MOD_Q
        get() = zeroModQ

    override val ONE_MOD_Q
        get() = oneModQ

    override val TWO_MOD_Q
        get() = twoModQ

    override val MAX_BYTES_P: Int
        get() = 512

    override val MAX_BYTES_Q: Int
        get() = 32

    override val NUM_P_BITS: Int
        get() = numPBits

    override fun isCompatible(ctx: GroupContext): Boolean {
        return ctx.isProductionStrength() && productionMode == (ctx as ProductionGroupContext).productionMode
    }

    override fun binaryToElementModPsafe(b: ByteArray, minimum: Int): ElementModP {
        if (minimum < 0) {
            throw IllegalArgumentException("minimum $minimum may not be negative")
        }
        val tmp = b.toBigInteger().mod(p)
        val mv = minimum.toBigInteger()
        val tmp2 = if (tmp < mv) tmp + mv else tmp
        return ProductionElementModP(tmp2, this)
    }

    override fun binaryToElementModQsafe(b: ByteArray, minimum: Int): ElementModQ {
        if (minimum < 0) {
            throw IllegalArgumentException("minimum $minimum may not be negative")
        }
        val tmp = b.toBigInteger().mod(q)
        val mv = minimum.toBigInteger()
        val tmp2 = if (tmp < mv) tmp + mv else tmp
        return ProductionElementModQ(tmp2, this)
    }

    override fun binaryToElementModP(b: ByteArray): ElementModP? =
        try {
            val tmp = b.toBigInteger()
            if (tmp >= p || tmp < BigInteger.ZERO) null else ProductionElementModP(tmp, this)
        } catch (t : Throwable) {
            null
        }

    override fun binaryToElementModQ(b: ByteArray): ElementModQ? =
        try {
            val tmp = b.toBigInteger()
            if (tmp >= q || tmp < BigInteger.ZERO) null else ProductionElementModQ(tmp, this)
        } catch (t : Throwable) {
            null
        }

    // TODO, for an election where limit > 1, might want to cache all encryption up to limit.

    override fun uIntToElementModQ(i: UInt) : ElementModQ = when (i) {
        0U -> ZERO_MOD_Q
        1U -> ONE_MOD_Q
        2U -> TWO_MOD_Q
        else -> ProductionElementModQ(i.toBigInteger(), this)
    }

    override fun uLongToElementModQ(i: ULong) : ElementModQ = when (i) {
        0UL -> ZERO_MOD_Q
        1UL -> ONE_MOD_Q
        2UL -> TWO_MOD_Q
        else -> ProductionElementModQ(i.toBigInteger(), this)
    }

    override fun Iterable<ElementModQ>.addQ(): ElementModQ {
        val input = iterator().asSequence().toList()

        // TODO why not return 0 ?
        if (input.isEmpty()) {
            throw ArithmeticException("addQ not defined on empty lists")
        }

        if (input.count() == 1) {
            return input[0]
        }

        val result = input.map {
            it.getCompat(this@ProductionGroupContext)
        }.reduce { a, b ->
            (a + b).mod(this@ProductionGroupContext.q)
        }

        return ProductionElementModQ(result, this@ProductionGroupContext)
    }

    override fun Iterable<ElementModP>.multP(): ElementModP {
        val input = iterator().asSequence().toList()

        // TODO why not return 1 ?
        if (input.isEmpty()) {
            throw ArithmeticException("multP not defined on empty lists")
        }

        if (input.count() == 1) {
            return input[0]
        }

        val result = input.map {
            it.getCompat(this@ProductionGroupContext)
        }.reduce { a, b ->
            (a * b).mod(this@ProductionGroupContext.p)
        }

        return ProductionElementModP(result, this@ProductionGroupContext)
    }

    override fun gPowP(exp: ElementModQ) = gModP powP exp

    override fun dLogG(p: ElementModP, maxResult: Int): Int? = dlogger.dLog(p, maxResult)

    var opCounts: HashMap<String, AtomicInteger> = HashMap()
    override fun getAndClearOpCounts(): Map<String, Int> {
        val result = HashMap<String, Int>()
        opCounts.forEach { (key, value) -> result[key] = value.get() }
        opCounts = HashMap()
        return result.toSortedMap()
    }
}

private fun Element.getCompat(other: ProductionGroupContext): BigInteger {
    context.assertCompatible(other)
    return when (this) {
        is ProductionElementModP -> this.element
        is ProductionElementModQ -> this.element
        else -> throw NotImplementedError("should only be two kinds of elements")
    }
}

class ProductionElementModQ(internal val element: BigInteger, val groupContext: ProductionGroupContext): ElementModQ,
    Element, Comparable<ElementModQ> {

    override fun byteArray(): ByteArray = element.toByteArray().normalize(32)

    private fun BigInteger.modWrap(): ElementModQ = this.mod(groupContext.q).wrap()
    private fun BigInteger.wrap(): ElementModQ = ProductionElementModQ(this, groupContext)

    override val context: GroupContext
        get() = groupContext

    override fun isZero() = element == BigInteger.ZERO

    override fun inBounds() = element >= BigInteger.ZERO && element < groupContext.q

    override operator fun compareTo(other: ElementModQ): Int = element.compareTo(other.getCompat(groupContext))

    override operator fun plus(other: ElementModQ) =
        (this.element + other.getCompat(groupContext)).modWrap()

    override operator fun minus(other: ElementModQ) =
        this + (-other)

    override operator fun times(other: ElementModQ) =
        (this.element * other.getCompat(groupContext)).modWrap()

    override fun multInv(): ElementModQ = element.modInverse(groupContext.q).wrap()

    override operator fun unaryMinus(): ElementModQ =
        if (this == groupContext.zeroModQ)
            this
        else
            (groupContext.q - element).wrap()

    override infix operator fun div(denominator: ElementModQ): ElementModQ =
        this * denominator.multInv()


    override fun equals(other: Any?) = when (other) {
        is ElementModQ -> byteArray().contentEquals(other.byteArray())
        else -> false
    }

    override fun hashCode() = byteArray().contentHashCode()

    override fun toString() = byteArray().toBase64()
}

open class ProductionElementModP(internal val element: BigInteger, val groupContext: ProductionGroupContext): ElementModP,
    Element, Comparable<ElementModP> {

    override fun byteArray(): ByteArray = element.toByteArray().normalize(512)

    private fun BigInteger.modWrap(): ElementModP = this.mod(groupContext.p).wrap()
    private fun BigInteger.wrap(): ElementModP = ProductionElementModP(this, groupContext)

    override val context: GroupContext
        get() = groupContext

    override fun inBounds() = element >= BigInteger.ZERO && element < groupContext.p

    override operator fun compareTo(other: ElementModP): Int = element.compareTo(other.getCompat(groupContext))

    override fun isValidResidue(): Boolean {
        groupContext.opCounts.getOrPut("exp") { AtomicInteger(0) }.incrementAndGet()
        val residue = this.element.modPow(groupContext.q, groupContext.p) == groupContext.ONE_MOD_P.element
        return inBounds() && residue
    }

    override infix fun powP(exp: ElementModQ) : ElementModP {
        groupContext.opCounts.getOrPut("exp") { AtomicInteger(0) }.incrementAndGet()
        return this.element.modPow(exp.getCompat(groupContext), groupContext.p).wrap()
    }

    override operator fun times(other: ElementModP) =
        (this.element * other.getCompat(groupContext)).modWrap()

    override fun multInv()
            = element.modInverse(groupContext.p).wrap()
//            = this powP groupContext.qMinus1Q

    // Performance note: multInv() can be expressed with the modInverse() method or we can do
    // this exponentiation thing with Q - 1, which works for the subgroup. On the JVM, we get
    // basically the same performance either way.

    override infix operator fun div(denominator: ElementModP) =
        (element * denominator.getCompat(groupContext).modInverse(groupContext.p)).modWrap()

    override fun acceleratePow() : ElementModP =
        AcceleratedElementModP(this)

    fun toMontgomeryElementModP(): MontgomeryElementModP =
        ProductionMontgomeryElementModP(
            element.shiftLeft(groupContext.productionMode.numBitsInP).mod(groupContext.p),
            groupContext)

    override fun equals(other: Any?) = when (other) {
        is ElementModP -> byteArray().contentEquals(other.byteArray())
        else -> false
    }

    override fun hashCode() = byteArray().contentHashCode()

    override fun toString() = byteArray().toBase64()
}

class AcceleratedElementModP(p: ProductionElementModP) : ProductionElementModP(p.element, p.groupContext) {
    // Laziness to delay computation of the table until its first use; saves space
    val powRadix by lazy { PowRadix(p, p.groupContext.powRadixOption) }

    override fun acceleratePow(): ElementModP = this

    override infix fun powP(exp: ElementModQ) : ElementModP {
        groupContext.opCounts.getOrPut("acc") { AtomicInteger(0) }.incrementAndGet()
        return powRadix.pow(exp)
    }
}

internal data class ProductionMontgomeryElementModP(val element: BigInteger, val groupContext: ProductionGroupContext):
    MontgomeryElementModP {
    internal fun MontgomeryElementModP.getCompat(other: GroupContext): BigInteger {
        context.assertCompatible(other)
        if (this is ProductionMontgomeryElementModP) {
            return this.element
        } else {
            throw NotImplementedError("unexpected MontgomeryElementModP type")
        }
    }

    internal fun BigInteger.modI(): BigInteger = this and groupContext.montgomeryIMinusOne

    internal fun BigInteger.divI(): BigInteger = this shr groupContext.productionMode.numBitsInP

    override fun times(other: MontgomeryElementModP): MontgomeryElementModP {
        // w = aI * bI = (ab)(I^2)
        val w: BigInteger = this.element * other.getCompat(this.context)

        // Z = ((((W mod I)⋅p^' )  mod I)⋅p+W)/I
        val z: BigInteger = (((w.modI() * groupContext.montgomeryPPrime).modI() * groupContext.p) + w).divI()

        return ProductionMontgomeryElementModP(
            if (z >= groupContext.p) z - groupContext.p else z,
            groupContext)
    }

    override fun toElementModP(): ElementModP =
        ProductionElementModP((element * groupContext.montgomeryIPrime).mod(groupContext.p), groupContext)

    override val context: GroupContext
        get() = groupContext

}
