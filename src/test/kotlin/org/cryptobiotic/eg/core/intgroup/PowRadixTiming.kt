package org.cryptobiotic.eg.core.intgroup

import org.cryptobiotic.eg.core.GroupContext
import org.cryptobiotic.util.Stopwatch
import org.cryptobiotic.util.pad
import org.cryptobiotic.util.sigfig
import kotlin.test.Test

class PowRadixTiming {

    @Test
    fun timeLow() {
        println("PowRadixOption.LOW_MEMORY_USE")
        val groupLow = productionIntGroup(PowRadixOption.LOW_MEMORY_USE, ProductionMode.Mode4096 )
        val nonce = groupLow.randomElementModQ()
        groupLow.gPowP(nonce) // first time fills table
        testWarmup(groupLow)
    }
    // PowRadixOption.LOW_MEMORY_USE
    //   0 acc 1000 = .851 msec per acc
    //1000 acc 1000 = .848 msec per acc
    //2000 acc 1000 = .852 msec per acc
    //3000 acc 1000 = .759 msec per acc // HotSpot warmup
    //4000 acc 1000 = .757 msec per acc
    //5000 acc 1000 = .752 msec per acc
    //6000 acc 1000 = .749 msec per acc
    //7000 acc 1000 = .755 msec per acc
    //8000 acc 1000 = .755 msec per acc
    //9000 acc 1000 = .752 msec per acc

    @Test
    fun timeHigh() {
        println("PowRadixOption.HIGH_MEMORY_USE")
        val groupHigh = productionIntGroup(PowRadixOption.HIGH_MEMORY_USE, ProductionMode.Mode4096 )
        val nonce = groupHigh.randomElementModQ()
        groupHigh.gPowP(nonce) // first time fills table
        testWarmup(groupHigh)
    }
    // PowRadixOption.HIGH_MEMORY_USE
    //   0 acc 1000 = .670 msec per acc
    //1000 acc 1000 = .561 msec per acc
    //2000 acc 1000 = .615 msec per acc
    //3000 acc 1000 = .588 msec per acc
    //4000 acc 1000 = .559 msec per acc
    //5000 acc 1000 = .573 msec per acc
    //6000 acc 1000 = .553 msec per acc
    //7000 acc 1000 = .554 msec per acc
    //8000 acc 1000 = .536 msec per acc
    //9000 acc 1000 = .540 msec per acc

    @Test
    fun timeExtreme() {
        println("PowRadixOption.EXTREME_MEMORY_USE")
        val groupExtreme = productionIntGroup(PowRadixOption.EXTREME_MEMORY_USE, ProductionMode.Mode4096 )
        val nonce = groupExtreme.randomElementModQ()
        groupExtreme.gPowP(nonce) // first time fills table
        testWarmup(groupExtreme)
    }
    // PowRadixOption.EXTREME_MEMORY_USE
    //   0 acc 1000 = .463 msec per acc
    //1000 acc 1000 = .421 msec per acc
    //2000 acc 1000 = .415 msec per acc
    //3000 acc 1000 = .427 msec per acc
    //4000 acc 1000 = .422 msec per acc
    //5000 acc 1000 = .492 msec per acc
    //6000 acc 1000 = .406 msec per acc
    //7000 acc 1000 = .404 msec per acc
    //8000 acc 1000 = .402 msec per acc
    //9000 acc 1000 = .480 msec per acc

    fun testWarmup(group: GroupContext) {
        var count = 0
        val incr = 111
        repeat(10) {
            timeAcc(group, count, incr)
            count += incr
        }
    }

    fun timeAcc(group: GroupContext, count: Int, n:Int) {
        val nonces = List(n) { group.randomElementModQ() }

        var stopwatch = Stopwatch()
        repeat(n) { group.gPowP(nonces[it]) }
        var duration = stopwatch.stop()
        val peracc = duration.toDouble() / n / 1_000_000
        println("  ${count.pad(4)} acc $n = ${peracc.sigfig(3)} msec per acc")
    }

    @Test
    // compare exp vs acc
    fun timeExp() {
        println("compare exp vs acc")
        val group = productionIntGroup()
        compareExp(group,100)
        compareExp(group,1000)
        compareExp(group,2000)
    }

    fun compareExp(group: GroupContext, n:Int) {
        val nonces = List(n) { group.randomElementModQ() }
        val h = group.gPowP(group.randomElementModQ())

        var stopwatch = Stopwatch()
        repeat(n) { group.gPowP(nonces[it]) }
        val accTime = stopwatch.stop()
        println(" acc ${stopwatch.tookPer(n, "acc")}")

        stopwatch.start()
        repeat(n) { h powP nonces[it] }
        val expTime = stopwatch.stop()
        println(" exp ${stopwatch.tookPer(n, "exp")}")

        println(" acc/exp ${Stopwatch.ratio(accTime, expTime)}")
    }


    @Test
    fun timeMultiply() {
        println("compare exp vs acc")
        val group = productionIntGroup()
        timeMultiply(group,1000)
        timeMultiply(group,2000)
    }

    fun timeMultiply(group: GroupContext, n:Int) {
        val nonces = List(n) { group.randomElementModQ() }
        val elemps = nonces.map { group.gPowP(it) }

        var stopwatch = Stopwatch()
        elemps.reduce { a, b -> a * b }
        println(" multiply ${stopwatch.tookPer(n, "multiply")}")

        stopwatch.start()
        elemps.forEach { it * it }
        println(" square ${stopwatch.tookPer(n, "square")}")
    }
}