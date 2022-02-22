import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

class Node(
    private val instructionList: List<Pair<Boolean, Instruction>>,
    private val registers: Map<String, AnyRegister>,
    private val jmpTable: Map<String, Int>
) {
    private var programPosition = 0
    private var disabledPositions = mutableSetOf<Int>()
    private var running = AtomicBoolean(true)

    override fun toString(): String =
        registers.toString() + "\n" +
                instructionList.toString()

    fun getRegister(identifier: String): AnyRegister {
        return registers[identifier]!!
    }

    fun jumpTo(label: String) {
        programPosition = jmpTable[label] ?: programPosition
    }

    fun stop() {
        running.set(false)
    }

    suspend fun sleep(duration: Int) {
        val speed = registers["clk"]?.get()?.toInt() ?: 500
        delay(duration * 1000L / speed)
    }

    fun start(): Job = GlobalScope.launch {
        while (running.get()) {
            val clock = registers["clk"]?.get()?.toInt() ?: 500
            val timer = async { delay(1000L / clock) }
            if (programPosition in disabledPositions) {
                programPosition = (programPosition + 1) % instructionList.size
                continue
            }
            val (runOnce, instruction) = instructionList[programPosition]
            instruction.modify(this@Node)
            if (runOnce) {
                disabledPositions.add(programPosition)
            }
            programPosition = (programPosition + 1) % instructionList.size
            timer.await()
        }
    }
}