package me.gavin.obdkt

// Standard OBD2 Mode 01 PIDs with their decoder functions.
// decode receives the raw data bytes (after stripping the mode/pid header bytes).
enum class OBD2PID(
    val mode: Int,
    val pid: Int,
    val label: String,
    val unit: String,
    val byteCount: Int,
    val decode: (ByteArray) -> Float,
) {
    ENGINE_LOAD(1, 0x04, "Engine Load",              "%",    1, { b -> b.u(0) * 100f / 255f }),
    COOLANT_TEMP(1, 0x05, "Coolant Temperature",     "°C",   1, { b -> b.u(0) - 40f }),
    INTAKE_PRESSURE(1, 0x0B, "Intake MAP",           "kPa",  1, { b -> b.u(0) }),
    ENGINE_RPM(1, 0x0C, "Engine RPM",                "rpm",  2, { b -> (b.u(0) * 256f + b.u(1)) / 4f }),
    VEHICLE_SPEED(1, 0x0D, "Vehicle Speed",          "km/h", 1, { b -> b.u(0) }),
    INTAKE_TEMP(1, 0x0F, "Intake Air Temperature",   "°C",   1, { b -> b.u(0) - 40f }),
    MAF_RATE(1, 0x10, "MAF Air Flow Rate",           "g/s",  2, { b -> (b.u(0) * 256f + b.u(1)) / 100f }),
    THROTTLE_POS(1, 0x11, "Throttle Position",       "%",    1, { b -> b.u(0) * 100f / 255f }),
    RUNTIME(1, 0x1F, "Engine Runtime",               "s",    2, { b -> b.u(0) * 256f + b.u(1) }),
    FUEL_LEVEL(1, 0x2F, "Fuel Tank Level",           "%",    1, { b -> b.u(0) * 100f / 255f }),
    AMBIENT_TEMP(1, 0x46, "Ambient Temperature",     "°C",   1, { b -> b.u(0) - 40f }),
    OIL_TEMP(1, 0x5C, "Engine Oil Temperature",      "°C",   1, { b -> b.u(0) - 40f }),
    ;

    // Formatted OBD2 request string, e.g. "010C"
    val request: String get() = "%02X%02X".format(mode, pid)

    // Expected response header, e.g. "410C" (mode + 0x40, pid)
    val responseHeader: String get() = "%02X%02X".format(mode + 0x40, pid)
}

// Reads a byte as unsigned (0–255) and converts to Float
private fun ByteArray.u(i: Int): Float = (this[i].toInt() and 0xFF).toFloat()
