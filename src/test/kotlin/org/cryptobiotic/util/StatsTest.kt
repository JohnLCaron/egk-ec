package org.cryptobiotic.util

import kotlin.test.Test
import kotlin.test.assertEquals

class StatsTest {
    val f = 1_000_000L // convert to nanosecs

    @Test
    fun testStatEmpty() {
        val stat = Stat("thing", "what")
        assertEquals("took    0 msecs = .000 msecs/thing (0 things) = .000 msecs/what for 0 whats", stat.show(4))
        assertEquals(0, stat.count())
    }

    @Test
    fun testStat() {
        val stat = Stat("thing", "what")
        stat.accum(99 * f, 2)
        stat.accum(101 * f, 2)
        stat.accum(15 * f, 11)
        assertEquals("took  215 msecs = 14.33 msecs/thing (15 things) = 71.67 msecs/what for 3 whats", stat.show(4))
        assertEquals(3, stat.count())
    }

    @Test
    fun testStatsEmpty() {
        val stats = Stats()
        assertEquals(listOf("stats is empty"), stats.showLines())
    }

    @Test
    fun testStatsStatEmpty() {
        val stats = Stats()
        stats.of("widgets")
        assertEquals(
            listOf(
                "             widgets: took    0 msecs = .000 msecs/decryption (0 decryptions) = .000 msecs/ballot for 0 ballots",
                "               total: took    0 msecs = .000 msecs/decryption (0 decryptions) = .000 msecs/ballot for 0 ballots"
            ),
            stats.showLines()
        )
    }

    @Test
    fun testStatsDefault() {
        val stats = Stats()
        stats.of("widgets").accum(15 * f, 11)
        stats.of("blivits").accum(11 * f, 15)
        stats.of("widgets").accum(7 * f, 7)

        assertEquals(
            listOf(
                "             widgets: took   22 msecs = 1.222 msecs/decryption (18 decryptions) = 11.00 msecs/ballot for 2 ballots",
                "             blivits: took   11 msecs = .7333 msecs/decryption (15 decryptions) = 11.00 msecs/ballot for 1 ballots",
                "               total: took   33 msecs = 1.833 msecs/decryption (18 decryptions) = 16.50 msecs/ballot for 2 ballots",
            ), stats.showLines()
        )
    }

    @Test
    fun testStats5() {
        val stats = Stats()
        stats.of("widgets", "flarble", "dit").accum(15 * f, 11)
        stats.of("blivits", "sims", "diss").accum(11 * f, 15)
        stats.of("widgets").accum(7 * f, 7)

        assertEquals(
            listOf(
                "             widgets: took    22 msecs = 1.2222 msecs/flarble (18 flarbles) = 11.000 msecs/dit for 2 dits",
                "             blivits: took    11 msecs = .73333 msecs/sims (15 simss) = 11.000 msecs/diss for 1 disss",
                "               total: took    33 msecs = 1.8333 msecs/flarble (18 flarbles) = 16.500 msecs/dit for 2 dits",
            ), stats.showLines(5)
        )
    }

    @Test
    fun testPad() {
        assertEquals(" 1", 1.pad(2))
        assertEquals(" 11", 11.pad(3))
        assertEquals("  222", 222.pad(5))
    }

    @Test
    fun testPadL() {
        assertEquals(" 1", 1L.pad(2))
        assertEquals(" 11", 11L.pad(3))
        assertEquals("  222", 222L.pad(5))
    }
}