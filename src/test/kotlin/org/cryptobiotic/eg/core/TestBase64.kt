package org.cryptobiotic.eg.core

import io.kotest.property.Arb
import io.kotest.property.arbitrary.byte
import io.kotest.property.arbitrary.byteArray
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.positiveLong
import io.kotest.property.checkAll
import io.kotest.property.forAll

import org.cryptobiotic.eg.core.Base16.toHex
import org.cryptobiotic.eg.core.Base16.fromHexSafe
import org.cryptobiotic.eg.core.Base64.fromBase64
import org.cryptobiotic.eg.core.Base64.fromBase64Safe
import org.cryptobiotic.eg.core.Base64.fromBase64Url
import org.cryptobiotic.eg.core.Base64.toBase64
import org.cryptobiotic.eg.core.Base64.toBase64Url
import org.cryptobiotic.eg.core.intgroup.ProductionGroupContext
import java.nio.charset.StandardCharsets
import kotlin.test.*

class TestBase64 {
    @Test
    fun base64RoundTrip() {
        runTest {
            val context = productionGroup()
            forAll(propTestFastConfig, elementsModP(context)) {
                it == context.base64ToElementModP(it.base64())
            }
            forAll(propTestFastConfig, elementsModQ(context)) {
                it == context.base64ToElementModQ(it.base64())
            }
        }
    }

    fun GroupContext.base64ToElementModQ(s: String): ElementModQ? =
        s.fromBase64()?.let { binaryToElementModQ(it) }

    fun GroupContext.base64ToElementModP(s: String): ElementModP? =
        s.fromBase64()?.let { binaryToElementModP(it) }

    // Converts from any [Element] to a base64 string representation.
    fun Element.base64(): String = byteArray().toBase64()

    @Test
    fun baseConversionPreturnsNull() {
        runTest {
            val context = productionGroup()
            listOf("", "@@", "-10", "1234567890".repeat(1000))
                .forEach {
                    assertNull(context.base64ToElementModP(it))
                }
        }
    }

    @Test
    fun baseConversionQreturnsNull() {
        runTest {
            val context = productionGroup()
            listOf("", "@@", "-10")
                .forEach {
                    assertNull(context.base64ToElementModQ(it))
                }
        }
    }

    @Test
    fun baseConversionQthrows() {
        runTest {
            val context = productionGroup()
            val test = "1234567890".repeat(1000)
            val e1 = assertFailsWith<IllegalArgumentException>(block = { println(" convert = ${context.base64ToElementModQ(test)}") })
        }
    }


    @Test
    fun comparingBase64ToJavaLong() {
        val encoder = java.util.Base64.getEncoder()
        runTest {
            checkAll(Arb.positiveLong()) { x ->
                val bytes = x.toBigInteger().toByteArray()
                val b64lib = bytes.toBase64()
                val j64lib = String(encoder.encode(bytes), StandardCharsets.ISO_8859_1)

                assertEquals(j64lib, b64lib)

                val bytesAgain = b64lib.fromBase64()
                assertContentEquals(bytes, bytesAgain)
            }
        }
    }

    @Test
    fun comparingBase64ToJavaByteArray() {
        val encoder = java.util.Base64.getEncoder()
        runTest {
            checkAll(Arb.byteArray(Arb.int(1, 200), Arb.byte())) { bytes ->
                val b64lib = bytes.toBase64()
                val j64lib = String(encoder.encode(bytes), StandardCharsets.ISO_8859_1)

                assertEquals(j64lib, b64lib)

                val bytesAgain = b64lib.fromBase64()
                assertContentEquals(bytes, bytesAgain)
            }
        }
    }

    @Test
    fun comparingBase64UrlToJavaLong() {
        val encoder = java.util.Base64.getUrlEncoder()
        runTest {
            checkAll(Arb.positiveLong()) { x ->
                val bytes = x.toBigInteger().toByteArray()
                val b64lib = bytes.toBase64Url()
                val j64lib = String(encoder.encode(bytes), StandardCharsets.ISO_8859_1)

                assertEquals(j64lib, b64lib)

                val bytesAgain = b64lib.fromBase64Url()
                assertContentEquals(bytes, bytesAgain)
            }
        }
    }

    @Test
    fun comparingBase64UrlToJavaByteArray() {
        val encoder = java.util.Base64.getUrlEncoder()
        runTest {
            checkAll(Arb.byteArray(Arb.int(1, 200), Arb.byte())) { bytes ->
                val b64lib = bytes.toBase64Url()
                val j64lib = String(encoder.encode(bytes), StandardCharsets.ISO_8859_1)

                assertEquals(j64lib, b64lib)

                val bytesAgain = b64lib.fromBase64Url()
                assertContentEquals(bytes, bytesAgain)
            }
        }
    }

    @Test
    fun testConstants() {
        val group = productionGroup("Integer4096")
        val constants = (group as ProductionGroupContext).groupConstants

        println("smallPrime size= ${constants.smallPrime.toByteArray().size}")
        println("smallPrime     = ${constants.smallPrime.toHex()}")
        println("qnormal        = ${constants.smallPrime.toByteArray().normalize(32).toHex()}")

        val q64 = "AP////////////////////////////////////////9D"
        val q64Bytes = q64.fromBase64Safe()
        println("q64 size= ${q64Bytes.size}")
        println("q64     = ${q64Bytes.toHex()}")

        val q16 = "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF43"
        val q16Bytes = q16.fromHexSafe()
        println("q16 size= ${q16Bytes.size}")
        println("q16     = ${q16Bytes.toHex()}")

        println("\n====================\n")
        println("largePrime size= ${constants.largePrime.toByteArray().size}")
        println("largePrime     = ${constants.largePrime.toHex()}")
        println("pnormal        = ${constants.largePrime.toByteArray().normalize(512).toHex()}")

        val p64 =
            "AP//////////////////////////////////////////k8Rn432wx6TRvj+BAVLLVqHOzDr2XMAZDAPfNHCa/72OS1n6A6nw7tBknMtiEFfREFaukTITWgjkO0Zz10uv6ljeuHjMhtcz2+e/OBVLNs+KltFWeJmqrgwJ1Mi2t7hv0qHqHeYv+GQ+x8Jxgnl3Il5qwvC9YcdGlhVCo8476l21T+cOY+bQn4/ChljoBWekfP3mDudB5dhae9RpMc7YIgNlWUlkuDmJb8qrzMmzGVnAg/Iq0+5ZHDL6ssdEjyoFfbLbSe5S4BgnQeU4ZfAEzI5wS3xcQL8wTE2MTxPt9gR8VVMC0iONjOEd8kJPG2bCxdI40HRNtnmvKJBIcDH5wK6hxLtv6VVO5Sj98bBeWyViI7LwkhXzcZ+cfMxp3fFy0NYjQhf8wAN/GLk+9TiRMLemYeXCblQhQGi7yv6jKmeBi9MHWtH1x+nMPRc3+ygXG6+E27ZhK3iBwaSOQ5zQOpK/UiJaKzjmVC6fcivOFaOBtXU+qEJ2M4HMroNRKzBRGzLl6NgDYhSa0DCqul86V5i7Iqp+wbbQ8XkD9OIthAc0qoWXP3mpP/uCp1xHwD1D0vnKAtAxmbrO3dRTOlJWav//////////////////////////////////////////"
        val p64Bytes = p64.fromBase64Safe()
        println("p64 size= ${p64Bytes.size}")
        println("p64     = ${p64Bytes.toHex()}")
        println("p64     = ${p64Bytes.toBase64()}")
        assertEquals(p64, p64Bytes.toBase64())

        val p16 =
            "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF93C467E37DB0C7A4D1BE3F810152CB56A1CECC3AF65CC0190C03DF34709AFFBD8E4B59FA03A9F0EED0649CCB621057D11056AE9132135A08E43B4673D74BAFEA58DEB878CC86D733DBE7BF38154B36CF8A96D1567899AAAE0C09D4C8B6B7B86FD2A1EA1DE62FF8643EC7C271827977225E6AC2F0BD61C746961542A3CE3BEA5DB54FE70E63E6D09F8FC28658E80567A47CFDE60EE741E5D85A7BD46931CED8220365594964B839896FCAABCCC9B31959C083F22AD3EE591C32FAB2C7448F2A057DB2DB49EE52E0182741E53865F004CC8E704B7C5C40BF304C4D8C4F13EDF6047C555302D2238D8CE11DF2424F1B66C2C5D238D0744DB679AF2890487031F9C0AEA1C4BB6FE9554EE528FDF1B05E5B256223B2F09215F3719F9C7CCC69DDF172D0D6234217FCC0037F18B93EF5389130B7A661E5C26E54214068BBCAFEA32A67818BD3075AD1F5C7E9CC3D1737FB28171BAF84DBB6612B7881C1A48E439CD03A92BF52225A2B38E6542E9F722BCE15A381B5753EA842763381CCAE83512B30511B32E5E8D80362149AD030AABA5F3A5798BB22AA7EC1B6D0F17903F4E22D840734AA85973F79A93FFB82A75C47C03D43D2F9CA02D03199BACEDDD4533A52566AFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF"
        val p16Bytes = p16.fromHexSafe()
        println("p16 size= ${p16Bytes.size}")
        println("p16     = ${p16Bytes.toHex()}")
        assertEquals(p16, p16Bytes.toHex())

        assertEquals(32, constants.smallPrime.toByteArray().normalize(32).size)
        assertEquals(512, constants.largePrime.toByteArray().normalize(512).size)
    }
}