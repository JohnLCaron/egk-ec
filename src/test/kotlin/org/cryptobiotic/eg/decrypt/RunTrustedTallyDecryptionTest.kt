package org.cryptobiotic.eg.decrypt

import org.cryptobiotic.eg.cli.RunTrustedTallyDecryption
import org.cryptobiotic.util.Testing

import kotlin.test.Test

/** Test Decryption with in-process DecryptingTrustee's. */
class RunTrustedTallyDecryptionTest {

    @Test
    fun testDecryptionAllJson() {
        RunTrustedTallyDecryption.main(
            arrayOf(
                "-in",
                "src/test/data/workflow/allAvailableEc",
                "-trustees",
                "src/test/data/workflow/allAvailableEc/private_data/trustees",
                "-out",
                "${Testing.testOut}/decrypt/testDecryptionJson",
                "-createdBy",
                "RunTrustedTallyDecryptionTest",
            )
        )
    }

    @Test
    fun testDecryptionSomeJson() {
        RunTrustedTallyDecryption.main(
            arrayOf(
                "-in",
                "src/test/data/workflow/someAvailableEc",
                "-trustees",
                "src/test/data/workflow/someAvailableEc/private_data/trustees",
                "-out",
                "${Testing.testOut}/decrypt/testDecryptionSome",
                "-createdBy",
                "RunTrustedTallyDecryptionTest",
                "-missing",
                "1,4"
            )
        )
    }


    @Test
    fun testDecryptionFromFile() {
        RunTrustedTallyDecryption.main(
            arrayOf(
                "-in", "src/test/data/workflow/someAvailableEc",
                "-trustees", "src/test/data/workflow/someAvailableEc/private_data/trustees",
                "-out", "${Testing.testOut}/decrypt/testDecryptionFromFile",
                "--encryptedTallyFile", "src/test/data/workflow/someAvailableEc/encrypted_tally.json",
                "-createdBy", "testDecryptionFromFile",
                "-missing", "1,4"
            )
        )
    }
}
