package org.cryptobiotic.eg.workflow

import org.cryptobiotic.eg.cli.RunCreateTestManifest
import kotlin.test.Test

class CreateTestManifest {

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

