package org.cryptobiotic.eg.decrypt

import com.github.michaelbull.result.unwrap
import org.cryptobiotic.eg.election.electionExtendedHash
import org.cryptobiotic.eg.election.makeDoerreTrustee
import org.cryptobiotic.eg.core.*

import org.cryptobiotic.eg.keyceremony.KeyCeremonyTrustee
import kotlin.test.Test
import kotlin.test.assertEquals

class EncryptDecryptTest {

    @Test
    fun testEncryptDecrypt() {
        val group = productionGroup()
        runEncryptDecrypt( group,1, 1, listOf(1))
        runEncryptDecrypt( group,2, 2, listOf(1, 2))
        runEncryptDecrypt( group,3, 3, listOf(1,2,3)) // all
        runEncryptDecrypt(group,5, 5, listOf(1,2,3,4,5)) // all
        runEncryptDecrypt(group,5, 3, listOf(2,3,4)) // quota
        runEncryptDecrypt(group,5, 3, listOf(1,2,3,4)) // between
    }
}

fun runEncryptDecrypt(
    group: GroupContext,
    nguardians: Int,
    quorum: Int,
    present: List<Int>,
) {
    val trustees: List<KeyCeremonyTrustee> = List(nguardians) {
        val seq = it + 1
        KeyCeremonyTrustee(group,"guardian$seq", seq, nguardians, quorum)
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

fun encryptDecrypt(
    group: GroupContext,
    publicKey: ElGamalPublicKey,
    trustees: List<DecryptingTrusteeIF>,
    present: List<Int>
) {
    val missing = trustees.filter {!present.contains(it.xCoordinate())}.map { it.id() }
    println("present $present, missing $missing")
    val vote = 42
    val evote = vote.encrypt(publicKey, group.randomElementModQ(minimum = 1))

    val available = trustees.filter {present.contains(it.xCoordinate())}
    val lagrangeCoefficients = available.associate { it.id() to computeLagrangeCoefficient(group, it.xCoordinate(), present) }

    val shares: List<PartialDecryption> = available.map {
        val pd = it.decrypt(listOf(evote.pad))
        pd.partial[0]
    }

    val weightedProduct = group.multP(
        shares.mapIndexed { idx, it ->
            val trustee = available[idx]
            val coeff = lagrangeCoefficients[trustee.id()] ?: throw IllegalArgumentException()
            it.Mi powP coeff
        } // eq 7
    )
    val bm = evote.data / weightedProduct
    val expected = publicKey powP vote.toElementModQ(group)
    assertEquals(expected, bm)

    val dlogM: Int = publicKey.dLog(bm, 100) ?: throw RuntimeException("dlog error")
    println("TestDoerreDecrypt answer is $dlogM")
    assertEquals(42, dlogM)
}




