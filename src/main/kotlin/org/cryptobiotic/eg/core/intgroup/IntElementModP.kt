package org.cryptobiotic.eg.core.intgroup

import org.cryptobiotic.eg.core.*
import org.cryptobiotic.eg.core.Base64.toBase64
import java.math.BigInteger
import java.util.concurrent.atomic.AtomicInteger

open class IntElementModP(internal val element: BigInteger, val groupContext: IntGroupContext): ElementModP,
    Element, Comparable<ElementModP> {

    override fun byteArray(): ByteArray = element.toByteArray().normalize(512)

    private fun BigInteger.modWrap(): ElementModP = this.mod(groupContext.p).wrap()
    private fun BigInteger.wrap(): ElementModP = IntElementModP(this, groupContext)

    override val group: GroupContext
        get() = groupContext

    override operator fun compareTo(other: ElementModP): Int = element.compareTo(other.getCompat(groupContext))

    /**
     * Validates that this element is in Z_p^r, "set of r-th-residues in Z_p".
     * "A value x is in Z_p^r if and only if x is an integer such that 0 ≤ x < p
     * and x^q mod p == 1", see spec 2.0 p.9.
     */
    override fun isValidElement(): Boolean {
        groupContext.opCounts.getOrPut("exp") { AtomicInteger(0) }.incrementAndGet()
        val inBounds = this.element >= BigInteger.ZERO && this.element < groupContext.p
        val residue = this.element.modPow(groupContext.q, groupContext.p) == groupContext.oneModP.element
        return inBounds && residue
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
            groupContext
        )

    override fun equals(other: Any?) = when (other) {
        is ElementModP -> byteArray().contentEquals(other.byteArray())
        else -> false
    }

    override fun hashCode() = byteArray().contentHashCode()

    override fun toString() = byteArray().toBase64()

    private fun ElementModP.getCompat(other: IntGroupContext): BigInteger {
        group.assertCompatible(other)
        return when (this) {
            is IntElementModP -> this.element
            else -> throw NotImplementedError("should only be two kinds of elements")
        }
    }
}

class AcceleratedElementModP(p: IntElementModP) : IntElementModP(p.element, p.groupContext) {
    // Laziness to delay computation of the table until its first use; saves space
    val powRadix by lazy { PowRadix(p, p.groupContext.powRadixOption) }

    override fun acceleratePow(): ElementModP = this

    override infix fun powP(exp: ElementModQ) : ElementModP {
        groupContext.opCounts.getOrPut("acc") { AtomicInteger(0) }.incrementAndGet()
        return powRadix.pow(exp)
    }
}

internal data class ProductionMontgomeryElementModP(val element: BigInteger, val groupContext: IntGroupContext):
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
        IntElementModP((element * groupContext.montgomeryIPrime).mod(groupContext.p), groupContext)

    override val context: GroupContext
        get() = groupContext

}