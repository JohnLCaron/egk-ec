package org.cryptobiotic.eg.keyceremony

import org.cryptobiotic.eg.cli.RunTrustedKeyCeremony
import org.cryptobiotic.eg.cli.RunVerifier
import org.cryptobiotic.eg.core.productionGroup
import kotlin.test.Test

class RunKeyCeremonyTest {

    @Test
    fun testKeyCeremonyJson() {
        RunTrustedKeyCeremony.main(
            arrayOf(
                "-in", "src/commonTest/data/startConfigJson",
                "-trustees", "testOut/keyceremony/testKeyCeremonyJson/private_data/trustees",
                "-out", "testOut/keyceremony/testKeyCeremonyJson",
            )
        )
        RunVerifier.runVerifier("testOut/keyceremony/testKeyCeremonyJson", 1, true)
    }

}

