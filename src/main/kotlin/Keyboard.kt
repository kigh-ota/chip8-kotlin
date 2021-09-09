import javafx.scene.Scene
import javafx.scene.input.KeyCode

interface Keyboard {
    val pressed: UShort
    fun isPressed(value: UByte): Boolean
}
