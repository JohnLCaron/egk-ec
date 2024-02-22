package org.cryptobiotic.eg.publish

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import kotlin.test.assertEquals
import kotlin.test.assertTrue

import org.cryptobiotic.eg.cli.ManifestBuilder.Companion.electionScopeId

class ConsumerJsonTest : FunSpec({
    val inputIg = "src/test/data/workflow/someAvailable"
    val inputEc = "src/test/data/workflow/someAvailableEc"
    context("ConsumerJson tests") {
        withData(inputIg, inputEc) { topDir ->
            testElectionRecord(topDir)
            readSpoiledBallotTallys(topDir)
            readEncryptedBallots(topDir)
            readEncryptedBallotsCast(topDir)
            readSubmittedBallotsSpoiled(topDir)
        }
    }
})

fun testElectionRecord(topdir: String) {
    val electionRecord = readElectionRecord(topdir)
    val electionInit = electionRecord.electionInit()

    if (electionInit == null) {
        println("readElectionRecord error $topdir")
    }

    val manifest = electionRecord.manifest()
    println("electionRecord.manifest.specVersion = ${manifest.specVersion}")
    assertEquals(electionScopeId, manifest.electionScopeId)
    // assertEquals(protocolVersion, manifest.specVersion)
}

fun readSpoiledBallotTallys(topdir: String) {
    val consumerIn = makeConsumer(topdir)
    var count = 0
    for (tally in consumerIn.iterateDecryptedBallots()) {
        println("$count tally = ${tally.id}")
        assertTrue(tally.id.startsWith("ballot-id"))
        count++
    }
}

fun readEncryptedBallots(topdir: String) {
    val consumerIn = makeConsumer(topdir)
    var count = 0
    for (ballot in consumerIn.iterateAllEncryptedBallots { true }) {
        println("$count ballot = ${ballot.ballotId}")
        assertTrue(ballot.ballotId.startsWith("ballot-id"))
        count++
    }
}

fun readEncryptedBallotsCast(topdir: String) {
    val consumerIn = makeConsumer(topdir)
    var count = 0
    for (ballot in consumerIn.iterateAllCastBallots()) {
        println("$count ballot = ${ballot.ballotId}")
        assertTrue(ballot.ballotId.startsWith("ballot-id"))
        count++
    }
}

fun readSubmittedBallotsSpoiled(topdir: String) {
    val consumerIn = makeConsumer(topdir)
    var count = 0
    for (ballot in consumerIn.iterateAllSpoiledBallots()) {
        println("$count ballot = ${ballot.ballotId}")
        assertTrue(ballot.ballotId.startsWith("ballot-id"))
        count++
    }
}