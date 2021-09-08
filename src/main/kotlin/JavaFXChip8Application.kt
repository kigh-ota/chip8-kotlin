import javafx.application.Application
import javafx.scene.Group
import javafx.scene.Scene
import javafx.scene.input.KeyCode
import javafx.stage.FileChooser
import javafx.stage.FileChooser.ExtensionFilter
import javafx.stage.Stage
import java.io.File


@ExperimentalUnsignedTypes
class JavaFXChip8Application : Application() {
    private var interpreter: Interpreter? = null

    override fun start(primaryStage: Stage?) {
        if (primaryStage == null) {
            throw RuntimeException()
        }
        val root = Group()
        val display = JavaFXDisplay(root)
        val scene = Scene(root)
        val keyboard = Keyboard(scene)

        primaryStage.scene = scene
        primaryStage.show()

        interpreter = Interpreter(display, keyboard)

        val romFile = chooseROMFile(primaryStage)
        if (romFile == null) {
            primaryStage.close()
            return
        }
        interpreter?.start(romFile)
    }

    override fun stop() {
        interpreter?.stop()
    }

    private fun chooseROMFile(primaryStage: Stage): File? {
        val fileChooser = FileChooser()
        fileChooser.title = "Open ROM File"
        fileChooser.extensionFilters.addAll(
            ExtensionFilter("CH8 Files", "*.ch8"),
            ExtensionFilter("All Files", "*.*")
        )
        return fileChooser.showOpenDialog(primaryStage)
    }
}
