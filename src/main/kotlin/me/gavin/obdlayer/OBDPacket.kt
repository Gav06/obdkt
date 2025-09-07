package me.gavin.obdlayer

typealias OBDData = String

data class OBDPacket(val type: DataType, val data: OBDData)
