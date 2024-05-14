package org.cryptobiotic.eg.core

import org.cryptobiotic.eg.core.Base16.toHex

interface Element {
    /** The [GroupContext] it belongs to */
    val group: GroupContext

    /** Validates that this element is a member of the Group */
    fun isValidElement(): Boolean

    /** Converts to a [ByteArray] representation. Inverse to group.binaryToElementModX(). */
    fun byteArray(): ByteArray

    fun toHex() : String = byteArray().toHex()
}

interface ElementModQ : Element, Comparable<ElementModQ> {
    /** Modular addition */
    operator fun plus(other: ElementModQ): ElementModQ

    /** Modular subtraction */
    operator fun minus(other: ElementModQ): ElementModQ

    /** Modular multiplication */
    operator fun times(other: ElementModQ): ElementModQ

    /** Computes the additive inverse */
    operator fun unaryMinus(): ElementModQ

    /** Finds the multiplicative inverse */
    fun multInv(): ElementModQ

    /** Multiplies by the modular inverse of [denominator] */
    infix operator fun div(denominator: ElementModQ): ElementModQ

    /** Allows elements to be compared (<, >, <=, etc.) using the usual arithmetic operators. */
    override operator fun compareTo(other: ElementModQ): Int

    /** Checks whether this element is zero. */
    fun isZero(): Boolean
}

interface ElementModP : Element, Comparable<ElementModP> {

    /** Computes b^e mod p */
    infix fun powP(exp: ElementModQ): ElementModP

    /** Modular multiplication */
    operator fun times(other: ElementModP): ElementModP

    /** Finds the multiplicative inverse */
    fun multInv(): ElementModP

    /** Multiplies by the modular inverse of [denominator] */
    infix operator fun div(denominator: ElementModP): ElementModP

    /** Allows elements to be compared (<, >, <=, etc.) using the usual arithmetic operators. */
    override operator fun compareTo(other: ElementModP): Int

    /** Create a new instance of this element where the `powP` function will possibly run faster. */
    fun acceleratePow(): ElementModP

    /** Short version of the String for readability. */
    fun toStringShort(): String {
        val s = toHex()
        val len = s.length
        return "${s.substring(0, 7)}...${s.substring(len-8, len)}"
    }
}