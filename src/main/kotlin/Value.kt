import kotlin.experimental.inv
import kotlin.math.max
import kotlin.math.min

sealed class Value {
    companion object {
        fun parse(token: String, registers: Map<String, AnyRegister>): Value {
            return when {
                token.toIntOrNull() != null -> IValue(token.toInt())
                token.toFloatOrNull() != null -> FValue(token.toFloat())
                token.startsWith("\"") && token.endsWith("\"") -> SValue(token.replace("\"", ""))
                registers.containsKey(token) -> RegisterRef(registers[token]!!)
                else -> throw NoWhenBranchMatchedException("Unknown token: '$token'")
            }
        }

    }

    abstract fun toInt(): Int
    abstract fun toFloat(): Float
    abstract fun flatten(): Value
    abstract operator fun plus(value: Value): Value
    abstract operator fun minus(value: Value): Value
    abstract operator fun times(value: Value): Value
    abstract operator fun not(): Value
    abstract fun dgt(i: Int): Value
    abstract fun dst(i: Int, v: Value): Value
    abstract operator fun compareTo(value: Value): Int

    data class RegisterRef(val register: AnyRegister) : Value() {
        fun lookup() = register.get()
        override fun toString() = register.get().toString()
        override fun toInt() = register.get().toInt()
        override fun toFloat() = register.get().toFloat()
        override fun flatten() = register.get()
        override fun plus(value: Value): Value {
            val lVal = this.flatten()
            val rVal = this.flatten()
            return lVal + rVal
        }

        override fun minus(value: Value): Value {
            val lVal = this.flatten()
            val rVal = this.flatten()
            return lVal - rVal
        }

        override fun times(value: Value): Value {
            val lVal = this.flatten()
            val rVal = this.flatten()
            return lVal * rVal
        }

        override fun not(): Value {
            return flatten().not()
        }

        override fun dgt(i: Int): Value {
            TODO("Not yet implemented")
        }

        override fun dst(i: Int, v: Value): Value {
            TODO("Not yet implemented")
        }

        override fun compareTo(value: Value): Int {
            TODO("Not yet implemented")
        }
    }

    data class SValue(val s: String) : Value() {
        override fun toString() = s
        override fun toInt() = s.toIntOrNull() ?: 0
        override fun toFloat() = s.toFloatOrNull() ?: 0f
        override fun flatten() = this
        override fun plus(value: Value): Value {
            return when (value) {
                is SValue -> SValue(s + value.s)
                is FValue -> SValue(s + value.f)
                is IValue -> SValue(s + value.i)
                else -> this.plus(value.flatten())
            }
        }

        override fun minus(value: Value): Value {
            return when (value) {
                is SValue -> SValue(s.replace(value.s, ""))
                is FValue -> String.format("%f.1", value.f).split(".").sorted().let {
                    val l = max(it.first().toInt(), s.length - 1)
                    val r = max(it.last().toInt(), s.length)
                    SValue(s.slice(l until r))
                }
                is IValue -> SValue(s.slice(0 until min(value.i, s.length)))
                else -> this.minus(value.flatten())
            }
        }

        override fun times(value: Value): Value {
            return when (value) {
                is SValue -> SValue(s.toCharArray().flatMap { i -> value.s.toCharArray().map { j -> "$i$j" } }
                    .fold("", String::plus))
                is FValue -> SValue(s.repeat((value.f * s.length.toFloat()).toInt() / s.length))
                is IValue -> SValue(s.repeat(value.i))
                else -> this.minus(value.flatten())
            }
        }

        override fun not(): Value {
            return SValue(s.toCharArray().map { it.code.toByte().inv() }.map { it.toInt().toChar() }
                .fold("", String::plus))
        }

        override fun dgt(i: Int): Value {
            return SValue("${s[i]}")
        }

        override fun dst(i: Int, v: Value): Value {
            TODO("Not yet implemented")
        }

        override fun compareTo(value: Value): Int {
            TODO("Not yet implemented")
        }
    }

    data class FValue(val f: Float) : Value() {
        override fun toString() = "$f"
        override fun toInt() = f.toInt()
        override fun toFloat() = f
        override fun flatten() = this
        override fun plus(value: Value) = FValue(f + value.toFloat())
        override fun minus(value: Value) = FValue(f - value.toFloat())
        override fun times(value: Value) = FValue(f * value.toFloat())
        override fun not(): Value {
            return IValue(if (f.toInt() != 0) 0 else 100)
        }

        override fun dgt(i: Int): Value {
            TODO("Not yet implemented")
        }

        override fun dst(i: Int, v: Value): Value {
            TODO("Not yet implemented")
        }

        override fun compareTo(value: Value): Int {
            TODO("Not yet implemented")
        }
    }

    data class IValue(val i: Int) : Value() {
        override fun toString() = "$i"
        override fun toInt() = i
        override fun toFloat() = i.toFloat()
        override fun flatten() = this
        override fun plus(value: Value) = IValue(i + value.toInt())
        override fun minus(value: Value) = IValue(i - value.toInt())
        override fun times(value: Value) = IValue(i * value.toInt())
        override fun not(): Value {
            return IValue(if (i == 0) 100 else 0)
        }

        override fun dgt(i: Int): Value {
            TODO("Not yet implemented")
        }

        override fun dst(i: Int, v: Value): Value {
            TODO("Not yet implemented")
        }

        override fun compareTo(value: Value): Int {
            TODO("Not yet implemented")
        }
    }
}
