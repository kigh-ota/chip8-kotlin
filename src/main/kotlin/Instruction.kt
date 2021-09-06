enum class Instruction(val mnemonic: String) {
    M00E0_CLS("CLS"),
    M00EE_RET("RET"),
    M0nnn_SYS("SYS"),
    M1nnn_JP("JP"),
    M2nnn_CALL("CALL"),
    M3xkk_SE("SE"),
    M4xkk_SNE("SNE"),
    M5xy0_SE("SE"),
    M6xkk_LD("LD"),
    M7xkk_ADD("ADD"),
    M8xy0_LD("LD"),
    M8xy1_OR("OR"),
    M8xy2_AND("AND"),
    M8xy3_XOR("XOR"),
    M8xy4_ADD("ADD"),
    M8xy5_SUB("SUB"),
    M8xy6_SHR("SHR"),
    M8xy7_SUBN("SUBN"),
    M8xyE_SHL("SHL"),
    M9xy0_SNE("SNE"),
    MAnnn_LD("LD"),
    MBnnn_JP("JP"),
    MCxkk_RND("RND"),
    MDxyn_DRW("DRW"),
    MEx9E_SKP("SKP"),
    MExA1_SKNP("SKNP"),
    MFx07_LD("LD"),
    MFx0A_LD("LD"),
    MFx15_LD("LD"),
    MFx18_LD("LD"),
    MFx1E_ADD("ADD"),
    MFx29_LD("LD"),
    MFx33_LD("LD"),
    MFx55_LD("LD"),
    MFx65_LD("LD");

    companion object {
        fun get(opcode: UShort): Instruction { // opcode is 2-Byte long
            val opcodeInt = opcode.toUInt()
            return when {
                opcodeInt in 0x0000u..0x0FFFu -> when (opcodeInt) {
                    0x00E0u -> M00E0_CLS
                    0x00EEu -> M00EE_RET
                    else -> M0nnn_SYS
                }
                opcodeInt in 0x1000u..0x1FFFu -> M1nnn_JP
                opcodeInt in 0x2000u..0x2FFFu -> M2nnn_CALL
                opcodeInt in 0x3000u..0x3FFFu -> M3xkk_SE
                opcodeInt in 0x4000u..0x4FFFu -> M4xkk_SNE
                opcodeInt in 0x5000u..0x5FFFu -> M5xy0_SE
                opcodeInt in 0x6000u..0x6FFFu -> M6xkk_LD
                opcodeInt in 0x7000u..0x7FFFu -> M7xkk_ADD
                opcodeInt in 0x8000u..0x8FFFu -> when (opcodeInt and 0x000Fu) {
                    0x0u -> M8xy0_LD
                    0x1u -> M8xy1_OR
                    0x2u -> M8xy2_AND
                    0x3u -> M8xy3_XOR
                    0x4u -> M8xy4_ADD
                    0x5u -> M8xy5_SUB
                    0x6u -> M8xy6_SHR
                    0x7u -> M8xy7_SUBN
                    0xEu -> M8xyE_SHL
                    else -> throw UnknownInstructionException(opcode)
                }
                opcodeInt in 0x9000u..0x9FFFu -> M9xy0_SNE
                opcodeInt in 0xA000u..0xAFFFu -> MAnnn_LD
                opcodeInt in 0xB000u..0xBFFFu -> MBnnn_JP
                opcodeInt in 0xC000u..0xCFFFu -> MCxkk_RND
                opcodeInt in 0xD000u..0xDFFFu -> MDxyn_DRW
                opcodeInt in 0xE000u..0xEFFFu -> when (opcodeInt and 0x00FFu) {
                    0x9Eu -> MEx9E_SKP
                    0xA1u -> MExA1_SKNP
                    else -> throw UnknownInstructionException(opcode)
                }
                opcodeInt in 0xF000u..0xFFFFu -> when (opcodeInt and 0x00FFu) {
                    0x07u -> MFx07_LD
                    0x0Au -> MFx0A_LD
                    0x15u -> MFx15_LD
                    0x18u -> MFx18_LD
                    0x1Eu -> MFx1E_ADD
                    0x29u -> MFx29_LD
                    0x33u -> MFx33_LD
                    0x55u -> MFx55_LD
                    0x65u -> MFx65_LD
                    else -> throw UnknownInstructionException(opcode)
                }
                else -> throw UnknownInstructionException(opcode)
            }
        }

        fun getParameters(instruction: Instruction, opcodeShort: UShort): Array<String> {
            val opcode = opcodeShort.toUInt()

            val addr = String.format("%03X", (opcode and 0x0FFFu).toInt())
            val x = ((opcode and 0x0F00u) shr 8).toString(16)
            val y = ((opcode and 0x00F0u) shr 4).toString(16)
            val n = (opcode and 0x000Fu).toString(16)
            val kk = String.format("%02X", (opcode and 0x00FFu).toInt())

            return when (instruction) {
                M00E0_CLS -> arrayOf()
                M00EE_RET -> arrayOf()
                M0nnn_SYS -> arrayOf(addr)
                M1nnn_JP -> arrayOf(addr)
                M2nnn_CALL -> arrayOf(addr)
                M3xkk_SE -> arrayOf("V${x}", kk)
                M4xkk_SNE -> arrayOf("V${x}", kk)
                M5xy0_SE -> arrayOf("V${x}", "V${y}")
                M6xkk_LD -> arrayOf("V${x}", kk)
                M7xkk_ADD -> arrayOf("V${x}", kk)
                M8xy0_LD -> arrayOf("V${x}", "V${y}")
                M8xy1_OR -> arrayOf("V${x}", "V${y}")
                M8xy2_AND -> arrayOf("V${x}", "V${y}")
                M8xy3_XOR -> arrayOf("V${x}", "V${y}")
                M8xy4_ADD -> arrayOf("V${x}", "V${y}")
                M8xy5_SUB -> arrayOf("V${x}", "V${y}")
                M8xy6_SHR -> arrayOf("V${x}")
                M8xy7_SUBN -> arrayOf("V${x}", "V${y}")
                M8xyE_SHL -> arrayOf("V${x}")
                M9xy0_SNE -> arrayOf("V${x}", "V${y}")
                MAnnn_LD -> arrayOf("I", addr)
                MBnnn_JP -> arrayOf("V0", addr)
                MCxkk_RND -> arrayOf("V${x}", kk)
                MDxyn_DRW -> arrayOf("V${x}", "V${y}", n)
                MEx9E_SKP -> arrayOf("V${x}")
                MExA1_SKNP -> arrayOf("V${x}")
                MFx07_LD -> arrayOf("V${x}", "DT")
                MFx0A_LD -> arrayOf("V${x}", "K")
                MFx15_LD -> arrayOf("DT", "V${x}")
                MFx18_LD -> arrayOf("ST", "V${x}")
                MFx1E_ADD -> arrayOf("I", "V${x}")
                MFx29_LD -> arrayOf("F", "V${x}")
                MFx33_LD -> arrayOf("B", "V${x}")
                MFx55_LD -> arrayOf("[I]", "V${x}")
                MFx65_LD -> arrayOf("V${x}", "[I]")
            }
        }
    }
}
