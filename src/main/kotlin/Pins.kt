object Pins {
    private val channels = mutableListOf<PinChannel>()
    fun getPinChannel(pinId: Int, isXBus: Boolean): PinChannel? {
        if (channels.size > pinId) {
            return when {
                isXBus && channels[pinId] is XBusChannel -> {
                    channels[pinId]
                }
                !isXBus && channels[pinId] is PowerChannel -> {
                    channels[pinId]
                }
                else -> null
            }
        }
        val candidates = channels.filter {
            when (it) {
                is PowerChannel -> !isXBus
                is XBusChannel -> isXBus
                else -> false
            }
        }
        if (candidates.isEmpty()) {
            val channel = if (isXBus) {
                XBusChannel()
            } else {
                PowerChannel()
            }
            channels.add(channel)
            return channel
        }
        return null
    }
}