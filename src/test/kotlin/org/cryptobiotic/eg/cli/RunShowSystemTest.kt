package org.cryptobiotic.eg.cli

import kotlin.test.Test

class RunShowSystemTest {
    // cant call eclib from test program, but it works from command line. yikes.
    @Test
    fun testRunShowSystem() {
        RunShowSystem.main(
            arrayOf(
                "-set", "/usr/local/lib:/usr/java/packages/lib:/usr/lib64:/lib64:/lib:/usr/lib",
                "-show", "eclib,hasVEC",
            )
        )
    }
}

