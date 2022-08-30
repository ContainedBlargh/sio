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
                Welcome to the SIO repl!
                
                Here you can execute sio instructions and define registers as you normally would.
                However, jmp's are not supported, neither are channels.
                
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
                    if (line.trim().startsWith("$")) {
                        val reg = line.split("#", ";").first().removePrefix("$")
                        registers.put(reg, Register.PlainRegister(reg))
                    }
                    if (!line.isBlank()) {
                        lines.add(line)
                    }
                    if (!setOf("teq", "tlt", "tgt", "tcp", "+", "-").any { line.contains(it) }) {
                        break
                    }
                }
                val instructions = Parser.parseLines(registers, *lines.toTypedArray())
                for ((_, instruction) in instructions) {
                    runBlocking { instruction.modify(this@Repl) }
                }
                lines.clear()
                println()
            } catch (exception: Exception) {
                System.err.println(exception)
            }
        }
    }
}