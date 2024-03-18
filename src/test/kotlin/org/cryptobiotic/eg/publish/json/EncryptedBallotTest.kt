package org.cryptobiotic.eg.publish.json

import org.cryptobiotic.eg.core.*
import org.cryptobiotic.eg.election.*
import org.cryptobiotic.util.ErrorMessages
import org.cryptobiotic.eg.core.ecgroup.EcGroupContext
import org.cryptobiotic.eg.core.productionGroup
import org.cryptobiotic.eg.core.intgroup.tinyGroup

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EncryptedBallotTest {
    val groups = listOf(
        tinyGroup(),
        productionGroup("Integer3072"),
        productionGroup("Integer4096"),
        EcGroupContext("P-256")
    )

    @Test
    fun roundtripEncryptedBallot() {
        groups.forEach { roundtripEncryptedBallot(it) }
    }

    fun roundtripEncryptedBallot(context: GroupContext) {
        val ballot = generateEncryptedBallot(42, context)
        val json = ballot.publishJson()
        val roundtrip = json.import(context, ErrorMessages(""))
        assertNotNull(roundtrip)
        assertEquals(ballot, roundtrip)
        assertTrue("baux".encodeToByteArray().contentEquals(roundtrip.codeBaux))
    }

    @Test
    fun roundtripEncryptedBallotTiny() {
        val context = tinyGroup()
        val ballot = generateEncryptedBallot(42, context)
        val json = ballot.publishJson()
        val roundtrip = json.import(context, ErrorMessages(""))
        assertNotNull(roundtrip)
        assertEquals(ballot, roundtrip)
    }

    fun generateEncryptedBallot(seq: Int, context: GroupContext): EncryptedBallot {
        val contests = List(9, { generateFakeContest(it, context) })
        return EncryptedBallot(
            "ballotId $seq",
            "ballotIdStyle",
            "device",
            42,
            "baux".encodeToByteArray(),
            generateUInt256(context),
            generateUInt256(context),
            contests,
            if (Random.nextBoolean())
                EncryptedBallot.BallotState.CAST
            else
                EncryptedBallot.BallotState.CHALLENGED,
            generateCiphertext(context),
        )
    }

    private fun generateFakeContest(cseq: Int, context: GroupContext): EncryptedBallot.Contest {
        val selections = List(11, { generateFakeSelection(it, context) })
        return EncryptedBallot.Contest(
            "contest" + cseq,
            cseq,
            generateUInt256(context),
            selections,
            generateRangeChaumPedersenProofKnownNonce(context),
            generateHashedCiphertext(context),
        )
    }

    private fun generateFakeSelection(
        sseq: Int,
        context: GroupContext
    ): EncryptedBallot.Selection {
        return EncryptedBallot.Selection(
            "selection" + sseq,
            sseq,
            generateCiphertext(context),
            generateRangeChaumPedersenProofKnownNonce(context),
        )
    }

    @Test
    fun unknownBallotState() {
        groups.forEach { unknownBallotState(it) }
    }

    fun unknownBallotState(context: GroupContext) {
        val ballot = generateEncryptedBallot(42, context).copy(state = EncryptedBallot.BallotState.UNKNOWN)
        val json = ballot.publishJson()
        val roundtrip = json.import(context, ErrorMessages(""))
        assertNotNull(roundtrip)
        assertEquals(ballot, roundtrip)
    }
}