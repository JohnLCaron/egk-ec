package org.cryptobiotic.eg.publish.json

import org.cryptobiotic.util.ErrorMessages
import org.cryptobiotic.eg.core.productionGroup

import kotlin.test.*

class ElectionConstantsTest {
    @Test
    fun badFieldsTest() {
        val errs = ErrorMessages("badFieldsTest")
        var json = ElectionConstantsJson(
            "any", "IntegerGroup", "any",
            mapOf("largePrime" to "any")
        )
        json.import(errs)
        assertContains(errs.toString(), "should be BigInteger in hex")
    }

    @Test
    fun missingFieldsTest() { // TODO no failure
        val errs = ErrorMessages("badFieldsTest")
        var json = ElectionConstantsJson(
            "any", "IntegerGroup", "any",
            mapOf("largePrime" to "123809afe")
        )
        val good = json.import(errs)
        assertFalse(errs.hasErrors())
        assertNotNull(good)
    }

    @Test
    fun goodTest() {
        val good = ElectionConstantsJson(
            "any", "IntegerGroup", "any",
            mapOf("largePrime" to "1234", "smallPrime" to "1234", "generator" to "1234")
        ).import(ErrorMessages("goodTest"))
        assertNotNull(good)
    }

    @Test
    fun roundtripTest() {
        val group = productionGroup()
        val json = group.constants.publishJson()
        val subject = json.import(ErrorMessages("roundtripTest"))
        assertNotNull(subject)
        assertEquals(group.constants, subject)
    }

}