package org.cryptobiotic.eg.cli

import org.cryptobiotic.util.testOut
import kotlin.test.Test

class RunCreateConfigTest {

    @Test
    fun testCreateConfigJson() {
        RunCreateElectionConfig.main(
            arrayOf(
                "-manifest", "src/test/data/startManifest/manifest.json",
                "-group", "P-256",
                "-nguardians", "3",
                "-quorum", "3",
                "-out",
                "$testOut/config/startConfigJson",
                "-device",
                "device information",
            )
        )
    }

    @Test
    fun testCreateConfigDirectoryJson() {
        RunCreateElectionConfig.main(
            arrayOf(
                "-manifest", "src/test/data/startManifest",
                "-group", "P-256",
                "-nguardians", "3",
                "-quorum", "3",
                "-createdBy", "testCreateConfigDirectoryJson",
                "-out",
                "$testOut/config/testCreateConfigDirectoryJson",
                "-device",
                "device information",
            )
        )
    }

}

