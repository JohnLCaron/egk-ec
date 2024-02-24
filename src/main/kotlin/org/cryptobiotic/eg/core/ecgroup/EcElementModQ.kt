package org.cryptobiotic.eg.core.ecgroup

import org.cryptobiotic.eg.core.*
import org.cryptobiotic.eg.core.Base16.toHex
import java.math.BigInteger

class EcElementModQ(val group: EcGroupContext, val element: BigInteger): ElementModQ {

    override fun byteArray(): ByteArray = element.toByteArray().normalize(32)

    private fun BigInteger.modWrap(): ElementModQ = this.mod(group.vecGroup.order).wrap()
    private fun BigInteger.wrap(): ElementModQ = EcElementModQ(group, this)

    override fun isZero() = element == BigInteger.ZERO
    override val context: GroupContext
        get() = group

    override fun inBounds() = element >= BigInteger.ZERO && element < group.vecGroup.order

    override operator fun compareTo(other: ElementModQ): Int = element.compareTo(other.getCompat(group))

    override operator fun plus(other: ElementModQ) =
        (this.element + other.getCompat(group)).modWrap()

    override operator fun minus(other: ElementModQ) =
        this + (-other)

    override operator fun times(other: ElementModQ) =
        (this.element * other.getCompat(group)).modWrap()

    override fun multInv(): ElementModQ = element.modInverse(group.vecGroup.order).wrap()

    override operator fun unaryMinus(): ElementModQ =
        if (this == group.ZERO_MOD_Q)
            this
        else
            (group.vecGroup.order - element).wrap()

    override infix operator fun div(denominator: ElementModQ): ElementModQ =
        this * denominator.multInv()


    override fun equals(other: Any?) = when (other) {
        is ElementModQ -> byteArray().contentEquals(other.byteArray())
        else -> false
    }

    override fun hashCode() = byteArray().contentHashCode()

    override fun toString() = byteArray().toHex()

    fun Element.getCompat(other: GroupContext): BigInteger {
        context.assertCompatible(other)
        return when (this) {
            is EcElementModQ -> this.element
            else -> throw NotImplementedError("should only be two kinds of elements")
        }
    }
}
