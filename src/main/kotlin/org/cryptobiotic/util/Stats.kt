package org.cryptobiotic.util

import io.github.oshai.kotlinlogging.KLogger
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.min

/** Keep track of timing stats. Thread-safe. */
class Stats {
    private val mutex = Mutex()
    private val stats = mutableMapOf<String, Stat>() // TODO need thread safe collection

    fun of(who: String, thing: String = "decryption", what: String = "ballot"): Stat {
        return runBlocking {
            mutex.withLock {
                stats.getOrPut(who) { Stat(thing, what) }
            }
        }
    }

    fun show(who: String) {
        val stat = stats.get(who)
        if (stat != null) println(" $who ${stat.show()}") else println("no stat named $who")
    }

    fun get(who: String) : Stat? = stats.get(who)

    fun show(len: Int = 3) {
        showLines(len).forEach { println(it) }
    }

    fun show(logger: KLogger) {
        showLines().forEach { logger.info { it } }
    }

    fun count() : Int {
        return if (stats.isNotEmpty()) stats.values.first().count() else 0
    }

    fun showLines(len: Int = 3): List<String> {
        val result = mutableListOf<String>()
        if (stats.isEmpty()) {
            result.add("stats is empty")
            return result
        }
        var sum = 0L
        stats.forEach {
            result.add("${it.key.padStart(20, ' ')}: ${it.value.show(len)}")
            sum += it.value.accum()
        }
        val total = stats.values.first().copy(sum)
        val totalName = "total".padStart(20, ' ')
        result.add("$totalName: ${total.show(len)}")
        return result
    }
}

fun Stat.show(len: Int = 3): String {
    val accumMs = accum() / 1_000_000
    val perThing = if (nthings() == 0) 0.0 else accum().toDouble() / nthings() / 1_000_000
    val perWhat = if (count() == 0) 0.0 else accum().toDouble() / count()  / 1_000_000
    return "took ${accumMs.pad(len)} msecs = ${perThing.sigfig(4)} msecs/${thing()} (${nthings()} ${thing()}s)" +
        " = ${perWhat.sigfig()} msecs/${what()} for ${count()} ${what()}s"
}

fun Int.pad(len: Int): String = "$this".padStart(len, ' ')
fun Long.pad(len: Int): String = "$this".padStart(len, ' ')


// LOOK can use println("SimpleBallot %.2f encryptions / sec".format(numBallots / encryptionTime)) instead of dfrac

/**
 * Format a double value to have a fixed number of decimal places.
 *
 * @param fixedDecimals number of fixed decimals
 * @return double formatted as a string
 */
fun Double.dfrac(fixedDecimals: Int = 2): String {
    val s: String = this.toString()

    // extract the sign
    val sign: String
    val unsigned: String
    if (s.startsWith("-") || s.startsWith("+")) {
        sign = s.substring(0, 1)
        unsigned = s.substring(1)
    } else {
        sign = ""
        unsigned = s
    }

    // deal with exponential notation
    val mantissa: String
    val exponent: String
    var eInd = unsigned.indexOf('E')
    if (eInd == -1) {
        eInd = unsigned.indexOf('e')
    }
    if (eInd == -1) {
        mantissa = unsigned
        exponent = ""
    } else {
        mantissa = unsigned.substring(0, eInd)
        exponent = unsigned.substring(eInd)
    }

    // deal with decimal point
    val number: StringBuilder
    val fraction: StringBuilder
    val dotInd = mantissa.indexOf('.')
    if (dotInd == -1) {
        number = StringBuilder(mantissa)
        fraction = StringBuilder()
    } else {
        number = StringBuilder(mantissa.substring(0, dotInd))
        fraction = StringBuilder(mantissa.substring(dotInd + 1))
    }

    // number of significant figures
    val fracFigs = fraction.length

    if (fixedDecimals == 0) {
        fraction.setLength(0)
    } else if (fixedDecimals > fracFigs) {
        val want = fixedDecimals - fracFigs
        for (i in 0 until want) {
            fraction.append("0")
        }
    } else if (fixedDecimals < fracFigs) {
        val chop = fracFigs - fixedDecimals // TODO should round !!
        fraction.setLength(fraction.length - chop)
    }

    return if (fraction.isEmpty()) {
        "$sign$number$exponent"
    } else {
        "$sign$number.$fraction$exponent"
    }
}

/**
 * Format a double value to have a minimum significant figures.
 *
 * @param minSigfigs minimum significant figures
 * @return double formatted as a string
 */
fun Double.sigfig(minSigfigs: Int = 4): String {
    val s: String = this.toString()

    // extract the sign
    val sign: String
    val unsigned: String
    if (s.startsWith("-") || s.startsWith("+")) {
        sign = s.substring(0, 1)
        unsigned = s.substring(1)
    } else {
        sign = ""
        unsigned = s
    }

    // deal with exponential notation
    val mantissa: String
    val exponent: String
    var eInd = unsigned.indexOf('E')
    if (eInd == -1) {
        eInd = unsigned.indexOf('e')
    }
    if (eInd == -1) {
        mantissa = unsigned
        exponent = ""
    } else {
        mantissa = unsigned.substring(0, eInd)
        exponent = unsigned.substring(eInd)
    }

    // deal with decimal point
    var number: StringBuilder
    val fraction: StringBuilder
    val dotInd = mantissa.indexOf('.')
    if (dotInd == -1) {
        number = StringBuilder(mantissa)
        fraction = StringBuilder()
    } else {
        number = StringBuilder(mantissa.substring(0, dotInd))
        fraction = StringBuilder(mantissa.substring(dotInd + 1))
    }

    // number of significant figures
    var numFigs = number.length
    var fracFigs = fraction.length

    // Don't count leading zeros in the fraction, if no number
    if (numFigs == 0 || number.toString() == "0" && fracFigs > 0) {
        numFigs = 0
        number = StringBuilder()
        for (element in fraction) {
            if (element != '0') {
                break
            }
            --fracFigs
        }
    }
    // Don't count trailing zeroes in the number if no fraction
    if (fracFigs == 0 && numFigs > 0) {
        for (i in number.length - 1 downTo 1) {
            if (number[i] != '0') {
                break
            }
            --numFigs
        }
    }
    // deal with min sig figures
    val sigFigs = numFigs + fracFigs
    if (sigFigs > minSigfigs) {
        // Want fewer figures in the fraction; chop (should round? )
        val chop: Int = min(sigFigs - minSigfigs, fracFigs)
        fraction.setLength(fraction.length - chop)
    }

    return if (fraction.isEmpty()) {
        "$sign$number$exponent"
    } else {
        "$sign$number.$fraction$exponent"
    }
}