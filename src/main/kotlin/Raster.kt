import kotlinx.coroutines.*
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics2D
import java.awt.GraphicsEnvironment
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.KeyListener
import java.awt.image.BufferedImage
import javax.swing.JFrame

@OptIn(DelicateCoroutinesApi::class)
class Raster(
    private val width: Int,
    private val height: Int,
    @Volatile private var frameWidth: Int = 800,
    @Volatile private var frameHeight: Int = 600,
    private val setMinSize: Boolean = false,
    private val title: String = "SIO"
) {
    private val graphicsDevice = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice
    private val source = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    private val icon = BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB)
    private var scaled = BufferedImage(frameWidth, frameHeight, BufferedImage.TYPE_INT_RGB)
    private val jf = JFrame()
    private var graphics: Graphics2D? = null

    private val coroutineDispatcher = Dispatchers.Default.limitedParallelism(2)

    init {
        jf.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        jf.isResizable = true
        jf.iconImage = source
        jf.title = title
        if (setMinSize) {
            jf.minimumSize = Dimension(frameWidth, frameHeight)
        } else {
            jf.size = Dimension(frameWidth, frameHeight)
        }
        jf.background = Color.WHITE
        jf.foreground = Color.WHITE
        jf.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent?) {
                frameWidth = jf.width
                frameHeight = jf.height
            }
        })
        GlobalScope.launch(coroutineDispatcher) {
            while (true) {
                refresh()
                delay(1000 / 256)
            }
        }
    }

    @Volatile
    var frameCount = 0

    val keyHandlersCount: Int
        get() = jf.keyListeners.size

    fun close() {
        clearKeyHandlers()
        jf.removeAll()
        jf.dispose()
    }

    fun refresh() {
        frameCount++
        if (scaled.width != frameWidth || scaled.height != frameHeight) {
            scaled = BufferedImage(frameWidth, frameHeight, BufferedImage.TYPE_INT_RGB)
        }
        graphics?.apply {
            scaled.createGraphics().apply {
                val offsets = if (isFullscreen) {
                    IntArray(4) { 0 }
                } else {
                    intArrayOf(8, 31, -16, -39)
                }
                drawImage(
                    source, offsets[0], offsets[1], frameWidth + offsets[2], frameHeight + offsets[3], null
                )
            }.dispose()
            drawImage(scaled, null, null)
        }
        if (frameCount % (256 / 16) == 0) {
            GlobalScope.launch(coroutineDispatcher) {
                icon.createGraphics().apply {
                    drawImage(source, 0, 0, 16, 16, null)
                }.dispose()
                jf.iconImage = icon
                frameCount = 0
            }
        }
    }

    var isVisible: Boolean = jf.isVisible
        get() = field
        set(value) {
            jf.isVisible = value
            field = value
            graphics = jf.graphics as Graphics2D
            suspend { refresh() }
        }

    var size: Dimension = jf.size
    var minSize: Dimension = jf.minimumSize
        get() = field
        set(value) {
            jf.minimumSize = value
        }

    var maxSize: Dimension = jf.maximumSize
        get() = field
        set(value) {
            jf.maximumSize = value
        }

    var beforeFullscreen: Dimension? = null
    var isFullscreen: Boolean = false
        get() = field
        set(value) {
            when (isFullscreen to value) {
                Pair(false, true) -> run {
                    beforeFullscreen = jf.size
                    jf.extendedState = JFrame.MAXIMIZED_BOTH
                    graphicsDevice.fullScreenWindow = jf
                    jf.isVisible = true
                    field = true
                }
                Pair(true, false) -> run {
                    jf.extendedState = JFrame.NORMAL
                    graphicsDevice.fullScreenWindow = null
                    jf.isVisible = true
                    if (beforeFullscreen != null) {
                        jf.size = beforeFullscreen
                    }
                    field = false
                }
            }
        }

    fun addKeyListener(keyListener: KeyListener) {
        jf.addKeyListener(keyListener)
    }

    fun setAll(color: Color) {
        for (y in 0 until height) {
            for (x in 0 until width) {
                source.setRGB(x, y, color.rgb)
            }
        }
        suspend {
            refresh()
        }
    }

    operator fun set(x: Int, y: Int, color: Color) {
        var i = x
        if (i < 0) {
            val iNeg = (i * -1) % width
            i = width - iNeg
        } else if (i >= width) {
            i %= width
        }
        var j = y
        if (j < 0) {
            val jNeg = (j * -1) % height
            j = height - jNeg
        } else if (j >= height) {
            j %= height
        }
        if (i < 0 || j < 0 || i > width || j > height) {
            throw IllegalArgumentException("Coordinate ($x, $y) out of bounds!")
        }
        source.setRGB(i, j, color.rgb)
//        suspend {
//            refresh()
//        }
    }

    operator fun set(coord: Pair<Int, Int>, color: Color) = set(coord.first, coord.second, color)

    operator fun set(coords: List<Pair<Int, Int>>, color: Color) {
        for (coord in coords) {
            set(coord, color)
        }
    }

    operator fun set(x: Int, ys: List<Int>, color: Color) {
        var i = x
        if (x < 0 && width + x >= 0) {
            i = width + x
        }
        if (i < 0 || i > width) {
            throw IllegalArgumentException("Coordinate ($x, $ys) out of bounds!")
        }
        ys.forEach { y ->
            source.setRGB(i, y, color.rgb)
        }
        suspend {
            refresh()
        }
    }

    operator fun set(xs: List<Int>, y: Int, color: Color) {
        var j = y
        if (y < 0 && height + y >= 0) {
            j = height + y
        }
        if (j < 0 || j > height) {
            throw IllegalArgumentException("Coordinate ($xs, $y) out of bounds!")
        }
        xs.forEach { x ->
            source.setRGB(x, j, color.rgb)
        }
        suspend {
            refresh()
        }
    }

    operator fun set(x: Int, ys: IntRange, color: Color) = set(x, ys.toList(), color)
    operator fun set(xs: IntRange, y: Int, color: Color) = set(xs.toList(), y, color)
    operator fun set(ax: Int, ay: Int, bx: Int, by: Int, color: Color) {
        (ay until by).forEach { x ->
            (ax until bx).forEach { y ->
                this[x, y] = color
            }
        }
    }

    operator fun set(x: Int, y: Int, size: Float = 0.8f, s: String) {
        println("drawing $s at $x,$y")
        val g2d = source.createGraphics()
        g2d.font = g2d.font.apply { deriveFont(size) }
        g2d.drawString(s, x, y)
        g2d.dispose()
        suspend { refresh() }
    }

    fun clearKeyHandlers(keyCode: Int? = null) {
        jf.keyListeners
            .forEach {
                jf.removeKeyListener(it)
            }
    }
}