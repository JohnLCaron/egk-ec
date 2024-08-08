package org.cryptobiotic.eg.verifier

import com.github.michaelbull.result.Ok
import org.cryptobiotic.eg.cli.RunVerifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VerifierTest {

    // @Test
    fun verifyRemoteWorkflow() {
        try {
            assertEquals(0, RunVerifier.runVerifier("src/test/data/testElectionRecord/remoteWorkflow/keyceremony", 11))
            assertEquals(0, RunVerifier.runVerifier("src/test/data/testElectionRecord/remoteWorkflow/electionRecord", 11))
        } catch (t :Throwable) {
            t.printStackTrace(System.out)
        }
    }

    @Test
    fun verificationEc() {
        assertEquals(0, RunVerifier.runVerifier("src/test/data/workflow/allAvailableEc", 11, true))
        assertEquals(0, RunVerifier.runVerifier("src/test/data/workflow/someAvailableEc", 11, true))
    }

    @Test
    fun verificationEcSingle() {
        assertEquals(0, RunVerifier.runVerifier("src/test/data/workflow/allAvailableEc", 1, true))
        assertEquals(0, RunVerifier.runVerifier("src/test/data/workflow/someAvailableEc", 1, true))
    }

    @Test
    fun verificationInteger() {
        assertEquals(0, RunVerifier.runVerifier("src/test/data/workflow/allAvailable", 11, true))
        assertEquals(0, RunVerifier.runVerifier("src/test/data/workflow/someAvailable", 11, true))
    }

    @Test
    fun verifyAddBallots() {
        assertEquals(0, RunVerifier.runVerifier("src/test/data/encrypt/testBallotNoChain", 11))
        assertEquals(0, RunVerifier.runVerifier("src/test/data/encrypt/testBallotChain", 11))
    }

    @Test
    fun readRecoveredElectionRecordAndValidate() {
        RunVerifier.main(
            arrayOf(
                "-in",
                "src/test/data/workflow/someAvailable",
                "-nthreads",
                "11",
                "--showTime",
                "--noexit"
            )
        )
    }

    @Test
    fun testVerifyEncryptedBallots() {
        assertTrue(RunVerifier.verifyEncryptedBallots("src/test/data/workflow/someAvailable", 11) is Ok)
    }

    @Test
    fun verifyDecryptedTallyWithRecoveredShares() {
        assertTrue(RunVerifier.verifyDecryptedTally("src/test/data/workflow/someAvailable") is Ok)
    }

    // @Test
    fun verifyChallengedBallots() {
        assertTrue(RunVerifier.verifyChallengedBallots("src/test/data/workflow/someAvailableEcChained") is Ok)
    }

    // Ordered lists of the ballots encrypted by each device. spec 2.0, section 3.7, p.46
    @Test
    fun testVerifyTallyBallotIds() {
        assertTrue(RunVerifier.verifyTallyBallotIds("src/test/data/workflow/allAvailableEc") is Ok)
        assertTrue(RunVerifier.verifyTallyBallotIds("src/test/data/workflow/someAvailable") is Ok)
    }
}