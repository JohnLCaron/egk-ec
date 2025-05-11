package org.cryptobiotic.eg.keyceremony

import com.github.michaelbull.result.unwrap
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

import org.cryptobiotic.eg.core.*
import org.cryptobiotic.eg.core.ecgroup.EcGroupContext
import org.cryptobiotic.eg.core.productionGroup

class ShareEncryptDecryptTest {
    val groups = listOf(
        productionGroup(),
        EcGroupContext("P-256")
    )

    @Test
    fun shareEncryptDecryptFuzzTest() {
        groups.forEach { shareEncryptDecryptFuzzTest(it) }
    }

    fun shareEncryptDecryptFuzzTest(group: GroupContext) {
        runTest {
            checkAll(
                propTestFastConfig,
                Arb.int(min = 1, max = 100),
                elementsModQ(group)
            ) { xcoord, pil ->
                val trustee1 = KeyCeremonyTrustee(group, "id1", xcoord, 4, 4)
                val trustee2 = KeyCeremonyTrustee(group, "id2", xcoord + 1, 4, 4)

                val publicKeys2: PublicKeys = trustee2.publicKeys().unwrap()
                val share: HashedElGamalCiphertext = trustee1.shareEncryption(pil, publicKeys2)
                val encryptedShare = EncryptedKeyShare(trustee1.xCoordinate, trustee1.id, trustee2.id, share)

                val pilbytes: ByteArray? = trustee2.shareDecryption(encryptedShare)
                assertNotNull(pilbytes)
                val decodedPil: ElementModQ = group.binaryToElementModQ(pilbytes) // Pi(ℓ)
                assertEquals(pil, decodedPil)
            }
        }
    }
}