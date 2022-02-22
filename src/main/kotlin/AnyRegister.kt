import java.nio.CharBuffer
import java.util.*
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.min

sealed class AnyRegister() {
    abstract val identifier: String
    abstract fun put(value: Value)
    abstract fun get(): Value

    data class Register(
        override val identifier: String,
        private var value: Value = Value.IValue(0)
    ) : AnyRegister() {
        override fun toString() = "Register(identifier=$identifier, value=$value)"
        override fun put(value: Value) {
            this.value = value
        }

        override fun get(): Value {
            return this.value
        }
    }

    data class ClockRegister(override val identifier: String = "clc") : AnyRegister() {
        private var clockSpeed = 500
        override fun put(value: Value) {
            max(min(value.toInt(), 1), 1000)
        }

        override fun get(): Value = Value.IValue(clockSpeed)
    }

    data class XBusRegister(
        override val identifier: String,
        val channel: XBusChannel
    ) : AnyRegister() {
        override fun put(value: Value) {
            channel.send(value)
        }

        override fun get(): Value = channel.receive()
    }

    abstract class WriteRegistry : AnyRegister() {
        private val tapeMemory = LinkedList<String>()
        abstract fun write(s: String)
        override fun put(value: Value) {
            val s = value.toString()
            tapeMemory.addLast(s)
            write(s)
        }

        override fun get(): Value {
            return (tapeMemory.pollLast() ?: "").let(Value::SValue)
        }
    }

    data class Stdout(override val identifier: String = "stdout") : WriteRegistry() {
        override fun write(s: String) {
            print(s)
        }
    }

    data class Stdin(override val identifier: String = "stdin") : AnyRegister() {
        private val reader = System.`in`.reader()
        private var outBuffer = ""

        private fun prepare(i: Int) {
            val buffer = CharBuffer.allocate(i)
            reader.read(buffer)
            outBuffer += buffer.toString()
        }

        override fun put(value: Value) {
            val inc = value.toInt().absoluteValue
            prepare(inc)
        }

        override fun get(): Value {
            return Value.SValue(outBuffer.slice(outBuffer.indices))
        }
    }

    data class StdErr(override val identifier: String = "stderr") : WriteRegistry() {
        private val writer = System.err.bufferedWriter()
        override fun write(s: String) {
            return writer.write(s)
        }
    }
}