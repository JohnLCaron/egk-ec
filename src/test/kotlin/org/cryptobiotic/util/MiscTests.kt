package org.cryptobiotic.util

import kotlin.test.Test
import kotlin.test.assertEquals

class MiscTests {

    @Test
    fun testFoldAdd() {
        val cues = listOf(1,2,3)
        val sum = cues.fold(0) { a, b -> a + b }
        assertEquals(6, sum)

        val cues2 = emptyList<Int>()
        val sum2 = cues2.fold(0) { a, b -> a + b }
        assertEquals(0, sum2)
    }

    @Test
    fun testFoldMul() {
        val cues = listOf(1,2,3,4)
        val sum = cues.fold(1) { a, b -> a * b }
        assertEquals(24, sum)

        val cues2 = emptyList<Int>()
        val sum2 = cues2.fold(1) { a, b -> a * b }
        assertEquals(1, sum2)
    }

}