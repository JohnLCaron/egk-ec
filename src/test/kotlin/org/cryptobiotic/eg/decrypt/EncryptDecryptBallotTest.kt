package org.cryptobiotic.eg.decrypt

import com.github.michaelbull.result.unwrap
import org.cryptobiotic.eg.election.*
import org.cryptobiotic.eg.core.*
import org.cryptobiotic.eg.encrypt.Encryptor
import org.cryptobiotic.eg.encrypt.submit
import org.cryptobiotic.eg.input.RandomBallotProvider
import org.cryptobiotic.eg.keyceremony.KeyCeremonyTrustee
import org.cryptobiotic.eg.publish.makePublisher
import org.cryptobiotic.eg.publish.readElectionRecord
import org.cryptobiotic.util.ErrorMessages
import org.cryptobiotic.util.Stats
import org.cryptobiotic.eg.verifier.VerifyDecryption
import org.cryptobiotic.util.Testing
import kotlin.math.roundToInt
import kotlin.test.*

class EncryptDecryptBallotTest {
    val configDir = "src/test/data/startConfigEc"
    val outputDir = "${Testing.testOut}/RecoveredDecryptionTest"
    val trusteeDir = "$outputDir/private_data"

    @Test
    fun testAllPresent() {
        runEncryptDecryptBallot(configDir, outputDir, trusteeDir, listOf(1, 2, 3, 4, 5)) // all
    }

    @Test
    fun testQuotaPresent() {
        runEncryptDecryptBallot(configDir, outputDir, trusteeDir, listOf(2, 3, 4)) // quota
    }

    @Test
    fun testSomePresent() {
        runEncryptDecryptBallot(configDir, outputDir, trusteeDir, listOf(1, 2, 3, 4)) // between
    }
}

private val writeout = false
private val nguardians = 4
private val quorum = 3
private val nballots = 3
private val debug = true

fun runEncryptDecryptBallot(
    configDir: String,
    outputDir: String,
    trusteeDir: String,
    present: List<Int>,
) {
    val electionRecord = readElectionRecord(configDir)
    val config = electionRecord.config()

    //// simulate key ceremony
    val trustees: List<KeyCeremonyTrustee> = List(nguardians) {
        val seq = it + 1
        KeyCeremonyTrustee(electionRecord.group, "guardian$seq", seq, nguardians, quorum)
    }.sortedBy { it.xCoordinate }
    trustees.forEach { t1 ->
        trustees.forEach { t2 ->
            t1.receivePublicKeys(t2.publicKeys().unwrap())
        }
    }
    trustees.forEach { t1 ->
        trustees.filter { it.id != t1.id }.forEach { t2 ->
            t2.receiveEncryptedKeyShare(t1.encryptedKeyShareFor(t2.id).unwrap())
        }
    }
    val guardianList: List<Guardian> = trustees.map { makeGuardian(it) }
    val guardians = Guardians(electionRecord.group, guardianList)
    val jointPublicKey: ElementModP =
        trustees.map { it.guardianPublicKey() }.reduce { a, b -> a * b }

    val extendedBaseHash = electionExtendedHash(config.electionBaseHash, jointPublicKey)
    val dTrustees: List<DecryptingTrustee> = trustees.map { makeDoerreTrustee(it, extendedBaseHash) }

    testEncryptDecryptVerify(
        electionRecord.group,
        electionRecord.manifest(),
        UInt256.TWO,
        ElGamalPublicKey(jointPublicKey),
        guardians,
        dTrustees,
        present
    )

    testEncryptDecryptCompare(
        electionRecord.group,
        electionRecord.manifest(),
        UInt256.TWO,
        ElGamalPublicKey(jointPublicKey),
        guardians,
        dTrustees,
        present
    )

    //////////////////////////////////////////////////////////
    if (writeout) {
        val init = ElectionInitialized(
            config,
            ElGamalPublicKey(jointPublicKey),
            extendedBaseHash,
            guardianList,
        )

        val publisher = makePublisher(outputDir)
        publisher.writeElectionInitialized(init)

        val trusteePublisher = makePublisher(trusteeDir)
        trustees.forEach { trusteePublisher.writeTrustee(trusteeDir, it) }
    }
}

fun testEncryptDecryptVerify(
    group: GroupContext,
    manifest: Manifest,
    extendedBaseHash: UInt256,
    publicKey: ElGamalPublicKey,
    guardians: Guardians,
    trustees: List<DecryptingTrustee>,
    present: List<Int>
) {
    println("present $present")

    val available = trustees.filter { present.contains(it.xCoordinate()) }
    val encryptor = Encryptor(group, manifest, publicKey, extendedBaseHash, "device")
    val decryptor = BallotDecryptor(group, extendedBaseHash, publicKey, guardians, available)
    val verifier = VerifyDecryption(group, manifest, publicKey, extendedBaseHash)

    var encryptTime = 0L
    var decryptTime = 0L

    // ballot matches decryptedBallot
    RandomBallotProvider(manifest, nballots).withWriteIns().ballots().forEach { ballot ->
        val startEncrypt = getSystemTimeInMillis()
        val ciphertextBallot = encryptor.encrypt(ballot, ByteArray(0), ErrorMessages("testEncryptDecryptVerify"))
        val encryptedBallot = ciphertextBallot!!.submit(EncryptedBallot.BallotState.CAST)
        encryptTime += getSystemTimeInMillis() - startEncrypt

        val startDecrypt = getSystemTimeInMillis()
        val errs = ErrorMessages("testEncryptDecryptVerify")
        val decryptedBallot = decryptor.decrypt(encryptedBallot, errs)
        if (decryptedBallot == null) {
            println("testEncryptDecryptVerify failed errors = $errs")
            return
        }
        decryptTime += getSystemTimeInMillis() - startDecrypt

        ballot.contests.forEach { orgContest ->
            val mcontest = manifest.contests.find { it.contestId == orgContest.contestId }!!
            val orgContestData =
                makeContestData(mcontest.contestSelectionLimit, orgContest.selections, orgContest.writeIns)

            val dcontest = decryptedBallot.contests.find { it.contestId == orgContest.contestId }
            assertNotNull(dcontest)
            assertNotNull(dcontest.decryptedContestData)
            assertEquals(dcontest.decryptedContestData!!.contestData.writeIns, orgContestData.writeIns)
            println("   ${orgContest.contestId} writeins = ${orgContestData.writeIns}")

            val status = dcontest.decryptedContestData!!.contestData.status
            val overvotes = dcontest.decryptedContestData!!.contestData.overvotes
            if (debug) println(" status = $status overvotes = $overvotes")

            // check if selection votes match
            orgContest.selections.forEach { selection ->
                val dselection = dcontest.selections.find { it.selectionId == selection.selectionId }

                if (status == ContestDataStatus.over_vote) {
                    // check if overvote was correctly recorded
                    val hasWriteIn = overvotes.find { it == selection.sequenceOrder } != null
                    assertEquals(selection.vote == 1, hasWriteIn)

                } else {
                    // check if selection votes match
                    assertNotNull(dselection)
                    assertEquals(selection.vote, dselection.tally)
                }
            }
        }

        verifier.verify(decryptedBallot, true, errs.nested("verify"), Stats())
        println(errs)
        assertFalse(errs.hasErrors())
    }

    val encryptPerBallot = (encryptTime.toDouble() / nballots).roundToInt()
    val decryptPerBallot = (decryptTime.toDouble() / nballots).roundToInt()
    println("testDecryptor for $nballots ballots took $encryptPerBallot encrypt, $decryptPerBallot decrypt msecs/ballot")
}

fun testEncryptDecryptCompare(
    group: GroupContext,
    manifest: Manifest,
    extendedBaseHash: UInt256,
    publicKey: ElGamalPublicKey,
    guardians: Guardians,
    trustees: List<DecryptingTrustee>,
    present: List<Int>
) {
    println("present $present")

    val available = trustees.filter { present.contains(it.xCoordinate()) }
    val encryptor = Encryptor(group, manifest, publicKey, extendedBaseHash, "device")
    val decryptor = BallotDecryptor(group, extendedBaseHash, publicKey, guardians, available)
    val verifier = VerifyDecryption(group, manifest, publicKey, extendedBaseHash)

    RandomBallotProvider(manifest, nballots).ballots().forEach { ballot ->
        val ciphertextBallot = encryptor.encrypt(ballot, ByteArray(0), ErrorMessages("testEncryptDecryptVerify"))
        val encryptedBallot = ciphertextBallot!!.submit(EncryptedBallot.BallotState.CAST)

        val errs = ErrorMessages("testEncryptDecryptVerify")
        val decryptedBallot = decryptor.decrypt(encryptedBallot, errs)
        if (decryptedBallot == null) {
            println("testEncryptDecryptVerify failed errors = $errs")
            return
        }

        decryptedBallot.compare(ballot, errs)
        if (errs.hasErrors()) {
            println("decryptedBallot.compare failed errors = $errs")
            return
        }

        verifier.verify(decryptedBallot, true, errs.nested("verify"), Stats())
        assertFalse(errs.hasErrors())
    }
}