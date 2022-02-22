import org.junit.jupiter.api.Test

class ParserTests {

    @Test
    fun `Try parsing example`() {
        val filePath = javaClass.getResource("test.sio")!!.path
        val parsed = Node.parseFromSource(filePath)
        println(parsed)
    }
}