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

    suspend fun connect(baudRate: Int = 38400): Boolean {
        try {
            if (!port.openPort()) {
                return false;
            }

            // setup port settings
            port.setComPortParameters(baudRate, 8, 1, SerialPort.NO_PARITY)
            port.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 1000, 0)
            port.setFlowControl(SerialPort.FLOW_CONTROL_DISABLED)

            // init ELM327
            if (!initELM327()) {
                println("ELM327 Initialization failed.")
                return false;
            }

            isConnected = true
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
        // thanks claude for helping so much :-)
        val initCmds = listOf(
            "ATZ" to { it: String -> it.contains("ELM327") && it.contains(">")}, // reset ELM327
            "ATE0" to { it: String -> it.contains("OK")}, // turn off echo
            "ATL0" to { it: String -> it.contains("OK")}, // turn off line feeds
            "ATS0" to { it: String -> it.contains("OK")}, // turn off spaces
            "ATSP0" to { it: String -> it.contains("OK")} // set protool to auto
        )

        for ((cmd, validator) in initCmds) {
            writeRequest(cmd)
            delay(if (cmd == "ATZ") 2000 else 200)

            val response = withTimeoutOrNull(2000) {
                readResponse()
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
        while (isConnected) {
            select {
                // send queued requests
                requestChannel.onReceive{ writeRequest(it) }

                // handle responses
                onTimeout(10) {
                    val response: ELMData? = readResponse()

                    response?.let {
                        responseChannel.send(it)
                    }
                }
            }
        }
    }

    // this function assumes that bytes are available.
    private fun readResponse(): ELMData? {
        val bytesAvail = port.bytesAvailable()
        if (bytesAvail > 0) {
            val bytes = ByteArray(port.bytesAvailable())
            port.readBytes(bytes, bytes.size)
            val byteStr = bytes.joinToString(" ") { "%02X".format(it) }
            val data = makeVisible(String(bytes, Charsets.US_ASCII))
            return data
        }

        return null
    }

    private fun writeRequest(obdRequest: ELMData) {
        // add carriage return, to mark end of request
        println("INPUT -> $obdRequest")
        val requestBytes = obdRequest.plus("\r").toByteArray(Charsets.US_ASCII)
        port.writeBytes(requestBytes, requestBytes.size)
    }

    // processing unique queries, as well as the frequent
    // "callback" queries, like for engine RPM
    private suspend fun queryManager() {
        // right now we just print responses

        val response = responseChannel.receive()

        println("OUTPUT <- $response")
    }

    private fun makeVisible(str: String): String {
        return str
            .replace("\r", "\\r")
            .replace("\n", "\\n")
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