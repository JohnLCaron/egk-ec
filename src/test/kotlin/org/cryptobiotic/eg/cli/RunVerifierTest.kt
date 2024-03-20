package org.cryptobiotic.eg.cli

import org.cryptobiotic.util.testOut
import kotlin.test.Test

class RunVerifierTest {
    @Test
    fun testRunVerifier() {
        RunVerifier.main(
            arrayOf(
                "-in", "/home/stormy/tmp/testOut/egmixnet/working/public",
            )
        )
    }
}

