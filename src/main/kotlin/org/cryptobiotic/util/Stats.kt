package org.cryptobiotic.util

import io.github.oshai.kotlinlogging.KLogger
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Keep track of timing stats. Thread-safe. */
class Stats {
    private val minSigfigsDefault = 4
    private val mutex = Mutex()
    private val stats = mutableMapOf<String, Stat>() // TODO need thread safe collection

    fun of(who: String, thing: String = "decryption", what: String = "ballot"): Stat {
        return runBlocking {
            mutex.withLock {
                stats.getOrPut(who) { Stat(thing, what) }
            }
        }
    }

    fun get(who: String): Stat? = stats.get(who)

    // prints individual lines
    fun show(minSigfigs: Int = minSigfigsDefault) {
        showLines(minSigfigs).forEach { println(it) }
    }

    // prints individual lines to logger
    fun show(logger: KLogger) {
        showLines().forEach { logger.info { it } }
    }

    fun count(): Int {
        return if (stats.isNotEmpty()) stats.values.first().count() else 0
    }

    // return lines in array
    fun showLines(minSigfigs: Int = minSigfigsDefault): List<String> {
        val result = mutableListOf<String>()
        if (stats.isEmpty()) {
            result.add("stats is empty")
            return result
        }
        var sum = 0L
        stats.forEach { stat ->
            result.add("${stat.key.padStart(20, ' ')}: ${stat.value.show(minSigfigs)}")
            sum += stat.value.accum()
        }
        val total = stats.values.first().copy(sum)
        val totalName = "total".padStart(20, ' ')
        result.add("$totalName: ${total.show(minSigfigs)}")
        return result
    }
}

fun Stat.show(minSigfigs: Int): String {
    val accumMs = accum() / 1_000_000
    val perThing = if (nthings() == 0) 0.0 else accum().toDouble() / nthings() / 1_000_000
    val perWhat = if (count() == 0) 0.0 else accum().toDouble() / count() / 1_000_000
    return "took ${accumMs.pad(minSigfigs)} msecs = ${perThing.sigfig(minSigfigs)} msecs/${thing()} (${nthings()} ${thing()}s)" +
            " = ${perWhat.sigfig(minSigfigs)} msecs/${what()} for ${count()} ${what()}s"
}

fun Int.pad(len: Int): String = "$this".padStart(len, ' ')
fun Long.pad(len: Int): String = "$this".padStart(len, ' ')

fun Double.sigfig(minSigfigs: Int = 4): String {
    val df = "%.${minSigfigs}G".format(this)
    return if (df.startsWith("0.")) df.substring(1) else df
}

