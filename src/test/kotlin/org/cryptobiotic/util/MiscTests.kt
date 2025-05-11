package org.cryptobiotic.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield
import java.math.BigInteger
import java.security.SecureRandom
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

/////////////////////////////////////////////////

fun main() {
    runVest(1, 100)
    runVest(10, 100)
    runVest(20, 100)
}

fun runVest(nthreads: Int, ntasks: Int) {
    val tasks: List<EstimationVTask> = List(ntasks) { EstimationVTask() }
    val runner = EstimationVTaskRunner()
    runner.calc(nthreads, tasks)
}

class EstimationVTaskRunner() {
    private val mutex = Mutex()
    private val results = mutableListOf<EstimationVResult>()

    fun calc(nthreads: Int, tasks: List<EstimationVTask>): List<EstimationVResult> {
        val stopWatch = Stopwatch()
        runBlocking {
            val jobs = mutableListOf<Job>()
            val producer = producer(tasks)
            repeat(nthreads) {
                jobs.add(launchCalculations(producer) { task -> task.estimate() })
            }
            // wait for all calculations to be done, then close everything
            joinAll(*jobs.toTypedArray())
        }
        println("$nthreads ${stopWatch.tookPer(tasks.size, "tasks")}")
        return results
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun CoroutineScope.producer(producer: Iterable<EstimationVTask>): ReceiveChannel<EstimationVTask> =
        produce {
            for (task in producer) {
                send(task)
                yield()
            }
            channel.close()
        }

    private fun CoroutineScope.launchCalculations(
        input: ReceiveChannel<EstimationVTask>,
        taskRunner: (EstimationVTask) -> EstimationVResult?,
    ) = launch(Dispatchers.Default) {
        for (task in input) {
            val result = taskRunner(task) // not inside the mutex!!
            if (result != null) {
                mutex.withLock {
                    results.add(result)
                }
            }
            yield()
        }
    }

}

val rng = SecureRandom.getInstanceStrong()

data class EstimationVResult(val sum: BigInteger)

class EstimationVTask() {
    fun estimate(): EstimationVResult {
        var sum = BigInteger.ONE
        val bytes = ByteArray(1000)
        rng.nextBytes(bytes)
        val q = BigInteger(1, bytes)
        repeat(1000) {
            val bytes = ByteArray(1000)
            rng.nextBytes(bytes)
            sum = (sum * BigInteger(1, bytes)).mod(q)
        }
        return EstimationVResult(sum)
    }
}