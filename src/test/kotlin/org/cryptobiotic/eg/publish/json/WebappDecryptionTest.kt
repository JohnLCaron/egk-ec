package org.cryptobiotic.eg.publish.json

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.single
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

import org.cryptobiotic.eg.core.*
import org.cryptobiotic.eg.core.ecgroup.EcGroupContext
import org.cryptobiotic.eg.core.productionGroup
import org.cryptobiotic.eg.decrypt.ChallengeResponses
import org.cryptobiotic.eg.decrypt.PartialDecryption
import org.cryptobiotic.eg.decrypt.PartialDecryptions

class WebappDecryptionTest {
    val groups = listOf(
        productionGroup("Integer3072"),
        productionGroup("Integer4096"),
        EcGroupContext("P-256")
    )

    @Test
    fun testSetMissingRequest() {
        groups.forEach { testSetMissingRequest(it) }
    }

    fun testSetMissingRequest(group: GroupContext) {
        runTest {
            checkAll(
                propTestFastConfig,
                Arb.string(minSize = 3),
                Arb.int(min = 1, max = 5),
                elementsModQ(group)
            ) { name, nmissing, lc ->
                val miss = List(nmissing) { name + it }
                val org = SetMissingRequest(lc, miss)

                assertEquals(org, org.publishJson().import(group))
            }
        }
    }

    @Test
    fun testDecryptRequest() {
        groups.forEach { testDecryptRequest(it) }
    }

    fun testDecryptRequest(group: GroupContext) {
        runTest {
            checkAll(
                propTestFastConfig,
                Arb.string(minSize = 3),
                Arb.int(min = 1, max = 11),
            ) {  name, nrequests ->
                val drequest =
                    DecryptRequest(
                        listOf( elementsModP(group).single(),
                        elementsModP(group).single(),
                        elementsModP(group).single())
                    )
                val drequestj = drequest.publishJson()
                val roundtrip = drequestj.import(group)
                assert (roundtrip is Ok)
                assertEquals(drequest.texts, roundtrip.unwrap())
            }
        }
    }

    @Test
    fun testPartialDecryptions() {
        groups.forEach { testPartialDecryptions(it) }
    }

    fun testPartialDecryptions(group: GroupContext) {
        runTest {
            checkAll(
                propTestFastConfig,
                Arb.string(minSize = 3),
                Arb.int(min = 1, max = 11),
            ) {  name, nrequests ->
                val crs = List(nrequests) {
                    PartialDecryption(
                        elementsModP(group).single(),
                        elementsModP(group).single(),
                        elementsModP(group).single(),
                    )
                }
                val org = PartialDecryptions(null, 42, crs)
                val challenges = org.publishJson().import(group)
                assertTrue(challenges is Ok)
                assertEquals(org, challenges.unwrap())
            }
        }
    }

    @Test
    fun testChallengeRequest() {
        groups.forEach { testChallengeRequest(it) }
    }

    fun testChallengeRequest(group: GroupContext) {
        runTest {
            checkAll(
                propTestFastConfig,
                Arb.int(min = 1, max = 122221),
                Arb.int(min = 1, max = 11),
            ) {  batchId, nrequests ->
                val crs = List(nrequests) { elementsModQ(group).single() }
                // ChallengeRequest(val batchId: Int, val texts: List<ElementModQ>)
                val org = ChallengeRequest(batchId, crs)
                val responses = org.publishJson().import(group)
                assertTrue(responses is Ok)
                assertEquals(org, responses.unwrap())
            }
        }
    }

    @Test
    fun testChallengeResponses() {
        groups.forEach { testChallengeResponses(it) }
    }

    fun testChallengeResponses(group: GroupContext) {
        runTest {
            checkAll(
                propTestFastConfig,
                Arb.string(minSize = 3),
                Arb.int(min = 1, max = 11),
            ) {  name, nrequests ->
                val crs = List(nrequests) { elementsModQ(group).single() }
                val org = ChallengeResponses(null, 42, crs)
                val responses = org.publishJson().import(group)
                assertTrue(responses is Ok)
                assertEquals(org, responses.unwrap())
            }
        }
    }
}