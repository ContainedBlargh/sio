import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicReference

class XBusChannel : PinChannel {
    private val channelValue: AtomicReference<Value?> = AtomicReference(null)
    override fun receive(): Value = runBlocking {
//        try {
//            withTimeout(5000L) {
                var value: Value? = null
                while (value == null) {
//                    println("waiting to receive...")
                    value = channelValue.get()
                    delay(100)
                }
                channelValue.set(null)
//                println("received!")
                value
//            }
//        } catch (e: CancellationException) {
//            throw XBusDesynchronized()
//        }
    }

    override fun send(value: Value) = runBlocking {
//        try {
//            withTimeout(5000L) {
                while (!channelValue.compareAndSet(null, value)) {
//                    println("waiting for channel to be empty")
                    delay(100)
                }
//                println("sent!")
//            }
//        } catch (e: CancellationException) {
//            throw XBusDesynchronized()
//        }
    }

    fun sleep() = runBlocking {
//        try {
//            withTimeout(5000L) {
                while (channelValue.get() == null) {
//                    println("slx'ing...")
                    delay(100)
                }
//            }
//        } catch (e: CancellationException) {
//            throw XBusDesynchronized()
//        }
    }
}