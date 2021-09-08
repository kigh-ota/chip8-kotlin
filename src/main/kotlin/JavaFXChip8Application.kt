import javafx.application.Application
import javafx.application.Platform
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
        val display = JavaFXDisplay(primaryStage)
        interpreter = Interpreter(display)

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
