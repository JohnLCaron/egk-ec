package org.cryptobiotic.eg.publish.json

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.single
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

import org.cryptobiotic.eg.core.*
import org.cryptobiotic.eg.core.ecgroup.EcGroupContext
import org.cryptobiotic.eg.core.productionGroup
import org.cryptobiotic.eg.decrypt.ChallengeRequest
import org.cryptobiotic.eg.decrypt.ChallengeResponse
import org.cryptobiotic.eg.decrypt.PartialDecryptionOld

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
                elementsModQ(group, minimum = 2)
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
                Arb.int(min = 1, max = 11),
            ) {  nrequests ->
                val qList = List(nrequests) { elementsModP(group, minimum = 2).single() }
                val org = DecryptRequest(qList)
                val request = org.publishJson().import(group)
                assertTrue(request is Ok)
                assertEquals(org, request.unwrap())
            }
        }
    }

    @Test
    fun testDecryptResponse() {
        groups.forEach { testDecryptResponse(it) }
    }

    fun testDecryptResponse(group: GroupContext) {
        runTest {
            checkAll(
                propTestFastConfig,
                Arb.string(minSize = 3),
                Arb.int(min = 1, max = 11),
            ) {  name, nrequests ->
                val partials = List(nrequests) {
                    PartialDecryptionOld(
                    name + it,
                        elementsModP(group, minimum = 2).single(),
                        elementsModQ(group, minimum = 2).single(),
                        elementsModP(group, minimum = 2).single(),
                        elementsModP(group, minimum = 2).single(),
                    )
                }
                val org = DecryptResponse(partials)
                val response = org.publishJson().import(group)
                assertTrue(response is Ok)
                assertEquals(org, response.unwrap())
            }
        }
    }

    @Test
    fun testChallengeRequests() {
        groups.forEach { testChallengeRequests(it) }
    }

    fun testChallengeRequests(group: GroupContext) {
        runTest {
            checkAll(
                propTestFastConfig,
                Arb.string(minSize = 3),
                Arb.int(min = 1, max = 11),
            ) {  name, nrequests ->
                val crs = List(nrequests) {
                    ChallengeRequest(
                        name + it,
                        elementsModQ(group, minimum = 2).single(),
                        elementsModQ(group, minimum = 2).single(),
                    )
                }
                val org = ChallengeRequests(crs)
                val challenges = org.publishJson().import(group)
                assertTrue(challenges is Ok)
                assertEquals(org, challenges.unwrap())
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
                val crs = List(nrequests) {
                    ChallengeResponse(
                        name + it,
                        elementsModQ(group, minimum = 2).single(),
                    )
                }
                val org = ChallengeResponses(crs)
                val responses = org.publishJson().import(group)
                assertTrue(responses is Ok)
                assertEquals(org, responses.unwrap())
            }
        }
    }
}