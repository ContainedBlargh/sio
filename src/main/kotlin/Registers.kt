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
        Register.StdErr(),
        Register.RandomRegister()
    )
}