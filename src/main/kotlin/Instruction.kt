import Value.*

sealed class Instruction {


    abstract suspend fun modify(node: Node)

    object Nop : Instruction() {
        override suspend fun modify(node: Node) {
            //It's nop, it doesn't do anything.
        }
    }

    object End : Instruction() {
        override suspend fun modify(node: Node) {
            node.stop()
        }
    }

    data class Mov(val take: Value, val to: RegisterRef) : Instruction() {
        override suspend fun modify(node: Node) {
            to.register.put(take.flatten())
        }
    }

    /**
     * Swp - Swap the values of two registers.
     *
     * @property left - a register reference
     * @property right - another register reference
     * @constructor Create empty Swp
     */
    data class Swp(val left: RegisterRef, val right: RegisterRef) : Instruction() {
        override suspend fun modify(node: Node) {
            val tmp = left.lookup()
            left.register.put(right.lookup())
            right.register.put(tmp)
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
        private val xBusRegister: Register.PinRegister<XBusChannel>

        init {
            if (xRegisterRef !is RegisterRef) {
                throw IllegalArgumentException("Cannot slx on non-XBus register!")
            }
            val register = xRegisterRef.register
            xBusRegister = (register as? Register.PinRegister<XBusChannel>)
                ?: throw IllegalArgumentException("Cannot slx on non-XBus register!")
        }

        override suspend fun modify(node: Node) {
            xBusRegister.channel.sleep()
        }
    }

    data class Gen(val registerRef: Value, val onDuration: Value, val offDuration: Value) : Instruction() {
        private val pinRegister: Register.PinRegister<*>

        init {
            if (registerRef !is RegisterRef) {
                throw IllegalArgumentException("Gen: first argument must be a register!")
            }
            val register = registerRef.register
            pinRegister = (register as? Register.PinRegister<*>)
                ?: throw IllegalArgumentException("Gen must be applied to a channel pin-register!")
        }

        override suspend fun modify(node: Node) {
            pinRegister.put(IValue(100))
            node.sleep(onDuration.toInt())
            pinRegister.put(IValue(0))
            node.sleep(offDuration.toInt())
        }
    }

    abstract class AccInstruction : Instruction() {
        abstract fun updateAcc(acc: Register.PlainRegister)
        override suspend fun modify(node: Node) {
            val acc = node.getRegister("acc")
            updateAcc(acc as Register.PlainRegister)
        }
    }

    data class Add(val operand: Value) : AccInstruction() {
        override fun updateAcc(acc: Register.PlainRegister) {
            acc.put(acc.get() + operand)
        }
    }

    data class Sub(val operand: Value) : AccInstruction() {
        override fun updateAcc(acc: Register.PlainRegister) {
            acc.put(acc.get() - operand)
        }
    }

    data class Mul(val operand: Value) : AccInstruction() {
        override fun updateAcc(acc: Register.PlainRegister) {
            acc.put(acc.get() * operand)
        }
    }

    class Not : AccInstruction() {
        override fun updateAcc(acc: Register.PlainRegister) {
            acc.put(acc.get().not())
        }
    }

    class Dgt(val operand: Value) : AccInstruction() {
        override fun updateAcc(acc: Register.PlainRegister) {
            acc.put(acc.get().dgt(operand.toInt()))
        }
    }

    class Dst(val left: Value, val right: Value) : AccInstruction() {
        override fun updateAcc(acc: Register.PlainRegister) {
            acc.put(acc.get().dst(left.toInt(), right))
        }
    }

    class Cst(val type: Value) : AccInstruction() {
        override fun updateAcc(acc: Register.PlainRegister) {
            fun update(givenType: Value) {
                when (givenType) {
                    is SValue -> when (givenType.s) {
                        "i" -> acc.put(IValue(acc.get().toInt()))
                        "f" -> acc.put(FValue(acc.get().toFloat()))
                        "s" -> acc.put(SValue(acc.get().toString()))
                        else -> acc.put(SValue(acc.get().toString()))
                    }
                    is IValue -> acc.put(IValue(acc.get().toInt()))
                    is FValue -> acc.put(FValue(acc.get().toFloat()))
                    is NullValue -> acc.put(NullValue())
                    is RegisterRef -> update(givenType.flatten())
                }
            }
            update(type)
        }
    }

    class Inc(val ref: Value) : AccInstruction() {
        override fun updateAcc(acc: Register.PlainRegister) {
            if (ref !is RegisterRef) {
                throw IllegalArgumentException("Cannot increment a non-register!")
            }
            val next = acc.get() + IValue(1)
            acc.put(next)
            ref.register.put(next)
        }
    }

    class Dec(val ref: Value) : AccInstruction() {
        override fun updateAcc(acc: Register.PlainRegister) {
            if (ref !is RegisterRef) {
                throw IllegalArgumentException("Cannot decrement a non-register!")
            }
            val next = acc.get() - IValue(-1)
            acc.put(next)
            ref.register.put(next)
        }
    }

    abstract class TestInstruction(
        val positive: List<Instruction>, val negative: List<Instruction>
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
        val left: Value, val right: Value, positive: List<Instruction>, negative: List<Instruction>
    ) : TestInstruction(positive, negative) {
        override fun toString(): String = "Teq($left, $right)"
        override fun test(): Boolean = left.compareTo(right) == 0
    }

    class Tgt(
        val left: Value,
        val right: Value,
        positive: List<Instruction>,
        negative: List<Instruction>,
    ) : TestInstruction(positive, negative) {
        override fun toString(): String = "Tgt($left, $right)"
        override fun test(): Boolean = left > right
    }

    class Tlt(
        val left: Value, val right: Value, positive: List<Instruction>, negative: List<Instruction>
    ) : TestInstruction(positive, negative) {
        override fun toString(): String = "Tlt($left, $right)"
        override fun test(): Boolean = left < right
    }

    class Tcp(
        val left: Value, val right: Value, val positive: List<Instruction>, val negative: List<Instruction>
    ) : Instruction() {
        override fun toString(): String = "Tcp($left, $right)"
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