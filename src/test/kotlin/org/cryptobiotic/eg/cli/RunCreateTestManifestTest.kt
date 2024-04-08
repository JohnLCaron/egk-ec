package org.cryptobiotic.eg.cli

import kotlin.test.Test
import org.cryptobiotic.util.Testing

class RunCreateTestManifestTest {

    @Test
    fun runCreateTestManifest() {
        RunCreateTestManifest.main(
            arrayOf(
                "--nstyles", "5",
                "-ncontests", "20",
                "-nselections", "4",
                "-out", "${Testing.testOut}/manifest/runCreateTestManifest",
            )
        )
    }

}

