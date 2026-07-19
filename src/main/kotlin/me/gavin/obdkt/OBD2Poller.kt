package me.gavin.obdkt

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

// A single reading published by OBD2Poller.
data class OBD2Reading(
    val pid: OBD2PID,
    val result: OBD2Result<Float>,
    val timestampMs: Long,
)

// Round-robins through subscribed PIDs over the single ELM327 serial link — only one
// command can be in flight at a time (see ELM327Session's transaction mutex), so this
// is the one place that decides what gets queried next. Each PID has its own minimum
// interval (e.g. RPM at 50ms, coolant temp at 1000ms); the loop scans continuously and
// only queries PIDs that are actually due, so slow-changing stats don't steal bandwidth
// from fast ones. Subscribers consume readings either as a Flow (`events`) or via plain
// callbacks (`onReading` / `onAny`).
class OBD2Poller(private val connection: OBD2Connection) {

    private class Subscription(val pid: OBD2PID, val intervalMs: Long) {
        @Volatile var nextDueMs: Long = 0L
    }

    private val subscriptions = ConcurrentHashMap<OBD2PID, Subscription>()

    private val _events = MutableSharedFlow<OBD2Reading>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    // Live stream of readings as they arrive. Collecting is the Flow-native way to consume
    // this; use onReading/onAny below for a plain-callback style instead.
    private val events: SharedFlow<OBD2Reading> = _events.asSharedFlow()

    private var job: Job? = null

    // Subscribe a PID for polling at (at least) the given interval. Safe to call while running.
    fun subscribe(pid: OBD2PID, intervalMs: Long = 100) {
        subscriptions[pid] = Subscription(pid, intervalMs)
    }

    fun unsubscribe(pid: OBD2PID) {
        subscriptions.remove(pid)
    }

    // Starts the round-robin loop on the given scope. Cancel the scope (or call stop()) to halt polling.
    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                val due = subscriptions.values.filter { it.nextDueMs <= now }
                if (due.isEmpty()) {
                    delay(5)
                    continue
                }
                for (sub in due) {
                    val result = connection.query(sub.pid)
                    sub.nextDueMs = System.currentTimeMillis() + sub.intervalMs
                    _events.emit(OBD2Reading(sub.pid, result, System.currentTimeMillis()))
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    // Callback-style subscription for one PID, e.g. for driving a single gauge widget.
    // Returns the Job backing the subscription; cancel it to stop listening.
    fun onReading(pid: OBD2PID, scope: CoroutineScope, callback: (OBD2Reading) -> Unit): Job =
        scope.launch {
            events.collect { reading -> if (reading.pid == pid) callback(reading) }
        }

    // Callback-style subscription for every reading, regardless of PID.
    fun onAny(scope: CoroutineScope, callback: (OBD2Reading) -> Unit): Job =
        scope.launch {
            events.collect(callback)
        }
}
