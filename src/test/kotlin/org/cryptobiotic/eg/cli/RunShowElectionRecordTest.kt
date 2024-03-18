package org.cryptobiotic.eg.cli

import org.cryptobiotic.util.testOut
import kotlin.test.Test

class RunShowElectionRecordTest {
    @Test
    fun testRunShowElectionRecord() {
        RunShowElectionRecord.main(
            arrayOf(
                "-in", "$testOut/workflow/allAvailableEc",
                "-show", "ballots",
            )
        )
    }
}

