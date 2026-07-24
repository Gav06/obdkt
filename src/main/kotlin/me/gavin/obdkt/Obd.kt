package me.gavin.obdkt

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds


fun main(args: Array<String>) {
    // init logger
    OBDLogger

    val usbPort = OBD2Connection.availablePorts().stream().findFirst().orElse(null)

    // Make sure we only have 1 instance of the UI websocket connection
    val isHardwareConnected = AtomicBoolean(false)

    embeddedServer(Netty, port=8080) {
        install(WebSockets) {
            pingPeriod = 15.seconds
            timeout = 15.seconds
            maxFrameSize = Long.MAX_VALUE
            masking = false
        }
        routing {
            staticResources("/", "static") // serves from src/main/resources/static, index.html by default at "/"

            webSocket("/obd") {
                if (!isHardwareConnected.compareAndSet(false, true)) {
                    close(CloseReason(
                        CloseReason.Codes.CANNOT_ACCEPT,
                        "Another tab or instance of OBDkt live dashboard already connected to OBD2 connection."
                    ))
                    OBDLogger.info("Frontend socket connection rejected (already connected)")
                    return@webSocket
                }

                OBDLogger.info("Frontend socket connection established")

                try {
                    send("Connected to OBD2 interface.")

                    for (frame in incoming) {
                        frame as? Frame.Text ?: continue
                        val command = frame.readText()
                        print("cmd: $command")
                    }
                } finally {
                    // release lock when tab closes or disconnects
                    isHardwareConnected.set(false)
                }
            }
        }
    }.start(true)
}