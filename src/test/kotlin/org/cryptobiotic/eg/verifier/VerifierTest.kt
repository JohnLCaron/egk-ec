package org.cryptobiotic.eg.verifier

import org.cryptobiotic.eg.cli.RunVerifier
import org.cryptobiotic.eg.core.productionGroup
import kotlin.test.Test

class VerifierTest {

    // @Test
    fun verifyRemoteWorkflow() {
        try {
            RunVerifier.runVerifier(
                "src/test/data/testElectionRecord/remoteWorkflow/keyceremony",
                11
            )
            RunVerifier.runVerifier(
                "src/test/data/testElectionRecord/remoteWorkflow/electionRecord",
                11
            )
        } catch (t :Throwable) {
            t.printStackTrace(System.out)
        }
    }

    @Test
    fun verificationEc() {
        RunVerifier.runVerifier("src/test/data/workflow/allAvailableEc", 11, true)
        RunVerifier.runVerifier("src/test/data/workflow/someAvailableEc", 11, true)
    }

    @Test
    fun verificationInteger() {
        RunVerifier.runVerifier("src/test/data/workflow/allAvailable", 11, true)
        RunVerifier.runVerifier("src/test/data/workflow/someAvailable", 11, true)
    }

    @Test
    fun verifyAddBallots() {
        RunVerifier.runVerifier("src/test/data/encrypt/testBallotNoChain", 11)
        RunVerifier.runVerifier("src/test/data/encrypt/testBallotChain", 11)
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
            )
        )
    }

    @Test
    fun testVerifyEncryptedBallots() {
        RunVerifier.verifyEncryptedBallots("src/test/data/workflow/someAvailable", 11)
    }

    @Test
    fun verifyDecryptedTallyWithRecoveredShares() {
        RunVerifier.verifyDecryptedTally("src/test/data/workflow/someAvailable")
    }

    // @Test
    fun verifyChallengedBallots() {
        RunVerifier.verifyChallengedBallots("src/test/data/workflow/someAvailableEcChained")
    }

    // Ordered lists of the ballots encrypted by each device. spec 2.0, section 3.7, p.46
    @Test
    fun testVerifyTallyBallotIds() {
        RunVerifier.verifyTallyBallotIds("src/test/data/workflow/allAvailableEc")
        RunVerifier.verifyTallyBallotIds("src/test/data/workflow/someAvailable")
    }
}