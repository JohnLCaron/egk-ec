package org.cryptobiotic.eg.publish.json

import org.cryptobiotic.eg.core.*
import org.cryptobiotic.util.ErrorMessages
import org.cryptobiotic.eg.core.productionGroup

import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.single
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.cryptobiotic.eg.core.ecgroup.EcGroupContext
import org.cryptobiotic.eg.core.intgroup.tinyGroup
import org.cryptobiotic.eg.keyceremony.EncryptedKeyShare
import org.cryptobiotic.eg.keyceremony.KeyShare
import org.cryptobiotic.eg.keyceremony.PublicKeys
import kotlin.test.*

class KeyCeremonyJsonTest {
    val groups = listOf(
        tinyGroup(),
        productionGroup("Integer3072"),
        productionGroup("Integer4096"),
        EcGroupContext("P-256")
    )

    @Test
    fun testPublicKeysRoundtrip() {
        groups.forEach { testPublicKeysRoundtrip(it) }
    }

    fun testPublicKeysRoundtrip(group: GroupContext) {
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

    @Test
    fun testKeyShareJsonRoundtrip() {
        groups.forEach { testKeyShareJsonRoundtrip(it) }
    }

    fun testKeyShareJsonRoundtrip(group: GroupContext) {
        runTest {
            checkAll(
                iterations = 33,
                Arb.int(min = 1, max = 10),
                Arb.string(minSize = 3),
                Arb.string(minSize = 3),
            ) { id, owner, secretShareFor,  ->
                val kshare = KeyShare(id, owner, secretShareFor, group.randomElementModQ())
                assertEquals(kshare, kshare.publishJson().import(group))
            }
        }
    }

    @Test
    fun testKeyEncryptedShareJsonRoundtrip() {
        groups.forEach { testEncryptedKeyShareJsonRoundtrip(it) }
    }

    fun testEncryptedKeyShareJsonRoundtrip(group: GroupContext) {
        runTest {
            checkAll(
                iterations = 33,
                Arb.int(min = 1, max = 10),
                Arb.string(minSize = 3),
                Arb.string(minSize = 3),
            ) { id, owner, secretShareFor,  ->
                //     val ownerXcoord: Int, // guardian i (owns the polynomial Pi) xCoordinate
                //    val polynomialOwner: String, // guardian i (owns the polynomial Pi)
                //    val secretShareFor: String, // guardian l with coordinate ℓ
                //    val encryptedCoordinate: HashedElGamalCiphertext, // El(Pi_(ℓ)), spec 2.0, eq 18
                val eshare = EncryptedKeyShare(id, owner, secretShareFor, generateHashedCiphertext(group))
                assertEquals(eshare, eshare.publishJson().import(group))
            }
        }
    }
}