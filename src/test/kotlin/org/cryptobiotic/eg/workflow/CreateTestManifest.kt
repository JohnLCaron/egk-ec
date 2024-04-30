package org.cryptobiotic.eg.workflow

import org.cryptobiotic.eg.cli.RunCreateTestManifest
import org.cryptobiotic.util.Testing
import kotlin.test.Test

class CreateTestManifest {

    @Test
    fun runCreateTestManifest() {
        RunCreateTestManifest.main(
            arrayOf(
                "-ncontests", "20",
                "-nselections", "4",
                "-out", "${Testing.testOut}/manifest/runCreateTestManifest",
                "--noexit"
            )
        )
    }

}

