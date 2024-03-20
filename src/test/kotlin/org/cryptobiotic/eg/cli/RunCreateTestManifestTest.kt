package org.cryptobiotic.eg.cli

import kotlin.test.Test
import org.cryptobiotic.eg.cli.RunCreateTestManifest
import org.cryptobiotic.util.testOut

class RunCreateTestManifestTest {

    @Test
    fun runCreateTestManifest() {
        RunCreateTestManifest.main(
            arrayOf(
                "--nstyles", "5",
                "-ncontests", "20",
                "-nselections", "4",
                "-out", "$testOut/manifest/runCreateTestManifest",
            )
        )
    }

}

