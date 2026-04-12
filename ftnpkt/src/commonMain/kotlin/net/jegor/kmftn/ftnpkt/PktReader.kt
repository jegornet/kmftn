package net.jegor.kmftn.ftnpkt

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray

/**
 * Reads a FidoNet packet file and returns the appropriate [Pkt] implementation.
 *
 * Type detection follows the HPT approach:
 * 1. pktVersion must be 2 (offset 0x12)
 * 2. Read capabilityWord (offset 0x2C) and its byte-swapped validation copy (offset 0x28)
 *    - capWord == 0 → Stone Age Type-2 ([Pkt2])
 *    - capWord != 0 and validation matches → check origNet for Type-2+ vs Type-2e
 *    - capWord != 0 and validation mismatches → error
 * 3. If capWord is valid and origNet == 0xFFFF and origPoint != 0 → Type-2+ ([Pkt2plus])
 *    Otherwise → Type-2e ([Pkt2e])
 */
public object PktReader {

    public fun read(path: Path): Pkt {
        val data = SystemFileSystem.source(path).buffered().use { it.readByteArray() }
        return fromByteArray(data)
    }

    public fun fromByteArray(data: ByteArray): Pkt {
        require(data.size >= HEADER_SIZE) {
            "Packet too small: ${data.size} bytes, minimum $HEADER_SIZE"
        }

        val pktVer = readLeUShort(data, OFS_PKT_VER)
        require(pktVer == 2.toUShort()) {
            "Invalid packet version: $pktVer, expected 2"
        }

        val capWord = readLeUShort(data, OFS_CAP_WORD)
        val capValid = readLeUShort(data, OFS_CAP_VALID)

        if (capWord == 0.toUShort()) {
            return Pkt2.fromByteArray(data)
        }

        // Validate: capValid must be byte-swapped copy of capWord
        val swapped = swapBytes(capWord)
        require(swapped == capValid) {
            "CapabilityWord validation error: capWord=0x${capWord.toString(16)}, " +
                "capValid=0x${capValid.toString(16)}"
        }

        // Distinguish Type-2+ from Type-2e:
        // Type-2+ sets origNet to 0xFFFF when origPoint != 0
        val rawOrigNet = readLeUShort(data, OFS_ORIG_NET)
        val origPoint = readLeUShort(data, OFS_ORIG_POINT)

        return if (rawOrigNet == 0xFFFFu.toUShort() && origPoint != 0.toUShort()) {
            Pkt2plus.fromByteArray(data)
        } else {
            Pkt2e.fromByteArray(data)
        }
    }

    private fun swapBytes(value: UShort): UShort {
        val v = value.toInt()
        return (((v and 0xFF) shl 8) or ((v shr 8) and 0xFF)).toUShort()
    }

    private const val HEADER_SIZE = 58
    private const val OFS_PKT_VER = 0x12
    private const val OFS_ORIG_NET = 0x14
    private const val OFS_CAP_VALID = 0x28
    private const val OFS_CAP_WORD = 0x2C
    private const val OFS_ORIG_POINT = 0x32
}
