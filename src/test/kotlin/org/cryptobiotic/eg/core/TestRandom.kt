package org.cryptobiotic.eg.core

import org.junit.jupiter.api.Test
import java.security.DrbgParameters
import java.security.SecureRandom
import java.security.Security
import java.util.*
import kotlin.test.assertFalse
import kotlin.test.assertTrue


class TestRandom {

    @Test
    fun testSecureRandom() {
        val rng = SecureRandom.getInstanceStrong()
        println("SecureRandom.getInstanceStrong")
        println("  algo=${rng.algorithm}")
        println("  params=${rng.parameters}")
        println("  provider=${rng.provider}")
    }

    @Test
    fun showAlgorithms() {
        val algorithms = Security.getAlgorithms ("SecureRandom");
        println("Available algorithms")
        algorithms.forEach { println("  $it") }
    }

    @Test
    fun showDRBG() {
        val rng = SecureRandom.getInstance("DRBG")
        println("SecureRandom.getInstanceStrong")
        println("  algo=${rng.algorithm}")
        println("  params=${rng.parameters}")
        println("  provider=${rng.provider}")
        println("  rng=${rng}")
        println("  class=${rng.javaClass.getName()}")
    }

    @Test
    fun testDRBG() {
        val n = 1
        val r1 = SecureRandom.getInstance("DRBG")
        val r2 = SecureRandom.getInstance("DRBG")
        assertFalse(r1 === r2)

        r1.setSeed(1234567L)
        r2.setSeed(1234567L)

        val ba1 = r1.fill(32, n)
        val ba2 = r2.fill(32, n)

        // not deterministic
        repeat (n) {
            assertFalse(ba1[it].contentEquals(ba2[it]))
        }
    }

    @Test
    fun testRandom() {
        val n = 1000
        val r1 = Random()
        val r2 = Random()
        assertFalse(r1 === r2)

        r1.setSeed(1234567L)
        r2.setSeed(1234567L)

        val ba1 = r1.fill(32, n)
        val ba2 = r2.fill(32, n)

        repeat (n) {
            assertTrue(ba1[it].contentEquals(ba2[it]))
        }
    }

    fun Random.fill(size: Int, n: Int): List<ByteArray> {
        val result = mutableListOf<ByteArray>()
        repeat (n) {
            val ba = ByteArray(size)
            this.nextBytes(ba)
            result.add(ba)
        }
        return result
    }

}