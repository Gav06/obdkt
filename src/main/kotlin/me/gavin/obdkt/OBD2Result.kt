package me.gavin.obdkt

sealed class OBD2Result<out T> {
    data class Value<T>(val value: T) : OBD2Result<T>()
    data object NotSupported : OBD2Result<Nothing>()
    data class Error(val message: String) : OBD2Result<Nothing>()
}
