package org.cryptobiotic.eg.publish.json

import org.cryptobiotic.eg.election.*
import org.cryptobiotic.eg.keyceremony.KeyCeremonyTrustee
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import org.cryptobiotic.eg.core.removeAllFiles
import org.cryptobiotic.eg.encrypt.EncryptedBallotChain
import org.cryptobiotic.eg.publish.DecryptedBallotSinkIF
import org.cryptobiotic.eg.publish.EncryptedBallotSinkIF
import org.cryptobiotic.eg.publish.Publisher
import org.cryptobiotic.util.ErrorMessages
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

/** Publishes the Election Record to JSON files.  */
@OptIn(ExperimentalSerializationApi::class)
class PublisherJson(topDir: String, createNew: Boolean) : Publisher {
    private var jsonPaths: ElectionRecordJsonPaths = ElectionRecordJsonPaths(topDir)
    val jsonReader = Json { explicitNulls = false; ignoreUnknownKeys = true; prettyPrint = true }

    init {
        val electionRecordDir = Path.of(topDir)
        if (createNew) {
            removeAllFiles(electionRecordDir)
        }
        val errs = ErrorMessages("PublisherJson  dir=$electionRecordDir")
        if (!validateOutputDir(electionRecordDir, errs))
            throw IOException("$errs")
    }

    override fun isJson() : Boolean = true
    override fun topdir(): String = jsonPaths.topDir

    override fun writeManifest(manifest: Manifest)  : String {
        val manifestJson = manifest.publishJson()
        FileOutputStream(jsonPaths.manifestPath()).use { out ->
            jsonReader.encodeToStream(manifestJson, out)
            out.close()
        }
        return jsonPaths.manifestPath()
    }

    override fun writeElectionConfig(config: ElectionConfig) {
        val constantsJson = config.constants.publishJson()
        FileOutputStream(jsonPaths.electionConstantsPath()).use { out ->
            jsonReader.encodeToStream(constantsJson, out)
            out.close()
        }

        FileOutputStream(jsonPaths.manifestPath()).use { out ->
            out.write(config.manifestBytes)
            out.close()
        }

        val configJson = config.publishJson()
        FileOutputStream(jsonPaths.electionConfigPath()).use { out ->
            jsonReader.encodeToStream(configJson, out)
            out.close()
        }
    }

    override fun writeElectionInitialized(init: ElectionInitialized) {
        writeElectionConfig(init.config)

        val contextJson = init.publishJson()
        FileOutputStream(jsonPaths.electionInitializedPath()).use { out ->
            jsonReader.encodeToStream(contextJson, out)
            out.close()
        }
    }

    override fun writeTallyResult(tally: TallyResult, complete: Boolean) {
        if (complete) writeElectionInitialized(tally.electionInitialized)

        val encryptedTallyJson = tally.encryptedTally.publishJson()
        FileOutputStream(jsonPaths.encryptedTallyPath()).use { out ->
            jsonReader.encodeToStream(encryptedTallyJson, out)
            out.close()
        }
    }

    override fun writeDecryptionResult(decryption: DecryptionResult) {
        writeTallyResult(decryption.tallyResult)
        writeDecryptedTally(decryption.decryptedTally)
    }

    override fun writeDecryptedTally(decryption: DecryptedTallyOrBallot) {
        val decryptedTallyJson = decryption.publishJson()
        FileOutputStream(jsonPaths.decryptedTallyPath()).use { out ->
            jsonReader.encodeToStream(decryptedTallyJson, out)
            out.close()
        }
    }

    override fun writePlaintextBallot(outputDir: String, plaintextBallots: List<PlaintextBallot>) {
        plaintextBallots.forEach { writePlaintextBallot(outputDir, it) }
    }

    private fun writePlaintextBallot(outputDir: String, plaintextBallot: PlaintextBallot) {
        val plaintextBallotJson = plaintextBallot.publishJson()
        val ballotFilename = jsonPaths.plaintextBallotPath(outputDir, plaintextBallot.ballotId)
        FileOutputStream(ballotFilename).use { out ->
            jsonReader.encodeToStream(plaintextBallotJson, out)
            out.close()
        }
    }

    override fun writeTrustee(trusteeDir: String, trustee: KeyCeremonyTrustee) {
        val decryptingTrusteeJson = trustee.publishJson()
        FileOutputStream(jsonPaths.decryptingTrusteePath(trusteeDir, trustee.id)).use { out ->
            jsonReader.encodeToStream(decryptingTrusteeJson, out)
            out.close()
        }
    }

    ////////////////////////////////////////////////

    override fun writeEncryptedBallotChain(closing: EncryptedBallotChain) {
        val jsonChain = closing.publishJson()
        val filename = jsonPaths.encryptedBallotChain(closing.encryptingDevice)

        FileOutputStream(filename).use { out ->
            jsonReader.encodeToStream(jsonChain, out)
            out.close()
        }
    }

    override fun encryptedBallotSink(device: String?): EncryptedBallotSinkIF {
        val ballotDir = jsonPaths.encryptedBallotDir(device)
        val errs = ErrorMessages("encryptedBallotSink  dir=$ballotDir")
        if (!validateOutputDir(Path.of(ballotDir), errs))
            throw IOException("$errs")
        return EncryptedBallotDeviceSink(ballotDir, device)
    }

    inner class EncryptedBallotDeviceSink(val ballotDir: String, val device: String?) : EncryptedBallotSinkIF {
        fun ballotDir() = ballotDir
        override fun writeEncryptedBallot(eballot: EncryptedBallot): String {
            val ballotFile = jsonPaths.encryptedBallotDevicePath(device, eballot.ballotId)
            val json = eballot.publishJson()
            FileOutputStream(ballotFile).use { out ->
                jsonReader.encodeToStream(json, out)
                out.close()
            }
            return ballotFile
        }
        override fun close() {
        }
    }

    /////////////////////////////////////////////////////////////

    override fun decryptedBallotSink(ballotOverrideDir: String?): DecryptedBallotSinkIF {
        val path = Path.of(jsonPaths.decryptedBallotDir(ballotOverrideDir))
        val errs = ErrorMessages("encryptedBallotSink  dir=$path")
        if (!validateOutputDir(path, errs))
            throw IOException("$errs")
        return DecryptedTallyOrBallotSink(ballotOverrideDir)
    }

    inner class DecryptedTallyOrBallotSink(val ballotOverrideDir: String?) : DecryptedBallotSinkIF {
        override fun writeDecryptedBallot(dballot: DecryptedTallyOrBallot) {
            val tallyJson = dballot.publishJson()
            FileOutputStream(jsonPaths.decryptedBallotPath(ballotOverrideDir, dballot.id)).use { out ->
                jsonReader.encodeToStream(tallyJson, out)
                out.close()
            }
        }
        override fun close() {
        }
    }

}

/** Make sure output directories exists and are writeable.  */
fun validateOutputDir(path: Path, errs: ErrorMessages): Boolean {
    if (!Files.exists(path)) {
        Files.createDirectories(path)
    }
    if (!Files.isDirectory(path)) {
        errs.add(" Output directory '$path' is not a directory")
    }
    if (!Files.isWritable(path)) {
        errs.add(" Output directory '$path' is not writeable")
    }
    if (!Files.isExecutable(path)) {
        errs.add(" Output directory '$path' is not executable")
    }
    return !errs.hasErrors()
}