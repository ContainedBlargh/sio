class PowerChannel: PinChannel {
    @Volatile
    private var output = 0
    override fun send(value: Value) {
        output = value.toInt()
    }

    override fun receive(): Value {
        return Value.IValue(output)
    }
}