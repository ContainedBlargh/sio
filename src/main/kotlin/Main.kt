import kotlinx.coroutines.runBlocking

object Main {


    @JvmStatic
    fun main(args: Array<String>) {
        if (args.isEmpty()) {
            //Launch REPL.
            val repl = Repl()
            repl.loop()
        }
        val nodes = args.map { path ->
            Parser.parseFromPath(path)
        }
        val jobs = nodes.map { it.start() }
        for (job in jobs) {
            runBlocking { job.join() }
        }
    }
}