import Value.*

sealed class Instruction {
    companion object {
        fun parse(
            registers: Map<String, AnyRegister>,
            jmpLabels: Set<String>,
            tokenIterator: Iterator<String>,
            lineIterator: Iterator<String>
        ): Instruction? {
            if (!tokenIterator.hasNext()) {
                return null
            }
            val first = tokenIterator.next().lowercase()
            val inst = when (first) {
                "nop" -> Nop
                "mov" -> {
                    val first = tokenIterator.next() ?: throw IllegalStateException("mov expected a first operand")
                    val second = tokenIterator.next() ?: throw IllegalStateException("mov expected a second operand")
                    val lVal = Value.parse(first, registers)
                    val rVal = Value.parse(second, registers)
                    if (rVal !is RegisterRef) {
                        throw IllegalStateException("mov right operand must be a registerRef!")
                    }
                    return Mov(lVal, rVal)
                }
                "jmp" -> {
                    val label = tokenIterator.next() ?: throw IllegalStateException("jmp must have a label!")
                    if (label !in jmpLabels) {
                        throw IllegalStateException("jmp requires a label!")
                    }
                    return Jmp(label)
                }
                "sub" -> {
                    val first = tokenIterator.next()
                    val value = Value.parse(first, registers)
                    return Sub(value)
                }
                else -> throw IllegalArgumentException("Unknown instruction: '$first'")
            }
            if (tokenIterator.hasNext()) {
                throw IllegalStateException("No more tokens expected!")
            }
            return inst
        }
    }

    abstract fun modify(node: Node)

    object Nop : Instruction() {
        override fun modify(node: Node) {
            //It's nop, it doesn't do anything.
        }
    }

    data class Mov(val take: Value, val to: RegisterRef): Instruction() {
        override fun modify(node: Node) {
            to.register.put(take.flatten())
        }
    }

    data class Jmp(val label: String) : Instruction() {
        override fun modify(node: Node) {
            node.jumpTo(label)
        }
    }

    data class Slp(private val duration: Value) : Instruction() {
        override fun modify(node: Node) {
            node.sleep(duration.toInt())
        }
    }

    data class Slx(private val xRegisterRef: Value) : Instruction() {
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

        override fun modify(node: Node) {
            xBusRegister.channel.sleep()
        }
    }

    abstract class AccInstruction: Instruction() {
        abstract fun updateAcc(acc: AnyRegister.Register)
        override fun modify(node: Node) {
            val acc = node.getRegister("acc")
            updateAcc(acc as AnyRegister.Register)
        }
    }

    data class Add(private val operand: Value) : AccInstruction() {
        override fun updateAcc(acc: AnyRegister.Register) {
            acc.put(acc.get() + operand)
        }
    }

    data class Sub(private val operand: Value): AccInstruction() {
        override fun updateAcc(acc: AnyRegister.Register) {
            acc.put(acc.get() - operand)
        }
    }

    data class Mul(private val operand: Value): AccInstruction() {
        override fun updateAcc(acc: AnyRegister.Register) {
            acc.put(acc.get() * operand)
        }
    }

    class Not : AccInstruction() {
        override fun updateAcc(acc: AnyRegister.Register) {
            acc.put(acc.get().not())
        }
    }

    class Dgt: AccInstruction() {
        override fun updateAcc(acc: AnyRegister.Register) {
            TODO("Not yet implemented")
        }
    }
}