package org.cryptobiotic.eg.ecgroup

import org.cryptobiotic.eg.core.*
import java.math.BigInteger

class EcElementModP(val group: EcGroupContext, val ec: VecElementModP): ElementModP {
    override val context: GroupContext = group

    override fun acceleratePow(): ElementModP {
        return this
    }

    override fun byteArray() = ec.toByteArray()

    override fun compareTo(other: ElementModP): Int {
        require (other is EcElementModP)
        return ec.compareTo(other.ec)
    }

    override fun div(denominator: ElementModP): ElementModP {
        require (denominator is EcElementModP)
        val inv = denominator.ec.inv()
        return EcElementModP(group, ec.mul(inv))
    }

    // what does it mean to be in bounds ??
    override fun inBounds(): Boolean {
        TODO("Not yet implemented")
    }

    override fun isValidResidue(): Boolean {
        return group.ecGroup.isPointOnCurve(this.ec.x, this.ec.y)
    }

    override fun multInv(): ElementModP {
        return EcElementModP(group, ec.inv())
    }

    override fun powP(exp: ElementModQ): ElementModP {
        require (exp is EcElementModQ)
        return EcElementModP(group, ec.exp(exp.element))
    }

    override fun times(other: ElementModP): ElementModP {
        require (other is EcElementModP)
        return EcElementModP(group, ec.mul(other.ec))
    }

    override fun toString(): String {
        return ec.toString()
    }

    override fun toStringShort(): String {
        return "ECqPGroupElement(${ec.x.toStringShort()}, ${ec.y.toStringShort()})"
    }
}

fun BigInteger.toStringShort(): String {
    val s = toHex()
    val len = s.length
    return if (len > 16)
      "${s.substring(0, 7)}...${s.substring(len-8, len)}"
    else s
}