package org.cryptobiotic.eg.decrypt

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.unwrap
import org.cryptobiotic.eg.election.EncryptedBallot
import org.cryptobiotic.eg.election.makeContestData
import org.cryptobiotic.eg.core.*
import org.cryptobiotic.eg.encrypt.Encryptor
import org.cryptobiotic.eg.encrypt.submit
import org.cryptobiotic.eg.input.RandomBallotProvider
import org.cryptobiotic.eg.publish.readElectionRecord
import org.cryptobiotic.util.ErrorMessages
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class DecryptionWithNonceTest {
    val input = "src/test/data/workflow/allAvailableEc"
    private val nballots = 20

    /** test DecryptionWithPrimaryNonce: encrypt ballot, decrypt with master nonce, check match. */
    @Test
    fun testDecryptionWithPrimaryNonce() {
        val electionRecord = readElectionRecord(input)
        val init = electionRecord.electionInit()!!
        val encryptor = Encryptor(electionRecord.group, electionRecord.manifest(), init.jointPublicKey, init.extendedBaseHash, "device")

        RandomBallotProvider(electionRecord.manifest(), nballots).ballots().forEach { ballot ->
            val primaryNonce = UInt256.random()
            val ciphertextBallot = encryptor.encrypt(ballot, ByteArray(0), ErrorMessages("testDecryptionWithPrimaryNonce"), primaryNonce, 0)
            assertEquals(primaryNonce, ciphertextBallot!!.ballotNonce)
            val encryptedBallot = ciphertextBallot.submit(EncryptedBallot.BallotState.CAST)

            // decrypt with primary nonce
            val decryptionWithPrimaryNonce = DecryptBallotWithNonce(electionRecord.group, init.jointPublicKey, init.extendedBaseHash)
            val decryptedBallotResult = with (decryptionWithPrimaryNonce) { encryptedBallot.decrypt(primaryNonce) }
            assertFalse(decryptedBallotResult is Err, "decryptionWithPrimaryNonce error on ballot ${ballot.ballotId} errors = $decryptedBallotResult")
            val decryptedBallot = decryptedBallotResult.unwrap()

            // all non zero votes match
            ballot.contests.forEach { orgContest ->
                val contest2 = decryptedBallot.contests.find { it.contestId == orgContest.contestId }
                assertNotNull(contest2)
                orgContest.selections.forEach { selection1 ->
                    val selection2 = contest2.selections.find { it.selectionId == selection1.selectionId }
                    assertNotNull(selection2)
                    assertEquals(selection1, selection2)
                }
            }

            // all votes match
            decryptedBallot.contests.forEach { contest2 ->
                val contest1 = decryptedBallot.contests.find { it.contestId == contest2.contestId }
                if (contest1 == null) {
                    contest2.selections.forEach { assertEquals(it.vote, 0) }
                } else {
                    contest2.selections.forEach { selection2 ->
                        val selection1 = contest1.selections.find { it.selectionId == selection2.selectionId }
                        if (selection1 == null) {
                            assertEquals(selection2.vote, 0)
                        } else {
                            assertEquals(selection1, selection2)
                        }
                    }
                }
            }
        }
    }

    /** test DecryptionWithPrimaryNonce: encrypt ballot, decrypt with master nonce, check match. */
    @Test
    fun testDecryptionOfContestData() {
        val electionRecord = readElectionRecord(input)
        val init = electionRecord.electionInit()!!
        val encryptor = Encryptor(electionRecord.group, electionRecord.manifest(), init.jointPublicKey, init.extendedBaseHash, "device")

        val nb = 100
        RandomBallotProvider(electionRecord.manifest(), nb).withWriteIns().ballots().forEach { ballot ->
            val primaryNonce = UInt256.random()
            val ciphertextBallot = encryptor.encrypt(ballot, ByteArray(0), ErrorMessages("testDecryptionOfContestData"), primaryNonce, 0)
            val encryptedBallot = ciphertextBallot!!.submit(EncryptedBallot.BallotState.CAST)

            // decrypt with primary nonce
            val decryptionWithPrimaryNonce = DecryptBallotWithNonce(electionRecord.group, init.jointPublicKey, init.extendedBaseHash)
            val decryptedBallotResult = with (decryptionWithPrimaryNonce) { encryptedBallot.decrypt(primaryNonce) }
            assertFalse(decryptedBallotResult is Err, "decryptionWithPrimaryNonce error on ballot ${ballot.ballotId} errors = $decryptedBallotResult")
            val decryptedBallot = decryptedBallotResult.unwrap()

            // contestData matches
            ballot.contests.forEach { orgContest ->
                val mcontest = electionRecord.manifest().contests.find { it.contestId == orgContest.contestId }!!
                val (orgContestData, _) = makeContestData(mcontest.contestSelectionLimit, mcontest.optionSelectionLimit, orgContest.selections, orgContest.writeIns)

                val dcontest = decryptedBallot.contests.find { it.contestId == orgContest.contestId }!!
                assertEquals(dcontest.writeIns, orgContestData.writeIns)

                // all votes match
                decryptedBallot.contests.forEach { contest2 ->
                    val contest1 = decryptedBallot.contests.find { it.contestId == contest2.contestId }
                    if (contest1 == null) {
                        contest2.selections.forEach { assertEquals(it.vote, 0) }
                    } else {
                        contest2.selections.forEach { selection2 ->
                            val selection1 = contest1.selections.find { it.selectionId == selection2.selectionId }
                            if (selection1 == null) {
                                assertEquals(selection2.vote, 0)
                            } else {
                                assertEquals(selection1, selection2)
                            }
                        }
                    }
                }
            }
        }
    }
}