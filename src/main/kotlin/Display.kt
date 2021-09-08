abstract class Display {
    companion object {
        const val WIDTH = 64u
        const val HEIGHT = 32u
    }

    abstract fun init()
    abstract fun flush()
    abstract fun clear()
    abstract fun draw(vx: UByte, vy: UByte, sprites: List<UByte>): Boolean
}
