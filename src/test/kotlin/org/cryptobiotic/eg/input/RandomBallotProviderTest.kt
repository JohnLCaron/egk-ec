package org.cryptobiotic.eg.input

import org.cryptobiotic.eg.publish.readElectionRecord
import org.junit.jupiter.api.Assertions.assertEquals
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith

class RandomBallotProviderTest {

    @Test
    fun testBadStyle() {
        val inputDir = "src/test/data/workflow/allAvailableEc"

        val electionRecord = readElectionRecord(inputDir)

        val exception = assertFailsWith<RuntimeException>(
            block = { RandomBallotProvider(electionRecord.manifest(), 1).ballots("badStyleId") }
        )
        assertContains(
            exception.message!!,
            "BallotStyle 'badStyleId' not found in manifest ballotStyles=",
        )
    }

}