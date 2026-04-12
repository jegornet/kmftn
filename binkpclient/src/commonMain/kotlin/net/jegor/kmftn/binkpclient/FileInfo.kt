package net.jegor.kmftn.binkpclient

/**
 * File transfer information
 */
internal data class FileInfo(
    val name: String,
    val size: Long,
    val unixTime: Long,
    val offset: Long = 0
) {
    companion object {
        fun parse(args: String): FileInfo? {
            val parts = args.split(" ")
            if (parts.size < 3) return null
            return try {
                FileInfo(
                    name = unescapeFileName(parts[0]),
                    size = parts[1].toLong(),
                    unixTime = parts[2].toLong(),
                    offset = if (parts.size > 3) parts[3].toLong() else 0
                )
            } catch (e: NumberFormatException) {
                null
            }
        }

        private fun unescapeFileName(name: String): String {
            val bytes = mutableListOf<Byte>()
            var i = 0

            while (i < name.length) {
                // Check for \xHH format
                if (name[i] == '\\' && i + 3 < name.length && name[i + 1].lowercaseChar() == 'x') {
                    try {
                        val hex = name.substring(i + 2, i + 4)
                        bytes.add(hex.toInt(16).toByte())
                        i += 4
                        continue
                    } catch (e: NumberFormatException) {
                        // Fall through to regular character
                    }
                }
                // Also handle \HH format (without 'x') for compatibility with binkd
                if (name[i] == '\\' && i + 2 < name.length) {
                    try {
                        val hex = name.substring(i + 1, i + 3)
                        if (hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
                            bytes.add(hex.toInt(16).toByte())
                            i += 3
                            continue
                        }
                    } catch (e: NumberFormatException) {
                        // Fall through to regular character
                    }
                }
                // Regular character - add as-is (ASCII byte)
                bytes.add(name[i].code.toByte())
                i++
            }

            // Convert bytes back to String using UTF-8
            return bytes.toByteArray().decodeToString()
        }
    }

    fun toCommandArgs(withOffset: Boolean = true): String {
        val escapedName = escapeFileName(name)
        return if (withOffset) {
            "$escapedName $size $unixTime $offset"
        } else {
            "$escapedName $size $unixTime"
        }
    }

    private fun escapeFileName(name: String): String {
        // Convert to UTF-8 bytes for proper encoding (compatible with binkd)
        val bytes = name.encodeToByteArray()
        val result = StringBuilder()

        for (byte in bytes) {
            val unsigned = byte.toInt() and 0xFF  // Convert to unsigned byte
            when {
                // Space
                unsigned == 0x20 -> result.append("\\x20")
                // Backslash
                unsigned == 0x5C -> result.append("\\x5c")
                // Printable ASCII characters (excluding space and backslash)
                unsigned in 0x21..0x7E && unsigned != 0x5C -> result.append(unsigned.toChar())
                // Control characters and non-ASCII - escape as \xHH
                else -> result.append("\\x${unsigned.toString(16).padStart(2, '0')}")
            }
        }
        return result.toString()
    }
}