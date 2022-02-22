import org.junit.jupiter.api.Test

class ParserTests {

    @Test
    fun `Try parsing example`() {
        val filePath = javaClass.getResource("test.sio")!!.path.let {
            if (it.contains(":")) it.removePrefix("/") else it
        }
        val parsed = Parser.parseFromSource(filePath)
        println(parsed)
    }
}