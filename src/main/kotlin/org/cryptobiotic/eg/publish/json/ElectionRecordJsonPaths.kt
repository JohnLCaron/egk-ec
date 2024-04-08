package org.cryptobiotic.eg.publish.json

/*
````
topdir/
    constants.json
    election_config.json
    election_initialized.json
    encrypted_tally.json
    manifest.json
    tally.json

    encrypted_ballots/
      eballot-<ballotId>.json
      eballot-<ballotId>.json
      eballot-<ballotId>.json
      ...

    challenged_ballots/
      dballot-<ballotId>.json
      dballot-<ballotId>.json
      dballot-<ballotId>.json
      ...
````

The encrypted_ballots directory may optionally be divided into "device" subdirectories.
If using ballot chaining, each such subdirectory is a separate ballot chain.

````
topdir/
    ...
    encrypted_ballots/
       deviceName1/
          ballot_chain.json
          eballot-<ballotId>.json
          eballot-<ballotId>.json
          eballot-<ballotId>.json
          ...
        deviceName2/
          ballot_chain.json
          eballot-<ballotId>.json
          eballot-<ballotId>.json
          eballot-<ballotId>.json
        deviceName3/
           ...
````
 */

data class ElectionRecordJsonPaths(val topDir : String) {
    private val electionRecordDir = topDir

    companion object {
        const val JSON_SUFFIX = ".json"
        const val DECRYPTING_TRUSTEE_PREFIX = "decryptingTrustee"
        const val ELECTION_CONSTANTS_FILE = "constants$JSON_SUFFIX"
        const val ELECTION_CONFIG_FILE = "election_config$JSON_SUFFIX"
        const val ELECTION_INITIALIZED_FILE = "election_initialized$JSON_SUFFIX"
        const val ENCRYPTED_TALLY_FILE = "encrypted_tally$JSON_SUFFIX"
        const val MANIFEST_FILE = "manifest$JSON_SUFFIX"
        const val DECRYPTED_TALLY_FILE = "tally$JSON_SUFFIX"

        const val ENCRYPTED_BALLOT_PREFIX = "eballot-"
        const val DECRYPTED_BALLOT_PREFIX = "dballot-"
        const val PLAINTEXT_BALLOT_PREFIX = "pballot-"

        const val ENCRYPTED_DIR = "encrypted_ballots"
        const val CHALLENGED_DIR = "challenged_ballots"
        const val ENCRYPTED_BALLOT_CHAIN = "ballot_chain"

    }

    fun manifestPath(): String {
        return "$electionRecordDir/$MANIFEST_FILE"
    }

    fun electionConstantsPath(): String {
        return "$electionRecordDir/$ELECTION_CONSTANTS_FILE"
    }

    fun electionConfigPath(): String {
        return "$electionRecordDir/$ELECTION_CONFIG_FILE"
    }

    fun electionInitializedPath(): String {
        return "$electionRecordDir/$ELECTION_INITIALIZED_FILE"
    }

    fun encryptedTallyPath(): String {
        return "$electionRecordDir/$ENCRYPTED_TALLY_FILE"
    }

    fun decryptedTallyPath(): String {
        return "$electionRecordDir/$DECRYPTED_TALLY_FILE"
    }

    fun plaintextBallotPath(ballotDir: String, ballotId: String): String {
        val id = ballotId.replace(" ", "_")
        return "$ballotDir/$PLAINTEXT_BALLOT_PREFIX$id$JSON_SUFFIX"
    }

    fun decryptedBallotPath(ballotOverrideDir: String?, ballotId : String): String {
        val id = ballotId.replace(" ", "_")
        return "${decryptedBallotDir(ballotOverrideDir)}/$DECRYPTED_BALLOT_PREFIX$id$JSON_SUFFIX"
    }

    fun decryptedBallotDir(ballotOverrideDir: String?): String {
        return if (ballotOverrideDir == null) "$electionRecordDir/$CHALLENGED_DIR"
        else ballotOverrideDir
    }

    fun decryptingTrusteePath(trusteeDir: String, guardianId: String): String {
        val id = guardianId.replace(" ", "_")
        return "$trusteeDir/$DECRYPTING_TRUSTEE_PREFIX-$id$JSON_SUFFIX"
    }

    //////////////////////////////////////

    fun encryptedBallotDir(): String {
        return "$electionRecordDir/$ENCRYPTED_DIR/"
    }

    fun encryptedBallotDir(device: String): String {
        val useDevice = device.replace(" ", "_")
        return "${encryptedBallotDir()}/$useDevice/"
    }

    fun encryptedBallotDevicePath(device: String?, ballotId: String): String {
        val id = ballotId.replace(" ", "_")
        return if (device != null) {
            val useDevice = device.replace(" ", "_")
            "${encryptedBallotDir(useDevice)}/${ENCRYPTED_BALLOT_PREFIX}$id${JSON_SUFFIX}"
        } else {
            "${topDir}/${ENCRYPTED_BALLOT_PREFIX}$id${JSON_SUFFIX}"
        }
    }

    fun encryptedBallotChain(device: String, ballotOverrideDir: String?): String {
        return if (ballotOverrideDir == null) {
            "${encryptedBallotDir(device)}/${ENCRYPTED_BALLOT_CHAIN}${JSON_SUFFIX}"
        } else {
            "${ballotOverrideDir}/${ENCRYPTED_BALLOT_CHAIN}${JSON_SUFFIX}"
        }
    }
}