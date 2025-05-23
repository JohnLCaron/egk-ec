package org.cryptobiotic.eg.encrypt

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

import org.cryptobiotic.eg.core.*
import org.cryptobiotic.eg.publish.readElectionRecord
import org.cryptobiotic.util.ErrorMessages

class EncryptorTest {
    val input = "src/test/data/workflow/allAvailableEc"

    // sanity check that encryption doesnt barf
    @Test
    fun testEncryption() {
        runTest {
            val electionRecord = readElectionRecord(input)
            val electionInit = electionRecord.electionInit()!!
            val ballotStyle = electionRecord.manifest().ballotStyles[0].ballotStyleId
            val ballot = makeBallot(electionRecord.manifest(), ballotStyle, 3, 0)

            val encryptor = Encryptor(
                electionRecord.group,
                electionRecord.manifest(),
                electionInit.jointPublicKey,
                electionInit.extendedBaseHash,
                "device"
            )
            val result = encryptor.encrypt(ballot, ByteArray(0), ErrorMessages("testEncryption"))!!

            var first = true
            println("result = ${result.confirmationCode} nonce ${result.ballotNonce}")
            for (contest in result.contests) {
                // println(" contest ${contest.contestId} = ${contest.cryptoHash} nonce ${contest.contestNonce}")
                for (selection in contest.selections) {
                    // println("  selection ${selection.selectionId} = ${selection.cryptoHash} nonce ${selection.selectionNonce}")
                    if (first) println("\n*****first ${selection}\n")
                    first = false
                }
            }
        }
    }

    // test that if you pass in the same ballot nonce, you get the same encryption
    @Test
    fun testEncryptionWithBallotNonce() {
        runTest {
            val electionRecord = readElectionRecord(input)
            val electionInit = electionRecord.electionInit()!!
            val ballotStyle = electionRecord.manifest().ballotStyles[0].ballotStyleId
            val ballot = makeBallot(electionRecord.manifest(), ballotStyle, 3, 0)

            val encryptor = Encryptor(
                electionRecord.group,
                electionRecord.manifest(),
                electionInit.jointPublicKey,
                electionInit.extendedBaseHash,
                "device"
            )
            val nonce1 = UInt256.random()
            val result1 =
                encryptor.encrypt(ballot, ByteArray(0), ErrorMessages("testEncryptionWithBallotNonce1"), nonce1, 0)!!
            val result2 =
                encryptor.encrypt(ballot, ByteArray(0), ErrorMessages("testEncryptionWithBallotNonce2"), nonce1, 0)!!

            result1.contests.forEachIndexed { index, contest1 ->
                val contest2 = result2.contests[index]
                contest1.selections.forEachIndexed { sindex, selection1 ->
                    val selection2 = contest2.selections[sindex]
                    assertEquals(selection1, selection2)
                }
                assertEquals(contest1, contest2)
            }
            // data class equals doesnt compare bytearray.contentEquals()
            assertEquals(result1.confirmationCode, result2.confirmationCode)
            assertEquals(result1.timestamp, result2.timestamp)
            assertEquals(result1.ballotNonce, result2.ballotNonce)
        }
    }

    // test sn encryption
    @Test
    fun testEncryptionWithSN() {
        runTest {
            val electionRecord = readElectionRecord(input)
            val electionInit = electionRecord.electionInit()!!
            val ballotStyle = electionRecord.manifest().ballotStyles[0].ballotStyleId
            val ballot = makeBallot(electionRecord.manifest(), ballotStyle, 3, 0)
            val plaintextSn: Int? = ballot.sn?.toInt()
            val key = electionInit.jointPublicKey

            val encryptor = Encryptor(electionRecord.group, electionRecord.manifest(), key, electionInit.extendedBaseHash, "device")
            val nonce1 = UInt256.random()
            val result1 = encryptor.encrypt(
                ballot,
                ByteArray(0),
                ErrorMessages("testEncryptionWithBallotNonce1"),
                nonce1,
                0,
            )!!
            val result2 = encryptor.encrypt(
                ballot,
                ByteArray(0),
                ErrorMessages("testEncryptionWithBallotNonce2"),
                nonce1,
                0,
            )!!

            result1.contests.forEachIndexed { index, contest1 ->
                val contest2 = result2.contests[index]
                contest1.selections.forEachIndexed { sindex, selection1 ->
                    val selection2 = contest2.selections[sindex]
                    assertEquals(selection1, selection2)
                }
                assertEquals(contest1, contest2)
            }
            // data class equals doesnt compare bytearray.contentEquals()
            assertEquals(result1.confirmationCode, result2.confirmationCode)
            assertEquals(result1.timestamp, result2.timestamp)
            assertEquals(result1.ballotNonce, result2.ballotNonce)
            assertEquals(result1.encryptedSN, result2.encryptedSN)

            // test decrypt with nonce
            val snNonce = hashFunction(electionInit.extendedBaseHash.bytes, 0x110.toByte(), nonce1).toElementModQ(electionRecord.group)
            val dvalue: Int? = result1.encryptedSN!!.decryptWithNonce(key, snNonce)
            assertNotNull(dvalue)
            assertEquals(plaintextSn, dvalue)
        }
    }
}