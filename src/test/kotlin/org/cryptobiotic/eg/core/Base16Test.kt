package org.cryptobiotic.eg.core

import io.kotest.property.Arb
import io.kotest.property.arbitrary.byte
import io.kotest.property.arbitrary.byteArray
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.positiveLong
import io.kotest.property.checkAll
import org.cryptobiotic.eg.core.Base16.fromHex
import org.cryptobiotic.eg.core.Base16.toHex
import kotlin.test.*

class Base16Test {

    ////// fromHex
    @Test
    fun emptyFieldTest() {
        val subject = "".fromHex()
        assertNotNull(subject)
        assertEquals(0, subject.size)
    }

    @Test
    fun goodCharsTest() {
        val subject = "ABCDEF012345670".fromHex()
        assertNotNull(subject)
        assertEquals(8, subject.size)
        assertEquals("[10, -68, -34, -16, 18, 52, 86, 112]", subject.contentToString())
    }

    @Test
    fun badCharsTest() {
        val subject = "ABCDEFG12345670".fromHex()
        assertNull(subject)
    }

    @Test
    fun oddCharsTest() {
        val subject = "ABCDEF12345670".fromHex()
        assertNotNull(subject)
        assertEquals(7, subject.size)
        assertEquals("[-85, -51, -17, 18, 52, 86, 112]", subject.contentToString())
    }

    ////// toHex
    @Test
    fun emptyFieldToTest() {
        val subject = ByteArray(0).toHex()
        assertNotNull(subject)
        assertTrue(subject.isEmpty())
    }

    @Test
    fun goodToTest() {
        val subject = byteArrayOf(-85, -51, -17, 18, 52, 86, 112).toHex()
        assertNotNull(subject)
        assertEquals("ABCDEF12345670", subject)
    }

    @Test
    fun basicsBase16() {
        val bytes = 1.toBigInteger().toByteArray()
        val b16lib = bytes.toHex()
        assertEquals("01", b16lib)

        val bytesAgain = b16lib.fromHex()
        assertContentEquals(bytes, bytesAgain)
    }

    @Test
    fun badInputFails() {
        assertNull("XYZZY".fromHex())
    }

    @Test
    fun comparingBase16() {
        runTest {
            checkAll(Arb.positiveLong()) { x ->
                val bytes = x.toBigInteger().toByteArray()
                val b16lib = bytes.toHex()
                val bytesAgain = b16lib.fromHex()
                assertContentEquals(bytes, bytesAgain)
            }
        }
    }

    @Test
    fun comparingBase16ToJavaByteArray() {
        runTest {
            checkAll(Arb.byteArray(Arb.int(1, 200), Arb.byte())) { bytes ->
                val b16lib = bytes.toHex()
                val bytesAgain = b16lib.fromHex()
                assertContentEquals(bytes, bytesAgain)
            }
        }
    }

}