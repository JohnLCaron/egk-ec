package org.cryptobiotic.eg.core.intgroup

import org.cryptobiotic.eg.core.Base16.fromHex
import org.cryptobiotic.eg.core.Base64.fromBase64Safe
import org.cryptobiotic.eg.core.Base64.toBase64
import org.cryptobiotic.eg.core.hashFunction
import org.cryptobiotic.eg.core.normalize
import org.cryptobiotic.eg.election.parameterBaseHash
import org.junit.jupiter.api.Test
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

class PrimesTest {

    @Test
    fun testPrimes4096() {
        Primes4096.pStr.forEach {
            assertTrue( !it.isWhitespace())
        }
        Primes4096.qStr.forEach {
            assertTrue( !it.isWhitespace())
        }
        Primes4096.rStr.forEach {
            assertTrue( !it.isWhitespace())
        }
        Primes4096.gStr.forEach {
            assertTrue( !it.isWhitespace())
        }

        checkPrime(Primes4096.qStr, 32)
        checkPrime(Primes4096.pStr, 512)
        checkPrime(Primes4096.rStr, 481)
        checkPrime(Primes4096.gStr, 512)
    }

    @Test
    fun testPrimes3072() {
        Primes3072.pStr.forEach {
            assertTrue( !it.isWhitespace())
        }
        Primes3072.qStr.forEach {
            assertTrue( !it.isWhitespace())
        }
        Primes3072.rStr.forEach {
            assertTrue( !it.isWhitespace())
        }
        Primes3072.gStr.forEach {
            assertTrue( !it.isWhitespace())
        }

        checkPrime(Primes3072.qStr, 32)
        checkPrime(Primes3072.pStr, 384)
        checkPrime(Primes3072.rStr, 353)
        checkPrime(Primes3072.gStr, 384)
    }

    fun checkPrime(primeString : String, expectSize : Int) {
        val fromHex = primeString.fromHex()
        assertNotNull(fromHex)
        assertEquals(expectSize, fromHex.size)

        val fromBI = BigInteger(primeString, 16).toByteArray()
        val fromBInormal = fromBI.normalize(expectSize)
        assertTrue(fromHex.contentEquals(fromBInormal))
    }

    // we have confirmation from Michael that this is the correct value of Hp
    @Test
    fun checkHp() {
        val version = "v2.0".toByteArray()
        val HV = ByteArray(32) { if (it < version.size) version[it] else 0 }

        val largePrime = Primes4096.pStr.fromHex()!!
        val smallPrime = Primes4096.qStr.fromHex()!!
        val generator = Primes4096.gStr.fromHex()!!

        val parameterBaseHash = hashFunction(
            HV,
            0x00.toByte(),
            largePrime,
            smallPrime,
            generator,
        )

        assertEquals("AB91D83C3DC3FEB76E57C2783CFE2CA85ADB4BC01FC5123EEAE3124CC3FB6CDE", parameterBaseHash.toHex())
    }

    @Test
    fun checkHp2() {
        val version = "2.0.0".toByteArray()
        val HV = ByteArray(32) { if (it < version.size) version[it] else 0 }

        val largePrime = Primes4096.pStr.fromHex()!!
        val smallPrime = Primes4096.qStr.fromHex()!!
        val generator = Primes4096.gStr.fromHex()!!

        val parameterBaseHash = hashFunction(
            HV,
            0x00.toByte(),
            largePrime,
            smallPrime,
            generator,
        )

        assertEquals("BAD5EEBFE2C98C9031BA8C36E7E4FB76DAC20665FD3621DF33F3F666BEC9AC0D", parameterBaseHash.toHex())
    }

    @Test
    fun checkHp3() {
        val version = "v2.0.0".toByteArray()
        val HV = ByteArray(32) { if (it < version.size) version[it] else 0 }

        val largePrime = Primes4096.pStr.fromHex()!!
        val smallPrime = Primes4096.qStr.fromHex()!!
        val generator = Primes4096.gStr.fromHex()!!

        val parameterBaseHash = hashFunction(
            HV,
            0x00.toByte(),
            largePrime,
            smallPrime,
            generator,
        )

        assertEquals("2B3B025E50E09C119CBA7E9448ACD1CABC9447EF39BF06327D81C665CDD86296", parameterBaseHash.toHex())
    }

    @Test
    fun parameterBaseHashTest() {
        val parameterBaseHash = parameterBaseHash(productionIntGroup().constants)
        assertEquals("2B3B025E50E09C119CBA7E9448ACD1CABC9447EF39BF06327D81C665CDD86296", parameterBaseHash.toHex())
    }

    @Test
    fun saneConstantsBig() {
        val p = Primes4096.largePrimeBytes.toBigInteger()
        val q = Primes4096.smallPrimeBytes.toBigInteger()
        val qInv = b64Production4096P256MinusQ.fromBase64Safe().toBigInteger()
        val g = Primes4096.generatorBytes.toBigInteger()
        val r = Primes4096.residualBytes.toBigInteger()

        val big1 = BigInteger.valueOf(1)

        assertTrue(p > big1)
        assertTrue(q > big1)
        assertTrue(g > big1)
        assertTrue(r > big1)
        assertTrue(qInv > big1)
        assertTrue(q < p)
        assertTrue(g < p)
    }

    @Test
    fun saneConstantsSmall() {
        val p = b64TestP.fromBase64Safe().toBigInteger()
        val q = b64TestQ.fromBase64Safe().toBigInteger()
        val g = b64TestG.fromBase64Safe().toBigInteger()
        val r = b64TestR.fromBase64Safe().toBigInteger()

        assertEquals(BigInteger.valueOf(intTestP.toLong()), p)
        assertEquals(BigInteger.valueOf(intTestQ.toLong()), q)
        assertEquals(BigInteger.valueOf(intTestG.toLong()), g)
        assertEquals(BigInteger.valueOf(intTestR.toLong()), r)
    }

    @Test
    fun expectedSmallConstants() {
        val pbytes = BigInteger.valueOf(intTestP.toLong()).toByteArray()
        val qbytes = BigInteger.valueOf(intTestQ.toLong()).toByteArray()
        val gbytes = BigInteger.valueOf(intTestG.toLong()).toByteArray()
        val rbytes = BigInteger.valueOf(intTestR.toLong()).toByteArray()

        val pp = pbytes.toBase64()
        val qq = qbytes.toBase64()
        val gg = gbytes.toBase64()
        val rr = rbytes.toBase64()

        assertEquals(b64TestQ, qq)
        assertEquals(b64TestG, gg)
        assertEquals(b64TestR, rr)
        assertEquals(b64TestP, pp)
    }


    @Test
    fun p256minusQBase64() {
        val p256 = BigInteger.valueOf(1) shl 256
        val q = Primes4096.smallPrimeBytes.toBigInteger()
        val p256minusQ = (p256 - q).toByteArray()
        assertEquals(2, p256minusQ.size)

        val p256minusQBase64 = p256minusQ.toBase64()
        assertEquals(p256minusQBase64, b64Production4096P256MinusQ)

        val p256minusQBytes = b64Production4096P256MinusQ.fromBase64Safe()
        assertEquals(2, p256minusQBytes.size)
    }

}