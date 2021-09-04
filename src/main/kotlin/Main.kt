import org.slf4j.LoggerFactory
import java.io.File

val log = LoggerFactory.getLogger("Main")

// Memory
/**
 * 0x000-0x1FF should store font sprite sets
 */
const val MEMORY_SIZE = 0x1000
const val MEMORY_PROGRAM_START = 0x200
val memory = UByteArray(MEMORY_SIZE) // 4KiB RAM

// Registers
const val REGISTER_SIZE = 0x10
val V = UByteArray(REGISTER_SIZE) // 16 general purpose 8-bit registers (VF is the carry flag)
var I: UShort =
    0x0000u // used to store memory addresses, so only the lowest (rightmost) 12 bits are usually used
var PC: UShort = 0x0000u // 16-bit program counter
fun progressPC() {
    PC = PC.plus(2u).toUShort()
}
var SP: UByte = 0x00u // 8-bit stack pointer
const val STACK_SIZE = 0x10
val stack = UShortArray(STACK_SIZE) // 16 stacks

// Keyboard
// Display
// Timer & Sound
var DT: UByte = 0x00u // delay timer register
var ST: UByte = 0x00u // sound timer register

// Instructions
val instructions = fun(opcodeShort: UShort): Boolean { // opcode is 2-Byte long
    var shouldProgressPC = true

    val opcode = opcodeShort.toUInt()

    fun addr() = (opcode and 0x0FFFu).toUShort()
    fun x() = ((opcode and 0x0F00u) shr 8).toInt()
    fun y() = ((opcode and 0x00F0u) shr 4).toInt()
    fun kk() = (opcode and 0x00FFu).toUByte()

    fun add(a: UByte, b: UByte): Pair<UByte, Boolean> {
        val res = a + b
        val value = (res and 0xFFu).toUByte()
        val carry = res >= 0x100u
        return Pair(value, carry)
    }

    fun subtract(a: UByte, b: UByte): Pair<UByte, Boolean> {
        val res = a - b
        val value = (res and 0xFFu).toUByte()
        val borrow = a < b
        return Pair(value, borrow)
    }

    when {
        // 0nnn - SYS addr
        //Jump to a machine code routine at nnn.
        //
        //This instruction is only used on the old computers on which Chip-8 was originally implemented. It is ignored by modern interpreters.

        opcode == 0x00E0u -> {
            //00E0 - CLS
            //Clear the display.
            // TODO
        }
        opcode == 0x00EEu -> {
            //00EE - RET
            //Return from a subroutine.
            //
            //The interpreter sets the program counter to the address at the top of the stack, then subtracts 1 from the stack pointer.
            if (SP == 0.toUByte()) {
                throw IllegalStateException()
            }
            PC = stack[SP.toInt()]
            SP--
            shouldProgressPC = false
        }
        opcode in 0x1000u..0x1FFFu -> {
            //1nnn - JP addr
            //Jump to location nnn.
            //
            //The interpreter sets the program counter to nnn.
            PC = addr()
            shouldProgressPC = false
        }
        opcode in 0x2000u..0x2FFFu -> {
            //2nnn - CALL addr
            //Call subroutine at nnn.
            //
            //The interpreter increments the stack pointer, then puts the current PC on the top of the stack. The PC is then set to nnn.
            if (PC.equals(STACK_SIZE - 1)) {
                throw IllegalStateException()
            }
            stack[SP.toInt()] = PC
            SP++
            PC = addr()
            shouldProgressPC = false
        }
        opcode in 0x3000u..0x3FFFu -> {
            //3xkk - SE Vx, UByte
            //Skip next instruction if Vx = kk.
            //
            //The interpreter compares register Vx to kk, and if they are equal, increments the program counter by 2.
            if (V[x()] == kk()) {
                progressPC()
            }
        }
        opcode in 0x4000u..0x4FFFu -> {
            //4xkk - SNE Vx, UByte
            //Skip next instruction if Vx != kk.
            //
            //The interpreter compares register Vx to kk, and if they are not equal, increments the program counter by 2.
            if (V[x()] != kk()) {
                progressPC()
            }
        }
        opcode in 0x5000u..0x5FFFu -> {
            //5xy0 - SE Vx, Vy
            //Skip next instruction if Vx = Vy.
            //
            //The interpreter compares register Vx to register Vy, and if they are equal, increments the program counter by 2.
            if (V[x()] == V[y()]) {
                progressPC()
            }
        }
        opcode in 0x6000u..0x6FFFu -> {
            //6xkk - LD Vx, UByte
            //Set Vx = kk.
            //
            //The interpreter puts the value kk into register Vx.
            V[x()] = kk()
        }
        opcode in 0x7000u..0x7FFFu -> {
            //7xkk - ADD Vx, UByte
            //Set Vx = Vx + kk.
            //
            //Adds the value kk to the value of register Vx, then stores the result in Vx.
            val xx = x()
            val (value, _) = add(V[xx], kk())
            V[xx] = value
        }
        opcode in 0x8000u..0x8FFFu ->
            when (opcode and 0x000Fu) {
                0x0u -> {
                    //8xy0 - LD Vx, Vy
                    //Set Vx = Vy.
                    //
                    //Stores the value of register Vy in register Vx.
                    V[x()] = V[y()]
                }
                0x1u -> {
                    //8xy1 - OR Vx, Vy
                    //Set Vx = Vx OR Vy.
                    //
                    //Performs a bitwise OR on the values of Vx and Vy, then stores the result in Vx. A bitwise OR compares the corrseponding bits from two values, and if either bit is 1, then the same bit in the result is also 1. Otherwise, it is 0.
                    val xx = x()
                    V[xx] = (V[xx].toInt() or V[y()].toInt()).toUByte()
                }
                0x2u -> {
                    //8xy2 - AND Vx, Vy
                    //Set Vx = Vx AND Vy.
                    //
                    //Performs a bitwise AND on the values of Vx and Vy, then stores the result in Vx. A bitwise AND compares the corrseponding bits from two values, and if both bits are 1, then the same bit in the result is also 1. Otherwise, it is 0.
                    val xx = x()
                    V[xx] = (V[xx].toInt() and V[y()].toInt()).toUByte()
                }
                0x3u -> {
                    //8xy3 - XOR Vx, Vy
                    //Set Vx = Vx XOR Vy.
                    //
                    //Performs a bitwise exclusive OR on the values of Vx and Vy, then stores the result in Vx. An exclusive OR compares the corrseponding bits from two values, and if the bits are not both the same, then the corresponding bit in the result is set to 1. Otherwise, it is 0.
                    val xx = x()
                    V[xx] = (V[xx].toInt() xor V[y()].toInt()).toUByte()
                }
                0x4u -> {
                    //8xy4 - ADD Vx, Vy
                    //Set Vx = Vx + Vy, set VF = carry.
                    //
                    //The values of Vx and Vy are added together. If the result is greater than 8 bits (i.e., > 255,) VF is set to 1, otherwise 0. Only the lowest 8 bits of the result are kept, and stored in Vx.
                    val xx = x()
                    val (value, carry) = add(V[xx], V[y()])
                    V[xx] = value
                    V[0xF] = if (carry) 1u else 0u
                }
                0x5u -> {
                    //8xy5 - SUB Vx, Vy
                    //Set Vx = Vx - Vy, set VF = NOT borrow.
                    //
                    //If Vx > Vy, then VF is set to 1, otherwise 0. Then Vy is subtracted from Vx, and the results stored in Vx.
                    val xx = x()
                    val (value, borrow) = subtract(V[xx], V[y()])
                    V[xx] = value
                    V[0xF] = if (!borrow) 1u else 0u
                }
                0x6u -> {
                    //8xy6 - SHR Vx {, Vy}
                    //Set Vx = Vx SHR 1.
                    //
                    //If the least-significant bit of Vx is 1, then VF is set to 1, otherwise 0. Then Vx is divided by 2.
                    val xx = x()
                    V[xx] = (V[xx].toInt() shr 1).toUByte()
                }
                0x7u -> {
                    //8xy7 - SUBN Vx, Vy
                    //Set Vx = Vy - Vx, set VF = NOT borrow.
                    //
                    //If Vy > Vx, then VF is set to 1, otherwise 0. Then Vx is subtracted from Vy, and the results stored in Vx.
                    val xx = x()
                    val (value, borrow) = subtract(V[y()], V[xx])
                    V[xx] = value
                    V[0xF] = if (!borrow) 1u else 0u
                }
                0xEu -> {
                    //8xyE - SHL Vx {, Vy}
                    //Set Vx = Vx SHL 1.
                    //
                    //If the most-significant bit of Vx is 1, then VF is set to 1, otherwise to 0. Then Vx is multiplied by 2.
                    val xx = x()
                    V[xx] = (V[xx].toInt() shl 1).toUByte()
                }
                else -> throw IllegalArgumentException()
            }
        opcode in 0x9000u..0x9FFFu -> {
            //9xy0 - SNE Vx, Vy
            //Skip next instruction if Vx != Vy.
            //
            //The values of Vx and Vy are compared, and if they are not equal, the program counter is increased by 2.
            if (V[x()] != V[y()]) {
                progressPC()
            }
        }
        opcode in 0xA000u..0xAFFFu -> {
            //Annn - LD I, addr
            //Set I = nnn.
            //
            //The value of register I is set to nnn.
            I = addr()
        }
        opcode in 0xB000u..0xBFFFu -> {
            //Bnnn - JP V0, addr
            //Jump to location nnn + V0.
            //
            //The program counter is set to nnn plus the value of V0.
            PC = (V[0x0] + addr()).toUShort()
            shouldProgressPC = false
        }
        opcode in 0xC000u..0xCFFFu -> {
            //Cxkk - RND Vx, UByte
            //Set Vx = random UByte AND kk.
            //
            //The interpreter generates a random number from 0 to 255, which is then ANDed with the value kk. The results are stored in Vx. See instruction 8xy2 for more information on AND.
            V[x()] = (kk().toInt() and (0x00..0xFF).random()).toUByte()
        }
        opcode in 0xD000u..0xDFFFu -> {
            //Dxyn - DRW Vx, Vy, nibble
            //Display n-UByte sprite starting at memory location I at (Vx, Vy), set VF = collision.
            //
            //The interpreter reads n UBytes from memory, starting at the address stored in I. These UBytes are then displayed as sprites on screen at coordinates (Vx, Vy). Sprites are XORed onto the existing screen. If this causes any pixels to be erased, VF is set to 1, otherwise it is set to 0. If the sprite is positioned so part of it is outside the coordinates of the display, it wraps around to the opposite side of the screen. See instruction 8xy3 for more information on XOR, and section 2.4, Display, for more information on the Chip-8 screen and sprites.
            // TODO
        }
        opcode in 0xE000u..0xEFFFu -> when (opcode and 0x00FFu) {
            0x9Eu -> {
                //Ex9E - SKP Vx
                //Skip next instruction if key with the value of Vx is pressed.
                //
                //Checks the keyboard, and if the key corresponding to the value of Vx is currently in the down position, PC is increased by 2.
                // TODO
            }
            0xA1u -> {
                //ExA1 - SKNP Vx
                //Skip next instruction if key with the value of Vx is not pressed.
                //
                //Checks the keyboard, and if the key corresponding to the value of Vx is currently in the up position, PC is increased by 2.
                // TODO
            }
        }
        opcode in 0xF000u..0xFFFFu -> when (opcode and 0x00FFu) {
            0x07u -> {
                //Fx07 - LD Vx, DT
                //Set Vx = delay timer value.
                //
                //The value of DT is placed into Vx.
                // TODO
            }
            0x0Au -> {
                //Fx0A - LD Vx, K
                //Wait for a key press, store the value of the key in Vx.
                //
                //All execution stops until a key is pressed, then the value of that key is stored in Vx.
                // TODO
            }
            0x15u -> {
                //Fx15 - LD DT, Vx
                //Set delay timer = Vx.
                //
                //DT is set equal to the value of Vx.
                // TODO
            }
            0x18u -> {
                //Fx18 - LD ST, Vx
                //Set sound timer = Vx.
                //
                //ST is set equal to the value of Vx.
                // TODO
            }
            0x1Eu -> {
                //Fx1E - ADD I, Vx
                //Set I = I + Vx.
                //
                //The values of I and Vx are added, and the results are stored in I.
                // TODO
            }
            0x29u -> {
                //Fx29 - LD F, Vx
                //Set I = location of sprite for digit Vx.
                //
                //The value of I is set to the location for the hexadecimal sprite corresponding to the value of Vx. See section 2.4, Display, for more information on the Chip-8 hexadecimal font.
                // TODO
            }
            0x33u -> {
                //Fx33 - LD B, Vx
                //Store BCD representation of Vx in memory locations I, I+1, and I+2.
                //
                //The interpreter takes the decimal value of Vx, and places the hundreds digit in memory at location in I, the tens digit at location I+1, and the ones digit at location I+2.
                // TODO
            }
            0x55u -> {
                //Fx55 - LD [I], Vx
                //Store registers V0 through Vx in memory starting at location I.
                //
                //The interpreter copies the values of registers V0 through Vx into memory, starting at the address in I.
                // TODO
            }
            0x65u -> {
                //Fx65 - LD Vx, [I]
                //Read registers V0 through Vx from memory starting at location I.
                //
                //The interpreter reads values from memory starting at location I into registers V0 through Vx.
                // TODO
            }
        }
    }
    return shouldProgressPC
}

fun main(args: Array<String>) {
    // Set up render system and register input callbacks
//    setupGraphics();
//    setupInput();

    // Initialize the Chip8 system and load the game into the memory
//    myChip8.initialize();
    initializeCpu()

//    myChip8.loadGame("pong");
    loadRom(args[0])

    // Emulation loop
    while (true) {
        // Emulate one cycle
//        myChip8.emulateCycle();
        val opcode = (((memory[PC.toInt()].toUInt() shl 8) or (memory[PC.toInt() + 1].toUInt()))).toUShort()
        if (log.isTraceEnabled) {
            log.trace("PC=${PC.toUInt().toString(16)},opcode=${opcode.toUInt().toString(16)}")
        }
        val shouldProgressPC = instructions(opcode)
        if (shouldProgressPC) {
            progressPC()
        }

        // If the draw flag is set, update the screen
//        if (myChip8.drawFlag)
//            drawGraphics();

        // Store key press state (Press and Release)
//        myChip8.setKeys();
    }
}

private fun initializeCpu() {
    PC = 0x200u
    I = 0x0000u
    SP = 0x0u
}

private fun loadRom(romPath: String) {
    val rom = File(romPath).readBytes()
    log.info("rom.size=${rom.size}")
    rom.forEachIndexed { i, b -> memory[MEMORY_PROGRAM_START + i] = b.toUByte() }
}
