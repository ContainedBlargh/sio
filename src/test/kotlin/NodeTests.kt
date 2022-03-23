import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class NodeTests {
    fun getResourceFilePath(name: String): String =
        javaClass.getResource(name)!!.path.let {
            if (it.contains(":")) it.removePrefix("/") else it
        }

    @Test
    fun `Test parse and execute program`() {
        val node = Parser.parseFromSource(getResourceFilePath("test.sio"))
        runBlocking { node.start().join() }
    }

    @Test
    fun `Test shorter example`() {
        val node = Parser.parseFromSource(getResourceFilePath("test_no_comments.sio"))
        runBlocking { node.start().join() }
    }

    @Test
    fun `Test reading from stdin`() {
        val node = Parser.parseFromSource(getResourceFilePath("read.sio"))
        runBlocking { node.start().join() }
    }

    @Test
    fun `Test jumping around`() {
        val node = Parser.parseFromSource(getResourceFilePath("jump.sio"))
        runBlocking { node.start().join() }
    }

    @Test
    fun `Test crazy text manipulation`() {
        val node = Parser.parseFromSource(getResourceFilePath("text.sio"))
        runBlocking { node.start().join() }
    }

    @Test
    fun `Test random node`() {
        val node = Parser.parseFromSource(getResourceFilePath("random.sio"))
        runBlocking { node.start().join() }
    }
}