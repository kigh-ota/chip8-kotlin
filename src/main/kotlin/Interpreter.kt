import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

@ExperimentalUnsignedTypes
class Interpreter(private val display: Display) {
    companion object {
        private const val MEMORY_SIZE = 0x1000 // 4KiB RAM
        private const val MEMORY_PROGRAM_START = 0x200u
        private const val MEMORY_FONT_SET_START = 0x000u
        private const val FONT_SPRITE_SIZE = 0x5u
        private const val STACK_SIZE = 0x10 // 16 stacks
    }

    private val mem = UByteArray(MEMORY_SIZE)

    // Registers
    private val V = UByteArray(0x10) // 16 general purpose 8-bit registers (VF is the carry flag)
    private var I: UShort = 0x0000u // used to store memory addresses
    private var PC: UShort = 0x0000u // 16-bit program counter
    private fun progressPC() {
        PC = PC.plus(2u).toUShort()
    }

    private var SP: UByte = 0x00u // 8-bit stack pointer
    private val stack = UShortArray(STACK_SIZE)

    // Timer & Sound
    private var DT: UByte = 0x00u // delay timer register
    private var ST: UByte = 0x00u // sound timer register

    private var shouldFlushDisplay = false

    private val log = LoggerFactory.getLogger(javaClass.name)

    private var scheduledFuture: ScheduledFuture<*>? = null

    fun start(romFile: File) {
        initializeRegisters()
        shouldFlushDisplay = false
        initializeMemory()
        display.init()

        loadROM(romFile)
        dumpMemory()

        var c = 0
        var t = System.currentTimeMillis()
        scheduledFuture = Executors.newScheduledThreadPool(0)
            .scheduleAtFixedRate({
                cycle()
                c++
                if (c % 60 == 0) {
                    val u = System.currentTimeMillis()
                    val d = u - t
                    val fps = 60.0 / d * 1000
                    log.debug("$fps fps")
                    t = u
                }
             }, 0L, 1000000 / 60, TimeUnit.MICROSECONDS)
    }

    fun stop() {
        scheduledFuture?.cancel(true)
    }

    private fun initializeRegisters() {
        repeat(V.size) { V[it] = 0x00u }
        I = 0x0000u
        PC = 0x200u
        SP = 0x0u
        repeat(stack.size) { stack[it] = 0x0000u }
        DT = 0x00u
        ST = 0x00u
    }

    private fun initializeMemory() {
        repeat(mem.size) { mem[it] = 0x00u }
        // TODO store font sprites
    }

    private fun cycle() {
        val opcode = fetchOpcodeAt(PC)
        progressPC()
        val instruction = Instruction.fromOpcode(opcode)
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

    private fun fetchOpcodeAt(addr: UShort) =
        (((mem[addr.toInt()].toUInt() shl 8) or (mem[addr.toInt() + 1].toUInt()))).toUShort()

    private fun executeInstruction(instruction: Instruction, opcodeShort: UShort) {
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
            Instruction.M00E0_CLS -> {
                display.clear()
                shouldFlushDisplay = true
            }
            Instruction.M00EE_RET -> {
                //Return from a subroutine.
                if (SP == 0.toUByte()) {
                    throw IllegalStateException()
                }
                PC = stack[SP.toInt() - 1]
                SP--
            }
            Instruction.M0nnn_SYS -> {
                //Jump to a machine code routine at nnn.
            }
            Instruction.M1nnn_JP -> {
                //Jump to location nnn.
                PC = addr
            }
            Instruction.M2nnn_CALL -> {
                //Call subroutine at nnn.
                if (PC.equals(STACK_SIZE - 1)) {
                    throw IllegalStateException()
                }
                stack[SP.toInt()] = PC
                SP++
                PC = addr
            }
            Instruction.M3xkk_SE -> {
                //Skip next instruction if Vx = kk.
                if (V[x] == kk) {
                    progressPC()
                }
            }
            Instruction.M4xkk_SNE -> {
                //Skip next instruction if Vx != kk.
                if (V[x] != kk) {
                    progressPC()
                }
            }
            Instruction.M5xy0_SE -> {
                //Skip next instruction if Vx = Vy.
                if (V[x] == V[y]) {
                    progressPC()
                }
            }
            Instruction.M6xkk_LD -> {
                //Set Vx = kk.
                V[x] = kk
            }
            Instruction.M7xkk_ADD -> {
                //Set Vx = Vx + kk.
                val (value, _) = add(V[x], kk)
                V[x] = value
            }
            Instruction.M8xy0_LD -> {
                //Set Vx = Vy.
                V[x] = V[y]
            }
            Instruction.M8xy1_OR -> {
                //Set Vx = Vx OR Vy.
                V[x] = (V[x].toInt() or V[y].toInt()).toUByte()
            }
            Instruction.M8xy2_AND -> {
                //Set Vx = Vx AND Vy.
                V[x] = (V[x].toInt() and V[y].toInt()).toUByte()
            }
            Instruction.M8xy3_XOR -> {
                //Set Vx = Vx XOR Vy.
                V[x] = (V[x].toInt() xor V[y].toInt()).toUByte()
            }
            Instruction.M8xy4_ADD -> {
                //Set Vx = Vx + Vy, set VF = carry.
                val (value, overflow) = add(V[x], V[y])
                V[x] = value
                V[0xF] = if (overflow) 1u else 0u
            }
            Instruction.M8xy5_SUB -> {
                //Set Vx = Vx - Vy, set VF = NOT borrow.
                val (value, borrow) = subtract(V[x], V[y])
                V[x] = value
                V[0xF] = if (!borrow) 1u else 0u
            }
            Instruction.M8xy6_SHR -> {
                //Set Vx = Vx SHR 1.
                V[x] = (V[x].toInt() shr 1).toUByte()
            }
            Instruction.M8xy7_SUBN -> {
                //Set Vx = Vy - Vx, set VF = NOT borrow.
                val (value, borrow) = subtract(V[y], V[x])
                V[x] = value
                V[0xF] = if (!borrow) 1u else 0u
            }
            Instruction.M8xyE_SHL -> {
                //Set Vx = Vx SHL 1.
                V[x] = (V[x].toInt() shl 1).toUByte()
            }
            Instruction.M9xy0_SNE -> {
                //Skip next instruction if Vx != Vy.
                if (V[x] != V[y]) {
                    progressPC()
                }
            }
            Instruction.MAnnn_LD -> {
                //Set I = nnn.
                I = addr
            }
            Instruction.MBnnn_JP -> {
                //Jump to location nnn + V0.
                PC = (V[0x0] + addr).toUShort()
            }
            Instruction.MCxkk_RND -> {
                //Set Vx = random UByte AND kk.
                V[x] = (kk.toInt() and (0x00..0xFF).random()).toUByte()
            }
            Instruction.MDxyn_DRW -> {
                //Display n-UByte sprite starting at memory location I at (Vx, Vy), set VF = collision.
                //
                // The interpreter reads n bytes from memory, starting at the address stored in I.
                // These bytes are then displayed as sprites on screen at coordinates (Vx, Vy).
                // Sprites are XORed onto the existing screen.
                // If this causes any pixels to be erased, VF is set to 1, otherwise it is set to 0.
                // If the sprite is positioned so part of it is outside the coordinates of the display, it wraps around to the opposite side of the screen.
                // See instruction 8xy3 for more information on XOR, and section 2.4, Display, for more information on the Chip-8 screen and sprites.
                val sprites = mem.slice(I.toInt() until (I + n).toInt())
                display.draw(V[x], V[y], sprites)
                shouldFlushDisplay = true
            }
            Instruction.MEx9E_SKP -> {
                //Skip next instruction if key with the value of Vx is pressed.
                //
                //TODO Checks the keyboard, and if the key corresponding to the value of Vx is currently in the down position, PC is increased by 2.
            }
            Instruction.MExA1_SKNP -> {
                //Skip next instruction if key with the value of Vx is not pressed.
                //
                //TODO Checks the keyboard, and if the key corresponding to the value of Vx is currently in the up position, PC is increased by 2.
                progressPC()
            }
            Instruction.MFx07_LD -> {
                //Fx07 - LD Vx, DT
                //Set Vx = delay timer value.
                //
                //The value of DT is placed into Vx.
                V[x] = DT
            }
            Instruction.MFx0A_LD -> {
                //Fx0A - LD Vx, K
                //Wait for a key press, store the value of the key in Vx.
                //
                //TODO All execution stops until a key is pressed, then the value of that key is stored in Vx.
                V[x] = 0x0u
            }
            Instruction.MFx15_LD -> {
                //Set delay timer = Vx.
                DT = V[x]
            }
            Instruction.MFx18_LD -> {
                //Set sound timer = Vx.
                ST = V[x]
            }
            Instruction.MFx1E_ADD -> {
                //Set I = I + Vx.
                val (value, overflow) = add(I, V[x])
                I = value
                V[0xF] = if (overflow) 1u else 0u
            }
            Instruction.MFx29_LD -> {
                //Set I = location of sprite for digit Vx.
                I = (MEMORY_FONT_SET_START + (V[x] and 0x0Fu) * FONT_SPRITE_SIZE).toUShort()
            }
            Instruction.MFx33_LD -> {
                //Store BCD representation of Vx in memory locations I, I+1, and I+2.
                val B = (V[x] / 100u) % 10u
                val C = (V[x] / 10u) % 10u
                val D = V[x] % 10u
                val i = I.toInt()
                mem[i] = B.toUByte()
                mem[i + 1] = C.toUByte()
                mem[i + 2] = D.toUByte()
            }
            Instruction.MFx55_LD -> {
                //Store registers V0 through Vx in memory starting at location I.
                val i = I.toInt()
                for (j in 0..x) {
                    mem[i + j] = V[j]
                }
            }
            Instruction.MFx65_LD -> {
                //Read registers V0 through Vx from memory starting at location I.
                val i = I.toInt()
                for (j in 0..x) {
                    V[j] = mem[i + j]
                }
            }
        }
    }

    private fun dumpMemory() {
        if (!log.isDebugEnabled) {
            return
        }
        for (addr in 0x0000 until MEMORY_SIZE step 2) {
            val opcode = fetchOpcodeAt(addr.toUShort())
            try {
                val instruction = Instruction.fromOpcode(opcode)
                val parameters = Instruction.getParameters(instruction, opcode)
                log.debug(
                    "${String.format("%04X", addr)}\t${
                        String.format(
                            "%04X",
                            opcode.toInt()
                        )
                    }\t${instruction.mnemonic}\t${parameters.joinToString("\t")}"
                )
            } catch (e: UnknownInstructionException) {
                log.debug(
                    "${String.format("%04X", addr)}\t${
                        String.format(
                            "%04X",
                            opcode.toInt()
                        )
                    }"
                )
            }
        }
    }

    private fun loadROM(romFile: File) {
        val rom = romFile.readBytes()
        log.info("rom.size=${rom.size}")
        rom.forEachIndexed { i, b -> mem[MEMORY_PROGRAM_START.toInt() + i] = b.toUByte() }
    }
}

class UnknownInstructionException(opcode: UShort) :
    IllegalArgumentException("unknown instruction, opcode=${opcode.toUInt().toString(16)}")
