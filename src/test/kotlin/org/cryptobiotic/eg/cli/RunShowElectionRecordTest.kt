package org.cryptobiotic.eg.cli

import kotlin.test.Test

class RunShowElectionRecordTest {
    @Test
    fun testRunShowElectionRecord() {
        RunShowElectionRecord.main(
            arrayOf(
                "-in", "src/test/data/workflow/allAvailableEc",
                "-show", "all,constants,manifest,guardians,trustees,ballots",
            )
        )
    }

    @Test
    fun testDetails() {
        RunShowElectionRecord.main(
            arrayOf(
                "-in", "src/test/data/workflow/allAvailableEc",
                "-show", "all,constants,manifest,guardians,trustees,ballots",
                "--details"
            )
        )
    }

    @Test
    fun testOneStyle() {
        RunShowElectionRecord.main(
            arrayOf(
                "-in", "src/test/data/workflow/allAvailableEc",
                "-show", "manifest",
                "-ballotStyle", "BallotStyle5",
            )
        )
    }
}

