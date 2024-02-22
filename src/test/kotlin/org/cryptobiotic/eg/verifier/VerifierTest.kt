package org.cryptobiotic.eg.verifier

import org.cryptobiotic.eg.cli.RunVerifier
import org.cryptobiotic.eg.core.productionGroup
import kotlin.test.Test

class VerifierTest {
    @Test
    fun verifyRemoteWorkflow() {
        try {
            RunVerifier.runVerifier(
                "src/commonTest/data/testElectionRecord/remoteWorkflow/keyceremony",
                11
            )
            RunVerifier.runVerifier(
                "src/commonTest/data/testElectionRecord/remoteWorkflow/electionRecord",
                11
            )
        } catch (t :Throwable) {
            t.printStackTrace(System.out)
        }
        // RunVerifier.runVerifier(productionGroup(), "/home/stormy/dev/github/egk-webapps/testOut/remoteWorkflow/keyceremony/", 11)
        // RunVerifier.runVerifier(productionGroup(), "/home/stormy/dev/github/egk-webapps/testOut/remoteWorkflow/electionRecord/", 11)
    }

    @Test
    fun verificationAllJson() {
        RunVerifier.runVerifier("src/commonTest/data/workflow/allAvailableJson", 11, true)
    }

    @Test
    fun verificationSomeJson() {
        RunVerifier.runVerifier("src/commonTest/data/workflow/someAvailableJson", 11, true)
    }

    // @Test
    fun testProblem() {
        RunVerifier.runVerifier("../testOut/cliWorkflow/electionRecord", 11, true)
    }

    @Test
    fun readRecoveredElectionRecordAndValidate() {
        RunVerifier.main(
            arrayOf(
                "-in",
                "src/commonTest/data/workflow/someAvailableJson",
                "-nthreads",
                "11",
                "--showTime",
            )
        )
    }

    @Test
    fun testVerifyEncryptedBallots() {
        RunVerifier.verifyEncryptedBallots("src/commonTest/data/workflow/someAvailableJson", 11)
    }

    @Test
    fun verifyDecryptedTallyWithRecoveredShares() {
        RunVerifier.verifyDecryptedTally("src/commonTest/data/workflow/someAvailableJson")
    }

    @Test
    fun verifySpoiledBallotTallies() {
        RunVerifier.verifyChallengedBallots("src/commonTest/data/workflow/chainedJson")
    }

    // Ordered lists of the ballots encrypted by each device. spec 2.0, section 3.7, p.46
    @Test
    fun testVerifyTallyBallotIds() {
        RunVerifier.verifyTallyBallotIds("src/commonTest/data/workflow/allAvailableJson")
        RunVerifier.verifyTallyBallotIds("src/commonTest/data/workflow/someAvailableJson")
    }
}