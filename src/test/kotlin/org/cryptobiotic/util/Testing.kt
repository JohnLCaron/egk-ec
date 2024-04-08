package org.cryptobiotic.util

//val testOut = "/home/stormy/tmp/testOut/egkec"
//val testOutMixnet = "/home/stormy/tmp/testOut/egmixnet"

class Testing {
    companion object {
        val tmpdir = java.nio.file.Files.createTempDirectory("testOut").toFile().absolutePath
        val testOut = "$tmpdir/egkec"
        val testOutMixnet = "$tmpdir/egmixnet"
    }
}
