package org.cryptobiotic.eg.publish.json

import org.cryptobiotic.eg.core.*
import org.cryptobiotic.eg.core.productionGroup

import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import org.cryptobiotic.eg.core.ecgroup.EcGroupContext
import org.cryptobiotic.eg.core.intgroup.tinyGroup
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalSerializationApi::class)
inline fun <reified T> jsonRoundTrip(value: T): T {
    val jsonReader = Json { explicitNulls = false; ignoreUnknownKeys = true; prettyPrint = true }

    val jsonT: JsonElement = jsonReader.encodeToJsonElement(value)
    val jsonS = jsonT.toString()
    val backToJ: JsonElement = jsonReader.parseToJsonElement(jsonS)
    val backToT: T = jsonReader.decodeFromJsonElement(backToJ)
    return backToT
}

class ChaumPedersenJsonTest {
    val groups = listOf(
        tinyGroup(),
        productionGroup("Integer3072"),
        productionGroup("Integer4096"),
        EcGroupContext("P-256")
    )

    @Test
    fun testRoundtrip() {
        groups.forEach { testRoundtrip(it) }
    }

    fun testRoundtrip(group: GroupContext) {
        println("Using group ${group.constants.name}")
        runTest {
            checkAll(
                iterations = 33,
                elementsModQ(group),
                elementsModQ(group),
            ) { challenge, response ->
                val goodProof = ChaumPedersenProof(challenge, response)
                assertEquals(goodProof, goodProof.publishJson().import(group))
                assertEquals(goodProof, jsonRoundTrip(goodProof.publishJson()).import(group))
            }
        }
    }

}