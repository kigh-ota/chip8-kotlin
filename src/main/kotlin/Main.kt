import Instruction.*
import javafx.animation.KeyFrame
import javafx.animation.Timeline
import javafx.application.Application
import javafx.scene.Group
import javafx.scene.Scene
import javafx.scene.canvas.Canvas
import javafx.scene.canvas.GraphicsContext
import javafx.scene.paint.Color
import javafx.stage.Stage
import javafx.util.Duration
import org.slf4j.LoggerFactory
import java.io.File

val log = LoggerFactory.getLogger("Main")

// Keyboard TODO

class Display(primaryStage: Stage) {
    companion object {
        private const val WIDTH = 64u
        private const val HEIGHT = 32u
        private const val PIXEL_SIZE = 8u
    }

    val gc: GraphicsContext

    private val bufferSizeX = WIDTH / 8u
    private val bufferSize = (bufferSizeX * HEIGHT).toInt()
    private val buffer: UByteArray

    init {
        buffer = UByteArray(bufferSize) { 0u }
        val root = Group()
        val canvas = Canvas((WIDTH * PIXEL_SIZE).toDouble(), (HEIGHT * PIXEL_SIZE).toDouble())
        gc = canvas.graphicsContext2D
        flush()
        root.children.add(canvas);
        primaryStage.scene = Scene(root);
        primaryStage.show()
    }

    fun flush() {
        buffer.forEachIndexed { i, buf ->
            val xStart = (i.toUInt() % bufferSizeX) * PIXEL_SIZE
            val y = i.toUInt() / bufferSizeX
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

    fun clear() {
        repeat(bufferSize) { buffer[it] = 0u }
    }

    fun draw(vx: UByte, vy: UByte, sprites: List<UByte>): Boolean {
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
            val index = iStart + ((y - yStart) * bufferSizeX).toInt()
            val sprite = sprites[(y - yStart).toInt()]
            if (xOffset == 0) {
                buffer[index] = applyXOR(buffer[index], sprite)
            } else {
                buffer[index] = applyXOR(buffer[index], (sprite.toUInt() shr xOffset).toUByte())
                if (index % (bufferSizeX).toInt() < (bufferSizeX - 1u).toInt()) {
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
        val index = (xStart / 8u) + yStart * bufferSizeX
        val xOffset = xStart % 8u
        return Pair(index.toInt(), xOffset.toInt())
    }
}

class Chip8Interpreter : Application() {
    // Memory
    val MEMORY_SIZE = 0x1000u // 4KiB RAM
    val MEMORY_PROGRAM_START = 0x200u
    val MEMORY_FONTSET_START = 0x000u
    val FONT_SPRITE_SIZE = 0x5u
    val memory = UByteArray(MEMORY_SIZE.toInt())

    // Registers
    val REGISTER_SIZE = 0x10 // 16 general purpose 8-bit registers (VF is the carry flag)
    val V = UByteArray(REGISTER_SIZE)
    var I: UShort =
        0x0000u // used to store memory addresses, so only the lowest (rightmost) 12 bits are usually used
    var PC: UShort = 0x0000u // 16-bit program counter
    fun progressPC() {
        PC = PC.plus(2u).toUShort()
    }

    var SP: UByte = 0x00u // 8-bit stack pointer
    val STACK_SIZE = 0x10 // 16 stacks
    val stack = UShortArray(STACK_SIZE)

    lateinit var display: Display

    override fun start(primaryStage: Stage?) {
        loadRom(parameters.raw[0])
        dumpMemory()

        if (primaryStage == null) {
            throw RuntimeException()
        }
        display = Display(primaryStage)
        // Set up render system and register input callbacks
        // setupGraphics();
        // setupInput();

        // Initialize the Chip8 system and load the game into the memory
        // myChip8.initialize();
        initializeCpu()

        val timer = Timeline(KeyFrame(Duration.millis(1000.0 / 60.0), {
            val opcode = fetchOpcodeAt(PC)
            progressPC()
            val instruction = Instruction.get(opcode)
            logCurrentInstruction(opcode, instruction)
            executeInstruction(instruction, opcode)

            if (shouldFlushDisplay) {
                display.flush()
                shouldFlushDisplay = false
            }

            if (DT > 0u) {
                DT--
            }
            if (ST > 0u) {
                ST--
                // TODO beep
            }

            // Store key press state (Press and Release)
            // myChip8.setKeys();
        }))
        timer.cycleCount = Timeline.INDEFINITE
        timer.play()
    }

    private fun logCurrentInstruction(opcode: UShort, instruction: Instruction) {
        if (log.isTraceEnabled) {
            log.trace(
                "PC=${PC.toUInt().toString(16)},opcode=${
                    opcode.toUInt().toString(16)
                }; ${instruction.mnemonic}\t${
                    Instruction.getParameters(
                        instruction,
                        opcode
                    ).joinToString("\t")
                }"
            )
        }
    }

    private fun initializeCpu() {
        PC = 0x200u
        I = 0x0000u
        SP = 0x0u
    }

    var shouldFlushDisplay = false

    // Timer & Sound
    var DT: UByte = 0x00u // delay timer register
    var ST: UByte = 0x00u // sound timer register

    // Instructions
    fun fetchOpcodeAt(addr: UShort) =
        (((memory[addr.toInt()].toUInt() shl 8) or (memory[addr.toInt() + 1].toUInt()))).toUShort()

    fun executeInstruction(instruction: Instruction, opcodeShort: UShort) {
        val opcode = opcodeShort.toUInt()

        val addr = (opcode and 0x0FFFu).toUShort()
        val x = ((opcode and 0x0F00u) shr 8).toInt()
        val y = ((opcode and 0x00F0u) shr 4).toInt()
        val n = (opcode and 0x000Fu).toUByte()
        val kk = (opcode and 0x00FFu).toUByte()

        fun add(a: UByte, b: UByte): Pair<UByte, Boolean> {
            val res = a + b
            val value = (res and 0xFFu).toUByte()
            val overflow = res >= 0x100u
            return Pair(value, overflow)
        }

        fun add(a: UShort, b: UByte): Pair<UShort, Boolean> {
            val res = a + b
            val value = (res and 0xFFFFu).toUShort()
            val overflow = res >= 0x10000u
            return Pair(value, overflow)
        }

        fun subtract(a: UByte, b: UByte): Pair<UByte, Boolean> {
            val res = a - b
            val value = (res and 0xFFu).toUByte()
            val borrow = a < b
            return Pair(value, borrow)
        }

        when (instruction) {
            M00E0_CLS -> {
                display.clear()
                shouldFlushDisplay = true
            }
            M00EE_RET -> {
                //Return from a subroutine.
                if (SP == 0.toUByte()) {
                    throw IllegalStateException()
                }
                PC = stack[SP.toInt() - 1]
                SP--
            }
            M0nnn_SYS -> {
                //Jump to a machine code routine at nnn.
            }
            M1nnn_JP -> {
                //Jump to location nnn.
                PC = addr
            }
            M2nnn_CALL -> {
                //Call subroutine at nnn.
                if (PC.equals(STACK_SIZE - 1)) {
                    throw IllegalStateException()
                }
                stack[SP.toInt()] = PC
                SP++
                PC = addr
            }
            M3xkk_SE -> {
                //Skip next instruction if Vx = kk.
                if (V[x] == kk) {
                    progressPC()
                }
            }
            M4xkk_SNE -> {
                //Skip next instruction if Vx != kk.
                if (V[x] != kk) {
                    progressPC()
                }
            }
            M5xy0_SE -> {
                //Skip next instruction if Vx = Vy.
                if (V[x] == V[y]) {
                    progressPC()
                }
            }
            M6xkk_LD -> {
                //Set Vx = kk.
                V[x] = kk
            }
            M7xkk_ADD -> {
                //Set Vx = Vx + kk.
                val (value, _) = add(V[x], kk)
                V[x] = value
            }
            M8xy0_LD -> {
                //Set Vx = Vy.
                V[x] = V[y]
            }
            M8xy1_OR -> {
                //Set Vx = Vx OR Vy.
                V[x] = (V[x].toInt() or V[y].toInt()).toUByte()
            }
            M8xy2_AND -> {
                //Set Vx = Vx AND Vy.
                V[x] = (V[x].toInt() and V[y].toInt()).toUByte()
            }
            M8xy3_XOR -> {
                //Set Vx = Vx XOR Vy.
                V[x] = (V[x].toInt() xor V[y].toInt()).toUByte()
            }
            M8xy4_ADD -> {
                //Set Vx = Vx + Vy, set VF = carry.
                val (value, overflow) = add(V[x], V[y])
                V[x] = value
                V[0xF] = if (overflow) 1u else 0u
            }
            M8xy5_SUB -> {
                //Set Vx = Vx - Vy, set VF = NOT borrow.
                val (value, borrow) = subtract(V[x], V[y])
                V[x] = value
                V[0xF] = if (!borrow) 1u else 0u
            }
            M8xy6_SHR -> {
                //Set Vx = Vx SHR 1.
                V[x] = (V[x].toInt() shr 1).toUByte()
            }
            M8xy7_SUBN -> {
                //Set Vx = Vy - Vx, set VF = NOT borrow.
                val (value, borrow) = subtract(V[y], V[x])
                V[x] = value
                V[0xF] = if (!borrow) 1u else 0u
            }
            M8xyE_SHL -> {
                //Set Vx = Vx SHL 1.
                V[x] = (V[x].toInt() shl 1).toUByte()
            }
            M9xy0_SNE -> {
                //Skip next instruction if Vx != Vy.
                if (V[x] != V[y]) {
                    progressPC()
                }
            }
            MAnnn_LD -> {
                //Set I = nnn.
                I = addr
            }
            MBnnn_JP -> {
                //Jump to location nnn + V0.
                PC = (V[0x0] + addr).toUShort()
            }
            MCxkk_RND -> {
                //Set Vx = random UByte AND kk.
                V[x] = (kk.toInt() and (0x00..0xFF).random()).toUByte()
            }
            MDxyn_DRW -> {
                //Display n-UByte sprite starting at memory location I at (Vx, Vy), set VF = collision.
                //
                // The interpreter reads n bytes from memory, starting at the address stored in I.
                // These bytes are then displayed as sprites on screen at coordinates (Vx, Vy).
                // Sprites are XORed onto the existing screen.
                // If this causes any pixels to be erased, VF is set to 1, otherwise it is set to 0.
                // If the sprite is positioned so part of it is outside the coordinates of the display, it wraps around to the opposite side of the screen.
                // See instruction 8xy3 for more information on XOR, and section 2.4, Display, for more information on the Chip-8 screen and sprites.
                val sprites = memory.slice(I.toInt() until (I + n).toInt())
                display.draw(V[x], V[y], sprites)
                shouldFlushDisplay = true
            }
            MEx9E_SKP -> {
                //Skip next instruction if key with the value of Vx is pressed.
                //
                //TODO Checks the keyboard, and if the key corresponding to the value of Vx is currently in the down position, PC is increased by 2.
            }
            MExA1_SKNP -> {
                //Skip next instruction if key with the value of Vx is not pressed.
                //
                //TODO Checks the keyboard, and if the key corresponding to the value of Vx is currently in the up position, PC is increased by 2.
                progressPC()
            }
            MFx07_LD -> {
                //Fx07 - LD Vx, DT
                //Set Vx = delay timer value.
                //
                //The value of DT is placed into Vx.
                V[x] = DT
            }
            MFx0A_LD -> {
                //Fx0A - LD Vx, K
                //Wait for a key press, store the value of the key in Vx.
                //
                //TODO All execution stops until a key is pressed, then the value of that key is stored in Vx.
                V[x] = 0x0u
            }
            MFx15_LD -> {
                //Set delay timer = Vx.
                DT = V[x]
            }
            MFx18_LD -> {
                //Set sound timer = Vx.
                ST = V[x]
            }
            MFx1E_ADD -> {
                //Set I = I + Vx.
                val (value, overflow) = add(I, V[x])
                I = value
                V[0xF] = if (overflow) 1u else 0u
            }
            MFx29_LD -> {
                //Set I = location of sprite for digit Vx.
                I = (MEMORY_FONTSET_START + (V[x] and 0x0Fu) * FONT_SPRITE_SIZE).toUShort()
            }
            MFx33_LD -> {
                //Store BCD representation of Vx in memory locations I, I+1, and I+2.
                val B = (V[x] / 100u) % 10u
                val C = (V[x] / 10u) % 10u
                val D = V[x] % 10u
                val i = I.toInt()
                memory[i] = B.toUByte()
                memory[i + 1] = C.toUByte()
                memory[i + 2] = D.toUByte()
            }
            MFx55_LD -> {
                //Store registers V0 through Vx in memory starting at location I.
                val i = I.toInt()
                for (j in 0..x) {
                    memory[i + j] = V[j]
                }
            }
            MFx65_LD -> {
                //Read registers V0 through Vx from memory starting at location I.
                val i = I.toInt()
                for (j in 0..x) {
                    V[j] = memory[i + j]
                }
            }
        }
    }

    private fun dumpMemory() {
        if (!log.isDebugEnabled) {
            return
        }
        for (addr in 0x0000u until MEMORY_SIZE step 2) {
            val opcode = fetchOpcodeAt(addr.toUShort())
            try {
                val instruction = Instruction.get(opcode)
                val parameters = Instruction.getParameters(instruction, opcode)
                log.debug(
                    "${String.format("%04X", addr.toInt())}\t${
                        String.format(
                            "%04X",
                            opcode.toInt()
                        )
                    }\t${instruction.mnemonic}\t${parameters.joinToString("\t")}"
                )
            } catch (e: UnknownInstructionException) {
                log.debug(
                    "${String.format("%04X", addr.toInt())}\t${
                        String.format(
                            "%04X",
                            opcode.toInt()
                        )
                    }"
                )
            }
        }
    }

    private fun loadRom(romPath: String) {
        val rom = File(romPath).readBytes()
        log.info("rom.size=${rom.size}")
        rom.forEachIndexed { i, b -> memory[MEMORY_PROGRAM_START.toInt() + i] = b.toUByte() }
    }
}

fun main(args: Array<String>) {
    Application.launch(Chip8Interpreter::class.java, *args)
}

class UnknownInstructionException(opcode: UShort) :
    IllegalArgumentException("unknown instruction, opcode=${opcode.toUInt().toString(16)}")
