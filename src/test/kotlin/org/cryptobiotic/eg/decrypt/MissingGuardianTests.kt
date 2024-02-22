package org.cryptobiotic.eg.decrypt

import com.github.michaelbull.result.unwrap
import org.cryptobiotic.eg.election.electionExtendedHash
import org.cryptobiotic.eg.election.makeDoerreTrustee
import org.cryptobiotic.eg.core.ElGamalPublicKey
import org.cryptobiotic.eg.core.ElementModP
import org.cryptobiotic.eg.core.UInt256
import org.cryptobiotic.eg.core.productionGroup
import org.cryptobiotic.eg.keyceremony.KeyCeremonyTrustee
import org.junit.jupiter.api.Test

/** Test decryption with various combinations of missing guardinas. */

class MissingGuardianTests {

    @Test
    fun testMissing() {
        testMissingGuardians(listOf(1, 2, 3))
        testMissingGuardians(listOf(1, 2, 3, 4))
        testMissingGuardians(listOf(2, 3, 4))
        testMissingGuardians(listOf(2, 4, 5))
        testMissingGuardians(listOf(2, 3, 4, 5))
        testMissingGuardians(listOf(5, 6, 7, 8, 9))
        testMissingGuardians(listOf(1, 2, 3, 4, 5, 6, 7, 8, 9))
        testMissingGuardians(listOf(2, 3, 4, 5, 6, 7, 9))
        testMissingGuardians(listOf(2, 3, 4, 5, 6, 9))
        testMissingGuardians(listOf(2, 3, 4, 5, 6, 7, 11))
        testMissingGuardians(listOf(2, 3, 4, 7, 11))
    }

    fun testMissingGuardians(present: List<Int>) {
        val group = productionGroup()
        val nguardians = present.maxOf { it }
        val quorum = present.count()

        val trustees: List<KeyCeremonyTrustee> = List(nguardians) {
            val seq = it + 1
            KeyCeremonyTrustee(group, "guardian$seq", seq, nguardians, quorum)
        }.sortedBy { it.xCoordinate }

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
        trustees.forEach { it.isComplete() }

        val jointPublicKey: ElementModP =
            trustees.map { it.guardianPublicKey() }.reduce { a, b -> a * b }
        val electionExtendedHash = electionExtendedHash(UInt256.random(), jointPublicKey)
        val dTrustees: List<DecryptingTrustee> = trustees.map { makeDoerreTrustee(it, electionExtendedHash) }

        encryptDecrypt(group, ElGamalPublicKey(jointPublicKey), dTrustees, present)
    }

}



