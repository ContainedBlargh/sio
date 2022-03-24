import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*;

class MiscTests {
    @Test
    fun `Empty string equals null`() {
        assertEquals(0, Value.SValue("").compareTo(Value.NullValue()))
    }
}