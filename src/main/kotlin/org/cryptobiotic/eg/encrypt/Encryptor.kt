package org.cryptobiotic.eg.encrypt

import org.cryptobiotic.eg.core.*
import org.cryptobiotic.eg.election.*
import org.cryptobiotic.util.ErrorMessages

/**
 * Encrypt Plaintext Ballots into PendingEncryptedBallot.
 * The election manifest is expected to have passed manifest validation (see ManifestInputValidation).
 * The input ballots are expected to have passed ballot validation
 * See RunExampleEncryption and BallotInputValidation to validate ballots before passing them to this class.
 */
class Encryptor(
    val group: GroupContext,
    val manifest: ManifestIF,
    val jointPublicKey: ElGamalPublicKey, // aka K
    val extendedBaseHash: UInt256, // aka He
    val encryptingDevice: String,
) {
    private val extendedBaseHashB = extendedBaseHash.bytes

    fun encrypt(
        ballot: PlaintextBallot,
        codeBaux : ByteArray,
        errs: ErrorMessages,
        ballotNonce: UInt256? = null,
        timestampOverride: Long? = null,
    ): PendingEncryptedBallot? {
        return ballot.encryptBallot(codeBaux, errs, ballotNonce ?: UInt256.random(), timestampOverride)
    }

    private fun PlaintextBallot.encryptBallot(
        codeBaux : ByteArray,
        errs: ErrorMessages,
        ballotNonce: UInt256,
        timestampOverride: Long? = null,
    ): PendingEncryptedBallot? {
        val plaintextContests = this.contests.associateBy { it.contestId }

        val encryptedContests = mutableListOf<PendingEncryptedBallot.Contest>()
        val manifestContests = manifest.contestsForBallotStyle(this.ballotStyle)
        if (manifestContests == null) {
            errs.add("Manifest does not have ballotStyle ${this.ballotStyle}")
            return null
        }
        if (manifestContests.isEmpty()) {
            errs.add("Manifest has no contests for ballotStyle ${this.ballotStyle}")
            return null
        }
        for (mcontest in manifestContests) {
            // If no contest on the pballot, create a well formed contest with all zeroes
            val pcontest = plaintextContests[mcontest.contestId] ?: makeZeroContest(mcontest)
            encryptedContests.add(
                pcontest.encryptContest(mcontest,
                    manifest.contestLimit(mcontest.contestId),
                    manifest.optionLimit(mcontest.contestId),
                    ballotNonce)
            )
        }
        val sortedContests = encryptedContests.sortedBy { it.sequenceOrder }

        // H(B) = H(HE ; 0x24, χ1 , χ2 , . . . , χmB , Baux ) ;  spec 2.0.0 eq 58
        val contestHashes = sortedContests.map { it.contestHash }
        val confirmationCode = hashFunction(extendedBaseHash.bytes, 0x24.toByte(), contestHashes, codeBaux)

        val encryptedSn: ElGamalCiphertext? = if (this.sn != null) {
            val snNonce = hashFunction(extendedBaseHashB, 0x110.toByte(), ballotNonce).toElementModQ(group)
            this.sn.encrypt(jointPublicKey, snNonce) // eq 24
        } else null

        return PendingEncryptedBallot(
            ballotId,
            ballotStyle,
            encryptingDevice,
            timestampOverride ?: (System.currentTimeMillis() / 1000), // secs since epoch,
            codeBaux,
            confirmationCode,
            extendedBaseHash,
            sortedContests,
            ballotNonce,
            encryptedSn,
            false,
        )
    }

    private fun makeZeroContest(mcontest: ManifestIF.Contest): PlaintextBallot.Contest {
        val selections = mcontest.selections.map { makeZeroSelection(it.selectionId, it.sequenceOrder) }
        return PlaintextBallot.Contest(mcontest.contestId, mcontest.sequenceOrder, selections)
    }

    private fun PlaintextBallot.Contest.encryptContest(
        mcontest: ManifestIF.Contest,
        contestLimit: Int,
        optionLimit: Int,
        ballotNonce: UInt256,
    ): PendingEncryptedBallot.Contest {

        val (contestData, votes) = makeContestData(contestLimit, optionLimit, this.selections, this.writeIns)

        val ballotSelections = this.selections.associateBy { it.selectionId }
        val encryptedSelections = mutableListOf<PendingEncryptedBallot.Selection>()
        for (mselection: ManifestIF.Selection in mcontest.selections) {
            var plaintextSelection = ballotSelections[mselection.selectionId]

            // Set vote to zero if not in manifest or this contest is overvoted. See 3.3.3 "Overvotes".
            if (plaintextSelection == null || (contestData.status == ContestDataStatus.over_vote)) {
                plaintextSelection = makeZeroSelection(mselection.selectionId, mselection.sequenceOrder)
            }
            encryptedSelections.add( plaintextSelection.encryptSelection(
                ballotNonce,
                this.sequenceOrder,
                optionLimit,
            ))
        }

        val contestDataEncrypted = contestData.encrypt(
            jointPublicKey,
            extendedBaseHash,
            mcontest.contestId,
            mcontest.sequenceOrder,
            ballotNonce,
            contestLimit)

        return this.encryptContest(
            group,
            jointPublicKey,
            extendedBaseHash,
            contestLimit,
            votes,
            encryptedSelections.sortedBy { it.sequenceOrder },
            contestDataEncrypted,
        )
    }

    private fun makeZeroSelection(selectionId: String, sequenceOrder: Int): PlaintextBallot.Selection {
        return PlaintextBallot.Selection(
            selectionId,
            sequenceOrder,
            0,
        )
    }

    private fun PlaintextBallot.Selection.encryptSelection(
        ballotNonce: UInt256,
        contestIndex: Int,
        optionLimit : Int,
        ): PendingEncryptedBallot.Selection {

        // ξi,j = H(HE ; 0x20, ξB , indc (Λi ), indo (λj )) ; spec 2.0.0 eq 25
        val selectionNonce = hashFunction(extendedBaseHashB, 0x20.toByte(), ballotNonce, contestIndex, this.sequenceOrder)

        return this.encryptSelection(
            this.vote,
            jointPublicKey,
            extendedBaseHash,
            selectionNonce.toElementModQ(group),
            optionLimit,
            )
    }
}

fun PlaintextBallot.Contest.encryptContest(
    group: GroupContext,
    jointPublicKey: ElGamalPublicKey,
    extendedBaseHash: UInt256,
    votesAllowed: Int, // The number of allowed votes for this contest
    totalVotedFor: Int, // The actual number of selections voted for, for the range proof
    encryptedSelections: List<PendingEncryptedBallot.Selection>,
    extendedDataCiphertext: HashedElGamalCiphertext,
): PendingEncryptedBallot.Contest {

    val ciphertexts: List<ElGamalCiphertext> = encryptedSelections.map { it.ciphertext }
    val ciphertextAccumulation: ElGamalCiphertext = ciphertexts.encryptedSum()?: 0.encrypt(jointPublicKey)
    val nonces: Iterable<ElementModQ> = encryptedSelections.map { it.selectionNonce }
    val aggNonce: ElementModQ = group.addQ(nonces)

    val proof = ciphertextAccumulation.makeChaumPedersen(
        totalVotedFor, // (ℓ in the spec)
        votesAllowed,  // (L in the spec)
        aggNonce,
        jointPublicKey,
        extendedBaseHash,
    )

    // χl = H(HE ; 0x23, indc (Λl ), K, α1 , β1 , α2 , β2 . . . , αm , βm ) ; spec 2.0.0 eq 57
    val ciphers = mutableListOf<ElementModP>()
    ciphertexts.forEach {
        ciphers.add(it.pad)
        ciphers.add(it.data)
    }
    val contestHash = hashFunction(extendedBaseHash.bytes, 0x23.toByte(), this.sequenceOrder, jointPublicKey, ciphers)

    return PendingEncryptedBallot.Contest(
        this.contestId,
        this.sequenceOrder,
        contestHash,
        encryptedSelections,
        proof,
        extendedDataCiphertext,
    )
}

fun PlaintextBallot.Selection.encryptSelection(
    vote: Int,
    jointPublicKey: ElGamalPublicKey,
    cryptoExtendedBaseHash: UInt256,
    selectionNonce: ElementModQ,
    optionLimit : Int,
): PendingEncryptedBallot.Selection {
    val elgamalEncryption: ElGamalCiphertext = vote.encrypt(jointPublicKey, selectionNonce) // eq 24

    val proof = elgamalEncryption.makeChaumPedersen(
        vote,
        optionLimit,
        selectionNonce,
        jointPublicKey,
        cryptoExtendedBaseHash
    )

    return PendingEncryptedBallot.Selection(
        this.selectionId,
        this.sequenceOrder,
        elgamalEncryption,
        proof,
        selectionNonce,
    )
}