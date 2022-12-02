object Pins {
    private val xBusChannels = mutableMapOf<Int, XBusChannel>()
    private val powerChannels = mutableMapOf<Int, PowerChannel>()

    private fun getXBusChannel(pinId: Int): XBusChannel = xBusChannels.computeIfAbsent(pinId) { XBusChannel() }

    private fun getPowerChannel(pinId: Int): PowerChannel = powerChannels.computeIfAbsent(pinId) { PowerChannel() }

    fun getPinChannel(pinId: Int, isXBus: Boolean): PinChannel =
        if (isXBus) getXBusChannel(pinId) else getPowerChannel(pinId)
}