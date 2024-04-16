package org.cryptobiotic.eg.encrypt

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.unwrap
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertNotNull

import org.cryptobiotic.eg.election.*
import org.cryptobiotic.eg.input.BallotInputValidation
import org.cryptobiotic.eg.input.RandomBallotProvider
import org.cryptobiotic.eg.publish.makePublisher
import org.cryptobiotic.eg.publish.readElectionRecord

import org.cryptobiotic.util.ErrorMessages
import org.cryptobiotic.util.Testing
import kotlin.test.assertTrue

// Test challenging ballots when using AddEncryptedBallot.
class AddEncryptedChallengedTest {
    val input = "src/test/data/workflow/allAvailableEc"
    val outputDirTop = "${Testing.testOut}/encrypt/addEncryptedBallot/ChallengedTest"

    val nballots = 30

    @Test
    fun testChallenged() {
        testChallenged("$outputDirTop/unchained", false)
        testChallenged("$outputDirTop/chained", true)
    }

    fun testChallenged(outputDir: String, chained: Boolean) {
        var allOk = true
        var count = 0
        var countChallenge = 0
        try {
            val device = "deviceM"

            val electionRecord = readElectionRecord(input)
            val electionInit = if (chained) {
                val configWithChaining = electionRecord.config().copy(chainConfirmationCodes = true)
                electionRecord.electionInit()!!.copy(config = configWithChaining)
            } else {
                electionRecord.electionInit()!!
            }
            val publisher = makePublisher(outputDir, true)
            publisher.writeElectionInitialized(electionInit)

            println("makePublisher makeNewOutput=$outputDir")
            repeat(3) {
                val encryptor = AddEncryptedBallot(
                    electionRecord.manifest(),
                    BallotInputValidation(electionRecord.manifest()),
                    electionInit.config.chainConfirmationCodes,
                    electionInit.config.configBaux0,
                    electionInit.jointPublicKey,
                    electionInit.extendedBaseHash,
                    device,
                    outputDir,
                    "${outputDir}/invalidDir",
                )
                val ballotProvider = RandomBallotProvider(electionRecord.manifest())

                repeat(nballots) {
                    count++
                    val ballot = ballotProvider.makeBallot()
                    val encrypted = encryptor.encrypt(ballot, ErrorMessages("testMultipleCalls"))
                    assertNotNull(encrypted)

                    val random = Random.nextInt(10)
                    val challengeThisOne = random < 2 // challenge 2 in 10
                    if (challengeThisOne) {
                        println("  challenged ${encrypted.ballotId}")
                        countChallenge++
                        val dresult = encryptor.challengeAndDecrypt(encrypted.confirmationCode)
                        assertTrue( dresult is Ok)
                        val dpballot = dresult.unwrap()
                        val dcompare = compare(ballot, dpballot)
                        if (dcompare is Err) {
                            println("Error $dresult")
                            allOk = false
                        }
                    } else {
                        encryptor.cast(encrypted.confirmationCode)
                    }
                }
                encryptor.close()
            }
            assertTrue(allOk)
            checkOutput(outputDir, 3 * nballots, chained)
        } catch (t: Throwable) {
            t.printStackTrace()
        }
        println(" challenged $countChallenge out of $count")
    }
}

fun compare(pballot: PlaintextBallot, dpballot: PlaintextBallot): Result<Boolean, String> {
    val errs = mutableListOf<String>()
    if (pballot.contests.size != dpballot.contests.size) {
        errs.add("Number of contests differ ${pballot.contests.size} != ${dpballot.contests.size}")
    }
    val pcontests = pballot.contests.associateBy { it.contestId }
    dpballot.contests.forEach { contest ->
        val pcontest = pcontests[contest.contestId]
        if (pcontest == null) {
            errs.add("Cant find ${contest.contestId}")
        } else {
            if (pcontest.selections.size != contest.selections.size) {
                errs.add("Number of selections for ${contest.contestId} differ ${pcontest.selections.size} != ${contest.selections}")
            }

            val pselections = pcontest.selections.associateBy { it.selectionId }
            contest.selections.forEach { selection ->
                val pselection = pselections[selection.selectionId]
                if (pselection == null) {
                    errs.add("Cant find ${contest.contestId}/${selection.selectionId}")
                } else {
                    if (pselection.vote != selection.vote)
                        errs.add(" Error ${contest.contestId}/${selection.selectionId} ${pselection.vote} != ${selection.vote}")
                }
            }
        }
    }
    return if (errs.isEmpty()) Ok(true) else Err(errs.joinToString(","))
}