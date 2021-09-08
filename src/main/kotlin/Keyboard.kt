import javafx.scene.Scene
import javafx.scene.input.KeyCode

class Keyboard(scene: Scene) {
    companion object {
        private val KEYS = mapOf<KeyCode, UByte>(
            Pair(KeyCode.DIGIT1, 0x1u),
            Pair(KeyCode.DIGIT2, 0x2u),
            Pair(KeyCode.DIGIT3, 0x3u),
            Pair(KeyCode.DIGIT4, 0xCu),
            Pair(KeyCode.Q, 0x4u),
            Pair(KeyCode.W, 0x5u),
            Pair(KeyCode.E, 0x6u),
            Pair(KeyCode.R, 0xDu),
            Pair(KeyCode.A, 0x7u),
            Pair(KeyCode.S, 0x8u),
            Pair(KeyCode.D, 0x9u),
            Pair(KeyCode.F, 0xEu),
            Pair(KeyCode.Z, 0xAu),
            Pair(KeyCode.X, 0x0u),
            Pair(KeyCode.C, 0xBu),
            Pair(KeyCode.V, 0xFu),
        )
    }

    var pressed: UShort = 0x00u
       private set

    private fun setPressed(value: UByte, b: Boolean) {
        pressed = when (b) {
            true -> (pressed.toUInt() or (0x01u shl value.toInt())).toUShort()
            false -> (pressed.toUInt() and (0x01u shl value.toInt()).inv()).toUShort()
        }
    }

    init {
        scene.setOnKeyPressed { keyEvent ->
            if (KEYS.containsKey(keyEvent.code)) {
                val value = KEYS[keyEvent.code]
                setPressed(value!!, true)
            }
        }

        scene.setOnKeyReleased { keyEvent ->
            if (KEYS.containsKey(keyEvent.code)) {
                val value = KEYS[keyEvent.code]
                setPressed(value!!, false)
            }
        }
    }

    fun isPressed(value: UByte): Boolean {
        return (pressed.toUInt() and (0x01u shl value.toInt())) > 0u
    }
}
