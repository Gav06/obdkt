package me.gavin.obdkt

import com.fazecast.jSerialComm.SerialPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
import me.gavin.obdkt.elm327.ELM327Session

class OBD2Connection private constructor(
    private val port: SerialPort,
    private val session: ELM327Session,
) {
    val isConnected: Boolean get() = port.isOpen

    // Query a typed PID and get a decoded result.
    suspend fun query(pid: OBD2PID): OBD2Result<Float> {
        val raw = session.transaction(pid.request)
        return parseResponse(raw, pid)
    }

    // Continuously poll a PID at the given interval. Collect this Flow in a coroutine scope;
    // cancel the scope to stop polling.
    fun queryFlow(pid: OBD2PID, intervalMs: Long = 200): Flow<OBD2Result<Float>> = flow {
        while (true) {
            emit(query(pid))
            delay(intervalMs)
        }
    }

    // Send a raw AT command or OBD request and return the unprocessed response string.
    // Useful for commands not covered by the typed API.
    suspend fun sendRaw(cmd: String): String = session.transaction(cmd)

    fun disconnect() {
        session.close()
    }

    private fun parseResponse(raw: String, pid: OBD2PID): OBD2Result<Float> {
        val clean = raw.replace(Regex("[\\s\\r\\n]"), "").uppercase()

        if (clean.isEmpty()) return OBD2Result.Error("Empty response")
        if ("NODATA" in clean) return OBD2Result.NotSupported
        if ("ERROR" in clean || "UNABLE" in clean || "STOPPED" in clean) {
            return OBD2Result.Error(raw.trim())
        }

        val headerIdx = clean.indexOf(pid.responseHeader)
        if (headerIdx == -1) return OBD2Result.Error("Missing response header in: $raw")

        val dataHex = clean.substring(headerIdx + 4) // skip "41XX"
        if (dataHex.length < pid.byteCount * 2) {
            return OBD2Result.Error("Insufficient data bytes in: $raw")
        }

        return try {
            val bytes = ByteArray(pid.byteCount) { i ->
                dataHex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
            OBD2Result.Value(pid.decode(bytes))
        } catch (e: NumberFormatException) {
            OBD2Result.Error("Failed to parse hex in: $raw")
        }
    }

    companion object {
        fun availablePorts(): List<SerialPort> = SerialPort.getCommPorts().toList()

        // Opens the port, runs ELM327 init, and returns a ready-to-use connection.
        // Returns null if the port fails to open or ELM327 init fails.
        suspend fun connect(port: SerialPort, baudRate: Int = 115200): OBD2Connection? {
            if (!port.openPort()) return null

            port.setComPortParameters(baudRate, 8, 1, SerialPort.NO_PARITY)
            // BLOCKING with 1000ms: readBytes() will wait up to 1s for data.
            // ELM327Session always checks bytesAvailable() > 0 before calling readBytes(),
            // so the blocking timeout is only a safety net and won't stall normal reads.
            port.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 1000, 0)
            port.setFlowControl(SerialPort.FLOW_CONTROL_DISABLED)

            val session = ELM327Session(port)
            if (!session.init()) {
                port.closePort()
                return null
            }

            return OBD2Connection(port, session)
        }
    }
}
