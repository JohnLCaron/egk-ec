package org.cryptobiotic.eg.core

import org.cryptobiotic.eg.core.Base16.toHex
import org.cryptobiotic.eg.core.Base64.toBase64
import kotlin.experimental.xor

/**
 * Superficially similar to an [ElementModQ], but guaranteed to be exactly 32 bytes long. Use with
 * care, because [ByteArray] allows for mutation, and the internal representation is available for
 * external use.TODO generalize in case we need hash386??
 */
data class UInt256(val bytes: ByteArray) {
    init {
        require(bytes.size == 32) { "UInt256 must have exactly 32 bytes" }
    }

    override fun equals(other: Any?): Boolean = other is UInt256 && other.bytes.contentEquals(bytes)

    override fun hashCode(): Int {
        return bytes.contentHashCode()
    }

    override fun toString(): String {
        return "UInt256(0x${bytes.toHex()})"
    }

    fun toHex(): String = bytes.toHex() // TODO why?
    fun toBase64(): String = bytes.toBase64() // TODO why?

    companion object {
        val ZERO = 0U.toUInt256()
        val ONE = 1U.toUInt256()
        val TWO = 2U.toUInt256()

        fun random() : UInt256 = UInt256(randomBytes(32))
    }
}

/** Computes a new [UInt256] that represents the bitwise xor between two [UInt256] values. */
infix fun UInt256.xor(other: UInt256) =
    UInt256(ByteArray(32) { this.bytes[it] xor other.bytes[it] })

/** Check for whether the UInt256 is all zeros. */
fun UInt256.isZero(): Boolean = UInt256.ZERO == this

/** Converts a [ByteArray] to a [UInt256] if it fits. Otherwise, throws an [Exception]. */
fun ByteArray.toUInt256safe(): UInt256 {
    return UInt256(this.normalize(32))
}

/**
 * Converts a [ByteArray] to a [UInt256], must be that x == x.bytes.toUInt256(), where x is a UInt256.
 * Returns null if the number doesnt fit in 32 bytes.
 */
fun ByteArray.toUInt256(): UInt256? {
    return try {
        UInt256(this.normalize(32))
    } catch (t : IllegalArgumentException) {
        null
    }
}

/** Make ByteArray have exactly [nbytes] bytes by zero padding or removing leading zeros.
 * Throws an [IllegalArgumentException] if not possible. */
fun ByteArray.normalize(nbytes: Int): ByteArray {
    return if (size == nbytes) {
        this
    } else if (size > nbytes) { // remove leading zeros
        val leading = size - nbytes
        for (idx in 0 until leading) {
            if (this[idx].compareTo(0) != 0) {
                throw IllegalArgumentException("ByteArray.normalize error; has $size bytes, want $nbytes, leading zeroes stop at $idx")
            }
        }
        this.copyOfRange(leading, this.size)
    } else { // pad with leading zeros
        val leftPad = ByteArray(nbytes - size) { 0 }
        leftPad + this
    }
}

/**
 * Safely converts a [UInt256] to an [ElementModQ], wrapping values outside the range back to the
 * beginning by computing "mod q".
 */
fun UInt256.toElementModQ(context: GroupContext): ElementModQ =
    context.binaryToElementModQsafe(bytes)

fun ElementModQ.toUInt256safe(): UInt256 = this.byteArray().toUInt256safe()
fun ULong.toUInt256(): UInt256 = this.toByteArray().toUInt256safe()
fun UInt.toUInt256(): UInt256 = this.toULong().toByteArray().toUInt256safe()
fun UShort.toUInt256(): UInt256 = this.toULong().toByteArray().toUInt256safe()