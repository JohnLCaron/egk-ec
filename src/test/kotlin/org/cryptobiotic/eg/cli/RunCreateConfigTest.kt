package org.cryptobiotic.eg.cli

import org.cryptobiotic.util.Testing
import java.nio.file.Files
import kotlin.test.Test

class RunCreateConfigTest {

    @Test
    fun showTestDirs() {
        println("topdir = ${Testing.tmpdir}")
        //println("testOut = $testOut")
        //println("testOutMixnet = $testOutMixnet")
    }

    @Test
    fun getTTT() {
        val tmpdir = Files.createTempDirectory("tmpDirPrefix").toFile().absolutePath
        val tmpDirsLocation = System.getProperty("java.io.tmpdir")
        require(tmpdir.startsWith(tmpDirsLocation))
        println("tmpdir = $tmpdir")
        println("tmpDirsLocation = $tmpDirsLocation")
    }

    @Test
    fun testCreateConfigJson() {
        RunCreateElectionConfig.main(
            arrayOf(
                "-manifest", "src/test/data/startManifest/manifest.json",
                "-group", "P-256",
                "-nguardians", "3",
                "-quorum", "3",
                "-out",
                "${Testing.testOut}/config/startConfigJson",
                "-device",
                "device information",
                "-noexit"
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
                "${Testing.testOut}/config/testCreateConfigDirectoryJson",
                "-device",
                "device information",
                "-noexit"
            )
        )
    }

}

