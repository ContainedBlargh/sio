import java.util.*
import kotlin.experimental.inv
import kotlin.math.max
import kotlin.math.min

sealed class Value {
    companion object {
        fun parse(token: String, registers: Map<String, Register>): Value {
            return when {
                token.toIntOrNull() != null -> IValue(token.toInt())
                token.toFloatOrNull() != null -> FValue(token.toFloat())
                token.startsWith("\"") && token.endsWith("\"") ->
                    SValue(
                        token
                            .replace("\"", "")
                            .replace("\\n", "\n")
                            .replace("\\t", "\t")
                            .replace("\\r", "\r")
                    )

                registers.containsKey(token) -> RegisterRef(registers[token]!!)
                else -> throw NoWhenBranchMatchedException("Unknown token: '$token'")
            }
        }

        fun setDigit(number: Int, i: Int, digit: Int): Int {
            val numberStr = number.toString()
            val mostSignificant = digit.toString().firstOrNull() ?: 0
            return (numberStr.substring(0, i) + mostSignificant + numberStr.substring(i + 1)).toInt()
        }

        fun String.replaceAt(i: Int, str: String): String {
            val left = if (i > 1) str.substring(0, i - 1) else ""
            val right = if (i < str.length - 1) str.substring(i + 1) else ""
            return left + str + right
        }
    }

    abstract fun asString(): String
    abstract fun toInt(): Int
    abstract fun toFloat(): Float
    abstract fun flatten(): Value
    abstract operator fun plus(value: Value): Value
    abstract operator fun minus(value: Value): Value
    abstract operator fun times(value: Value): Value
    abstract operator fun div(value: Value): Value
    abstract operator fun not(): Value
    abstract fun dgt(i: Int): Value
    abstract fun dst(i: Int, v: Value): Value
    abstract operator fun compareTo(value: Value): Int

    class NullValue() : Value() {
        override fun asString(): String = "null"

        override fun toInt(): Int = 0

        override fun toFloat(): Float = Float.NaN

        override fun flatten(): Value = this

        override fun plus(value: Value): Value = this

        override fun minus(value: Value): Value = this

        override fun times(value: Value): Value = this

        override fun div(value: Value): Value = this

        override fun not(): Value = this

        override fun dgt(i: Int): Value = this

        override fun dst(i: Int, v: Value): Value = this

        override fun compareTo(value: Value): Int {
            return when (value) {
                is RegisterRef -> compareTo(value.flatten())
                is NullValue -> 0
                is SValue -> if (value.s.trim().isBlank() || value.s == "\u0000") 0 else -1
                else -> -1
            }
        }
    }

    data class RegisterRef(val register: Register) : Value() {
        fun lookup() = register.get()
        override fun asString() = register.get().asString()
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

        override fun div(value: Value): Value {
            val lVal = this.flatten()
            val rVal = this.flatten()
            return lVal / rVal
        }

        override fun not() = flatten().not()

        override fun dgt(i: Int) = flatten().dgt(i)

        override fun dst(i: Int, v: Value) = flatten().dst(i, v)

        override fun compareTo(value: Value) = flatten().compareTo(value)
    }

    data class SValue(val s: String) : Value() {
        override fun asString() = s
        override fun toInt() = s.trim().toIntOrNull() ?: s.singleOrNull()?.code ?: 0
        override fun toFloat() = s.toFloatOrNull() ?: 0f
        override fun flatten() = this
        override fun plus(value: Value): Value {
            return when (value) {
                is RegisterRef -> this.plus(value.flatten())
                is SValue -> SValue(s + value.s)
                is FValue -> SValue(s + value.f)
                is IValue -> SValue(s + value.i)
                is NullValue -> this
            }
        }

        override fun minus(value: Value): Value {
            return when (value) {
                is RegisterRef -> this.minus(value.flatten())
                is SValue -> SValue(s.replace(value.s, ""))
                is FValue -> String.format("%f.1", value.f).split(".").sorted().let {
                    val l = max(it.first().toInt(), s.length - 1)
                    val r = max(it.last().toInt(), s.length)
                    SValue(s.slice(l until r))
                }

                is IValue -> SValue(s.slice(0 until min(value.i, s.length)))
                is NullValue -> this
            }
        }

        private fun scale(s: String, f: Float): String {
            var p = (f * s.length).toInt()
            val builder = StringBuilder()
            while (p > 0) {
                val r = min(p, s.length)
                for (i in 0 until r) {
                    builder.append(s[i])
                }
                p -= r
            }
            return builder.toString()
        }

        override fun times(value: Value): Value {
            return when (value) {
                is RegisterRef -> this.times(value.flatten())
                is SValue -> SValue(s.toCharArray().flatMap { i -> value.s.toCharArray().map { j -> "$i$j" } }
                    .fold("", String::plus))

                is FValue -> SValue(scale(s, value.f))
                is IValue -> SValue(s.repeat(value.i))
                is NullValue -> SValue("")
            }
        }

        override fun div(value: Value): Value = this

        override fun not(): Value {
            return SValue(s.toCharArray().map { it.code.toByte().inv() }.map { it.toInt().toChar() }
                .fold("", String::plus))
        }

        override fun dgt(i: Int): Value {
            return SValue(s.getOrNull(i)?.toString() ?: "")
        }

        override fun dst(i: Int, v: Value): Value = SValue(s.replaceAt(i, v.asString()))

        override fun compareTo(value: Value): Int = when (value) {
            is RegisterRef -> compareTo(value.flatten())
            is NullValue -> value.compareTo(this)
            else -> s.compareTo(value.asString())
        }
    }

    data class FValue(val f: Float) : Value() {
        override fun asString() = String.format(Locale.ENGLISH, "%.8f", f)
        override fun toInt() = f.toInt()
        override fun toFloat() = f
        override fun flatten() = this
        override fun plus(value: Value) = FValue(f + value.toFloat())
        override fun minus(value: Value) = FValue(f - value.toFloat())
        override fun times(value: Value) = FValue(f * value.toFloat())
        override fun div(value: Value) = FValue(f / value.toFloat())

        override fun not(): Value {
            return IValue(if (f.toInt() != 0) 0 else 100)
        }

        override fun dgt(i: Int): Value = IValue(f.toInt().toString().getOrNull(i)?.digitToInt() ?: 0)

        override fun dst(i: Int, v: Value): Value =
            IValue(setDigit(toInt(), i, v.toInt()))

        override fun compareTo(value: Value): Int = f.compareTo(value.toFloat())
    }

    data class IValue(val i: Int) : Value() {
        override fun asString() = "$i"
        override fun toInt() = i
        override fun toFloat() = i.toFloat()
        override fun flatten() = this
        override fun plus(value: Value) = IValue(i + value.toInt())
        override fun minus(value: Value) = IValue(i - value.toInt())
        override fun times(value: Value) = IValue(i * value.toInt())
        override fun div(value: Value) = IValue(i / value.toInt())
        override fun not(): Value {
            return IValue(if (i == 0) 100 else 0)
        }

        override fun dgt(i: Int): Value = IValue(this.i.toString().getOrNull(i)?.digitToInt() ?: 0)

        override fun dst(i: Int, v: Value): Value = IValue(setDigit(this.i, i, v.toInt()))

        override fun compareTo(value: Value): Int = i.compareTo(value.toInt())
    }
}
