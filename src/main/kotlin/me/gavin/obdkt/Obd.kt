package me.gavin.obdkt

import kotlinx.coroutines.runBlocking
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticResources
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import java.time.Duration


fun main(args: Array<String>) {
    // init logger
    OBDLogger

    val usbPort = OBD2Connection.availablePorts().stream().findFirst().orElse(null)

    embeddedServer(Netty, port=8080) {
//        install(WebSockets) {
//            pingPeriod = Duration.ofSeconds(15L)
//            timeout = Duration.ofSeconds(15L)
//            maxFrameSize = Long.MAX_VALUE
//            masking = false
//        }
        routing {
            staticResources("/", "static") // serves from src/main/resources/static, index.html by default at "/"
        }
    }.start(wait = true)
}