package org.cryptobiotic.eg.publish.json

import org.cryptobiotic.eg.core.*
import org.cryptobiotic.eg.core.productionGroup

import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import org.cryptobiotic.eg.core.ecgroup.EcGroupContext
import org.cryptobiotic.eg.core.intgroup.ProductionMode
import org.cryptobiotic.eg.core.intgroup.tinyGroup
import kotlin.test.*

class ElGamalTest {
    val groups = listOf(
        tinyGroup(),
        productionGroup("Integer group 3072"),
        productionGroup("Integer group 4096"),
        EcGroupContext("P-256")
    )

    @Test
    fun importExportForElGamal() {
        groups.forEach { importExportForElGamal(it) }
    }

    fun importExportForElGamal(group: GroupContext) {
        runTest {
            checkAll(
                iterations = 33,
                elGamalKeypairs(group),
                Arb.int(0..100),
                elementsModQNoZero(group)) { kp, v, r ->
                    // first, we'll check that the keys serialize down to basic hex-strings
                    // rather than any fancier structure
                    assertEquals(
                        kp.publicKey.key,
                        jsonRoundTripWithStringPrimitive(kp.publicKey.key.publishJson()).import(group)
                    )

                    assertEquals(
                        kp.secretKey.key,
                        jsonRoundTripWithStringPrimitive(kp.secretKey.key.publishJson()).import(group)
                    )

                    val ciphertext = v.encrypt(keypair = kp, nonce = r)
                    val ciphertextAgain = jsonRoundTrip(ciphertext.publishJson()).import(group)
                    assertEquals(ciphertext, ciphertextAgain)
                }
        }
    }
}