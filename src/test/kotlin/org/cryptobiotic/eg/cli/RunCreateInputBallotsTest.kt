package org.cryptobiotic.eg.cli

import org.cryptobiotic.util.testOut
import kotlin.test.Test

class RunCreateInputBallotsTest {
    @Test
    fun testRunCreateInputBallots() {
        RunCreateInputBallots.main(
            arrayOf(
                "-manifest", "src/test/data/startConfigEc",
                "-out", "$testOut/generateInputBallots",
                "-n", "42"
            )
        )
    }
}

