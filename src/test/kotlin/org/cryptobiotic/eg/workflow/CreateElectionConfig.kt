package org.cryptobiotic.eg.workflow

import org.cryptobiotic.eg.cli.RunCreateElectionConfig
import org.cryptobiotic.util.testOut

import kotlin.test.Test

class CreateElectionConfig {

    @Test
    fun testCreateConfig() {
        RunCreateElectionConfig.main(
            arrayOf(
                "-manifest", "src/test/data/startManifest/manifest.json",
                "-group", "Integer4096",
                "-nguardians", "3",
                "-quorum", "3",
                "-out", "$testOut/config/startConfig",
                "-device", "device information",
            )
        )
    }

    @Test
    fun testCreateConfigEc() {
        RunCreateElectionConfig.main(
            arrayOf(
                "-manifest", "src/test/data/startManifest/manifest.json",
                "-group", "P-256",
                "-nguardians", "3",
                "-quorum", "3",
                "-out", "$testOut/config/startConfigEc",
                "-device", "device information",
            )
        )
    }

}

