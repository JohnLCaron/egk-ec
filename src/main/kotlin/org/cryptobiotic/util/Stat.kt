package org.cryptobiotic.util

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class Stat(val thing : String, val what: String) {
    var accum : AtomicLong = AtomicLong(0)
    var count : AtomicInteger = AtomicInteger(0)
    var nthings : AtomicInteger = AtomicInteger(0)

    fun accum(amount : Long, nthings : Int) {
        accum.addAndGet(amount)
        this.nthings.addAndGet(nthings)
        count.incrementAndGet()
    }

    fun copy(accum: Long): Stat {
        val copy = Stat(this.thing, this.what)
        copy.accum = AtomicLong(accum)
        copy.count = this.count
        copy.nthings = this.nthings
        return copy
    }

    fun thing() = this.thing

    fun what() = this.what

    fun accum() = this.accum.get()

    fun nthings() = this.nthings.get()

    fun count() = this.count.get()
}