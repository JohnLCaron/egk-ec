package org.cryptobiotic.eg.cli

import kotlin.test.Test

class RunShowSystemTest {
    // cant call eclib from test program, but it works from command line. yikes.
    @Test
    fun testRunShowSystem() {
        RunShowSystem.main(
            arrayOf(
                "-show", "properties,java.library.path,hasVEC",
            )
        )
    }
}

