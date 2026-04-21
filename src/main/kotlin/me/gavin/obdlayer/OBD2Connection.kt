package me.gavin.obdlayer

import com.fazecast.jSerialComm.SerialPort
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select

typealias ELMData = String

class OBD2Connection(private val port: SerialPort) {

    private var isConnected = false

    fun isConnected(): Boolean = isConnected

    // OBDData is a wrapper for string, just so it's clear
    // what is meant to be sent over the pipeline.
    private val requestChannel = Channel<ELMData>(Channel.UNLIMITED)
    private val responseChannel = Channel<ELMData>(Channel.UNLIMITED)

    fun getRequestChannel(): Channel<ELMData> = requestChannel
    fun getResponseChannel(): Channel<ELMData> = responseChannel

    private val connectionScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    suspend fun connect(baudRate: Int = 115200): Boolean {
        try {
            if (!port.openPort()) {
                return false;
            }

            // setup port settings
            port.setComPortParameters(baudRate, 8, 1, SerialPort.NO_PARITY)
            port.setComPortTimeouts(SerialPort.TIMEOUT_NONBLOCKING, 1000, 0)
            port.setFlowControl(SerialPort.FLOW_CONTROL_DISABLED)

            // init ELM327
            isConnected = true
            if (!initELM327()) {
                isConnected = false
                println("ELM327 Initialization failed.")
                return false;
            }

            connectionScope.launch { communicationLoop() }
            connectionScope.launch { queryManager() }
            return true

        } catch (e: Exception) {
            println("Exception while establishing port connection!")
            e.printStackTrace()
            port.closePort()
            return false
        }
    }

    // We avoid using our async channels for init, because init needs to be synchronous.
    private suspend fun initELM327(): Boolean {
        // clear junk first
        if (port.bytesAvailable() > 0) {
            val junkData = ByteArray(port.bytesAvailable())
            port.readBytes(junkData, junkData.size)
            println("Cleared ${junkData.size} junk bytes from port connection.")
        }

        val initCmds = listOf(
            "ATZ" to { it: String -> it.contains("ELM327") && it.contains(">")}, // reset ELM327
            "ATE0" to { it: String -> it.contains("OK")}, // turn off echo
            "ATL0" to { it: String -> it.contains("OK")}, // turn off line feeds
            "ATS0" to { it: String -> it.contains("OK")}, // turn off spaces
            "ATSP0" to { it: String -> it.contains("OK")} // set protool to auto
        )

        for ((cmd, validator) in initCmds) {
            writeRequest(cmd)
            delay(if (cmd == "ATZ") 5000 else 500)

            val response = withTimeoutOrNull(2000) {
                readResponseUntilPrompt()
            }

            if (response == null || !validator(response)) {
                println("Init fail: $cmd")
                return false
            }


            println("OUTPUT <- $cmd: $response")
        }

        return true
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun communicationLoop() {
        // regular loop because ELM327 is sequential
        for (request in requestChannel) {
            if (!isConnected) break

            try {
                writeRequest(request)

                val response = readResponseUntilPrompt()
                responseChannel.send(response)
            } catch (e: Exception) {
                error("Communication Error: ${e.message}")
            }
        }
    }

    private suspend fun readResponseUntilPrompt(): ELMData {
        val sb = StringBuilder()
        val buf = ByteArray(1024)

        while (isConnected) {
            val bytesAvail = port.bytesAvailable()
            if (bytesAvail > 0) {
                val numRead = port.readBytes(buf, buf.size)
                if (numRead > 0) {
                    val chunk = String(buf, 0, numRead, Charsets.US_ASCII)
                    sb.append(chunk)

                    if (chunk.contains(">")) {
                        println("Breaking due to terminator character in chunk:")
                        println(chunk)
                        break
                    }
                }
            }

            // 20ms delay so we can wait for hardware to think
            delay(20)
        }

        return sb.toString().replace(">", "").trim()
    }

    private fun writeRequest(obdRequest: ELMData) {
        // add carriage return, to mark end of request
        println("INPUT -> $obdRequest")
        val requestBytes = obdRequest.plus("\r").toByteArray(Charsets.US_ASCII)
        port.writeBytes(requestBytes, requestBytes.size)
    }

    private suspend fun queryManager() {
        // iterate over channel to keep coroutine alive
        for (response in responseChannel) {
            println("OBD RESPONSE: $response")
        }
    }

    private fun makeVisible(str: String): String {
        return str
            .replace("\r", "\\r")
            .replace(" \n", "\\n")
            .replace("\t", "\\t")
            .replace(Regex("[\u0000-\u001f]")) { "\\x%02X".format(it.value[0].code) }
    }

    // the public method to enqueue a request via async channels
    fun trySend(request: ELMData): Boolean {
        return requestChannel.trySend(request).isSuccess
    }

    fun tryGetResponse(): ELMData? {
        return responseChannel.tryReceive().getOrNull()
    }

    fun closeConnection() {
        isConnected = false

        connectionScope.cancel()

        requestChannel.close()
        responseChannel.close()

        if (port.isOpen) {
            port.closePort()
        }
    }
}