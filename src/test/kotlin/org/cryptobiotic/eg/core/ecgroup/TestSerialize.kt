package org.cryptobiotic.eg.core.ecgroup

import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.cryptobiotic.eg.core.*
import org.cryptobiotic.eg.core.Base64.toBase64
import org.cryptobiotic.eg.publish.json.import
import org.cryptobiotic.eg.publish.json.publishJson
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.test.Test
import kotlin.test.assertEquals

class TestSerialize {

    @Test
    fun testElementType() {
        val group = productionGroup("P-256")
        runTest {
            checkAll(
                elementsModP(group),
            ) { p ->
                assertTrue(p is EcElementModP)
                val ec = (p as EcElementModP).ec
                if (VecGroups.hasNativeLibrary()) {
                    assertTrue(ec is VecElementPnative)
                }
            }
        }
    }

    @Test
    fun testElementPublishImport() {
        val group = productionGroup("P-256")
        runTest {
            checkAll(
                iterations = 10,
                elementsModP(group),
            ) { p ->
                assertEquals(p, p.publishJson().import(group))
                val ps = p.publishJson().toString()
                println("ps = $ps len = ${ps.length} p.size = ${p.byteArray().size}")

                val ec = (p as EcElementModP).ec
                val vec1 = ec.toByteArray1()
                val vec1s = vec1.toBase64()
                println("vec1s = $vec1s len = ${vec1s.length} byte size = ${vec1.size}")
            }
        }
    }

    @Test
    fun test1vs2() {
        val group = productionGroup("P-256")
        runTest {
            checkAll(
                iterations = 10000,
                elementsModP(group),
            ) { p ->
                val ec = (p as EcElementModP).ec
                val vecGroup = ec.pGroup
                val convert2: VecElementP? = vecGroup.elementFromByteArray2(ec.toByteArray2())
                assertNotNull(convert2)
                assertEquals(ec, convert2)

                val convert12: VecElementP? = vecGroup.elementFromByteArray1from2(ec.toByteArray2())
                assertNotNull(convert12)
                assertEquals(ec, convert12)

                val convert1: VecElementP? = vecGroup.elementFromByteArray1(ec.toByteArray1())
                assertNotNull(convert1)
                assertEquals(ec, convert1)
            }
        }
    }

}