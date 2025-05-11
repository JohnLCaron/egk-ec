package org.cryptobiotic.eg.publish.json

import com.github.michaelbull.result.Ok
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

import org.cryptobiotic.eg.core.*
import org.cryptobiotic.eg.core.ecgroup.EcGroupContext
import org.cryptobiotic.eg.core.intgroup.ProductionMode
import org.cryptobiotic.eg.core.productionGroup
import org.cryptobiotic.eg.core.intgroup.tinyGroup

class SchnorrProofTest {
    val groups = listOf(
        productionGroup("Integer3072"),
        productionGroup("Integer4096"),
        EcGroupContext("P-256"),
        tinyGroup(),
    )

    @Test
    fun testRoundtrip() {
        groups.forEach { testRoundtrip(it) }
    }

    fun testRoundtrip(group: GroupContext) {
        runTest {
            checkAll(
                iterations = 33,
                elGamalKeypairs(group),
                elementsModQ(group),
                Arb.int(min = 1, max = 10),
                Arb.int(min = 1, max = 10),
            ) { kp, nonce, i, j ->
                val goodProof = kp.schnorrProof(i, j, nonce)
                assertTrue(goodProof.validate(i, j) is Ok)

                assertEquals(goodProof, goodProof.publishJson().import(group))
                assertEquals(goodProof, jsonRoundTrip(goodProof.publishJson()).import(group))
            }
        }
    }
}