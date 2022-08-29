import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicBoolean

class Repl(
    private val registers: MutableMap<String, Register> = mutableMapOf()
) : Executable {
    private var running = AtomicBoolean(true)

    init {
        registers.putAll(Registers.getDefault().map {
            it.identifier to it
        })
    }

    override fun getRegister(identifier: String): Register {
        return registers.get(identifier)!!
    }

    override fun jumpTo(label: String) {
        println("Cannot jump in REPL-mode.")
    }

    override fun stop() {
        running.set(false)
    }

    override suspend fun sleep(duration: Int) {
        Thread.sleep(duration.toLong())
    }

    fun loop() {
        println(
            """
                Welcome to the sio repl!
                
                Here you can execute sio instructions and define registers as you normally would.
                However, jmp's are not supported, neither are channels.
                
                In order to allow multiple instructions, such as teq instructions, 
                you must conclude a command with blank newline.
                
                Remember that you can use the `end` instruction to quit.
            """.trimIndent()
        )
        while (running.get()) {
            try {
                val lines = mutableListOf<String>()
                while (true) { //Reading...
                    print("> ")
                    val line = readln()
                    if (line.trim() in setOf("#clear", "#reset")) {
                        registers.clear()
                        registers.putAll(Registers.getDefault().map {
                            it.identifier to it
                        })
                    }
                    if (line.trim() == "#registers") {
                        println(registers.entries.joinToString("\n") { "${it.key}: ${it.value}" })
                    }
                    if (!line.isBlank()) {
                        lines.add(line)
                    } else {
                        break
                    }
                }
                val instructions = Parser.parseLines(registers, *lines.toTypedArray())
                println(instructions)
                for ((_, instruction) in instructions) {
                    runBlocking { instruction.modify(this@Repl) }
                }
                println()
            } catch (exception: Exception) {
                System.err.println(exception)
            }
        }
    }
}