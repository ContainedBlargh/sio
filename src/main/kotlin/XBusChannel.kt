import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

class XBusChannel : PinChannel {
    private val channel = ArrayBlockingQueue<Value>(1, false)
    override fun receive(): Value {
        return channel.poll(2, TimeUnit.MINUTES) ?: throw XBusDesynchronized()
    }
    override fun send(value: Value) {
        if(!channel.offer(value, 2, TimeUnit.MINUTES)) {
            throw XBusDesynchronized()
        }
    }
    fun sleep() {
        val v = channel.poll(2, TimeUnit.MINUTES) ?: throw XBusDesynchronized()
        channel.put(v)
    }
}