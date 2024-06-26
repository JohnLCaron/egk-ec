package org.cryptobiotic.eg.core

import org.cryptobiotic.eg.election.ElectionConstants

fun productionGroup(groupName: String = "P-256", useNative: Boolean = true): GroupContext {
    return if (groupName.startsWith("Integer")) org.cryptobiotic.eg.core.intgroup.productionIntGroup(groupName)
    else org.cryptobiotic.eg.core.ecgroup.EcGroupContext(groupName, useNative)
}

/**
 * The GroupContext interface provides all the necessary context to define the arithmetic that we'll
 * be doing, such as the moduli P and Q, the generator G, and so forth. This also allows us to
 * encapsulate acceleration data structures that we'll use to support various operations.
 */
interface GroupContext {

    /** Useful constant: one mod p */
    val ONE_MOD_P: ElementModP

    /** Useful constant: the group generator */
    val G_MOD_P: ElementModP

    /** Useful constant: zero mod q */
    val ZERO_MOD_Q: ElementModQ

    /** Useful constant: one mod q */
    val ONE_MOD_Q: ElementModQ

    /** Useful constant: two mod q */
    val TWO_MOD_Q: ElementModQ

    /** The maximum number of bytes to represent any element mod p when serialized as a ByteArray. */
    val MAX_BYTES_P: Int

    /** The maximum number of bytes to represent any element mod q when serialized as a ByteArray. */
    val MAX_BYTES_Q: Int

    /** The number of bits it takes to represent any element mod p. */
    val NUM_P_BITS: Int

    /** group parameters */
    val constants: ElectionConstants

    /**
     * Identifies whether two internal GroupContexts are "compatible", so elements made in one
     * context would work in the other. Groups with the same primes will be compatible. Note that
     * this is meant to be fast, so only makes superficial checks. The [ElectionConstants] variant
     * of this method validates that all the group constants are the same.
     */
    fun isCompatible(ctx: GroupContext): Boolean

    /**
     * Converts a [ByteArray] to an [ElementModP], inverse of ElementModP.byteArray().
     * Returns null if the number is out of bounds or malformed.
     */
    fun binaryToElementModP(b: ByteArray): ElementModP?

    /**
     * Converts a [ByteArray] to an [ElementModQ], inverse of ElementModQ.byteArray().
     * Guarantees the result is in [0, Q), by computing the result mod Q.
     * Returns null if the number is out of bounds or malformed.
     */
    fun binaryToElementModQ(b: ByteArray): ElementModQ

    /** Converts a hash value to an [ElementModQ] */
    fun hashToElementModQ(hash: UInt256): ElementModQ

    /**
     * Returns a uniform random number in [2, Q).
     * Uses a "secure" random number generator, such that the results are suitable for use as cryptographic keys.
     */
    fun randomElementModQ() : ElementModQ

    /**
     * Returns a uniform random ElementModP.
     * Uses a "secure" random number generator, such that the results are suitable for use as cryptographic keys.
     * TODO no one actually needs this
     */
    fun randomElementModP() : ElementModP

    /** Converts an integer to an ElementModQ, with optimizations when possible for small integers */
    fun uIntToElementModQ(i: UInt): ElementModQ

    /** Converts a long to an ElementModQ, with optimizations when possible for small integers */
    fun uLongToElementModQ(i: ULong): ElementModQ

    /**
     * Computes the sum of the given elements, mod q; this can be faster than using the addition
     * operation for large numbers of inputs.
     */
    fun addQ(cues: Iterable<ElementModQ>): ElementModQ

    /**
     * Computes the product of the given elements, mod p; this can be faster than using the
     * multiplication operation for large numbers of inputs.
     */
    fun multP(pees: Iterable<ElementModP>): ElementModP

    /** Computes G^e mod p, where G is our generator */
    fun gPowP(exp: ElementModQ): ElementModP

    /** Standard algorithm for Product ( base_i^exp_i), to allow subclass speedups. */
    fun prodPowers(bases: List<ElementModP>, exps: List<ElementModQ>): ElementModP {
        require(exps.size == bases.size)
        if (bases.isEmpty()) {
            return ONE_MOD_P
        }
        val pows = List( exps.size) { bases[it].powP(exps[it]) }
        return pows.reduce { a, b -> (a * b) }
    }

    /**
     * Given an element x for which there exists an e, such that g^e = x, this will find e,
     * so long as e is less than [maxResult], which if unspecified defaults to a platform-specific
     * value designed not to consume too much memory. This will consume O(e)
     * time, the first time, after which the results are memoized for all values between 0 and e,
     * for better future performance.
     * If the result is not found, null is returned.
     */
    fun dLogG(p: ElementModP, maxResult: Int = - 1): Int?

    /** debugging operation counts. */
    fun getAndClearOpCounts(): Map<String, Int>
}

// Converts an integer to an ElementModQ, with optimizations when possible for small integers
fun Int.toElementModQ(ctx: GroupContext) =
    when {
        this < 0 -> throw NoSuchElementException("no negative numbers allowed")
        else -> ctx.uIntToElementModQ(this.toUInt())
    }

// Converts an integer to an ElementModQ, with optimizations when possible for small integers
fun Long.toElementModQ(ctx: GroupContext) =
    when {
        this < 0 -> throw NoSuchElementException("no negative numbers allowed")
        else -> ctx.uLongToElementModQ(this.toULong())
    }

/**
 * Throughout our bignum arithmetic, every operation needs to check that its operands are compatible
 * (i.e., that we're not trying to use the test group and the production group interchangeably).
 * This will verify that compatibility and throw an `ArithmeticException` if they're not.
 */
fun GroupContext.assertCompatible(other: GroupContext) {
    if (!this.isCompatible(other)) {
        throw ArithmeticException("incompatible group contexts")
    }
}

/**
 * Verifies that every element has a compatible [GroupContext] and returns the first context.
 *
 * @throws IllegalArgumentException if there's an incompatibility.
 */
fun compatibleContextOrFail(vararg elements: Element): GroupContext {
    // Engineering note: If this method fails, that means we have a bug in our program.
    // We should never allow incompatible data to be processed. We should catch
    // this when we're loading the data in the first place.

    if (elements.isEmpty()) throw IllegalArgumentException("no arguments")

    val headContext = elements[0].group

    // Note: this is comparing the head of the list to itself, which seems inefficient,
    // but adding something like drop(1) in here would allocate an ArrayList and
    // entail a bunch of copying overhead. What's here is almost certainly cheaper.
    val allCompat = elements.all { it.group.isCompatible(headContext) }

    if (!allCompat) {
        throw IllegalArgumentException("incompatible contexts")
    }

    return headContext
}

fun GroupContext.showOpCountResults(where: String): String {
    val opCounts = this.getAndClearOpCounts()
    return buildString {
        appendLine("$where:")
        opCounts.toSortedMap().forEach { (key, value) ->
            appendLine("   $key : $value")
        }
    }
}



