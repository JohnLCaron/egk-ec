package org.cryptobiotic.eg.cli

import org.cryptobiotic.util.testOut
import kotlin.test.Test

class RunTrustedKeyCeremonyTest {
    @Test
    fun testRunTrustedKeyCeremony() {
        RunTrustedKeyCeremony.main(
            arrayOf(
                "-in", "src/test/data/startConfigEc",
                "-trustees", "$testOut/cliWorkflow/keyceremonyEc/trustees",
                "-out", "$testOut/cliWorkflow/keyceremonyEc",
            )
        )
    }
}

