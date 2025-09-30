import com.fazecast.jSerialComm.SerialPort
import kotlinx.coroutines.withTimeoutOrNull
import me.gavin.obdlayer.OBD2Connection

val portCache = ArrayList<SerialPort>()
var connection: OBD2Connection? = null

suspend fun main(args: Array<String>) {
    var running = true
    println("==== obd-kt test CLI ====")
    while (running) {
        println("\n1. List all connected USB ports")
        println("2. Open port connection")
        println("3. Exit")
        print("\nEnter choice: ")

        val input = readln().toIntOrNull() ?: println("Invalid option entered.")

        println()
        when (input) {
            1 -> listPorts()
            2 -> connectToPort()
            3 -> { running = false; println("Quitting...") }
            else -> println("Invalid option entered.")
        }
    }
}

fun listPorts() {
    println("Checking ports.")
    scanPorts()
    if (portCache.isEmpty()) {
        println("No port connections found.")
    } else {
        portCache.forEachIndexed { index, serialPort ->
            println("Port [$index]: ${serialPort.systemPortName} - ${serialPort.descriptivePortName}")
        }
    }
}

fun scanPorts() {
    val ports = SerialPort.getCommPorts()
    portCache.clear()
    ports.forEach { port ->
        portCache.add(port)
    }
}

suspend fun connectToPort() {
    print("Enter the port index for which to connect: ")
    val portIndex: Int? = readln().toIntOrNull()

    if (portIndex == null) {
        println("Please try again and enter a valid number.")
        return
    }
    // scan again for ports in case we haven't already
    scanPorts()

    if (portCache.isEmpty()) {
        println("No connected ports found! Try checking device connection.")
        return
    }

    if (portIndex > portCache.size - 1 || portIndex < 0) {
        println("Provided index is out of range.")
        return
    }

    connection = OBD2Connection(portCache[portIndex])
    val result = connection!!.connect(115200)


    handleInputLoop()
}

suspend fun handleInputLoop() {
    println("ELM327 Connection Established, enter a commands to send. Type STOP to stop")
    while (true) {
        print("CMD > ")
        val input = readln()

        if (input == "STOP") {
            break
        }

        connection!!.trySend(input.replace("\n", ""))

        val response = withTimeoutOrNull(5000) {
            connection!!.getResponseChannel().receive()
        }
    }

    connection!!.closeConnection()
}