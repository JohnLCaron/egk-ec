package org.cryptobiotic.eg.cli

import kotlin.test.Test
import org.cryptobiotic.eg.cli.RunCreateTestManifest
import org.cryptobiotic.util.testOut

class RunCreateTestManifestTest {

    @Test
    fun runCreateTestManifest() {
        RunCreateTestManifest.main(
            arrayOf(
                "-ncontests", "11",
                "-nselections", "3",
                "-out", "$testOut/manifest/runCreateTestManifest",
            )
        )
    }

}

