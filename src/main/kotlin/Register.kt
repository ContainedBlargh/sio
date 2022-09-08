import java.awt.Color
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import java.util.*
import kotlin.math.absoluteValue
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
            clockSpeed = value.toInt().coerceIn(1, 6000)
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

    class SizedMemoryRegister(
        override val identifier: String,
        private val offsetRegister: OffsetRegister,
        private val size: Int
    ) : Register() {
        private var memory = Array<Value>(size) { _ -> Value.NullValue() }

        private fun getIndex(): Int {
            val offset = offsetRegister.offset
            return when {
                offset == memory.size -> 0
                offset > memory.size -> offset % memory.size
                offset < 0 -> run {
                    val negative = (offset * -1) % memory.size
                    memory.size - negative
                }
                else -> offset
            }
        }

        override fun put(value: Value) {
            val index = getIndex()
            try { //TODO: This shouldn't be necessary, but there's an error in the getIndex() function.
                memory[index] = value
                offsetRegister.offset = index
            } catch (e: ArrayIndexOutOfBoundsException) {
                memory[0] = value
                offsetRegister.offset = 0
            }
        }

        override fun get(): Value {
            val index = getIndex()
            val value = memory[index]
            offsetRegister.offset = index
            return value
        }

        fun resize(newSize: Int) {
            memory = Array(newSize) {
                if (it < memory.size) {
                    memory[it]
                } else {
                    Value.NullValue()
                }
            }
        }
    }

    class UnsizedMemoryRegister(
        override val identifier: String,
        private val offsetRegister: OffsetRegister
    ) : Register() {
        private val memory = ArrayList<Value>(0)

        override fun put(value: Value) {
            while (memory.size <= offsetRegister.offset) {
                memory.add(Value.NullValue())
            }
            if (offsetRegister.offset < 0) {
                offsetRegister.offset += memory.size
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

    class GfxRegister(
        private val xSizeRegister: PlainRegister,
        private val ySizeRegister: PlainRegister,
        private val pixelsOffset: OffsetRegister,
        private val pixelsMemory: SizedMemoryRegister,
        private val keyboardChannel: PowerChannel,
        override val identifier: String = "gfx"
    ) : Register() {
        private var raster: Raster? = null

        private fun intToColor(i: Int): Color {
            if (i in 100..999) { //Assume that we want three colors.
                val s = i.toString()
                val r = s[0].digitToInt() / 9.0f
                val g = s[1].digitToInt() / 9.0f
                val b = s[2].digitToInt() / 9.0f
                return Color(r, g, b)
            }
            val candidate = Result.runCatching { Color(i) }.getOrNull()
            if (candidate != null) {
                return candidate
            }
            return Color.BLACK
        }

        private fun stringToColor(s: String): Color {
            val sysColor = Color.getColor(s)
            if (sysColor != null) {
                return sysColor
            }
            val candidate = s.lowercase().removePrefix("#").let { Color.decode("#$it") }
            if (candidate != null) {
                return candidate
            }
            return Color.BLACK
        }

        private fun valueToColor(value: Value): Color =
            when (value) {
                is Value.IValue -> intToColor(value.i)
                is Value.FValue -> (value.f).absoluteValue.let { Color(it, it, it) }
                is Value.SValue -> stringToColor(value.s)
                else -> Color.BLACK
            }

        private fun refresh() {
            val xSize = xSizeRegister.get().toInt()
            val ySize = ySizeRegister.get().toInt()
            val before = pixelsOffset.get()
            for (y in 0 until ySize) {
                for (x in 0 until xSize) {
                    val pos = x + y * xSize
                    pixelsOffset.put(Value.IValue(pos))
                    val value = pixelsMemory.get()
                    val color = valueToColor(value)
                    raster?.set(x, y, color)
                }
            }
            pixelsOffset.put(before)
            raster?.refresh()
        }

        override fun put(value: Value) {
            val i = value.toInt()
            when (i) {
                2 -> run {
                    raster?.isFullscreen = !(raster?.isFullscreen ?: false)
                }

                1 -> run {
                    if (raster == null) {
                        val x = xSizeRegister.get().toInt()
                        val y = ySizeRegister.get().toInt()
                        pixelsMemory.resize(x * y)
                        val r = Raster(x, y)
                        r.isVisible = true
                        r.addKeyListener(object : KeyListener {
                            override fun keyTyped(e: KeyEvent?) {
                                e?.consume()
                            }

                            override fun keyPressed(e: KeyEvent?) {
                                e?.apply {
                                    keyboardChannel.send(Value.IValue(e.keyCode))
                                }
                                e?.consume()
                            }

                            override fun keyReleased(e: KeyEvent?) {
                                keyboardChannel.send(Value.NullValue())
                                e?.consume()
                            }
                        })
                        raster = r
                    } else {
                        refresh()
                    }
                }

                0 -> refresh()
                -1 -> raster?.close()
            }
        }

        override fun get(): Value {
            if (raster == null) {
                return Value.IValue(-1)
            } else {
                return Value.IValue(1)
            }
        }
    }
}