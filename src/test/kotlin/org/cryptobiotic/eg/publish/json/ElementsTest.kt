package org.cryptobiotic.eg.publish.json

import org.cryptobiotic.eg.core.*
import org.cryptobiotic.eg.core.Base16.fromHex
import org.cryptobiotic.eg.core.productionGroup

import io.kotest.property.checkAll
import kotlin.test.*
import kotlinx.serialization.json.*
import org.cryptobiotic.eg.core.ecgroup.EcGroupContext
import org.cryptobiotic.eg.core.intgroup.ProductionMode
import org.cryptobiotic.eg.core.intgroup.tinyGroup


inline fun <reified T> jsonRoundTripWithStringPrimitive(value: T): T {
    val jsonReader = Json { explicitNulls = false; ignoreUnknownKeys = true; prettyPrint = true }

    val jsonT: JsonElement = jsonReader.encodeToJsonElement(value)

    if (jsonT is JsonPrimitive) {
        assertTrue(jsonT.isString)
        assertNotNull(jsonT.content.fromHex()) // validates that we have a base16 string
    } else {
        fail("expected jsonT to be JsonPrimitive")
    }

    val jsonS = jsonT.toString()
    val backToJ: JsonElement = jsonReader.parseToJsonElement(jsonS)
    val backToT: T = jsonReader.decodeFromJsonElement(backToJ)
    return backToT
}

class ElementsTest {
    val groups = listOf(
        tinyGroup(),
        productionGroup("Integer group 3072"),
        productionGroup("Integer group 4096"),
        EcGroupContext("P-256")
    )

    @Test
    fun testElementRoundtrip() {
        groups.forEach { testElementRoundtrip(it) }
    }

    fun testElementRoundtrip(group: GroupContext) {
        runTest {
            checkAll(elementsModP(group), elementsModQ(group)) { p, q ->
                assertEquals(p, p.publishJson().import(group))
                assertEquals(q, q.publishJson().import(group))

                // longer round-trip through serialized JSON strings and back
                assertEquals(p, jsonRoundTripWithStringPrimitive(p.publishJson()).import(group))
                assertEquals(q, jsonRoundTripWithStringPrimitive(q.publishJson()).import(group))
            }
        }
    }

    @Test
    fun importTinyElements() {
        runTest {
            val group = tinyGroup()
            checkAll(elementsModP(group), elementsModQ(group)) { p, q ->
                // shorter round-trip from the core classes to JsonElement and back
                assertEquals(p, p.publishJson().import(group))
                assertEquals(q, q.publishJson().import(group))

                // longer round-trip through serialized JSON strings and back
                assertEquals(p, jsonRoundTripWithStringPrimitive(p.publishJson()).import(group))
                assertEquals(q, jsonRoundTripWithStringPrimitive(q.publishJson()).import(group))
            }
        }
    }

    @Test
    fun testUInt256Roundtrip() {
        groups.forEach { testUInt256Roundtrip(it) }
    }

    fun testUInt256Roundtrip(group: GroupContext) {
        runTest {
            checkAll(elementsModQ(group)) { q ->
                val u : UInt256 = q.toUInt256safe()
                assertEquals(u, u.publishJson().import())
                assertEquals(u, jsonRoundTripWithStringPrimitive(u.publishJson()).import())
            }
        }
    }
}