package org.cryptobiotic.eg.core.intgroup

import io.kotest.property.checkAll
import org.cryptobiotic.eg.core.*
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.Test

// Unlike the "normal" group tests, these ones need to see inside at the internal
// data structures (e.g., BigInteger for Java), so we put these tests in the JVM-only
// section to make this possible.
class MontgomeryJVMTests {
    @Test
    fun shiftyModAndDiv4096() =
        shiftyModAndDiv { productionGroup("Integer4096") }

    @Test
    fun shiftyModAndDiv3072() =
        shiftyModAndDiv { productionGroup("Integer3072") }

    @Test
    fun shiftyModAndDivTiny() = runTest {
        val context = tinyGroup()

        checkAll(validResiduesOfP(context), validResiduesOfP(context)) { a, b ->
            val aVal = (a as TinyElementModP).toMontgomeryElementModP() as TinyMontgomeryElementModP
            val bVal = (b as TinyElementModP).toMontgomeryElementModP() as TinyMontgomeryElementModP
            with (aVal) {
                val twoPowPBits: ULong = 1UL shl context.NUM_P_BITS
                assertEquals(element.toULong(), element.toULong().modI())
                assertEquals(element.toULong(), (element.toULong() + (bVal.element.toULong() * twoPowPBits)).modI())
                assertEquals(element, (element.toULong() * twoPowPBits).divI())
            }
        }
    }

    fun shiftyModAndDiv(contextF: () -> GroupContext) {
        runTest {
            val context = contextF() as ProductionGroupContext

            checkAll(
                propTestSlowConfig,
                validResiduesOfP(context), validResiduesOfP((context))
            ) { a, b ->
                val aVal = (a as ProductionElementModP).toMontgomeryElementModP() as ProductionMontgomeryElementModP
                val bVal = (b as ProductionElementModP).toMontgomeryElementModP() as ProductionMontgomeryElementModP
                with (aVal) {
                    val twoPowPBits = BigInteger.TWO.pow(context.NUM_P_BITS)
                    assertEquals(element, element.modI())
                    assertEquals(element, (element + (bVal.element * twoPowPBits)).modI())
                    assertEquals(element, (element * twoPowPBits).divI())
                    assertEquals(element, (element * twoPowPBits + bVal.element).divI())
                }
            }
        }
    }

    @Test
    fun relationshipsIAndPTiny() {
        runTest {
            val pPrime = intTestMontgomeryPPrime.toULong()
            val p = intTestP.toULong()
            val iPrime = intTestMontgomeryIPrime.toULong()
            val i = intTestMontgomeryI.toULong()

            assertEquals(1UL, (iPrime * i) % p)
            assertEquals(1UL, ((i - pPrime) * p) % i)
        }
    }
    @Test
    fun relationshipsIAndPProduction() {
        runTest {
            listOf(productionGroup("Integer4096"), productionGroup("Integer3072"))
                .forEach { context ->
                    val pContext = context as ProductionGroupContext
                    val pPrime = pContext.montgomeryPPrime
                    val p = pContext.p
                    val iPrime = pContext.montgomeryIPrime
                    val i = pContext.montgomeryIMinusOne + BigInteger.ONE

                    assertEquals(BigInteger.ONE, (iPrime * i) % p)
                    assertEquals(BigInteger.ONE, ((i - pPrime) * p) % i)
                }
        }
    }
}