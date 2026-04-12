package net.jegor.kmftn.binkpclient

/**
 * Represents a binkp protocol frame
 */
internal data class BinkpFrame(
    val isCommand: Boolean,
    val data: ByteArray
) {
    val commandId: BinkpCommand?
        get() = if (isCommand && data.isNotEmpty()) BinkpCommand.fromId(data[0].toInt() and 0xFF) else null

    val argument: String
        get() = if (isCommand && data.size > 1) {
            data.decodeToString(1, data.size).trimEnd('\u0000')
        } else if (!isCommand) {
            ""
        } else {
            ""
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BinkpFrame) return false
        return isCommand == other.isCommand && data.contentEquals(other.data)
    }

    override fun hashCode(): Int = 31 * isCommand.hashCode() + data.contentHashCode()
}