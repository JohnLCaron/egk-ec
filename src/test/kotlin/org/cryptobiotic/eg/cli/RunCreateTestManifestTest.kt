package org.cryptobiotic.eg.cli

import kotlin.test.Test
import org.cryptobiotic.eg.cli.RunCreateTestManifest

class RunCreateTestManifestTest {

    @Test
    fun runCreateTestManifest() {
        RunCreateTestManifest.main(
            arrayOf(
                "-ncontests", "20",
                "-nselections", "5",
                "-out", "testOut/manifest/runCreateTestManifest",
            )
        )
    }

}

