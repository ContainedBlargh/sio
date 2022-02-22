interface PinChannel {
    fun send(value: Value)
    fun receive(): Value
}