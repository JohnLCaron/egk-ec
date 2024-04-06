package org.cryptobiotic.eg.cli

import org.cryptobiotic.util.testOutMixnet
import kotlin.test.Test

class RunVerifierTest {
    @Test
    fun testRunVerifier() {
        RunVerifier.main(
            arrayOf(
                "-in", "$testOutMixnet/public",
            )
        )
    }
}

