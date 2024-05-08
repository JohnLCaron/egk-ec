package org.cryptobiotic.eg.core.ecgroup

import org.cryptobiotic.eg.core.*
import org.cryptobiotic.eg.core.Base64.toBase64
import java.math.BigInteger

class EcElementModQ(override val group: EcGroupContext, val element: BigInteger): ElementModQ {

    override fun byteArray(): ByteArray = element.toByteArray().normalize(32)

    private fun BigInteger.modWrap(): ElementModQ = this.mod(this@EcElementModQ.group.vecGroup.order).wrap()
    private fun BigInteger.wrap(): ElementModQ = EcElementModQ(this@EcElementModQ.group, this)

    override fun isZero() = element == BigInteger.ZERO
    override fun isValidElement() = element >= BigInteger.ZERO && element < this.group.vecGroup.order

    override operator fun compareTo(other: ElementModQ): Int = element.compareTo(other.getCompat(this.group))

    override operator fun plus(other: ElementModQ) =
        (this.element + other.getCompat(this.group)).modWrap()

    override operator fun minus(other: ElementModQ) =
        this + (-other)

    override operator fun times(other: ElementModQ) =
        (this.element * other.getCompat(this.group)).modWrap()

    override fun multInv(): ElementModQ = element.modInverse(this.group.vecGroup.order).wrap()

    override operator fun unaryMinus(): ElementModQ =
        if (this == this.group.ZERO_MOD_Q)
            this
        else
            (this.group.vecGroup.order - element).wrap()

    override infix operator fun div(denominator: ElementModQ): ElementModQ =
        this * denominator.multInv()


    override fun equals(other: Any?) = when (other) {
        is ElementModQ -> byteArray().contentEquals(other.byteArray())
        else -> false
    }

    override fun hashCode() = byteArray().contentHashCode()

    override fun toString() = byteArray().toBase64()

    fun Element.getCompat(other: GroupContext): BigInteger {
        group.assertCompatible(other)
        return when (this) {
            is EcElementModQ -> this.element
            else -> throw NotImplementedError("should only be two kinds of elements")
        }
    }
}
