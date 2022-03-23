import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

class Node(
    private val instructionList: List<Pair<Boolean, Instruction>>,
    private val registers: Map<String, Register>,
    private val jmpTable: Map<String, Int>
) {
    private var programPosition = 0
    private var disabledPositions = mutableSetOf<Int>()
    private var running = AtomicBoolean(true)
    private val clock = registers["clk"] as Register.ClockRegister

    override fun toString(): String =
        registers.toString() + "\n" +
                instructionList.toString()

    fun getRegister(identifier: String): Register {
        return registers[identifier]!!
    }

    fun jumpTo(label: String) {
        //Remember to subtract 1 since the program counter will add 1 after jumping.
        programPosition = (jmpTable[label] ?: programPosition) - 1
    }

    fun stop() {
        running.set(false)
    }

    suspend fun sleep(duration: Int) {
        val speed = registers["clk"]?.get()?.toInt() ?: 500
        delay(duration * 1000L / speed)
    }

    private fun showRegister(register: String) {
        val reg = getRegister(register)
        val v = reg.get()
        when(v) {
            is Value.SValue -> println("DEBUG: $register: '$v'")
            else -> println("DEBUG: $register: $v")
        }
    }

    fun start(): Job = GlobalScope.launch {
        var timer: Deferred<Unit> = async {  }
        while (running.get()) {
            if (clock.active) {
                val speed = clock.get().toInt()
                timer = async { delay(1000L / speed) }
            }
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
            if (clock.active) {
                timer.await()
            }
        }
    }
}