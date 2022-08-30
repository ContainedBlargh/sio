import java.util.*
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

sealed class Register {
    abstract val identifier: String
    abstract fun put(value: Value)
    abstract fun get(): Value

    data class RandomRegister(override val identifier: String = "rng") : Register() {
        enum class RandomType {
            INT,
            FLOAT,
            STRING
        }

        var type = RandomType.INT
        var random: Random? = null
        var seed = 0

        private fun disturbinglyRandom(): Random {
            random = Random(System.currentTimeMillis())
            val i = random!!.nextInt(0, 3)
            type = RandomType.values()[i]
            return random!!
        }

        private fun seedRandom(value: Value): Pair<Random, RandomType> {
            return when (value) {
                is Value.NullValue -> disturbinglyRandom() to type
                is Value.RegisterRef -> seedRandom(value.flatten())
                is Value.SValue -> {
                    seed = value.toInt()
                    Random(System.currentTimeMillis().countOneBits()) to RandomType.STRING
                }

                is Value.FValue -> Random((value.f * 9999).toInt()) to RandomType.FLOAT
                is Value.IValue -> Random(value.i) to RandomType.INT
            }
        }

        override fun put(value: Value) {
            val (r, t) = seedRandom(value)
            random = r
            type = t
        }

        override fun get(): Value {
            if (random == null) {
                random = disturbinglyRandom()
            }
            return when (type) {
                RandomType.INT -> Value.IValue(random!!.nextInt(0, 999))
                RandomType.FLOAT -> Value.FValue(random!!.nextFloat())
                RandomType.STRING -> {
                    val stringBuilder = StringBuilder()
                    for (i in 0 until seed.absoluteValue) {
                        stringBuilder.append(random!!.nextInt(32, 127).toChar())
                    }
                    Value.SValue(stringBuilder.toString())
                }
            }
        }
    }

    data class NullRegister(override val identifier: String = "null") : Register() {
        override fun toString() = "null"
        override fun put(value: Value) {}
        override fun get(): Value {
            return Value.NullValue()
        }
    }

    data class PlainRegister(
        override val identifier: String,
        private var value: Value = Value.IValue(0)
    ) : Register() {
        override fun toString() = "Register(identifier=$identifier, value=$value)"
        override fun put(value: Value) {
            this.value = value
        }

        override fun get(): Value {
            return this.value
        }
    }

    data class ClockRegister(override val identifier: String = "clk") : Register() {
        private var clockSpeed = 500
        var active = true
        override fun put(value: Value) {
            active = value.toInt() != -1
            max(min(value.toInt(), 1), 1000)
        }

        override fun get(): Value = Value.IValue(clockSpeed)
    }

    class PinRegister<C : PinChannel>(
        override val identifier: String,
        val channel: C
    ) : Register() {
        override fun put(value: Value) = channel.send(value)
        override fun get() = channel.receive()
    }

    class OffsetRegister(
        override val identifier: String
    ) : Register() {
        var offset: Int = 0
        override fun put(value: Value) {
            offset = value.toInt()
        }

        override fun get(): Value {
            return Value.IValue(offset)
        }
    }

    class MemoryRegister(
        override val identifier: String,
        private val offsetRegister: OffsetRegister,
        sizeHint: Int = 8
    ) : Register() {
        private val memory = ArrayList<Value>(sizeHint)

        init {
            while (memory.size <= sizeHint) {
                memory.add(Value.NullValue())
            }
        }

        override fun put(value: Value) {
            while (memory.size <= offsetRegister.offset) {
                memory.add(Value.NullValue())
            }
            memory[offsetRegister.offset] = value
        }

        override fun get(): Value {
            if (offsetRegister.offset < 0) {
                val negative = (offsetRegister.offset * -1) % memory.size
                return memory.getOrNull(memory.size - negative) ?: Value.NullValue()
            }
            return memory.getOrNull(offsetRegister.offset) ?: Value.NullValue()
        }
    }


    abstract class TapeRegister : Register() {
        private val tapeMemory = LinkedList<String>()
        abstract fun write(s: String)
        override fun put(value: Value) {
            val s = value.asString()
            tapeMemory.addLast(s)
            write(s)
        }

        override fun get(): Value {
            return (tapeMemory.pollLast() ?: "").let(Value::SValue)
        }
    }

    data class Stdout(override val identifier: String = "stdout") : TapeRegister() {
        private val writer = System.out.bufferedWriter()

        override fun write(s: String) {
            writer.write(s)
            writer.flush()
        }
    }

    data class Stdin(override val identifier: String = "stdin") : Register() {
        private val reader = System.`in`.bufferedReader()
        private var outBuffer = StringBuilder()
        private var closed = false

        private fun prepare(i: Int) {
            try {
                val buffer = CharArray(i)
                val read = reader.read(buffer)
                outBuffer.append(buffer.slice(0 until read).toCharArray())
            } catch (_: Exception) {
                closed = true
            }
        }

        private fun search(pattern: String) {
            try {
                val patternArr = pattern.toCharArray()
                val buffer = CharArray(pattern.length)
                while (true) {
                    reader.mark(pattern.length * 2)
                    val read = reader.read(buffer)
                    if (read < 1) {
                        closed = true
                        break
                    }
                    if (buffer.contentEquals(patternArr)) {
                        reader.reset()
                        break
                    }
                    outBuffer.append(buffer.slice(0 until read).toCharArray())
                }
            } catch (_: Exception) {
                closed = true
            }
        }

        override fun put(value: Value) {
            if (!closed) {
                when (value) {
                    is Value.SValue -> search(value.s)
                    else -> prepare(value.toInt().absoluteValue)
                }
            }
        }

        override fun get(): Value {
            if (closed && outBuffer.isBlank()) {
                return Value.NullValue()
            }
            val str = outBuffer.toString()
            val out = Value.SValue(str)
            outBuffer.clear()
            return out
        }
    }

    data class StdErr(override val identifier: String = "stderr") : TapeRegister() {
        private val writer = System.err.bufferedWriter()
        override fun write(s: String) {
            writer.write(s)
            writer.flush()
        }
    }
}