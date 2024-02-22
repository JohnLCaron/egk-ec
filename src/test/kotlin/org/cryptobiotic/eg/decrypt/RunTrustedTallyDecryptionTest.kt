package org.cryptobiotic.eg.decrypt

import org.cryptobiotic.eg.cli.RunTrustedTallyDecryption

import kotlin.test.Test

/** Test Decryption with in-process DecryptingTrustee's. */
class RunTrustedTallyDecryptionTest {

    @Test
    fun testDecryptionAllJson() {
        RunTrustedTallyDecryption.main(
            arrayOf(
                "-in",
                "src/commonTest/data/workflow/allAvailableJson",
                "-trustees",
                "src/commonTest/data/workflow/allAvailableJson/private_data/trustees",
                "-out",
                "testOut/decrypt/testDecryptionJson",
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
                "src/commonTest/data/workflow/someAvailableJson",
                "-trustees",
                "src/commonTest/data/workflow/someAvailableJson/private_data/trustees",
                "-out",
                "testOut/decrypt/testDecryptionSome",
                "-createdBy",
                "RunTrustedTallyDecryptionTest",
                "-missing",
                "1,4"
            )
        )
    }
}
