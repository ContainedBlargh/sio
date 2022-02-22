/**
 * Registers - an object to keep track of the default registers.
 */
object Registers {
    private val clk = AnyRegister.ClockRegister()
    private val acc = AnyRegister.Register("acc")
    private val stdout = AnyRegister.Stdout()
    private val stdin = AnyRegister.Stdin()
    private val stderr = AnyRegister.StdErr()
    fun getDefault(): List<AnyRegister> = listOf(clk, acc, stdout, stdin, stderr)
}