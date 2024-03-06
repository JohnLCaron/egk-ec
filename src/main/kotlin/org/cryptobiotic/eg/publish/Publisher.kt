package org.cryptobiotic.eg.publish

import org.cryptobiotic.eg.election.*
import org.cryptobiotic.eg.encrypt.EncryptedBallotChain
import org.cryptobiotic.eg.keyceremony.KeyCeremonyTrustee
import org.cryptobiotic.eg.publish.json.PublisherJson

/** Write the Election Record as protobuf or json files. */
interface Publisher {
    fun isJson() : Boolean

    fun writeManifest(manifest: Manifest) : String // return filename
    fun writeElectionConfig(config: ElectionConfig)
    fun writeElectionInitialized(init: ElectionInitialized)
    fun writeTallyResult(tally: TallyResult)
    fun writeDecryptionResult(decryption: DecryptionResult)

    fun encryptedBallotSink(device: String?, batched : Boolean = false): EncryptedBallotSinkIF
    fun writeEncryptedBallotChain(closing: EncryptedBallotChain, ballotDir: String? = null)
    fun decryptedTallyOrBallotSink(): DecryptedTallyOrBallotSinkIF

    fun writePlaintextBallot(outputDir: String, plaintextBallots: List<PlaintextBallot>)
    fun writeTrustee(trusteeDir: String, trustee: KeyCeremonyTrustee)
}

interface EncryptedBallotSinkIF : Closeable {
    fun writeEncryptedBallot(ballot: EncryptedBallot): String // return filename
}

interface DecryptedTallyOrBallotSinkIF : Closeable {
    fun writeDecryptedTallyOrBallot(tally: DecryptedTallyOrBallot)
}

// copied from io.ktor.utils.io.core.Closeable to break package dependency
interface Closeable {
    fun close()
}

fun makePublisher(
    topDir: String,
    createNew: Boolean = false, // false = create directories if not already exist, true = create clean directories,
): Publisher {
    return PublisherJson(topDir, createNew)
}
