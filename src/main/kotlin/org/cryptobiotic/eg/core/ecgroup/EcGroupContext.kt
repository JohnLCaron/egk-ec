package org.cryptobiotic.eg.core.ecgroup


import org.cryptobiotic.eg.core.*
import org.cryptobiotic.eg.core.intgroup.GroupConstants
import java.math.BigInteger

class EcGroupContext(val name: String): GroupContext {
    val ecGroup: VecGroup = VecGroups.getEcGroup(name)
    val ONE = EcElementModP(this, ecGroup.ONE)

    override val GINV_MOD_P: ElementModP = EcElementModP(this, ecGroup.g.inv())
    override val G_MOD_P: ElementModP = EcElementModP(this, ecGroup.g)
    override val G_SQUARED_MOD_P: ElementModP  = EcElementModP(this, ecGroup.g.square())
    override val NUM_P_BITS: Int  = ecGroup.pbitLength
    override val MAX_BYTES_P: Int = ecGroup.pbyteLength
    override val ONE_MOD_P: ElementModP = this.ONE

    override val MAX_BYTES_Q: Int = ecGroup.qbyteLength
    override val ZERO_MOD_Q: ElementModQ = EcElementModQ(this, BigInteger.ZERO)
    override val ONE_MOD_Q: ElementModQ = EcElementModQ(this, BigInteger.ONE)
    override val TWO_MOD_Q: ElementModQ = EcElementModQ(this, BigInteger.TWO)
    val NUM_Q_BITS: Int  = ecGroup.qbitLength

    override val constants = ecGroup.constants
    val dlogg = DLogarithm(G_MOD_P)

    // TODO diff of this and safe version?
    override fun binaryToElementModP(b: ByteArray): ElementModP? {
        val elem = ecGroup.elementFromByteArray(b)
        return if (elem != null) EcElementModP(this, elem) else null
    }

    override fun binaryToElementModPsafe(b: ByteArray, minimum: Int): ElementModP {
        return binaryToElementModP(b)?: throw RuntimeException("invalid input")
    }

    override fun binaryToElementModQ(b: ByteArray): ElementModQ? {
        return EcElementModQ(this, BigInteger(1, b))
    }

    override fun binaryToElementModQsafe(b: ByteArray, minimum: Int): ElementModQ {
        return EcElementModQ(this, BigInteger(1, b))
    }

    override fun dLogG(p: ElementModP, maxResult: Int): Int? {
        require (p is EcElementModP)
        return dlogg.dLog(p, maxResult)
    }

    override fun gPowP(exp: ElementModQ): ElementModP {
        require( exp is EcElementModQ)
        return EcElementModP(this, ecGroup.g.exp(exp.element))
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

    override fun randomElementModP(minimum: Int) = EcElementModP(this, ecGroup.randomElement())

    // TODO could these be done with just a mod at the end?
    fun addQQ(cues: Iterable<ElementModQ>): ElementModQ {
        val sum = cues.fold(BigInteger.ZERO) { a, b -> a.plus((b as EcElementModQ).element) }
        return EcElementModQ( this, sum.mod(ecGroup.order))
    }
}