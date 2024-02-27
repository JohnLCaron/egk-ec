package org.cryptobiotic.eg.core

import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import io.kotest.property.forAll
import org.cryptobiotic.eg.core.ecgroup.EcGroupContext
import org.cryptobiotic.eg.core.intgroup.tinyGroup
import kotlin.test.*

class GroupTest {
    val groups = listOf(
        tinyGroup(),
        productionGroup("Integer group 3072"),
        productionGroup("Integer group 4096"),
        EcGroupContext("P-256")
    )

    @Test
    fun basics() {
        groups.forEach { testBasics(it) }
    }

    fun testBasics(context: GroupContext) {
        val three = context.uIntToElementModQ(3U)
        val four = context.uIntToElementModQ(4U)
        val seven = context.uIntToElementModQ(7U)
        assertEquals(seven, three + four)
    }

    @Test
    fun comparisonOperations() {
        groups.forEach { comparisonOperations(it) }
    }

    fun comparisonOperations(context: GroupContext) {
        val three = context.uIntToElementModQ(3U)
        val four = context.uIntToElementModQ(4U)

        assertTrue(three < four)
        assertTrue(three <= four)
        assertTrue(four > three)
        assertTrue(four >= four)
    }

    @Test
    fun generatorsWork() {
        groups.forEach { generatorsWork(it) }
    }

    fun generatorsWork(context: GroupContext) {
        runTest {
            forAll(propTestFastConfig, elementsModP(context)) { it.inBounds() }
            forAll(propTestFastConfig, elementsModQ(context)) { it.inBounds() }
        }
    }

    @Test
    fun validResiduesForGPowP() {
        groups.forEach { validResiduesForGPowP(it) }
    }

    fun validResiduesForGPowP(context: GroupContext) {
        runTest {
            forAll(propTestFastConfig, validResiduesOfP(context)) { it.isValidResidue() }
        }
    }

    @Test
    fun binaryArrayRoundTrip() {
        groups.forEach { binaryArrayRoundTrip(it) }
    }

    fun binaryArrayRoundTrip(context: GroupContext) {
        runTest {
            forAll(propTestFastConfig, elementsModP(context)) {
                it == context.binaryToElementModP(it.byteArray())
            }
            forAll(propTestFastConfig, elementsModQ(context)) {
                it == context.binaryToElementModQ(it.byteArray())
            }
        }
    }

    /*
    @Test
    fun base64RoundTrip() {
        runTest {
            val context = productionGroup()
            forAll(propTestFastConfig, elementsModP(context)) {
                it == context.base64ToElementModP(it.base64())
            }
            forAll(propTestFastConfig, elementsModQ(context)) {
                it == context.base64ToElementModQ(it.base64())
            }
        }
    }

    fun GroupContext.base64ToElementModQ(s: String): ElementModQ? =
        s.fromBase64()?.let { binaryToElementModQ(it) }

    fun GroupContext.base64ToElementModP(s: String): ElementModP? =
        s.fromBase64()?.let { binaryToElementModP(it) }

    // Converts from any [Element] to a base64 string representation.
    fun Element.base64(): String = byteArray().toBase64()

    @Test
    fun baseConversionFails() {
        runTest {
            val context = productionGroup()
            listOf("", "@@", "-10", "1234567890".repeat(1000))
                .forEach {
                    assertNull(context.base64ToElementModP(it))
                    assertNull(context.base64ToElementModQ(it))
                }
        }
    }
     */

    @Test
    fun additionBasics() {
        groups.forEach { additionBasics(it) }
    }

    fun additionBasics(context: GroupContext) {
        runTest {
            checkAll(
                propTestFastConfig,
                elementsModQ(context),
                elementsModQ(context),
                elementsModQ(context)
            ) { a, b, c ->
                assertEquals(a, a + context.ZERO_MOD_Q) // identity
                assertEquals(a + b, b + a) // commutative
                assertEquals(a + (b + c), (a + b) + c) // associative
            }
        }
    }

    @Test
    fun additionWrappingQ() {
        groups.forEach { additionWrappingQ(it) }
    }

    internal val intTestQ = 134217689 // KLUDGE
    fun additionWrappingQ(context: GroupContext) {
        runTest {
            checkAll(propTestFastConfig, Arb.int(min=0, max= intTestQ - 1)) { i ->
                val iq = context.uIntToElementModQ(i.toUInt())
                val q = context.ZERO_MOD_Q - iq
                assertTrue(q.inBounds())
                assertEquals(context.ZERO_MOD_Q, q + iq)
            }
        }
    }

    @Test
    fun multiplicationBasicsP() {
        groups.forEach { multiplicationBasicsP(it) }
    }

    fun multiplicationBasicsP(context: GroupContext) {
        runTest {
            checkAll(
                propTestFastConfig,
                elementsModPNoZero(context),
                elementsModPNoZero(context),
                elementsModPNoZero(context)
            ) { a, b, c ->
                assertEquals(a, a * context.ONE_MOD_P) // identity
                assertEquals(a * b, b * a) // commutative
                assertEquals(a * (b * c), (a * b) * c) // associative
            }
        }
    }

    @Test
    fun multiplicationBasicsQ() {
        groups.forEach { multiplicationBasicsQ(it) }
    }

    fun multiplicationBasicsQ(context: GroupContext) {
        runTest {
            checkAll(
                propTestFastConfig,
                elementsModQNoZero(context),
                elementsModQNoZero(context),
                elementsModQNoZero(context)
            ) { a, b, c ->
                assertEquals(a, a * context.ONE_MOD_Q)
                assertEquals(a * b, b * a)
                assertEquals(a * (b * c), (a * b) * c)
            }
        }
    }

    @Test
    fun subtractionBasics() {
        groups.forEach { subtractionBasics(it) }
    }

    fun subtractionBasics(context: GroupContext) {
        runTest {
            checkAll(
                propTestFastConfig,
                elementsModQNoZero(context),
                elementsModQNoZero(context),
                elementsModQNoZero(context)
            ) { a, b, c ->
                assertEquals(a, a - context.ZERO_MOD_Q, "identity")
                assertEquals(a - b, -(b - a), "commutativity-ish")
                assertEquals(a - (b - c), (a - b) + c, "associativity-ish")
            }
        }
    }

    @Test
    fun negation() {
        groups.forEach { negation(it) }
    }

    fun negation(context: GroupContext) {
        runTest {
            forAll(propTestFastConfig, elementsModQ(context)) { context.ZERO_MOD_Q == (-it) + it }
        }
    }

    @Test
    fun multiplicativeInversesP() {
        groups.forEach { multiplicativeInversesP(it) }
    }

    fun multiplicativeInversesP(context: GroupContext) {
        runTest {
            // our inverse code only works for elements in the subgroup, which makes it faster
            forAll(propTestFastConfig, validResiduesOfP(context)) {
                it.multInv() * it == context.ONE_MOD_P
            }
        }
    }

    @Test
    fun multiplicativeInversesQ() {
        groups.forEach { multiplicativeInversesQ(it) }
    }

    fun multiplicativeInversesQ(context: GroupContext) {
        runTest {
            checkAll(propTestFastConfig, elementsModQNoZero(context)) {
                assertEquals(context.ONE_MOD_Q, it.multInv() * it)
            }
        }
    }

    @Test
    fun divisionP() {
        groups.forEach { divisionP(it) }
    }

    fun divisionP(context: GroupContext) {
        runTest {
            forAll(propTestFastConfig, validResiduesOfP(context), validResiduesOfP(context))
                { a, b -> (a * b) / b == a }
        }
    }

    @Test
    fun exponentiation() {
        groups.forEach { exponentiation(it) }
    }

    fun exponentiation(context: GroupContext) {
        runTest {
            forAll(propTestFastConfig, elementsModQ(context), elementsModQ(context)) { a, b ->
                context.gPowP(a) * context.gPowP(b) == context.gPowP(a + b)
            }
        }
    }

    @Test
    fun acceleratedExponentiation() {
        groups.forEach { acceleratedExponentiation(it) }
    }

    fun acceleratedExponentiation(context: GroupContext) {
        runTest {
            forAll(propTestFastConfig, elementsModQ(context), elementsModQ(context)) { a, b ->
                val ga = context.gPowP(a)
                val normal = ga powP b
                val gaAccelerated = ga.acceleratePow()
                val faster = gaAccelerated powP b
                normal == faster
            }
        }
    }

    @Test
    fun subgroupInverses() {
        groups.forEach { subgroupInverses(it) }
    }

    fun subgroupInverses(context: GroupContext) {
        runTest {
            forAll(propTestFastConfig, elementsModQ(context)) {
                val p1 = context.gPowP(it)
                val p2 = p1 powP (context.ZERO_MOD_Q - context.ONE_MOD_Q)
                p1 * p2 == context.ONE_MOD_P
            }
        }
    }

    @Test
    fun iterableAddition() {
        groups.forEach { iterableAddition(it) }
    }

    fun iterableAddition(context: GroupContext) {
        runTest {
            checkAll(
                propTestFastConfig,
                elementsModQ(context),
                elementsModQ(context),
                elementsModQ(context)
            ) { a, b, c ->
                val expected = a + b + c
                assertEquals(expected, context.addQ(a, b, c))
                assertEquals(expected, with(context) { listOf(a, b, c).addQ() })
            }
        }
    }

    @Test
    fun iterableMultiplication() {
        groups.forEach { iterableMultiplication(it) }
    }

    fun iterableMultiplication(context: GroupContext) {
        runTest {
            checkAll(
                propTestFastConfig,
                validResiduesOfP(context),
                validResiduesOfP(context),
                validResiduesOfP(context)
            ) { a, b, c ->
                val expected = a * b * c
                assertEquals(expected, context.multP(a, b, c))
                assertEquals(expected, with(context) { listOf(a, b, c).multP() })
            }
        }
    }

    /*
    @Test
    fun groupConstantsCompatibility() {
        runTest {
            val ctxP =
                productionGroup(
                    acceleration = PowRadixOption.NO_ACCELERATION,
                    mode = ProductionMode.Mode4096
                )
            val ctxP2 =
                productionGroup(
                    acceleration = PowRadixOption.LOW_MEMORY_USE,
                    mode = ProductionMode.Mode4096
                )
            val ctx3 =
                productionGroup(
                    acceleration = PowRadixOption.NO_ACCELERATION,
                    mode = ProductionMode.Mode3072
                )
            val ctxT = tinyGroup()

            assertTrue(ctxP.isCompatible(ctxP.constants))
            assertTrue(ctxP.isCompatible(ctxP2.constants))
            assertFalse(ctx3.isCompatible(ctxP))
            assertFalse(ctxT.isCompatible(ctxP.constants))
        }
    }
     */
}

fun GroupContext.showOpCountResults(where: String): String {
    val opCounts = this.getAndClearOpCounts()
    return buildString {
        appendLine("$where:")
        opCounts.toSortedMap().forEach{ (key, value) ->
            appendLine("   $key : $value")
        }
    }
}