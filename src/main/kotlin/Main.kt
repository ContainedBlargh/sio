import kotlinx.coroutines.runBlocking

object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        if (args.isEmpty()) {
            println("Usage:\nsio <source files ...>")
            return
        }
        val nodes = args.map { path ->
            Parser.parseFromSource(path)
        }
        val jobs = nodes.map { it.start() }
        for (job in jobs) {
            runBlocking { job.join() }
        }
    }
}