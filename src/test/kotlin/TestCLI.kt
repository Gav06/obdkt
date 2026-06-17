import com.fazecast.jSerialComm.SerialPort
import me.gavin.obdkt.OBD2Connection
import me.gavin.obdkt.OBD2PID
import me.gavin.obdkt.OBD2Result

var connection: OBD2Connection? = null

suspend fun main() {
    var running = true
    println("==== obd-kt test CLI ====")
    while (running) {
        println("\n1. List all connected USB ports")
        println("2. Connect to port")
        println("3. Query a PID")
        println("4. Send raw command")
        println("5. Disconnect")
        println("6. Exit")
        print("\nEnter choice: ")

        when (readln().toIntOrNull()) {
            1 -> listPorts()
            2 -> connectToPort()
            3 -> queryPID()
            4 -> sendRaw()
            5 -> disconnect()
            6 -> { running = false; println("Quitting...") }
            else -> println("Invalid option.")
        }
    }
}

fun listPorts() {
    val ports = OBD2Connection.availablePorts()
    if (ports.isEmpty()) {
        println("No ports found.")
    } else {
        ports.forEachIndexed { i, p -> println("[$i] ${p.systemPortName} - ${p.descriptivePortName}") }
    }
}

suspend fun connectToPort() {
    val ports = OBD2Connection.availablePorts()
    if (ports.isEmpty()) { println("No ports found."); return }

    ports.forEachIndexed { i, p -> println("[$i] ${p.systemPortName} - ${p.descriptivePortName}") }
    print("Enter port index: ")
    val idx = readln().toIntOrNull() ?: run { println("Invalid index."); return }
    if (idx !in ports.indices) { println("Index out of range."); return }

    print("Enter baud rate (default 115200): ")
    val baud = readln().toIntOrNull() ?: 115200

    println("Connecting...")
    connection = OBD2Connection.connect(ports[idx], baud)
    if (connection != null) {
        println("Connected!")
    } else {
        println("Connection failed. Check device and baud rate.")
    }
}

suspend fun queryPID() {
    val conn = connection ?: run { println("Not connected."); return }

    println("Available PIDs:")
    OBD2PID.entries.forEachIndexed { i, pid ->
        println("[$i] ${pid.label} (${pid.unit})")
    }
    print("Enter PID index: ")
    val idx = readln().toIntOrNull() ?: run { println("Invalid index."); return }
    if (idx !in OBD2PID.entries.indices) { println("Index out of range."); return }

    val pid = OBD2PID.entries[idx]
    print("Querying ${pid.label}... ")

    when (val result = conn.query(pid)) {
        is OBD2Result.Value      -> println("${result.value} ${pid.unit}")
        is OBD2Result.NotSupported -> println("Not supported by this vehicle.")
        is OBD2Result.Error      -> println("Error: ${result.message}")
    }
}

suspend fun sendRaw() {
    val conn = connection ?: run { println("Not connected."); return }
    print("Enter command: ")
    val cmd = readln().trim()
    if (cmd.isBlank()) return
    val response = conn.sendRaw(cmd)
    println("Response: ${response.trim()}")
}

fun disconnect() {
    connection?.disconnect()
    connection = null
    println("Disconnected.")
}
