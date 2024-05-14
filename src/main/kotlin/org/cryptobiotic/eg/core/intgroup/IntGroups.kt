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

private val productionGroups4096 : Map<PowRadixOption, IntGroupContext> =
    PowRadixOption.entries.associateWith {
        IntGroupContext(
            pBytes = Primes4096.largePrimeBytes,
            qBytes = Primes4096.smallPrimeBytes,
            gBytes = Primes4096.generatorBytes,
            rBytes = Primes4096.residualBytes,
            montIMinus1Bytes = (montgomeryI4096 - BigInteger.ONE).toByteArray(),
            montIPrimeBytes = (montgomeryI4096.modPow(p4096 - BigInteger.TWO, p4096)).toByteArray(),
            montPPrimeBytes = ((montgomeryI4096 - p4096).modInverse(montgomeryI4096)).toByteArray(),
            name = "Integer4096 (${it.description})",
            powRadixOption = it,
            productionMode = ProductionMode.Mode4096,
            numPBits = Primes4096.nbits
        )
    }

private val montgomeryI3072 = BigInteger.ONE shl Primes3072.nbits
private val p3072 = BigInteger(Primes3072.pStr, 16)

private val productionGroups3072 : Map<PowRadixOption, IntGroupContext> =
    PowRadixOption.entries.associateWith {
        IntGroupContext(
            pBytes = Primes3072.largePrimeBytes,
            qBytes = Primes3072.smallPrimeBytes,
            gBytes = Primes3072.generatorBytes,
            rBytes = Primes3072.residualBytes,
            montIMinus1Bytes = (montgomeryI3072 - BigInteger.ONE).toByteArray(),
            montIPrimeBytes = (montgomeryI3072.modPow(p3072 - BigInteger.TWO, p3072)).toByteArray(),
            montPPrimeBytes = ((montgomeryI3072 - p3072).modInverse(montgomeryI3072)).toByteArray(),
            name = "Integer3072 (${it.description})",
            powRadixOption = it,
            productionMode = ProductionMode.Mode3072,
            numPBits = Primes3072.nbits
        )
    }

fun productionIntGroup(groupName: String): GroupContext {
    // could also parse the PowRadix
    return if (groupName.startsWith("Integer3072")) productionIntGroup(mode = ProductionMode.Mode3072)
    else productionIntGroup(mode = ProductionMode.Mode4096)
}

fun productionIntGroup(acceleration: PowRadixOption = PowRadixOption.LOW_MEMORY_USE,
                    mode: ProductionMode = ProductionMode.Mode4096
) : GroupContext =
    when(mode) {
        ProductionMode.Mode4096 -> productionGroups4096[acceleration] ?: throw Error("can't happen")
        ProductionMode.Mode3072 -> productionGroups3072[acceleration] ?: throw Error("can't happen")
    }
