package org.cryptobiotic.util

import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StopWatchTest {

    @Test
    fun basics() {
        val stopwatchNotRunning =  Stopwatch(false)
        assertEquals(0, stopwatchNotRunning.stop())
        stopwatchNotRunning.start()
        assertTrue(stopwatchNotRunning.stop() > 0)

        val stopwatch =  Stopwatch()
        assertTrue(stopwatch.stop() > 0)
    }

    @Test
    fun elapsed() {
        val stopwatch =  Stopwatch()
        val elapsed1 = stopwatch.elapsed(TimeUnit.NANOSECONDS)
        val elapsed2 = stopwatch.elapsed(TimeUnit.NANOSECONDS)
        assertTrue(elapsed2 > elapsed1)

        val elapsed3 = stopwatch.elapsed()
        val elapsed4 = stopwatch.elapsed()
        assertTrue(elapsed4 >= elapsed3)

        val elapsed6 = stopwatch.elapsed(TimeUnit.SECONDS)
        Thread.sleep(1001)
        val elapsed7 = stopwatch.elapsed(TimeUnit.SECONDS)
        assertTrue(elapsed7 > elapsed6)
    }

    @Test
    fun convert() {
        val stopwatch =  Stopwatch(false)
        println(stopwatch)
        assertTrue(stopwatch.toString().contains("ns"))

        stopwatch.start()
        stopwatch.stop()
        println(stopwatch)
        assertTrue(stopwatch.toString().contains("μs"))

        stopwatch.start()
        Thread.sleep(1)
        stopwatch.stop()
        println(stopwatch)
        assertTrue(stopwatch.toString().contains("ms"))

        stopwatch.start()
        Thread.sleep(1000)
        stopwatch.stop()
        println(stopwatch)
        assertTrue(stopwatch.toString().endsWith("s"))
    }

}