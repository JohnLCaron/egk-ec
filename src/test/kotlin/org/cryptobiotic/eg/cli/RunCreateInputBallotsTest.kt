package org.cryptobiotic.eg.cli

import org.cryptobiotic.util.Testing
import kotlin.test.Test

class RunCreateInputBallotsTest {
    @Test
    fun testRunCreateInputBallots() {
        RunCreateInputBallots.main(
            arrayOf(
                "-manifest", "src/test/data/startConfigEc",
                "-out", "${Testing.testOut}/generateInputBallots",
                "-n", "42"
            )
        )
    }
}

