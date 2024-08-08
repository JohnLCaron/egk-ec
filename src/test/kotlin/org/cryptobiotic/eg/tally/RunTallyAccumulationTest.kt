package org.cryptobiotic.eg.tally

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.unwrap
import org.cryptobiotic.eg.election.EncryptedTally
import org.cryptobiotic.eg.cli.RunAccumulateTally
import org.cryptobiotic.eg.cli.RunTrustedTallyDecryption.Companion.readDecryptingTrustees
import org.cryptobiotic.eg.core.GroupContext
import org.cryptobiotic.eg.core.UInt256
import org.cryptobiotic.eg.core.productionGroup
import org.cryptobiotic.eg.decrypt.DecryptingTrusteeIF
import org.cryptobiotic.eg.decrypt.Guardians
import org.cryptobiotic.eg.decrypt.TallyDecryptor
import org.cryptobiotic.eg.election.DecryptedTallyOrBallot
import org.cryptobiotic.eg.election.ElectionInitialized
import org.cryptobiotic.eg.election.EncryptedBallot.BallotState
import org.cryptobiotic.eg.input.ManifestInputValidation
import org.cryptobiotic.eg.publish.makeConsumer
import org.cryptobiotic.util.ErrorMessages
import org.cryptobiotic.util.Stopwatch
import org.cryptobiotic.util.Testing
import kotlin.test.*

class RunTallyAccumulationTest {
    val group = productionGroup("P-256")

    @Test
    fun runTallyAccumulationTestJson() {
        RunAccumulateTally.main(
            arrayOf(
                "-in",
                "src/test/data/workflow/someAvailableEc",
                "-out",
                "${Testing.testOut}/tally/testRunBatchEncryptionJson",
                "--noexit"
            )
        )
    }

    @Test
    fun runTallyAccumulationTestJsonNoBallots() {
        val inputDir = "src/test/data/workflow/someAvailableEc"
        val trusteeDir = "src/test/data/workflow/someAvailableEc/private_data/trustees"
        val consumerIn = makeConsumer(inputDir)
        val initResult = consumerIn.readElectionInitialized()
        val electionInit = initResult.unwrap()
        val manifest = consumerIn.makeManifest(electionInit.config.manifestBytes)

        val accumulator = AccumulateTally(group, manifest, "name", electionInit.extendedBaseHash, electionInit.jointPublicKey)
        // nothing accumulated
        val etally: EncryptedTally = accumulator.build()
        assertNotNull(etally)

        val tally = decryptTally(group, etally, electionInit, readDecryptingTrustees(inputDir, trusteeDir))
        tally.contests.forEach {
            it.selections.forEach {
                assertEquals(0, it.tally)
            }
        }
    }

    @Test
    fun testAccumulateTally() {
        val inputDir = "src/test/data/workflow/someAvailableEc"
        val trusteeDir = "src/test/data/workflow/someAvailableEc/private_data/trustees"
        val consumerIn = makeConsumer(inputDir)
        val initResult = consumerIn.readElectionInitialized()
        val electionInit = initResult.unwrap()
        val manifest = consumerIn.makeManifest(electionInit.config.manifestBytes)
        val styleCount = ManifestInputValidation(manifest).countEncryptions()

        val accum = AccumulateTally(group, manifest, "name", electionInit.extendedBaseHash, electionInit.jointPublicKey)
        val stopwatch = Stopwatch() // start timing here
        var countEncryptions = 0

        val errs = ErrorMessages("addCastBallots")
        consumerIn.iterateAllCastBallots().forEach { eballot ->
            accum.addCastBallot(eballot, errs)
            countEncryptions += styleCount[eballot.ballotStyleId] ?: 0
        }
        assertFalse(errs.hasErrors())

        val etally: EncryptedTally = accum.build()
        println( "testAccumulateTally ${stopwatch.tookPer(countEncryptions, "encryptions")}")

        val tally = decryptTally(group, etally, electionInit, readDecryptingTrustees(inputDir, trusteeDir))

        val decryptResult = consumerIn.readDecryptionResult()
        assertFalse(decryptResult is Err)
        val actualTally = decryptResult.unwrap().decryptedTally

        assertTrue(compareTallies(tally, actualTally, true))
    }

    @Test
    fun testAccumulateTallyErrors() {
        val inputDir = "src/test/data/workflow/someAvailableEc"
        val consumerIn = makeConsumer(inputDir)
        val initResult = consumerIn.readElectionInitialized()
        val electionInit = initResult.unwrap()
        val manifest = consumerIn.makeManifest(electionInit.config.manifestBytes)

        val accum = AccumulateTally(group, manifest, "name", electionInit.extendedBaseHash, electionInit.jointPublicKey)

        val firstBallot = consumerIn.iterateAllCastBallots().iterator().next()

        // not cast
        val uballot =  firstBallot.copy( state = BallotState.UNKNOWN)
        val uerrs = ErrorMessages("addCastBallots")
        accum.addCastBallot(uballot, uerrs)
        assertTrue(uerrs.hasErrors())
        assertTrue(uerrs.toString().contains("does not have state CAST"))

        // bad electionId
        val idballot =  firstBallot.copy( electionId = UInt256.random())
        val iderrs = ErrorMessages("addCastBallots")
        accum.addCastBallot(idballot, iderrs)
        assertTrue(iderrs.hasErrors())
        assertTrue(iderrs.toString().contains("has wrong electionId"))

        // duplicate
        val errs = ErrorMessages("addCastBallots")
        accum.addCastBallot(firstBallot, errs)
        assertFalse(errs.hasErrors())
        accum.addCastBallot(firstBallot, errs)
        assertTrue(errs.hasErrors())
        assertTrue(errs.toString().contains("is duplicate"))
    }
}

fun decryptTally(
    group: GroupContext,
    encryptedTally: EncryptedTally,
    electionInit: ElectionInitialized,
    decryptingTrustees: List<DecryptingTrusteeIF>,
): DecryptedTallyOrBallot {
    val guardians = Guardians(group, electionInit.guardians)
    val decryptor = TallyDecryptor(
        group,
        electionInit.extendedBaseHash,
        electionInit.jointPublicKey,
        guardians,
        decryptingTrustees,
    )

    return decryptor.decrypt(encryptedTally, ErrorMessages(""))!!
}

fun compareTallies(
    tally1: DecryptedTallyOrBallot,
    tally2: DecryptedTallyOrBallot,
    diffOnly: Boolean,
): Boolean {
    var same = true
    println("Compare  ${tally1.id} to ${tally2.id}")
    val tally2ContestMap = tally2.contests.associateBy { it.contestId }
    tally1.contests.sortedBy { it.contestId }.forEach { contest1 ->
        if (!diffOnly) println(" Contest ${contest1.contestId}")
        val contest2 = tally2ContestMap[contest1.contestId] ?: throw IllegalStateException("Cant find contest ${contest1.contestId}")
        val tally2SelectionMap = contest2.selections.associateBy { it.selectionId }
        contest1.selections.sortedBy { it.selectionId }.forEach { selection1 ->
            val selection2 = tally2SelectionMap[selection1.selectionId] ?: throw IllegalStateException("Cant find selection ${selection1.selectionId}")
            val selSame = selection1.tally == selection2.tally
            if (!diffOnly) {
                println(
                    "  Selection ${selection1.selectionId}: ${selection1.tally} vs ${selection2.tally}" +
                            if (selSame) "" else "*********"
                )
            } else if (!selSame) {
                println("  Selection ${contest1.contestId}/${selection1.selectionId}: ${selection1.tally} != ${selection2.tally}")
                same = false
            }
        }
    }
    return same
}