import com.fazecast.jSerialComm.SerialPort

val portCache = ArrayList<SerialPort>()

fun main(args: Array<String>) {
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
            2 -> continue // nothing yet
            3 -> { running = false; println("Quitting...") }
            else -> println("Invalid option entered.")
        }
    }
}

private fun listPorts() {
    println("Checking ports.")
    val ports = SerialPort.getCommPorts()

    // refresh the "cached" ports
    portCache.clear()

    if (ports.isEmpty()) {
        println("No port connections found.")
    } else {
        ports.forEachIndexed { index, serialPort ->
            println("Port [$index]: ${serialPort.systemPortName} - ${serialPort.descriptivePortName}")
            portCache.add(serialPort)
        }
    }
}