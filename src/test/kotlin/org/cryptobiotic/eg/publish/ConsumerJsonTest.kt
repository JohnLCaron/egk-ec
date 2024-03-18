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
            readChallengedBallots(topDir)
            readEncryptedBallots(topDir)
            readEncryptedBallotsCast(topDir)
            readAllChallengedBallots(topDir)
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

fun readChallengedBallots(topdir: String) {
    val consumerIn = makeConsumer(topdir)
    var count = 0
    for (ballot in consumerIn.iterateDecryptedBallots()) {
        println("$count tally = ${ballot.id}")
        assertTrue(ballot.id.startsWith("ballot-id"))
        count++
    }
}

fun readEncryptedBallots(topdir: String) {
    val consumerIn = makeConsumer(topdir)
    var count = 0
    for (ballot in consumerIn.iterateAllEncryptedBallots { true }) {
        println("$count ballot = ${ballot.ballotId}")
        assertTrue(ballot.ballotId.contains("id-"))
        count++
    }
}

fun readEncryptedBallotsCast(topdir: String) {
    val consumerIn = makeConsumer(topdir)
    var count = 0
    for (ballot in consumerIn.iterateAllCastBallots()) {
        println("$count ballot = ${ballot.ballotId}")
        assertTrue(ballot.ballotId.contains("id-"))
        count++
    }
}

fun readAllChallengedBallots(topdir: String) {
    val consumerIn = makeConsumer(topdir)
    var count = 0
    for (ballot in consumerIn.iterateAllChallengedBallots()) {
        println("$count ballot = ${ballot.ballotId}")
        assertTrue(ballot.ballotId.contains("id-"))
        count++
    }
}