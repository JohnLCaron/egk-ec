package org.cryptobiotic.eg.core.intgroup

import org.cryptobiotic.eg.core.*
import java.math.BigInteger
import java.util.concurrent.atomic.AtomicInteger

class IntGroupContext(
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
    val oneModP: IntElementModP
    val gModP: IntElementModP
    val zeroModQ: IntElementModQ
    val oneModQ: IntElementModQ
    val twoModQ: IntElementModQ
    val dlogger: DLogarithm
    val qMinus1Q: IntElementModQ
    val montgomeryIMinusOne: BigInteger
    val montgomeryIPrime: BigInteger
    val montgomeryPPrime: BigInteger

    init {
        p = pBytes.toBigInteger()
        q = qBytes.toBigInteger()
        g = gBytes.toBigInteger()
        r = rBytes.toBigInteger()
        oneModP = IntElementModP(1U.toBigInteger(), this)
        gModP = IntElementModP(g, this).acceleratePow() as IntElementModP
        zeroModQ = IntElementModQ(0U.toBigInteger(), this)
        oneModQ = IntElementModQ(1U.toBigInteger(), this)
        twoModQ = IntElementModQ(2U.toBigInteger(), this)
        dlogger = DLogarithm(gModP)
        qMinus1Q = (zeroModQ - oneModQ) as IntElementModQ
        montgomeryIMinusOne = montIMinus1Bytes.toBigInteger()
        montgomeryIPrime = montIPrimeBytes.toBigInteger()
        montgomeryPPrime = montPPrimeBytes.toBigInteger()
    }

    val groupConstants = IntGroupConstants(name, p, q, r, g)
    val pm1overq = (p - BigInteger.ONE).div(q) // (p-1)/q

    override val constants = groupConstants.constants

    override fun toString() : String = name

    override val ONE_MOD_P : ElementModP
        get() = oneModP

    override val G_MOD_P
        get() = gModP

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
        return (ctx is IntGroupContext) && productionMode == ctx.productionMode
    }

    /** Returns a random number in [2, P). */
    override fun randomElementModP(statBytes:Int): ElementModP {
        val b = randomBytes(MAX_BYTES_P+statBytes)
        val bi = b.toBigInteger()
        val ti = bi.modPow(pm1overq, p) // by magic this makes it into a group element

        val tinbounds = if (ti < BigInteger.TWO) ti + BigInteger.TWO else ti
        return IntElementModP(tinbounds, this)
    }

    override fun binaryToElementModP(b: ByteArray): ElementModP? =
        try {
            val tmp = b.toBigInteger()
            if (tmp >= p || tmp < BigInteger.ZERO) null else IntElementModP(tmp, this)
        } catch (t : Throwable) {
            null
        }

    /** Returns a random number in [2, Q). */
    override fun randomElementModQ(statBytes:Int) : ElementModQ  {
        val b = randomBytes(MAX_BYTES_Q + statBytes)
        val tmp = b.toBigInteger().mod(q)
        val tmp2 = if (tmp < BigInteger.TWO) tmp + BigInteger.TWO else tmp
        return IntElementModQ(tmp2, this)
    }

    override fun binaryToElementModQ(b: ByteArray): ElementModQ {
        val big = b.toBigInteger().mod(q)
        return IntElementModQ(big, this)
    }

    override fun uIntToElementModQ(i: UInt) : ElementModQ = when (i) {
        0U -> ZERO_MOD_Q
        1U -> ONE_MOD_Q
        2U -> TWO_MOD_Q
        else -> IntElementModQ(i.toBigInteger(), this)
    }

    override fun uLongToElementModQ(i: ULong) : ElementModQ = when (i) {
        0UL -> ZERO_MOD_Q
        1UL -> ONE_MOD_Q
        2UL -> TWO_MOD_Q
        else -> IntElementModQ(i.toBigInteger(), this)
    }

    override fun addQ(cues: Iterable<ElementModQ>): ElementModQ {
        val sum = cues.fold(BigInteger.ZERO) { a, b -> a.plus((b as IntElementModQ).element) }
        return IntElementModQ(sum.mod(q), this)
    }

    override fun multP(pees: Iterable<ElementModP>): ElementModP {
        return pees.fold(ONE_MOD_P) { a, b -> a * b }
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
