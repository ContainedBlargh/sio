/**
 * Registers - an object to keep track of the default registers.
 */
object Registers {
    fun getDefault(): List<Register> = listOf(
        Register.NullRegister(),
        Register.ClockRegister(),
        Register.PlainRegister("acc"),
        Register.Stdout(),
        Register.Stdin(),
        Register.Stderr(),
        Register.RandomRegister()
    ) + getGraphical()

    fun getGraphical(): List<Register> {
        //Add graphics registers...
        val width = 800
        val height = 600
        val x = Register.PlainRegister("xsz", Value.IValue(width))
        val y = Register.PlainRegister("ysz", Value.IValue(height))
        val pixelsOffset = Register.OffsetRegister("&pxl")
        val pixelsMemory = Register.SizedMemoryRegister("*pxl", pixelsOffset, width * height)
        val keyboardChannel = PowerChannel()
        val keyboardPinRegister = Register.PinRegister("kb0", keyboardChannel)
        return listOf(
            x,
            y,
            pixelsOffset,
            pixelsMemory,
            keyboardPinRegister,
            Register.GfxRegister(x, y, pixelsOffset, pixelsMemory, keyboardChannel)
        )
    }
}