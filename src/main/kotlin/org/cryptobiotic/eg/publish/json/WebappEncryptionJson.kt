package org.cryptobiotic.eg.publish.json

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// stuff used by the server webapp

@Serializable
@SerialName("EncryptionResponse")
data class EncryptionResponseJson(
    val confirmationCode : String
)