package org.cryptobiotic.eg.decrypt


import com.github.michaelbull.result.unwrap
import org.cryptobiotic.eg.core.ElementModQ
import org.cryptobiotic.eg.core.GroupContext
import org.cryptobiotic.eg.core.productionGroup
import org.cryptobiotic.eg.keyceremony.KeyCeremonyTrustee
import org.cryptobiotic.eg.keyceremony.generatePolynomial

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


/** Test KeyCeremony Trustee generation and recovered decryption. */
class LagrangeTest {
    private val group = productionGroup()

    @Test
    fun testLagrangeInterpolation() {
        val w1 = computeLagrangeCoefficient(group, 1, listOf(1, 2))
        val w2 = computeLagrangeCoefficient(group, 2, listOf(1, 2))

        val polly = group.generatePolynomial("guardian1", 1, 2)
        val y1 = polly.valueAt(group, 1)
        val y2 = polly.valueAt(group, 2)

        val expected = polly.coefficients[0]
        val computed = y1 * w1 + y2 * w2
        assertEquals(expected, computed)
    }

    @Test
    fun testLagrangePolySum() {
        val w1 = computeLagrangeCoefficient(group, 1, listOf(1, 2))
        val w2 = computeLagrangeCoefficient(group, 2, listOf(1, 2))

        val polly1 = group.generatePolynomial("guardian1", 1, 2)
        val y11 = polly1.valueAt(group, 1)
        val y12 = polly1.valueAt(group, 2)

        val polly2 = group.generatePolynomial("guardian2", 2, 2)
        val y21 = polly2.valueAt(group, 1)
        val y22 = polly2.valueAt(group, 2)

        val expected = polly1.coefficients[0] + polly2.coefficients[0]
        val computed = (y11 + y21) * w1 + (y12 + y22) * w2
        assertEquals(expected, computed)
    }

    @Test
    fun testTrusteePolySum() {
        val w1 = computeLagrangeCoefficient(group, 1, listOf(1, 2))
        val w2 = computeLagrangeCoefficient(group, 2, listOf(1, 2))

        val polly1 = KeyCeremonyTrustee(group, "guardian1", 1, 2, 2)
        val y11 = polly1.valueAt(group, 1)
        val y12 = polly1.valueAt(group, 2)

        val polly2 = KeyCeremonyTrustee(group, "guardian2", 2, 2, 2)
        val y21 = polly2.valueAt(group, 1)
        val y22 = polly2.valueAt(group, 2)

        val expected = polly1.electionPrivateKey() + polly2.electionPrivateKey()
        val computed = (y11 + y21) * w1 + (y12 + y22) * w2
        assertEquals(expected, computed)
    }

    @Test
    fun testTrusteePolySum2() {
        val w1 = computeLagrangeCoefficient(group, 1, listOf(1, 2))
        val w2 = computeLagrangeCoefficient(group, 2, listOf(1, 2))

        val polly1 = KeyCeremonyTrustee(group, "guardian1", 1, 2, 2)
        val y11 = polly1.valueAt(group, 1)
        val y12 = polly1.valueAt(group, 2)

        val polly2 = KeyCeremonyTrustee(group, "guardian2", 2, 2, 2)
        val y21 = polly2.valueAt(group, 1)
        val y22 = polly2.valueAt(group, 2)

        val trustees = listOf(polly1, polly2)

        // exchange PublicKeys
        trustees.forEach { t1 ->
            trustees.forEach { t2 ->
                t1.receivePublicKeys(t2.publicKeys().unwrap())
            }
        }

        // exchange SecretKeyShares
        trustees.forEach { t1 ->
            trustees.filter { it.id != t1.id }.forEach { t2 ->
                t2.receiveEncryptedKeyShare(t1.encryptedKeyShareFor(t2.id).unwrap())
            }
        }
        assertEquals(y11 + y21, polly1.computeSecretKeyShare())
        assertEquals(y12 + y22, polly2.computeSecretKeyShare())

        val expected = polly1.electionPrivateKey() + polly2.electionPrivateKey()
        val computed = polly1.computeSecretKeyShare() * w1 + polly2.computeSecretKeyShare() * w2
        assertEquals(expected, computed)

        testKeyShares(group, trustees, listOf(1, 2))
    }

    private fun testKeyShares(group: GroupContext,
                      trustees: List<KeyCeremonyTrustee>,
                      present: List<Int>) {
        val available = trustees.filter {present.contains(it.xCoordinate())}
        val lagrangeCoefficients = available.associate { it.id to computeLagrangeCoefficient(group, it.xCoordinate, present) }
        lagrangeCoefficients.values.forEach { assertTrue( it.isValidElement()) }

        val weightedSum = group.addQ(
            trustees.map {
                assertTrue(it.computeSecretKeyShare().isValidElement())
                val coeff = lagrangeCoefficients[it.id] ?: throw IllegalArgumentException()
                it.computeSecretKeyShare() * coeff
            }
        ) // eq 7

        var weightedSum2 = group.ZERO_MOD_Q
        trustees.forEach {
            val coeff = lagrangeCoefficients[it.id] ?: throw IllegalArgumentException()
            weightedSum2 += it.computeSecretKeyShare() * coeff
        }
        assertEquals(weightedSum, weightedSum2)

        val jointPrivateKey: ElementModQ =
            trustees.map { it.electionPrivateKey() }.reduce { a, b -> a + b }

        var key2: ElementModQ = group.ZERO_MOD_Q
        trustees.forEach { key2 += it.electionPrivateKey() }
        assertEquals(jointPrivateKey, key2)

        assertEquals(jointPrivateKey, weightedSum)
    }
}