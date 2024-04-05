package org.cryptobiotic.eg.encrypt

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.unwrap
import io.github.oshai.kotlinlogging.KotlinLogging

import org.cryptobiotic.eg.core.*
import org.cryptobiotic.eg.decrypt.DecryptBallotWithNonce
import org.cryptobiotic.eg.election.*
import org.cryptobiotic.eg.input.BallotInputValidation
import org.cryptobiotic.eg.publish.EncryptedBallotSinkIF
import org.cryptobiotic.eg.publish.makeConsumer
import org.cryptobiotic.eg.publish.makePublisher
import org.cryptobiotic.util.ErrorMessages
import java.io.Closeable

private val logger = KotlinLogging.logger("AddEncryptedBallot")

/**
 * Encrypt a ballot and add to election record. TODO Single threaded only?.
 *  Note that chaining is controlled by config.chainConfirmationCodes, and handled here.
 *  Note that this allows for benolah challenge, ie voter submits a ballot, gets a confirmation code
 *  (with or without ballot chaining), then decide to challenge or cast.
 */
class AddEncryptedBallot(
    val manifest: ManifestIF, // should already be validated
    val ballotValidator: BallotInputValidation,
    val chaining: Boolean,
    val configBaux0: ByteArray,
    val jointPublicKey: ElGamalPublicKey,
    val extendedBaseHash: UInt256,
    val device: String,
    val outputDir: String, // write ballots to outputDir/encrypted_ballots/deviceName, must not have multiple writers to same directory
    val invalidDir: String, // write plaintext ballots that fail validation
) : Closeable {
    val publisher = makePublisher(outputDir, false)
    val consumerIn = makeConsumer(outputDir)

    // note that the encryptor doesnt know if its chained
    val encryptor = Encryptor(
        jointPublicKey.context,
        manifest,
        jointPublicKey,
        extendedBaseHash,
        device,
    )
    val decryptor = DecryptBallotWithNonce(
        jointPublicKey.context,
        jointPublicKey,
        extendedBaseHash
    )

    private val sink: EncryptedBallotSinkIF = publisher.encryptedBallotSink(device)
    private var currentChain: EncryptedBallotChain? = null
    private val pending = mutableMapOf<UInt256, PendingEncryptedBallot>() // key = ccode.toHex()
    private var closed = false

    fun encrypt(ballot: PlaintextBallot, errs : ErrorMessages): PendingEncryptedBallot? {
        if (closed) {
            errs.add("Trying to add ballot after chain has been closed")
            return null
        }

        val validation = ballotValidator.validate(ballot)
        if (validation.hasErrors()) {
            publisher.writePlaintextBallot(invalidDir, listOf(ballot))
            errs.add("${ballot.ballotId} did not validate (wrote to invalidDir=$invalidDir) because $validation")
            return null
        }

        val bauxj = if (!chaining) {
            configBaux0
        } else {
            // this will read in an existing chain, and so recover from machine going down.
            val pair = EncryptedBallotChain.makeCodeBaux(
                consumerIn,
                device,
                null,
                configBaux0,
                extendedBaseHash,
            )
            currentChain = pair.second
            pair.first!! // cant be null
        }

        val ciphertextBallot = encryptor.encrypt(ballot, bauxj, errs)
        if (ciphertextBallot == null || errs.hasErrors()) {
            return null
        }
        // challenged ballots are also in the chain
        if (chaining) {
            currentChain = EncryptedBallotChain.writeChain(
                publisher,
                null,
                ciphertextBallot.ballotId,
                ciphertextBallot.confirmationCode,
                currentChain!!
            )
        }

        // hmmm you could write CiphertextBallot to a log, in case of crash
        pending[ciphertextBallot.confirmationCode] = ciphertextBallot
        return ciphertextBallot
    }

    /** encrypt and cast, does not leave in pending. optional write. */
    fun encryptAndCast(ballot: PlaintextBallot, errs : ErrorMessages, writeToDisk: Boolean = true): EncryptedBallot? {
        val cballot = encrypt(ballot, errs)
        if (errs.hasErrors()) {
            return null
        }
        val eballot = cballot!!.submit(EncryptedBallot.BallotState.CAST)
        if (writeToDisk) {
            submit(eballot.confirmationCode, EncryptedBallot.BallotState.CAST)
        } else {
            // remove from pending
           pending.remove(eballot.confirmationCode)
        }
        return eballot
    }

    fun submit(ccode: UInt256, state: EncryptedBallot.BallotState): Result<EncryptedBallot, String> {
        val cballot = pending.remove(ccode)
        if (cballot == null) {
            logger.error { "Tried to submit state=$state  unknown ballot ccode=$ccode" }
            return Err("Tried to submit state=$state  unknown ballot ccode=$ccode")
        }
        return try {
            val eballot = cballot.submit(state)
            sink.writeEncryptedBallot(eballot)
            Ok(eballot)
        } catch (t: Throwable) {
            logger.throwing(t) // TODO
            Err("Tried to submit Ciphertext ballot state=$state ccode=$ccode error = ${t.message}")
        }
    }

    fun cast(ccode: UInt256): Result<EncryptedBallot, String> {
        return submit(ccode, EncryptedBallot.BallotState.CAST)
    }

    fun challenge(ccode: UInt256): Result<EncryptedBallot, String> {
        return submit(ccode, EncryptedBallot.BallotState.CHALLENGED)
    }

    fun challengeAndDecrypt(ccode: UInt256): Result<PlaintextBallot, String> {
        val cballot = pending.remove(ccode)
        if (cballot == null) {
            logger.error { "Tried to submit unknown ballot ccode=$ccode" }
            return Err("Tried to submit unknown ballot ccode=$ccode")
        }
        try {
            val eballot = cballot.challenge()
            sink.writeEncryptedBallot(eballot) // record the encrypted, challenged ballot

            with(decryptor) {
                val dballotResult: Result<PlaintextBallot, String> = eballot.decrypt(cballot.ballotNonce)
                if (dballotResult is Ok) {
                    return Ok(dballotResult.unwrap())
                } else {
                    return Err("Decryption error ballot ccode=$ccode")
                }
            }
        } catch (t: Throwable) {
            logger.throwing(t) // TODO
            return Err("Tried to challenge Ciphertext ballot ccode=$ccode error = ${t.message}")
        }
    }

    // write out pending encryptedBallots, and chain if needed
    fun sync() {
        if (pending.isNotEmpty()) {
            val copyPending = pending.toMap() // make copy so it can be modified
            copyPending.keys.forEach {
                logger.error { "pending Ciphertext ballot $it was not submitted, marking 'UNKNOWN'" }
                submit(it, EncryptedBallot.BallotState.UNKNOWN)
            }
        }

        if (chaining) {
            EncryptedBallotChain.terminateChain(
                publisher,
                device,
                null,
                currentChain!!
            )
        }
    }

    override fun close() {
        sync()
        sink.close()
        closed = true
    }
}