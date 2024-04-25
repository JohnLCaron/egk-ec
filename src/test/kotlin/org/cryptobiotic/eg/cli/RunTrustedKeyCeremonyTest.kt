package org.cryptobiotic.eg.cli

import org.cryptobiotic.util.Testing
import kotlin.test.Test

class RunTrustedKeyCeremonyTest {
    @Test
    fun testRunTrustedKeyCeremony() {
        RunTrustedKeyCeremony.main(
            arrayOf(
                "-in", "src/test/data/startConfigEc",
                "-trustees", "${Testing.testOut}/cliWorkflow/keyceremonyEc/trustees",
                "-out", "${Testing.testOut}/cliWorkflow/keyceremonyEc",
                "--noexit"
            )
        )
    }
}

