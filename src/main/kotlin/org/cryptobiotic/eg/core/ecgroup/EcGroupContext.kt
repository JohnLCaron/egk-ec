package org.cryptobiotic.eg.core.ecgroup


import org.cryptobiotic.eg.core.*
import java.math.BigInteger

class EcGroupContext(val name: String, useNative: Boolean = true): GroupContext {
    val vecGroup: VecGroup = VecGroups.getEcGroup(name, useNative)
    val ONE = EcElementModP(this, vecGroup.ONE)

    override val GINV_MOD_P: ElementModP = EcElementModP(this, vecGroup.g.inv())
    override val G_MOD_P: ElementModP = EcElementModP(this, vecGroup.g)
    override val G_SQUARED_MOD_P: ElementModP = EcElementModP(this, vecGroup.g.square())
    override val NUM_P_BITS: Int = vecGroup.pbitLength
    override val MAX_BYTES_P: Int = vecGroup.pbyteLength + 1 // x plus sign of y
    override val ONE_MOD_P: ElementModP = this.ONE

    override val MAX_BYTES_Q: Int = vecGroup.qbyteLength
    override val ZERO_MOD_Q: ElementModQ = EcElementModQ(this, BigInteger.ZERO)
    override val ONE_MOD_Q: ElementModQ = EcElementModQ(this, BigInteger.ONE)
    override val TWO_MOD_Q: ElementModQ = EcElementModQ(this, BigInteger.TWO)
    val NUM_Q_BITS: Int = vecGroup.qbitLength

    override val constants = vecGroup.constants
    val dlogg = DLogarithm(G_MOD_P)

    // TODO diff of this and safe version?
    override fun binaryToElementModP(b: ByteArray): ElementModP? {
        val elem = vecGroup.elementFromByteArray(b)
        return if (elem != null) EcElementModP(this, elem) else null
    }

    override fun binaryToElementModPsafe(b: ByteArray, minimum: Int): ElementModP {
        return binaryToElementModP(b) ?: throw RuntimeException("invalid input")
    }

    override fun binaryToElementModQ(b: ByteArray): ElementModQ? {
        return EcElementModQ(this, BigInteger(1, b))
    }

    override fun binaryToElementModQsafe(b: ByteArray, minimum: Int): ElementModQ {
        return EcElementModQ(this, BigInteger(1, b))
    }

    override fun dLogG(p: ElementModP, maxResult: Int): Int? {
        require(p is EcElementModP)
        return dlogg.dLog(p, maxResult)
    }

    override fun gPowP(exp: ElementModQ): ElementModP {
        require(exp is EcElementModQ)
        return EcElementModP(this, vecGroup.g.exp(exp.element))
    }

    override fun getAndClearOpCounts(): Map<String, Int> {
        return emptyMap()
    }

    override fun isCompatible(ctx: GroupContext): Boolean {
        return (ctx is EcGroupContext) && name == ctx.name
    }

    override fun isProductionStrength(): Boolean {
        return true
    }

    override fun uIntToElementModQ(i: UInt): ElementModQ {
        return EcElementModQ(this, BigInteger.valueOf(i.toLong()))
    }

    override fun uLongToElementModQ(i: ULong): ElementModQ {
        return EcElementModQ(this, BigInteger.valueOf(i.toLong()))
    }

    override fun Iterable<ElementModQ>.addQ(): ElementModQ {
        return addQQ(this)
    }

    override fun Iterable<ElementModP>.multP(): ElementModP {
        return this.reduce { a, b -> a * b }
    }

    override fun randomElementModP(minimum: Int) = EcElementModP(this, vecGroup.randomElement())

    // TODO could these be done with just a mod at the end?
    fun addQQ(cues: Iterable<ElementModQ>): ElementModQ {
        val sum = cues.fold(BigInteger.ZERO) { a, b -> a.plus((b as EcElementModQ).element) }
        return EcElementModQ(this, sum.mod(vecGroup.order))
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EcGroupContext

        return name == other.name
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }

    override fun prodPowers(bases: List<ElementModP>, exps: List<ElementModQ>): ElementModP {
        require(exps.size == bases.size)
        if (bases.isEmpty()) {
            return ONE_MOD_P
        }
        // TODO seems a bit awkward....
        val ec = vecGroup.prodPowers(bases, exps)
        return EcElementModP(this, ec)
    }
}
