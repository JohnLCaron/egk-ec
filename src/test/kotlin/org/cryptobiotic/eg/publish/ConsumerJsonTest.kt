package org.cryptobiotic.eg.publish

import org.cryptobiotic.eg.cli.ManifestBuilder.Companion.electionScopeId
import kotlin.test.Test
import kotlin.test.assertEquals

class ConsumerJsonTest {
    val detail = false
    val topdirs = listOf("src/test/data/workflow/someAvailable",
                         "src/test/data/workflow/someAvailableEc",
                         "src/test/data/workflow/allAvailable",
                         "src/test/data/workflow/allAvailableEc",
                         "src/test/data/encrypt/testBallotChain",
                         "src/test/data/encrypt/testBallotNoChain",
                         "src/test/data/encrypt/testChallenged",
    )

    @Test
    fun testElectionRecord() {
        topdirs.forEach { topdir ->
            val electionRecord = readElectionRecord(topdir)
            val electionInit = electionRecord.electionInit()
            if (electionInit == null) {
                println("readElectionRecord error $topdir")
            }

            val manifest = electionRecord.manifest()
            assertEquals(electionScopeId, manifest.electionScopeId)
        }
    }

    @Test
    fun iterateEncryptedBallots() {
        topdirs.forEach { topdir ->
            val consumerIn = makeConsumer(topdir)
            val electionId = readElectionRecord(consumerIn).extendedBaseHash()

            var countEncrypted = 0
            for (ballot in consumerIn.iterateAllEncryptedBallots { true }) {
                if (detail) println(" $countEncrypted ballot = ${ballot.ballotId}")
                assertEquals(ballot.electionId, electionId)
                countEncrypted++
            }

            var countCast = 0
            for (ballot in consumerIn.iterateAllCastBallots()) {
                assertEquals(ballot.electionId, electionId)
                countCast++
            }

            var countChallenged = 0
            for (ballot in consumerIn.iterateAllChallengedBallots()) {
                assertEquals(ballot.electionId, electionId)
                countChallenged++
            }

            println("iterateEncryptedBallots cast = $countCast challenged = $countChallenged total = $countEncrypted for $topdir")
            assertEquals(countEncrypted, countCast + countChallenged)
        }
    }

    @Test
    fun iterateDecryptedBallots() {
        topdirs.forEach { topdir ->
            val consumerIn = makeConsumer(topdir)
            val electionId = readElectionRecord(consumerIn).extendedBaseHash()
            var count = 0
            for (ballot in consumerIn.iterateDecryptedBallots()) {
                assertEquals(ballot.electionId, electionId)
                count++
            }
            println("iterateDecryptedBallots count = $count for $topdir")
        }
    }

    @Test
    fun iterateEncryptedBallotsSkipChain() {
        val topdir = "src/test/data/encrypt/testBallotChain"

        val consumerIn = makeConsumer(topdir)
        val electionId = readElectionRecord(consumerIn).extendedBaseHash()
        var count = 0
        for (ballot in consumerIn.iterateAllEncryptedBallots(null)) {
            assertEquals(ballot.electionId, electionId)
            count++
        }
        println("iterateDecryptedBallots count = $count for $topdir")
    }

}