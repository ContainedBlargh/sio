import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class InstrTests {
    @Test
    fun `Test that comparing an empty s-value with null returns true`() {
        val teq = Instruction.Teq(Value.SValue("  \r\n"), Value.NullValue(), emptyList(), emptyList())
        assertTrue(teq.test())
    }
}