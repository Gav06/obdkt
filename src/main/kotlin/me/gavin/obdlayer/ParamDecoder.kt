package me.gavin.obdlayer

object ParamDecoder {

    fun getEngineSpeed(a: UByte, b: UByte): Float {
        return ((a.toFloat() * 256.0f) + b.toFloat()) / 4.0f
    }


}