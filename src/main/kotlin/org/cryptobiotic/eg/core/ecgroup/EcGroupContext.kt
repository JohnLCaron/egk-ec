package org.cryptobiotic.eg.core.ecgroup

import org.cryptobiotic.eg.core.*
import java.math.BigInteger
import java.util.concurrent.atomic.AtomicInteger

class EcGroupContext(val name: String, useNative: Boolean = true): GroupContext {
    val vecGroup: VecGroup = VecGroups.getEcGroup(name, useNative)
    val ONE = EcElementModP(this, vecGroup.ONE)

    override val G_MOD_P: ElementModP = EcElementModP(this, vecGroup.g)
    override val NUM_P_BITS: Int = vecGroup.pbitLength
    override val MAX_BYTES_P: Int = vecGroup.pbyteLength + 1 // x plus sign of y
    override val ONE_MOD_P: ElementModP = this.ONE

    override val MAX_BYTES_Q: Int = vecGroup.nbyteLength
    override val ZERO_MOD_Q: ElementModQ = EcElementModQ(this, BigInteger.ZERO)
    override val ONE_MOD_Q: ElementModQ = EcElementModQ(this, BigInteger.ONE)
    override val TWO_MOD_Q: ElementModQ = EcElementModQ(this, BigInteger.TWO)

    override val constants = vecGroup.constants

    private val dlogg = DLogarithm(G_MOD_P)
    private val rfc9380 = RFC9380(this, "QUUX-V01-CS02-with-P256_XMD:SHA-256_SSWU_RO_".toByteArray(), statBytesQ/8)

    override fun binaryToElementModP(b: ByteArray): ElementModP? {
        val elem = vecGroup.elementFromByteArray(b)
        return if (elem != null) EcElementModP(this, elem) else null
    }

    override fun binaryToElementModQ(b: ByteArray) = EcElementModQ(this, BigInteger(1, b))

    override fun hashToElementModQ(hash: UInt256): ElementModQ = rfc9380.hash_to_field(hash.bytes)

    override fun randomElementModQ() : ElementModQ  {
        val b = randomBytes(MAX_BYTES_Q)
        return rfc9380.hash_to_field(b)
    }

    override fun randomElementModP() : EcElementModP {
        return EcElementModP(this, vecGroup.randomElement(statBytesP))
    }

    override fun dLogG(p: ElementModP, maxResult: Int): Int? {
        require(p is EcElementModP)
        return dlogg.dLog(p, maxResult)
    }

    override fun gPowP(exp: ElementModQ): ElementModP {
        require(exp is EcElementModQ)
        return EcElementModP(this, vecGroup.g.exp(exp.element))
    }

    override fun isCompatible(ctx: GroupContext): Boolean {
        return (ctx is EcGroupContext) && name == ctx.name
    }

    override fun uIntToElementModQ(i: UInt): ElementModQ {
        return EcElementModQ(this, BigInteger.valueOf(i.toLong()))
    }

    override fun uLongToElementModQ(i: ULong): ElementModQ {
        return EcElementModQ(this, BigInteger.valueOf(i.toLong()))
    }

    override fun addQ(cues: Iterable<ElementModQ>): ElementModQ {
        val sum = cues.fold(BigInteger.ZERO) { a, b -> a.plus((b as EcElementModQ).element) }
        return EcElementModQ(this, sum.mod(vecGroup.order))
    }

    override fun multP(pees: Iterable<ElementModP>): ElementModP {
       return pees.fold(ONE_MOD_P) { a, b -> a * b }
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
        val ec = vecGroup.prodPowers(bases, exps)
        return EcElementModP(this, ec)
    }


    var opCounts: HashMap<String, AtomicInteger> = HashMap()
    override fun getAndClearOpCounts(): Map<String, Int> {
        val result = HashMap<String, Int>()
        opCounts.forEach { (key, value) -> result[key] = value.get() }
        opCounts = HashMap()
        return result.toSortedMap()
    }

    companion object {
        const val statBytesQ = 128
        const val statBytesP = 128
    }
}
