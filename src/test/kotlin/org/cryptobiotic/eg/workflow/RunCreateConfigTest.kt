package org.cryptobiotic.eg.workflow

import org.cryptobiotic.eg.cli.RunCreateElectionConfig

import kotlin.test.Test

class RunCreateConfigTest {

    @Test
    fun testCreateConfig() {
        RunCreateElectionConfig.main(
            arrayOf(
                "-manifest", "src/test/data/startManifestJson/manifest.json",
                "-group", "Integer group",
                "-nguardians", "3",
                "-quorum", "3",
                "-out",
                "testOut/config/startConfig",
                "-device",
                "device information",
            )
        )
    }

    @Test
    fun testCreateConfigEc() {
        RunCreateElectionConfig.main(
            arrayOf(
                "-manifest", "src/test/data/startManifestJson/manifest.json",
                "-group", "P-256",
                "-nguardians", "3",
                "-quorum", "3",
                "-out",
                "testOut/config/startConfigEc",
                "-device",
                "device information",
            )
        )
    }

}

