package me.gavin.obdkt.elm327

import com.fazecast.jSerialComm.SerialPort
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class ELM327Session(private val port: SerialPort) {

    private val mutex = Mutex()

    // Send a command and return the full response up to (but not including) the '>' prompt.
    // The mutex ensures only one transaction runs at a time — ELM327 is strictly serial.
    suspend fun transaction(cmd: String): String = mutex.withLock {
        writeCmd(cmd)
        readUntilPrompt()
    }

    private fun writeCmd(cmd: String) {
        val bytes = (cmd + "\r").toByteArray(Charsets.US_ASCII)
        port.writeBytes(bytes, bytes.size)
    }

    // Reads bytes from the port until the ELM327 '>' prompt is seen.
    // Checks bytesAvailable() before reading so we never call readBytes() on an
    // empty buffer (which would block for the full TIMEOUT_READ_BLOCKING window).
    private suspend fun readUntilPrompt(): String = withContext(Dispatchers.IO) {
        val sb = StringBuilder()
        val buf = ByteArray(256)
        while (isActive) {
            val avail = port.bytesAvailable()
            if (avail > 0) {
                val n = port.readBytes(buf, minOf(avail, buf.size))
                if (n > 0) {
                    sb.append(String(buf, 0, n, Charsets.US_ASCII))
                    if ('>' in sb) break
                }
            } else {
                delay(10)
            }
        }
        sb.toString().substringBefore('>')
    }

    // Init uses the same write-then-delay-then-read pattern as the original code,
    // because ELM327 init is strictly synchronous: write the command, wait for the
    // device to finish processing (or reset in the case of ATZ), then read whatever
    // bytes arrived during that window.
    suspend fun init(): Boolean {
        val initSequence = listOf(
            "ATZ"   to Pair(2000L, { r: String -> "ELM327" in r }),
            "ATE0"  to Pair(200L,  { r: String -> "OK" in r }),
            "ATL0"  to Pair(200L,  { r: String -> "OK" in r }),
            "ATS0"  to Pair(200L,  { r: String -> "OK" in r }),
            "ATSP0" to Pair(200L,  { r: String -> "OK" in r }),
        )
        for ((cmd, pair) in initSequence) {
            val (delayMs, validate) = pair
            writeCmd(cmd)
            delay(delayMs)

            val response = readAvailable()
            if (response == null || !validate(response)) {
                println("ELM327 init failed on $cmd — got: ${response?.trim() ?: "<no response>"}")
                return false
            }
            println("$cmd => ${response.trim()}")
        }
        return true
    }

    // Read all bytes currently in the receive buffer in one shot.
    // Called after a deliberate delay so the device has had time to respond.
    // Returns null if the buffer is empty.
    private fun readAvailable(): String? {
        val avail = port.bytesAvailable()
        if (avail <= 0) return null
        val bytes = ByteArray(avail)
        port.readBytes(bytes, avail)
        return String(bytes, Charsets.US_ASCII)
    }

    fun close() {
        if (port.isOpen) port.closePort()
    }
}
