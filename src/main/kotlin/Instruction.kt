import Value.RegisterRef
import kotlinx.coroutines.delay

sealed class Instruction {


    abstract suspend fun modify(node: Node)

    object Nop : Instruction() {
        override suspend fun modify(node: Node) {
            //It's nop, it doesn't do anything.
        }
    }

    object End: Instruction() {
        override suspend fun modify(node: Node) {
            node.stop()
        }
    }

    data class Mov(val take: Value, val to: RegisterRef) : Instruction() {
        override suspend fun modify(node: Node) {
            to.register.put(take.flatten())
        }
    }

    data class Jmp(val label: String) : Instruction() {
        override suspend fun modify(node: Node) {
            node.jumpTo(label)
        }
    }

    data class Slp(val duration: Value) : Instruction() {
        override suspend fun modify(node: Node) = node.sleep(duration.toInt())

    }

    data class Slx(val xRegisterRef: Value) : Instruction() {
        private val xBusRegister: AnyRegister.XBusRegister

        init {
            if (xRegisterRef !is RegisterRef) {
                throw IllegalArgumentException("Cannot slx on non-XBus register!")
            }
            val register = xRegisterRef.register
            if (register !is AnyRegister.XBusRegister) {
                throw IllegalArgumentException("Cannot slx on non-XBus register!")
            }
            xBusRegister = register
        }

        override suspend fun modify(node: Node) {
            xBusRegister.channel.sleep()
        }
    }

    abstract class AccInstruction : Instruction() {
        abstract fun updateAcc(acc: AnyRegister.Register)
        override suspend fun modify(node: Node) {
            val acc = node.getRegister("acc")
            updateAcc(acc as AnyRegister.Register)
        }
    }

    data class Add(val operand: Value) : AccInstruction() {
        override fun updateAcc(acc: AnyRegister.Register) {
            acc.put(acc.get() + operand)
        }
    }

    data class Sub(val operand: Value) : AccInstruction() {
        override fun updateAcc(acc: AnyRegister.Register) {
            acc.put(acc.get() - operand)
        }
    }

    data class Mul(val operand: Value) : AccInstruction() {
        override fun updateAcc(acc: AnyRegister.Register) {
            acc.put(acc.get() * operand)
        }
    }

    class Not : AccInstruction() {
        override fun updateAcc(acc: AnyRegister.Register) {
            acc.put(acc.get().not())
        }
    }

    class Dgt(val operand: Value) : AccInstruction() {
        override fun updateAcc(acc: AnyRegister.Register) {
            acc.put(acc.get().dgt(operand.toInt()))
        }
    }

    class Dst(val left: Value, val right: Value) : AccInstruction() {
        override fun updateAcc(acc: AnyRegister.Register) {
            acc.put(acc.get().dst(left.toInt(), right))
        }
    }

    abstract class TestInstruction(
        val positive: List<Instruction>,
        val negative: List<Instruction>
    ) : Instruction() {
        abstract fun test(): Boolean
        override suspend fun modify(node: Node) {
            if (test()) {
                positive.forEach { it.modify(node) }
            } else {
                negative.forEach { it.modify(node) }
            }
        }
    }

    class Teq(
        val left: Value,
        val right: Value,
        positive: List<Instruction>,
        negative: List<Instruction>
    ) : TestInstruction(positive, negative) {
        override fun test(): Boolean = left.compareTo(right) == 0
    }

    class Tgt(
        val left: Value,
        val right: Value,
        positive: List<Instruction>,
        negative: List<Instruction>,
    ) : TestInstruction(positive, negative) {
        override fun test(): Boolean = left > right
    }

    class Tlt(
        val left: Value,
        val right: Value,
        positive: List<Instruction>,
        negative: List<Instruction>
    ) : TestInstruction(positive, negative) {
        override fun test(): Boolean = left < right
    }

    class Tcp(
        val left: Value,
        val right: Value,
        val positive: List<Instruction>,
        val negative: List<Instruction>
    ) : Instruction() {
        override suspend fun modify(node: Node) {
            if (left > right) {
                positive.forEach { it.modify(node) }
            }
            if (left < right) {
                negative.forEach { it.modify(node) }
            }
        }
    }
}