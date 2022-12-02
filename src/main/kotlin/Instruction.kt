import Value.*

sealed class Instruction {


    abstract suspend fun modify(executable: Executable)

    object Nop : Instruction() {
        override suspend fun modify(executable: Executable) {
            //It's nop, it doesn't do anything.
        }
    }

    object End : Instruction() {
        override suspend fun modify(executable: Executable) {
            executable.stop()
        }
    }

    data class Mov(val take: Value, val to: RegisterRef) : Instruction() {
        override suspend fun modify(executable: Executable) {
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
        override suspend fun modify(executable: Executable) {
            val tmp = left.lookup()
            left.register.put(right.lookup())
            right.register.put(tmp)
        }
    }

    data class Jmp(val label: String) : Instruction() {
        override suspend fun modify(executable: Executable) {
            executable.jumpTo(label)
        }
    }

    data class Slp(val duration: Value) : Instruction() {
        override suspend fun modify(executable: Executable) = executable.sleep(duration.toInt())
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

        override suspend fun modify(executable: Executable) {
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

        override suspend fun modify(executable: Executable) {
            pinRegister.put(IValue(100))
            executable.sleep(onDuration.toInt())
            pinRegister.put(IValue(0))
            executable.sleep(offDuration.toInt())
        }
    }

    abstract class AccInstruction : Instruction() {
        abstract fun updateAcc(acc: Register.PlainRegister)
        override suspend fun modify(executable: Executable) {
            val acc = executable.getRegister("acc")
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

    data class Div(val operand: Value): AccInstruction() {
        override fun updateAcc(acc: Register.PlainRegister) {
            acc.put(acc.get() / operand)
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
                    is SValue -> when {
                        givenType.s == "c" -> acc.get().let {
                            when (it) {
                                is IValue -> acc.put(SValue(Char(it.i).toString()))
                                is SValue -> acc.put(IValue(it.s.toCharArray().firstOrNull()?.code ?: 0))
                                else -> acc.put(IValue(-1))
                            }
                        }

                        givenType.s == "i" -> acc.put(IValue(acc.get().toInt()))
                        givenType.s == "f" -> acc.put(FValue(acc.get().toFloat()))
                        givenType.s == "s" -> acc.put(SValue(acc.get().asString()))
                        givenType.s.startsWith("i") -> runCatching {
                            val str = givenType.s.removePrefix("i")
                            val radix = str.toInt()
                            acc.put(IValue(acc.get().asString().toInt(radix)))
                        }.getOrNull() ?: throw IllegalArgumentException("Invalid cast: '${givenType.asString()}'")

                        else -> acc.put(SValue(acc.get().asString()))
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
            val next = ref.lookup() + IValue(1)
            acc.put(next)
            ref.register.put(next)
        }
    }

    class Dec(val ref: Value) : AccInstruction() {
        override fun updateAcc(acc: Register.PlainRegister) {
            if (ref !is RegisterRef) {
                throw IllegalArgumentException("Cannot decrement a non-register!")
            }
            val next = ref.lookup() - IValue(1)
            acc.put(next)
            ref.register.put(next)
        }
    }

    abstract class TestInstruction(
        val positive: List<Instruction>, val negative: List<Instruction>
    ) : Instruction() {
        abstract fun test(): Boolean
        override suspend fun modify(executable: Executable) {
            if (test()) {
                positive.forEach { it.modify(executable) }
            } else {
                negative.forEach { it.modify(executable) }
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
        val left: Value, val right: Value, positive: List<Instruction>, negative: List<Instruction>
    ) : TestInstruction(positive, negative) {
        override fun toString(): String = "Tcp($left, $right)"
        override fun test(): Boolean = false; //Override the usual behaviour.

        override suspend fun modify(executable: Executable) {
            if (left > right) {
                positive.forEach { it.modify(executable) }
            }
            if (left < right) {
                negative.forEach { it.modify(executable) }
            }
        }
    }
}