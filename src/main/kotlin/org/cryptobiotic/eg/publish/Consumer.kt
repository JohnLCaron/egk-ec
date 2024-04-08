package org.cryptobiotic.eg.publish

import com.github.michaelbull.result.Result
import io.github.oshai.kotlinlogging.KotlinLogging

import org.cryptobiotic.eg.core.*
import org.cryptobiotic.eg.decrypt.DecryptingTrusteeIF
import org.cryptobiotic.eg.election.*
import org.cryptobiotic.eg.encrypt.EncryptedBallotChain
import org.cryptobiotic.eg.input.ManifestInputValidation
import org.cryptobiotic.eg.publish.json.ConsumerJson
import org.cryptobiotic.eg.publish.json.ElectionRecordJsonPaths
import org.cryptobiotic.util.ErrorMessages
import java.nio.file.Path
import java.util.function.Predicate

private val logger = KotlinLogging.logger("Consumer")

/** Public API to read from the election record */
interface Consumer {
    val group : GroupContext

    fun topdir() : String
    fun isJson() : Boolean

    fun readManifestBytes(filename : String): ByteArray
    fun makeManifest(manifestBytes: ByteArray): Manifest

    fun readElectionConfig(): Result<ElectionConfig, ErrorMessages>
    fun readElectionInitialized(): Result<ElectionInitialized, ErrorMessages>
    fun readTallyResult(): Result<TallyResult, ErrorMessages>
    fun readEncryptedTallyFromFile(filename: String): Result<EncryptedTally, ErrorMessages>
    fun readDecryptionResult(): Result<DecryptionResult, ErrorMessages>
    fun readDecryptedTallyFromFile(filename: String): Result<DecryptedTallyOrBallot, ErrorMessages>

    /** Are there any encrypted ballots? */
    fun hasEncryptedBallots() : Boolean
    /** Find and read the encrypted ballot with the given ballotId. */
    fun readEncryptedBallot(device: String?, ballotId: String) : Result<EncryptedBallot, ErrorMessages>
    /** Read encrypted ballots for all devices. */
    fun iterateAllEncryptedBallots(filter : ((EncryptedBallot) -> Boolean)? ): Iterable<EncryptedBallot>
    fun iterateAllCastBallots(): Iterable<EncryptedBallot>  = iterateAllEncryptedBallots{  it.state == EncryptedBallot.BallotState.CAST }
    fun iterateAllChallengedBallots(): Iterable<EncryptedBallot>  = iterateAllEncryptedBallots{  it.state == EncryptedBallot.BallotState.CHALLENGED }

    /** The list of devices that have subdirectories in the encrypted ballots directory. */
    fun encryptingDevices(): List<String>
    /** Read encrypted ballots for specified device. */
    fun iterateEncryptedBallots(device: String, filter : Predicate<EncryptedBallot>?): Iterable<EncryptedBallot>
    /** Read  encrypted ballot chain for the specified device. If the ballotDir is not overridden, then 'encrypted_ballots/device' will be used. */
    fun readEncryptedBallotChain(device: String, ballotOverrideDir: String? = null) : Result<EncryptedBallotChain, ErrorMessages>

    /** Read all decrypted ballots in the given directory, or the default if null. */
    fun iterateDecryptedBallots(ballotOverrideDir: String? = null): Iterable<DecryptedTallyOrBallot>

    //// may be outside the election record
    /** read encrypted ballots in given directory. */
    @Deprecated("to be removed")
    fun iterateEncryptedBallotsFromDir(ballotDir: String, filter : Predicate<EncryptedBallot>? ): Iterable<EncryptedBallot> // TODO remove?
    /** read plaintext ballots in given directory, private data. */
    fun iteratePlaintextBallots(ballotDir: String, filter : ((PlaintextBallot) -> Boolean)? ): Iterable<PlaintextBallot>
    /** read trustee in given directory for given guardianId, private data. */
    fun readTrustee(trusteeDir: String, guardianId: String): Result<DecryptingTrusteeIF, ErrorMessages>
    /** read plaintext ballot. */
    fun readPlaintextBallot(ballotFilepath: String): Result<PlaintextBallot, ErrorMessages>
}

/**
 * Read only access to an election record.
 * There must at least be a constants.json and an election_config.json file. If not, then
 * the caller must provide the group.
 * [topDir] a directory or a zip file with the election record in it.
 * [usegroup] caller may provide the group to be used. Must match the election record.
 * Throw an Exception on failure.
 */
fun makeConsumer(
    topDir: String,
    usegroup: GroupContext? = null
): Consumer {
    return ConsumerJson(topDir, usegroup)
}

/**
 * Read the manifest and check that the file parses and validates.
 * @param manifestDirOrFile manifest filename, or the directory that its in. May be JSON or proto. If JSON, may be zipped
 * @return isJson, manifest, manifestBytes
 */
fun readAndCheckManifest(manifestDirOrFile: String): Triple<Boolean, Manifest, ByteArray> {

    val isZip = manifestDirOrFile.endsWith(".zip")
    val isDirectory = isDirectory(manifestDirOrFile)
    val isJson = if (isDirectory) {
        pathExists("$manifestDirOrFile/${ElectionRecordJsonPaths.MANIFEST_FILE}")
    } else {
        isZip || manifestDirOrFile.endsWith(".json")
    }

    val manifestFile = if (isDirectory) {
        "$manifestDirOrFile/${ElectionRecordJsonPaths.MANIFEST_FILE}"
    } else if (isZip) {
        ElectionRecordJsonPaths.MANIFEST_FILE
    } else {
        manifestDirOrFile
    }

    val manifestDir = if (isDirectory || isZip) {
        manifestDirOrFile
    } else {
        manifestDirOrFile.substringBeforeLast("/")
    }

    try {
        // have to read manifest without using Consumer, since config may not exist
        val consumer =  ConsumerJson(manifestDir, productionGroup()) // production group wont get used
        val manifestBytes = consumer.readManifestBytes(manifestFile)
        // make sure it parses
        val manifest = consumer.makeManifest(manifestBytes)
        // make sure it validates
        val errors = ManifestInputValidation(manifest).validate()
        if (errors.hasErrors()) {
            logger.error { "*** ManifestInputValidation error on manifest file= $manifestFile \n $errors" }
            throw RuntimeException("*** ManifestInputValidation error on manifest file= $manifestFile \n $errors")
        }
        return Triple(isJson, manifest, manifestBytes)

    } catch (t: Throwable) {
        logger.error {"readAndCheckManifestBytes Exception= ${t.message} ${t.stackTraceToString()}" }
        throw t
    }

}