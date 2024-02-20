package org.cryptobiotic.eg.core.intgroup

import org.cryptobiotic.eg.core.*
import java.math.BigInteger

internal const val b64Production4096P256MinusQ = "AL0="
internal const val intProduction4096PBits = 4096

internal const val b64Production3072P256MinusQ = "AL0="
internal const val intProduction3072PBits = 3072

// 32-bit "tiny" group", suitable for accelerated testing
internal const val intTestP = 1879047647
internal const val intTestQ = 134217689
internal const val intTestR = 14
internal const val intTestG = 16384

internal val intTestMontgomeryI = 2147483648U
internal val intTestMontgomeryIMinus1 = 2147483647U
internal val intTestMontgomeryIPrime = 1533837288U
internal val intTestMontgomeryPPrime = 1752957409U

// 64-bit "small" group, suitable for accelerated testing
internal const val b64TestP = "b//93w=="
internal const val b64TestQ = "B///2Q=="
internal const val b64TestP256MinusQ = "AP/////////////////////////////////////4AAAn"
internal const val b64TestR = "Dg=="
internal const val b64TestG = "QAA="

internal const val b64TestMontgomeryI = "AIAAAAA="
internal const val b64TestMontgomeryIMinus1 = "f////w=="
internal const val b64TestMontgomeryIPrime = "W2x/6A=="
internal const val b64TestMontgomeryPPrime = "aHwB4Q=="

internal const val intTestPBits = 31

/**
 * ElectionGuard defines two modes of operation, with P having either 4096 bits or 3072 bits. We'll
 * track which mode we're using with this enum. For testing purposes, and only available to the
 * `test` modules, see `TinyGroup`, which provides "equivalent" modular arithmetic for
 * stress-testing code, while running significantly faster.
 */
enum class ProductionMode(val numBitsInP: Int) {
    Mode4096(intProduction4096PBits),
    Mode3072(intProduction3072PBits);

    override fun toString() = "ProductionMode($numBitsInP bits)"

    val numBytesInP: Int = numBitsInP / 8
    val numLongWordsInP: Int = numBitsInP / 64
}

private val montgomeryI4096 = BigInteger.ONE shl Primes4096.nbits
private val p4096 = BigInteger(Primes4096.pStr, 16)

private val productionGroups4096 : Map<PowRadixOption, ProductionGroupContext> =
    PowRadixOption.entries.associateWith {
        ProductionGroupContext(
            pBytes = Primes4096.largePrimeBytes,
            qBytes = Primes4096.smallPrimeBytes,
            gBytes = Primes4096.generatorBytes,
            rBytes = Primes4096.residualBytes,
            montIMinus1Bytes = (montgomeryI4096 - BigInteger.ONE).toByteArray(),
            montIPrimeBytes = (montgomeryI4096.modPow(p4096 - BigInteger.TWO, p4096)).toByteArray(),
            montPPrimeBytes = ((montgomeryI4096 - p4096).modInverse(montgomeryI4096)).toByteArray(),
            name = "production group, ${it.description}, 4096 bits",
            powRadixOption = it,
            productionMode = ProductionMode.Mode4096,
            numPBits = Primes4096.nbits
        )
    }

private val montgomeryI3072 = BigInteger.ONE shl Primes3072.nbits
private val p3072 = BigInteger(Primes3072.pStr, 16)

private val productionGroups3072 : Map<PowRadixOption, ProductionGroupContext> =
    PowRadixOption.entries.associateWith {
        ProductionGroupContext(
            pBytes = Primes3072.largePrimeBytes,
            qBytes = Primes3072.smallPrimeBytes,
            gBytes = Primes3072.generatorBytes,
            rBytes = Primes3072.residualBytes,
            montIMinus1Bytes = (montgomeryI3072 - BigInteger.ONE).toByteArray(),
            montIPrimeBytes = (montgomeryI3072.modPow(p3072 - BigInteger.TWO, p3072)).toByteArray(),
            montPPrimeBytes = ((montgomeryI3072 - p3072).modInverse(montgomeryI3072)).toByteArray(),
            name = "production group, ${it.description}, 3072 bits",
            powRadixOption = it,
            productionMode = ProductionMode.Mode3072,
            numPBits = Primes3072.nbits
        )
    }


fun productionGroup(acceleration: PowRadixOption = PowRadixOption.LOW_MEMORY_USE,
                    mode: ProductionMode = ProductionMode.Mode4096
) : GroupContext =
    when(mode) {
        ProductionMode.Mode4096 -> productionGroups4096[acceleration] ?: throw Error("can't happen")
        ProductionMode.Mode3072 -> productionGroups3072[acceleration] ?: throw Error("can't happen")
    }

//
// /*
// * Verifies that every element has a compatible [GroupContext] and returns the first context.
// *
// * @throws IllegalArgumentException if there's an incompatibility.
// fun compatibleContextOrFail(vararg elements: Element): GroupContext {
// // Engineering note: If this method fails, that means we have a bug in our program.
// // We should never allow incompatible data to be processed. We should catch
// // this when we're loading the data in the first place.
//
// if (elements.isEmpty()) throw IllegalArgumentException("no arguments")
//
// val headContext = elements[0].context
//
// // Note: this is comparing the head of the list to itself, which seems inefficient,
// // but adding something like drop(1) in here would allocate an ArrayList and
// // entail a bunch of copying overhead. What's here is almost certainly cheaper.
// val allCompat = elements.all { it.context.isCompatible(headContext) }
//
// if (!allCompat) {
// throw IllegalArgumentException("incompatible contexts")
// }
//
// return headContext
// }
//
// * Given an element x for which there exists an e, such that g^e = x, this will find e,
// * so long as e is less than [maxResult], which if unspecified defaults to a platform-specific
// * value designed not to consume too much memory (perhaps 10 million). This will consume O(e)
// * time, the first time, after which the results are memoized for all values between 0 and e,
// * for better future performance.
// *
// * If the result is not found, `null` is returned.
// fun ElementModP.dLogG(maxResult: Int = -1): Int? = context.dLogG(this, maxResult)
//
// * Converts from an external [ElectionConstants] to an internal [GroupContext]. Note the optional
// * `acceleration` parameter, to specify the speed versus memory tradeoff for subsequent computation.
// * See [PowRadixOption] for details. Note that this function can return `null`, which indicates that
// * the [ElectionConstants] were incompatible with this particular library.
// *
// fun ElectionConstants.toGroupContext(
// acceleration: PowRadixOption = PowRadixOption.LOW_MEMORY_USE
// ) : GroupContext? {
// val group4096 = productionGroup(acceleration = acceleration, mode = ProductionMode.Mode4096)
// val group3072 = productionGroup(acceleration = acceleration, mode = ProductionMode.Mode3072)
//
// return when {
// group4096.isCompatible(this) -> group4096
// group3072.isCompatible(this) -> group3072
// else -> {
// logger.error {
// "unrecognized cryptographic parameters; this election was encrypted using a " +
// "library incompatible with this one: $this"
// }
// null
// }
// }
// }
//
// /**
// * Computes the sum of the given elements, mod q; this can be faster than using the addition
// * operation for large numbers of inputs by potentially reusing scratch-space memory.
// */
// fun GroupContext.addQ(vararg elements: ElementModQ) = elements.asIterable().addQ()
//
// /**
// * Computes the product of the given elements, mod p; this can be faster than using the
// * multiplication operation for large numbers of inputs by potentially reusing scratch-space memory.
// */
// fun GroupContext.multP(vararg elements: ElementModP) = elements.asIterable().multP()
//
// */