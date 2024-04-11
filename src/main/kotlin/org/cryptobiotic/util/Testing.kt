package org.cryptobiotic.util

class Testing {
    companion object {
        val tmpdir = java.nio.file.Files.createTempDirectory("testOut").toFile().absolutePath
        val testOut = "$tmpdir/egkec"
        val testOutMixnet = "$tmpdir/egmixnet"
    }
}
