package org.cryptobiotic.eg.publish.json

import org.cryptobiotic.eg.core.*
import org.cryptobiotic.util.ErrorMessages
import org.cryptobiotic.eg.core.productionGroup

import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.single
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import org.cryptobiotic.eg.core.ecgroup.EcGroupContext
import org.cryptobiotic.eg.core.intgroup.ProductionMode
import org.cryptobiotic.eg.core.intgroup.tinyGroup
import org.cryptobiotic.eg.keyceremony.PublicKeys
import kotlin.test.*

class PublicKeysTest {
    val groups = listOf(
        tinyGroup(),
        productionGroup("Integer group 3072"),
        productionGroup("Integer group 4096"),
        EcGroupContext("P-256")
    )

    @Test
    fun testRoundtrip() {
        groups.forEach { testRoundtrip(it) }
    }

    fun testRoundtrip(group: GroupContext) {
        runTest {
            checkAll(
                iterations = 33,
                Arb.string(minSize = 3),
                Arb.int(min = 1, max = 10),
                Arb.int(min = 1, max = 10),
                ) { id, xcoord, quota,  ->

                val proofs = mutableListOf<SchnorrProof>()
                repeat(quota) {
                    val kp = elGamalKeypairs(group).single()
                    val nonce = elementsModQ(group).single()
                    proofs.add(kp.schnorrProof(xcoord, it, nonce))
                }
                val publicKey = PublicKeys(id, xcoord, proofs)
                assertEquals(publicKey, publicKey.publishJson().import(group, ErrorMessages("round")))
                assertEquals(publicKey, jsonRoundTrip(publicKey.publishJson()).import(group, ErrorMessages("trip")))
            }
        }
    }
}