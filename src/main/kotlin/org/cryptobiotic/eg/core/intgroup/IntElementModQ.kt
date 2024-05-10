package org.cryptobiotic.eg.core.intgroup

import org.cryptobiotic.eg.core.Base64.toBase64
import org.cryptobiotic.eg.core.Element
import org.cryptobiotic.eg.core.ElementModQ
import org.cryptobiotic.eg.core.assertCompatible
import org.cryptobiotic.eg.core.normalize
import java.math.BigInteger

class IntElementModQ(internal val element: BigInteger, override val group: IntGroupContext): ElementModQ,
    Element, Comparable<ElementModQ> {

    override fun byteArray(): ByteArray = element.toByteArray().normalize(32)

    private fun BigInteger.modWrap(): ElementModQ = this.mod(this@IntElementModQ.group.q).wrap()
    private fun BigInteger.wrap(): ElementModQ = IntElementModQ(this, this@IntElementModQ.group)

    override fun isZero() = element == BigInteger.ZERO
    override fun isValidElement() = element >= BigInteger.ZERO && element < this.group.q

    override operator fun compareTo(other: ElementModQ): Int = element.compareTo(other.getCompat(this.group))

    override operator fun plus(other: ElementModQ) =
        (this.element + other.getCompat(this.group)).modWrap()

    override operator fun minus(other: ElementModQ) =
        this + (-other)

    override operator fun times(other: ElementModQ) =
        (this.element * other.getCompat(this.group)).modWrap()

    override fun multInv(): ElementModQ = element.modInverse(this.group.q).wrap()

    override operator fun unaryMinus(): ElementModQ =
        if (this == this.group.zeroModQ)
            this
        else
            (this.group.q - element).wrap()

    override infix operator fun div(denominator: ElementModQ): ElementModQ =
        this * denominator.multInv()


    override fun equals(other: Any?) = when (other) {
        is ElementModQ -> byteArray().contentEquals(other.byteArray())
        else -> false
    }

    override fun hashCode() = byteArray().contentHashCode()

    override fun toString() = byteArray().toBase64()
}

internal fun ElementModQ.getCompat(other: IntGroupContext): BigInteger {
    group.assertCompatible(other)
    return when (this) {
        is IntElementModQ -> this.element
        else -> throw NotImplementedError("should only be two kinds of elements")
    }
}