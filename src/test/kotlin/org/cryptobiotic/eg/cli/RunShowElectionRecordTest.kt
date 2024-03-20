package org.cryptobiotic.eg.cli

import org.cryptobiotic.util.testOut
import kotlin.test.Test

class RunShowElectionRecordTest {
    @Test
    fun testRunShowElectionRecord() {
        RunShowElectionRecord.main(
            arrayOf(
                "-in", "/home/stormy/tmp/testOut/egmixnet/working/public",
 //               "-in", "$testOut/workflow/allAvailableEc",
                "-show", "ballots",
            )
        )
    }
}

