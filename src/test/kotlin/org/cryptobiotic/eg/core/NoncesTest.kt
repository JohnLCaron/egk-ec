package org.cryptobiotic.eg.core

import kotlinx.coroutines.test.runTest
import org.cryptobiotic.eg.core.Base64.toBase64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class NoncesTest {
    @Test
    fun equalsTest() {
        runTest {
            val context = productionGroup()
            val nonces1 = Nonces(context.ONE_MOD_Q, "sample_text1")
            val nonces1p = Nonces(context.ONE_MOD_Q, "sample_text1")
            val nonces2 = Nonces(context.ONE_MOD_Q, "sample_text2")

            assertEquals(nonces1, nonces1p)
            assertNotEquals(nonces1, nonces2)
            assertNotEquals(nonces1.hashCode(), nonces2.hashCode())

            assertEquals(nonces1.toString(), "Nonces(jfuELVNiNwQGvH+9FDEUhpZiQRBnKbheWeqQ8HJ8eYo=)")

            assertEquals(nonces1, nonces1p)
            println("${nonces1.internalSeed.toBase64()} vs ${nonces1p.internalSeed.toBase64()}")
            assertEquals(nonces1.internalSeed.toBase64(), nonces1p.internalSeed.toBase64())

            println("${nonces1.hashCode()} vs ${nonces1p.hashCode()}")
            assertEquals(nonces1.hashCode(), nonces1p.hashCode())
        }
    }

    @Test
    fun sequencesAreLazy() {
        runTest {
            val context = productionGroup()
            val nonces = Nonces(context.ONE_MOD_Q, "sample_text")
            val expected2 = nonces.asPair().toList()
            val expected3 = nonces.asTriple().toList()

            // If there was eager rather than lazy behavior, this would take forever
            // and the test timeout would activate.
            val actual2 = nonces.asSequence().take(2).toList()
            val actual3 = nonces.asSequence().take(3).toList()

            assertEquals(expected2, actual2)
            assertEquals(expected3, actual3)
        }
    }

    @Test
    fun noncesSupportDestructuring() {
        runTest {
            val context = productionGroup()
            val nonces = Nonces(context.ONE_MOD_Q, "sample_text")
            val expected0 = nonces[0]
            val expected1 = nonces[1]
            val (actual0, actual1) = nonces.asPair()

            assertEquals(expected0, actual0)
            assertEquals(expected1, actual1)

            val (also0, also1) = nonces
            assertEquals(expected0, also0)
            assertEquals(expected1, also1)
        }
    }

    @Test
    fun noncesSupportComponents() {
        runTest {
            val context = productionGroup()
            val nonces = Nonces(context.ONE_MOD_Q, "sample_text")

            assertEquals(nonces[0], nonces.component1())
            assertEquals(nonces[1], nonces.component2())
            assertEquals(nonces[2], nonces.component3())
            assertEquals(nonces[3], nonces.component4())
            assertEquals(nonces[4], nonces.component5())
            assertEquals(nonces[5], nonces.component6())
            assertEquals(nonces[6], nonces.component7())
            assertEquals(nonces[7], nonces.component8())
            assertEquals(nonces[8], nonces.component9())
        }
    }
}
