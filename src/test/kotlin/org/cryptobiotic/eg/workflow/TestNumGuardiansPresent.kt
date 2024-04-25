package org.cryptobiotic.eg.workflow

import org.cryptobiotic.eg.election.DecryptedTallyOrBallot
import org.cryptobiotic.eg.cli.RunAccumulateTally.Companion.runAccumulateBallots
import org.cryptobiotic.eg.cli.RunBatchEncryption.Companion.batchEncryption
import org.cryptobiotic.eg.cli.RunCreateElectionConfig
import org.cryptobiotic.eg.cli.RunTrustedBallotDecryption
import org.cryptobiotic.eg.cli.RunTrustedTallyDecryption.Companion.readDecryptingTrustees
import org.cryptobiotic.eg.cli.RunTrustedTallyDecryption.Companion.runDecryptTally
import org.cryptobiotic.eg.core.*
import org.cryptobiotic.eg.decrypt.DecryptingTrusteeIF
import org.cryptobiotic.eg.publish.*
import org.cryptobiotic.util.Stats
import org.cryptobiotic.eg.verifier.Verifier
import org.cryptobiotic.util.Testing
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

/**
 * Run workflow with varying number of guardians, on the same ballots, and compare the results.
 * Also show operation Counts.
 */
class TestNumGuardiansPresent {
    val group = productionGroup()

    private val manifestJson = "src/test/data/startManifest/manifest.json"
    private val inputBallotDir = "src/test/data/fakeBallots"
    val name1 = "runWorkflowOneGuardian"
    val name2 = "runWorkflowThreeGuardian"
    val name3 = "runWorkflow5of6Guardian"
    val name4 = "runWorkflow8of10Guardian"

    @Test
    fun runWorkflows() {
        println("productionGroup (Default) = $group class = ${group.javaClass.name}")
        //runWorkflow(name1, 1, 1, listOf(1), 1)
        runWorkflow(name1, 1, 1, listOf(), 25)

        //runWorkflow(name2, 3, 3, listOf(1,2,3), 1)
        runWorkflow(name2, 3, 3, listOf(), 25)

        // runWorkflow(name3, 6, 5, listOf(1,2,4,5,6), 1)
        runWorkflow(name3, 6, 5, listOf(3), 25)

        //runWorkflow(name4, 10, 8, listOf(1,2,4,5,6,7,8,9), 1)
        runWorkflow(name4, 10, 8, listOf(3, 10), 25)

        checkTalliesAreEqual()
        checkBallotsAreEqual()
    }

    fun runWorkflow(name : String, nguardians: Int, quorum: Int, missing: List<Int>, nthreads: Int) {
        println("===========================================================")
        group.getAndClearOpCounts()
        val workingDir =  "${Testing.testOut}/workflow/$name"
        val privateDir =  "$workingDir/private_data"
        val trusteeDir =  "${privateDir}/trustees"
        val invalidDir =  "${privateDir}/invalid"

        // delete current workingDir
        makePublisher(workingDir, true)

        RunCreateElectionConfig.main(
            arrayOf(
                "-manifest", manifestJson,
                "-group", group.constants.name,
                "-nguardians", nguardians.toString(),
                "-quorum", quorum.toString(),
                "-out", workingDir,
                "-device", "device11",
                "-createdBy", name1,
                "-noexit"
            )
        )

        // key ceremony
        runFakeKeyCeremony(workingDir, workingDir, trusteeDir, nguardians, quorum, false)
        println("FakeKeyCeremony created ElectionInitialized, missing guardians = $missing")
        println(group.showOpCountResults("----------- after keyCeremony"))

        // encrypt
        batchEncryption(inputDir = workingDir, ballotDir = inputBallotDir, device = "device11",
            outputDir = workingDir, null, invalidDir = invalidDir, nthreads, name1)
        println(group.showOpCountResults("----------- after encrypt"))

        // tally
        runAccumulateBallots(workingDir, workingDir, null, "RunWorkflow", name1)
        println(group.showOpCountResults("----------- after tally"))

        val dtrustees : List<DecryptingTrusteeIF> = readDecryptingTrustees(workingDir, trusteeDir, missing.joinToString(","))
        runDecryptTally(workingDir, workingDir, dtrustees, name1)
        println(group.showOpCountResults("----------- after decrypt tally"))

        // decrypt ballots
        RunTrustedBallotDecryption.main(
            arrayOf(
                "-in", workingDir,
                "-trustees", trusteeDir,
                "-out", workingDir,
                "-challenged", "all",
                "-nthreads", nthreads.toString(),
                "--noexit"
            )
        )
        println(group.showOpCountResults("----------- after decrypt ballots"))

        // verify
        println("\nRun Verifier")
        val record = readElectionRecord(workingDir)
        val verifier = Verifier(record, nthreads)
        val stats = Stats()
        val ok = verifier.verify(stats)
        stats.show()
        println("Verify is $ok")
        assertTrue(ok)
        println(group.showOpCountResults("----------- after verify"))
    }

    fun checkTalliesAreEqual() {
        val record1 =  readElectionRecord("${Testing.testOut}/workflow/$name1")
        val record2 =  readElectionRecord("${Testing.testOut}/workflow/$name2")
        testEqualTallies(record1.decryptedTally()!!, record2.decryptedTally()!!)

        val record3 =  readElectionRecord("${Testing.testOut}/workflow/$name3")
        testEqualTallies(record1.decryptedTally()!!, record3.decryptedTally()!!)
        testEqualTallies(record2.decryptedTally()!!, record3.decryptedTally()!!)

        val record4 =  readElectionRecord("${Testing.testOut}/workflow/$name4")
        testEqualTallies(record1.decryptedTally()!!, record4.decryptedTally()!!)
        testEqualTallies(record2.decryptedTally()!!, record4.decryptedTally()!!)
    }

    fun checkBallotsAreEqual() {
        val record1 =  readElectionRecord("${Testing.testOut}/workflow/$name1")
        val record2 =  readElectionRecord("${Testing.testOut}/workflow/$name2")
        println("compare ${record1.topdir()} ${record2.topdir()}")

        val ballotsa = record1.challengedBallots().iterator()
        val ballotsb = record2.challengedBallots().iterator()
        while (ballotsa.hasNext()) {
            testEqualTallies(ballotsa.next(), ballotsb.next())
        }
    }

    fun testEqualTallies(tallya : DecryptedTallyOrBallot, tallyb : DecryptedTallyOrBallot) {
        assertEquals(tallya.id, tallyb.id)
        print(" compare ${tallya.id} ${tallyb.id}")
        tallya.contests.zip(tallyb.contests).forEach { (contesta, contestb) ->
            assertEquals(contesta.contestId, contestb.contestId)
            contesta.selections.zip(contestb.selections).forEach { (selectiona, selectionb) ->
                assertEquals(selectiona.selectionId, selectionb.selectionId)
                assertEquals(selectiona.tally, selectionb.tally)
                print(" OK")
            }
        }
        println()
    }
}