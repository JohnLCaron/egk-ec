package org.cryptobiotic.eg.decrypt

import com.github.michaelbull.result.unwrap
import org.cryptobiotic.eg.election.*
import org.cryptobiotic.eg.core.*
import org.cryptobiotic.eg.encrypt.Encryptor
import org.cryptobiotic.eg.encrypt.submit
import org.cryptobiotic.eg.input.RandomBallotProvider
import org.cryptobiotic.eg.keyceremony.KeyCeremonyTrustee
import org.cryptobiotic.eg.publish.readElectionRecord
import org.cryptobiotic.util.ErrorMessages
import org.cryptobiotic.eg.verifier.VerifyDecryption
import org.cryptobiotic.util.testOut
import kotlin.test.*

class EncryptDecrypt2Test {
    val configDir = "src/test/data/startConfigEc"
    val outputDir = "$testOut/RecoveredDecryptionTest"
    val trusteeDir = "$outputDir/private_data"

    @Test
    fun testOnePresent() {
        runEncryptDecrypt2(configDir, listOf(3), 1) // all
    }

    @Test
    fun testAllPresent() {
        runEncryptDecrypt2(configDir, listOf(1, 2, 3, 4, 5)) // all
    }

    @Test
    fun testQuotaPresent() {
        runEncryptDecrypt2(configDir, listOf(2, 3, 4)) // quota
    }

    @Test
    fun testSomePresent() {
        runEncryptDecrypt2(configDir, listOf(1, 2, 3, 4)) // between
    }
}

private val nguardians = 5
private val nballots = 10

fun runEncryptDecrypt2(
    configDir: String,
    present: List<Int>,
    quorum: Int = 3
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

    testEncryptDecrypt2Verify(
        electionRecord.group,
        electionRecord.manifest(),
        UInt256.TWO,
        ElGamalPublicKey(jointPublicKey),
        guardians,
        dTrustees,
        present
    )
}

fun testEncryptDecrypt2Verify(
    group: GroupContext,
    manifest: Manifest,
    extendedBaseHash: UInt256,
    publicKey: ElGamalPublicKey,
    guardians: Guardians,
    trustees: List<DecryptingTrustee>,
    present: List<Int>
) {
    println("nballots $nballots")
    println("manifest styles = ${manifest.ballotStyles} ncontests = ${manifest.contests.size} nselections = ${manifest.contests[0].selections.size} ")
    // val ballot = makeBallot(manifest, listOf(5, 9, 15, 17, 24, 26, 31, 39, 41, 48, 52, 53, 60, 61, 65, 69, 74))

    val available = trustees.filter { present.contains(it.xCoordinate()) }
    val encryptor = Encryptor(group, manifest, publicKey, extendedBaseHash, "device")
    val decryptor2 = CipherDecryptor(group, extendedBaseHash, publicKey, guardians, available)
    val verifier = VerifyDecryption(group, manifest, publicKey, extendedBaseHash)

    RandomBallotProvider(manifest, nballots).ballots().forEach { ballot ->
        println("plaintext ${makeVoteFor(ballot)}")

        val errsE = ErrorMessages("testEncryptDecryptVerify")
        val ciphertextBallot = encryptor.encrypt(ballot, ByteArray(0), errsE)
        if (errsE.hasErrors()) {
            println("$errsE")
            fail()
        }
        val encryptedBallot = ciphertextBallot!!.submit(EncryptedBallot.BallotState.CAST)

        val texts: MutableList<Ciphertext> = mutableListOf()
        for (contest in encryptedBallot.contests) {
            for (selection in contest.selections) {
                texts.add(Ciphertext(selection.encryptedVote))
            }
        }
        println("ntexts = ${texts.size}")

        val errs = ErrorMessages("testEncryptDecryptVerify")
        val decryptions = decryptor2.decrypt(texts, errs)
        if (errs.hasErrors()) {
            println("decryptor2.decrypt failed errors = $errs")
            fail()
        }
        assertNotNull(decryptions)

        var count = 0
        for (contest in ballot.contests) {
            for (selection in contest.selections) {
                val voteQ = selection.vote.toElementModQ(group)
                val expectedKt = publicKey powP voteQ
                val (decryption, proof) = decryptions[count++]
                val (T, tally) = decryption.decryptCiphertext(publicKey)
                assertEquals(expectedKt, T)
                val ciphertext = (decryption.cipher as Ciphertext).delegate
                val verify = proof.verifyDecryption(extendedBaseHash, publicKey.key, ciphertext, T)
                assertTrue(verify)
            }
        }
    }
}

fun makeBallot(manifest: Manifest, votefor: List<Int>, ballotId: String = "wtf", sn: Long? = 99): PlaintextBallot {
    val ballotStyleId = "ballotStyle"
    var count = 1
    val contests = mutableListOf<PlaintextBallot.Contest>()
    for (contestp in manifest.contestsForBallotStyle(ballotStyleId)!!) {
        val selections = contestp.selections.map { selectionp ->
            val voteFor = if (votefor.contains(count)) 1 else 0
            count++
            PlaintextBallot.Selection(selectionp.selectionId, selectionp.sequenceOrder, voteFor)
        }
        contests.add(PlaintextBallot.Contest(contestp.contestId, contestp.sequenceOrder, selections))
    }
    return PlaintextBallot(ballotId, ballotStyleId, contests, sn)
}

fun makeVoteFor(ballot: PlaintextBallot): List<Int> {
    val votesFor = mutableListOf<Int>()
    var count = 1
    ballot.contests.forEach { contestp ->
        contestp.selections.forEach { selectionp ->
            if (selectionp.vote > 0) votesFor.add(count)
            count++
        }
    }
    return votesFor
}
