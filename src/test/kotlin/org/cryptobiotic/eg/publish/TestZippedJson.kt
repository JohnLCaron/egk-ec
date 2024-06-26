package org.cryptobiotic.eg.publish

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.cryptobiotic.eg.cli.RunVerifier
import org.cryptobiotic.eg.core.createDirectories
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.spi.FileSystemProvider
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertNotNull

import org.cryptobiotic.eg.publish.json.*
import org.cryptobiotic.util.ErrorMessages
import org.cryptobiotic.util.Testing


// run verifier on zipped JSON record
@OptIn(ExperimentalSerializationApi::class)
class TestZippedJson {
    val jsonReader = Json { explicitNulls = false; ignoreUnknownKeys = true; prettyPrint = true }

    val inputDir = "src/test/data/workflow/allAvailableEc"
    val zippedJson = "${Testing.testOut}/zip/allAvailableEc.zip"
    val fs: FileSystem
    val fsp: FileSystemProvider

    init {
        createDirectories("${Testing.testOut}/zip/")
        zipFolder(File(inputDir), File(zippedJson))
        fs = FileSystems.newFileSystem(Path.of(zippedJson), mutableMapOf<String, String>())
        fsp = fs.provider()
    }

    @Test
    fun showEntryPaths() {
        val wtf = fs.rootDirectories
        wtf.forEach { root ->
                Files.walk(root).forEach { path -> println(path) }
            }
    }

    @Test
    fun readConstants() {
        val path : Path = fs.getPath("/constants.json")
        fsp.newInputStream(path).use { inp ->
            val json = jsonReader.decodeFromStream<ElectionConstantsJson>(inp)
            val result = json.import(ErrorMessages("readConstants"))
            assertNotNull(result)
            println("constants = ${result}")
        }
    }

    @Test
    fun testVerifyEncryptedBallots() {
        RunVerifier.verifyEncryptedBallots(zippedJson, 11)
        RunVerifier.verifyChallengedBallots(zippedJson)
        RunVerifier.runVerifier(zippedJson, 11)
    }
}

fun zipFolder(unzipped: File, zipped: File) {
    if (!unzipped.exists() || !unzipped.isDirectory) return
    ZipOutputStream(BufferedOutputStream(FileOutputStream(zipped))).use { zos ->
        unzipped.walkTopDown().filter { it.absolutePath != unzipped.absolutePath }.forEach { file ->
            val zipFileName = file.absolutePath.removePrefix(unzipped.absolutePath).removePrefix(File.separator)
            val entry = ZipEntry("$zipFileName${(if (file.isDirectory) "/" else "" )}")
            zos.putNextEntry(entry)
            if (file.isFile) file.inputStream().use { it.copyTo(zos) }
        }
    }
}