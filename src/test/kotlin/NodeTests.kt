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
        val node = Parser.parseFromPath(getResourceFilePath("test.sio"))
        runBlocking { node.start().join() }
    }

    @Test
    fun `Test shorter example`() {
        val node = Parser.parseFromPath(getResourceFilePath("test_no_comments.sio"))
        runBlocking { node.start().join() }
    }

    @Test
    fun `Test jumping around`() {
        val node = Parser.parseFromPath(getResourceFilePath("jump.sio"))
        runBlocking { node.start().join() }
    }

    @Test
    fun `Test crazy text manipulation`() {
        val node = Parser.parseFromPath(getResourceFilePath("text.sio"))
        runBlocking { node.start().join() }
    }

    @Test
    fun `Test random node`() {
        val node = Parser.parseFromPath(getResourceFilePath("random.sio"))
        runBlocking { node.start().join() }
    }

    @Test
    fun `Test XBus nodes`() {
        val receiver = Parser.parseFromPath(getResourceFilePath("xbus-receiver.sio"))
        val sender = Parser.parseFromPath(getResourceFilePath("xbus-sender.sio"))
        runBlocking {
            sender.start()
            receiver.start().join()
        }
    }

    @Test
    fun `Test AOC1 node`() {
        val input = Files.newInputStream(Paths.get(getResourceFilePath("aoc1.test")))
        System.setIn(input)
        val node = Parser.parseFromPath(getResourceFilePath("aoc1.sio"))
        val handle = node.start()
        runBlocking {
            handle.join()
        }
    }

    @Test
    fun `Test radix-casting`() {
        val node = Parser.parseFromPath(getResourceFilePath("radix-test.sio"))
        runBlocking {
            node.start().join()
        }
    }
}