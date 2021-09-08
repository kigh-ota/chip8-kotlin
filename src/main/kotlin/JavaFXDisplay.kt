import javafx.scene.Group
import javafx.scene.Scene
import javafx.scene.canvas.Canvas
import javafx.scene.canvas.GraphicsContext
import javafx.scene.paint.Color
import javafx.stage.Stage

@ExperimentalUnsignedTypes
class JavaFXDisplay(primaryStage: Stage) : Display() {
    companion object {
        private const val PIXEL_SIZE = 8u
        private const val BUFFER_SIZE_X = 8u // WIDTH / 8
        private const val BUFFER_SIZE = 8 * 32 // BUFFER_SIZE_X * HEIGHT
    }

    private val gc: GraphicsContext
    private val buffer: UByteArray = UByteArray(BUFFER_SIZE) { 0u }

    init {
        val root = Group()
        val canvas = Canvas((WIDTH * PIXEL_SIZE).toDouble(), (HEIGHT * PIXEL_SIZE).toDouble())
        gc = canvas.graphicsContext2D
        flush()
        root.children.add(canvas)
        primaryStage.scene = Scene(root)
        primaryStage.show()
    }

    override fun init() {
        repeat(buffer.size) { buffer[it] = 0u }
    }

    override fun flush() {
        buffer.forEachIndexed { i, buf ->
            val xStart = (i.toUInt() % BUFFER_SIZE_X) * PIXEL_SIZE
            val y = i.toUInt() / BUFFER_SIZE_X
            for (dx in 0u..7u) {
                val x = xStart + dx
                putPixel(x, y, bit(buf, 7 - dx.toInt()))
            }
        }
    }

    private fun bit(buf: UByte, d: Int) = (buf and (0b00000001u shl d).toUByte()) > 0u

    private fun putPixel(x: UInt, y: UInt, on: Boolean) {
        gc.fill = if (on) Color.WHITE else Color.BLACK
        gc.fillRect(
            (x * PIXEL_SIZE).toDouble(), (y * PIXEL_SIZE).toDouble(),
            PIXEL_SIZE.toDouble(), PIXEL_SIZE.toDouble()
        )
    }

    override fun clear() {
        repeat(BUFFER_SIZE) { buffer[it] = 0u }
    }

    override fun draw(vx: UByte, vy: UByte, sprites: List<UByte>): Boolean {
        val xStart = vx % WIDTH
        val yStart = vy % HEIGHT
        val (iStart, xOffset) = getBufferIndexAndXOffset(xStart, yStart)
        var collision = false

        fun applyXOR(buf: UByte, spr: UByte): UByte {
            val res = buf xor spr
            if (buf xor res.inv() > 0u) {
                collision = true
            }
            return res
        }

        for (y in yStart until minOf(HEIGHT, yStart + sprites.size.toUInt())) {
            val index = iStart + ((y - yStart) * BUFFER_SIZE_X).toInt()
            val sprite = sprites[(y - yStart).toInt()]
            if (xOffset == 0) {
                buffer[index] = applyXOR(buffer[index], sprite)
            } else {
                buffer[index] = applyXOR(buffer[index], (sprite.toUInt() shr xOffset).toUByte())
                if (index % (BUFFER_SIZE_X).toInt() < (BUFFER_SIZE_X - 1u).toInt()) {
                    buffer[index + 1] =
                        applyXOR(buffer[index + 1], (sprite.toUInt() shl (8 - xOffset)).toUByte())
                }
            }
        }
        return collision
    }

    private fun getBufferIndexAndXOffset(xStart: UInt, yStart: UInt): Pair<Int, Int> {
        if (xStart >= WIDTH || yStart >= HEIGHT) {
            throw IllegalArgumentException()
        }
        val index = (xStart / 8u) + yStart * BUFFER_SIZE_X
        val xOffset = xStart % 8u
        return Pair(index.toInt(), xOffset.toInt())
    }
}
