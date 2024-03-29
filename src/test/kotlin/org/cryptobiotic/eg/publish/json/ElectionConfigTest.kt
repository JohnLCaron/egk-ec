package org.cryptobiotic.eg.publish.json

import org.cryptobiotic.eg.core.*
import org.cryptobiotic.eg.election.*
import org.cryptobiotic.util.ErrorMessages
import org.cryptobiotic.eg.core.Base16.fromHex
import org.cryptobiotic.eg.core.ecgroup.EcGroupContext
import org.cryptobiotic.eg.core.intgroup.ProductionMode
import org.cryptobiotic.eg.core.productionGroup
import org.cryptobiotic.eg.core.intgroup.tinyGroup

import kotlin.test.*

class ElectionConfigTest {
    val groups = listOf(
        productionGroup("Integer3072"),
        productionGroup("Integer4096"),
        EcGroupContext("P-256"),
        tinyGroup(),
    )

    @Test
    fun testRoundtrip() {
        groups.forEach { roundtripTest(it) }
    }

    fun roundtripTest(group: GroupContext) {
        val constants = group.constants
        val manifestBytes = "1234ABC".fromHex()!!
        val config = makeConfig(constants, manifestBytes)
        val json = config.publishJson()
        val errs = ErrorMessages("roundtripTest")
        val subject = json.import(constants, manifestBytes, errs)
        assertNotNull(subject)
        assertFalse(errs.hasErrors())
        assertEquals(config, subject)
    }

    fun makeConfig(constants: ElectionConstants, manifestBytes: ByteArray) = ElectionConfig(
        "configVersion",
        constants,
        7, 4,
        UInt256.random(),
        UInt256.random(),
        UInt256.random(),
        manifestBytes,
        true,
        "ABCDEF".fromHex()!!,
        mapOf(),
    )

    @Test
    fun badField1Test() {
        groups.forEach { badField1Test(it) }
    }

    fun badField1Test(group: GroupContext) {
        val constants = group.constants
        val manifestBytes = "1234ABC".fromHex()!!
        val config = makeConfig(constants, manifestBytes)
        val json = config.publishJson()

        val badjson = json.copy(parameter_base_hash = UInt256Json(manifestBytes))
        val errs = ErrorMessages("")
        val subject = badjson.import(constants, manifestBytes, errs)
        assertNull(subject)
        assertTrue(errs.hasErrors())
        assertTrue(errs.contains("malformed parameter_base_hash"))
    }

    @Test
    fun badField2Test() {
        groups.forEach { badField2Test(it) }
    }

    fun badField2Test(group: GroupContext) {
        val constants = group.constants
        val manifestBytes = "1234ABC".fromHex()!!
        val config = makeConfig(constants, manifestBytes)
        val json = config.publishJson()

        val badjson = json.copy(manifest_hash = UInt256Json(manifestBytes))
        val errs = ErrorMessages("")
        val subject = badjson.import(constants, manifestBytes, errs)
        assertNull(subject)
        assertTrue(errs.hasErrors())
        assertTrue(errs.contains("malformed manifest_hash"))
    }

    @Test
    fun badField3Test() {
        groups.forEach { badField3Test(it) }
    }

    fun badField3Test(group: GroupContext) {
        val constants = group.constants
        val manifestBytes = "1234ABC".fromHex()!!
        val config = makeConfig(constants, manifestBytes)
        val json = config.publishJson()

        val badjson = json.copy(election_base_hash = UInt256Json(manifestBytes))
        val errs = ErrorMessages("")
        val subject = badjson.import(constants, manifestBytes, errs)
        assertNull(subject)
        assertTrue(errs.hasErrors())
        assertTrue(errs.contains("malformed election_base_hash"))
    }

    @Test
    fun emptyBauxTest() {
        val group = EcGroupContext("P-256")
        val constants = group.constants
        val manifestBytes = "1234ABC".fromHex()!!
        val config = ElectionConfig(
            "emptyBauxTest",
            constants,
            7, 4,
            UInt256.random(),
            UInt256.random(),
            UInt256.random(),
            manifestBytes,
            true,
            ByteArray(0)
        )
        val json = config.publishJson()

        val errs = ErrorMessages("emptyBauxTest")
        val roundtrip = json.import(constants, manifestBytes, errs)
        assertNotNull(roundtrip)
        assertNotNull(roundtrip.configBaux0)
        assertTrue(roundtrip.configBaux0.size == 0)
        assertFalse(errs.hasErrors())
        assertEquals(config, roundtrip)
    }

}