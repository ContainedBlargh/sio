interface Executable {

    fun getRegister(identifier: String): Register
    fun jumpTo(label: String)
    fun stop()
    suspend fun sleep(duration: Int)
}