package org.cryptobiotic.eg.core.ecgroup

import org.cryptobiotic.eg.core.*
import java.util.concurrent.atomic.AtomicInteger

class EcElementModP(override val group: EcGroupContext, val ec: VecElementP): ElementModP {

    override fun acceleratePow(): ElementModP {
        return EcElementModP(this.group, this.ec.acceleratePow())
    }

    override fun byteArray() = ec.toByteArray()

    override fun compareTo(other: ElementModP): Int {
        require (other is EcElementModP)
        return ec.compareTo(other.ec)
    }

    override fun div(denominator: ElementModP): ElementModP {
        require (denominator is EcElementModP)
        val inv = denominator.ec.inv()
        return EcElementModP(this.group, ec.mul(inv))
    }

    /** Validate that this element is a member of the elliptic curve Group.*/
    override fun isValidElement(): Boolean {
        return this.ec.isValidElement()
    }

    override fun multInv(): ElementModP {
        return EcElementModP(this.group, ec.inv())
    }

    override fun powP(exp: ElementModQ): ElementModP {
        require (exp is EcElementModQ)
        this.group.opCounts.getOrPut("exp") { AtomicInteger(0) }.incrementAndGet()
        return EcElementModP(this.group, ec.exp(exp.element))
    }

    override fun times(other: ElementModP): ElementModP {
        require (other is EcElementModP)
        return EcElementModP(this.group, ec.mul(other.ec))
    }

    override fun toString(): String {
        return ec.toString()
    }

    override fun toStringShort(): String {
        return "EcElementModP(${ec.x.toStringShort()}, ${ec.y.toStringShort()})"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EcElementModP

        if (this.group != other.group) return false
        if (ec != other.ec) return false

        return true
    }

    override fun hashCode(): Int {
        var result = this.group.hashCode()
        result = 31 * result + ec.hashCode()
        return result
    }
}