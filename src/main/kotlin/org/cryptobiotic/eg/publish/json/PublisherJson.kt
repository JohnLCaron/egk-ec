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
import java.io.FileOutputStream
import java.io.OutputStream
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
        validateOutputDir(electionRecordDir, Formatter())
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

    override fun writeEncryptedBallotChain(closing: EncryptedBallotChain, ballotOverrideDir: String?) {
        val jsonChain = closing.publishJson()
        val filename = jsonPaths.encryptedBallotChain(closing.encryptingDevice, ballotOverrideDir)

        FileOutputStream(filename).use { out ->
            jsonReader.encodeToStream(jsonChain, out)
            out.close()
        }
    }

    // batched is only used by proto, so is ignored here
    override fun encryptedBallotSink(device: String?, batched: Boolean): EncryptedBallotSinkIF {
        val ballotDir = if (device != null) jsonPaths.encryptedBallotDir(device) else jsonPaths.topDir
        validateOutputDir(Path.of(ballotDir), Formatter()) // TODO
        return EncryptedBallotDeviceSink(device)
    }

    inner class EncryptedBallotDeviceSink(val device: String?) : EncryptedBallotSinkIF {

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
        validateOutputDir(Path.of(jsonPaths.decryptedBallotDir(ballotOverrideDir)), Formatter())
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

// not used for now, keep for proto or batch
fun writeVlen(input: Int, output: OutputStream) {
    var value = input
    while (true) {
        if (value and 0x7F.inv() == 0) {
            output.write(value)
            return
        } else {
            output.write(value and 0x7F or 0x80)
            value = value ushr 7
        }
    }
}

/** Make sure output directories exists and are writeable.  */
fun validateOutputDir(path: Path, error: Formatter): Boolean {
    if (!Files.exists(path)) {
        Files.createDirectories(path)
    }
    if (!Files.isDirectory(path)) {
        error.format(" Output directory '%s' is not a directory%n", path)
        return false
    }
    if (!Files.isWritable(path)) {
        error.format(" Output directory '%s' is not writeable%n", path)
        return false
    }
    if (!Files.isExecutable(path)) {
        error.format(" Output directory '%s' is not executable%n", path)
        return false
    }
    return true
}