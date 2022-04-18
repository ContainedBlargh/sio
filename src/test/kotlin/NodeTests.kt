import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

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

    @Test
    fun `Test XBus nodes`() {
        val receiver = Parser.parseFromSource(getResourceFilePath("xbus-receiver.sio"))
        val sender = Parser.parseFromSource(getResourceFilePath("xbus-sender.sio"))
        runBlocking {
            sender.start()
            receiver.start().join()
        }
    }

    @Test
    fun `Test AOC1 node`() {
        val input = Files.newInputStream(Paths.get(getResourceFilePath("aoc1.test")))
        System.setIn(input)
        val node = Parser.parseFromSource(getResourceFilePath("aoc1.sio"))
        val handle = node.start()
        runBlocking {
            handle.join()
        }
    }
}