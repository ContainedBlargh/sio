/**
 * Registers - an object to keep track of the default registers.
 */
object Registers {
    private val nil = Register.NullRegister()
    private val clk = Register.ClockRegister()
    private val acc = Register.PlainRegister("acc")
    private val stdout = Register.Stdout()
    private val stdin = Register.Stdin()
    private val stderr = Register.StdErr()
    fun getDefault(): List<Register> = listOf(nil, clk, acc, stdout, stdin, stderr)
}