package net.jegor.kmftn.ftnpkt

/**
 * FTS-0001 Packed Message.
 *
 * Fixed header (14 bytes) followed by four null-terminated strings:
 *
 *   0x0002          message type
 *   origNode        2 bytes, little-endian
 *   destNode        2 bytes, little-endian
 *   origNet         2 bytes, little-endian
 *   destNet         2 bytes, little-endian
 *   attribute       2 bytes, little-endian
 *   cost            2 bytes, little-endian
 *   dateTime        20 bytes, null-terminated   "01 Jan 86  02:34:56\0"
 *   toUserName      max 36 bytes, null-terminated
 *   fromUserName    max 36 bytes, null-terminated
 *   subject         max 72 bytes, null-terminated
 *   text            unbounded, null-terminated
 */
public class PackedMsg(
    public val origNode: UShort,
    public val destNode: UShort,
    public val origNet: UShort,
    public val destNet: UShort,
    public val attribute: UShort,
    public val cost: UShort,
    public val dateTime: ByteArray,
    public val toUserName: ByteArray,
    public val fromUserName: ByteArray,
    public val subject: ByteArray,
    public val text: ByteArray,
) {

    public val isPrivate: Boolean get() = attribute.toInt() and 0x0001 != 0
    public val isCrash: Boolean get() = attribute.toInt() and 0x0002 != 0
    public val isRecd: Boolean get() = attribute.toInt() and 0x0004 != 0
    public val isSent: Boolean get() = attribute.toInt() and 0x0008 != 0
    public val isFileAttached: Boolean get() = attribute.toInt() and 0x0010 != 0
    public val isInTransit: Boolean get() = attribute.toInt() and 0x0020 != 0
    public val isOrphan: Boolean get() = attribute.toInt() and 0x0040 != 0
    public val isKillSent: Boolean get() = attribute.toInt() and 0x0080 != 0
    public val isLocal: Boolean get() = attribute.toInt() and 0x0100 != 0
    public val isHoldForPickup: Boolean get() = attribute.toInt() and 0x0200 != 0
    public val isFileRequest: Boolean get() = attribute.toInt() and 0x0800 != 0
    public val isReturnReceiptRequest: Boolean get() = attribute.toInt() and 0x1000 != 0
    public val isReturnReceipt: Boolean get() = attribute.toInt() and 0x2000 != 0
    public val isAuditRequest: Boolean get() = attribute.toInt() and 0x4000 != 0
    public val isFileUpdateReq: Boolean get() = attribute.toInt() and 0x8000 != 0

    public fun toByteArray(): ByteArray {
        val dtBytes = encodeDateTimeField(dateTime)
        val size = HEADER_SIZE + dtBytes.size +
            toUserName.size + 1 + fromUserName.size + 1 +
            subject.size + 1 + text.size + 1
        val buf = ByteArray(size)
        var pos = 0

        pos = writeLeUShort(buf, pos, MSG_TYPE)
        pos = writeLeUShort(buf, pos, origNode)
        pos = writeLeUShort(buf, pos, destNode)
        pos = writeLeUShort(buf, pos, origNet)
        pos = writeLeUShort(buf, pos, destNet)
        pos = writeLeUShort(buf, pos, attribute)
        pos = writeLeUShort(buf, pos, cost)

        dtBytes.copyInto(buf, pos)
        pos += dtBytes.size

        toUserName.copyInto(buf, pos); pos += toUserName.size; buf[pos++] = 0
        fromUserName.copyInto(buf, pos); pos += fromUserName.size; buf[pos++] = 0
        subject.copyInto(buf, pos); pos += subject.size; buf[pos++] = 0
        text.copyInto(buf, pos); pos += text.size; buf[pos++] = 0

        return buf
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PackedMsg) return false
        return origNode == other.origNode &&
            destNode == other.destNode &&
            origNet == other.origNet &&
            destNet == other.destNet &&
            attribute == other.attribute &&
            cost == other.cost &&
            dateTime.contentEquals(other.dateTime) &&
            toUserName.contentEquals(other.toUserName) &&
            fromUserName.contentEquals(other.fromUserName) &&
            subject.contentEquals(other.subject) &&
            text.contentEquals(other.text)
    }

    override fun hashCode(): Int {
        var result = origNode.hashCode()
        result = 31 * result + destNode.hashCode()
        result = 31 * result + origNet.hashCode()
        result = 31 * result + destNet.hashCode()
        result = 31 * result + attribute.hashCode()
        result = 31 * result + cost.hashCode()
        result = 31 * result + dateTime.contentHashCode()
        result = 31 * result + toUserName.contentHashCode()
        result = 31 * result + fromUserName.contentHashCode()
        result = 31 * result + subject.contentHashCode()
        result = 31 * result + text.contentHashCode()
        return result
    }

    override fun toString(): String =
        "PackedMsg(from=$origNet/$origNode to=$destNet/$destNode)"

    public companion object {
        internal const val MSG_TYPE: UShort = 0x0002u
        internal const val HEADER_SIZE: Int = 14 // 7 x UShort
        internal const val DATE_TIME_SIZE: Int = 20

        public fun fromByteArray(data: ByteArray, offset: Int = 0): PackedMsg {
            var pos = offset
            val type = readLeUShort(data, pos); pos += 2
            require(type == MSG_TYPE) {
                "Invalid packed message type: 0x${type.toString(16)}, expected 0x0002"
            }

            val origNode = readLeUShort(data, pos); pos += 2
            val destNode = readLeUShort(data, pos); pos += 2
            val origNet = readLeUShort(data, pos); pos += 2
            val destNet = readLeUShort(data, pos); pos += 2
            val attribute = readLeUShort(data, pos); pos += 2
            val cost = readLeUShort(data, pos); pos += 2

            val dateTime = readDateTimeField(data, pos)
            pos += DATE_TIME_SIZE

            val (toUserName, toEnd) = readNullTerminated(data, pos)
            pos = toEnd
            val (fromUserName, fromEnd) = readNullTerminated(data, pos)
            pos = fromEnd
            val (subject, subjEnd) = readNullTerminated(data, pos)
            pos = subjEnd
            val (text, _) = readNullTerminated(data, pos)

            return PackedMsg(
                origNode = origNode,
                destNode = destNode,
                origNet = origNet,
                destNet = destNet,
                attribute = attribute,
                cost = cost,
                dateTime = dateTime,
                toUserName = toUserName,
                fromUserName = fromUserName,
                subject = subject,
                text = text,
            )
        }

        private fun readDateTimeField(data: ByteArray, offset: Int): ByteArray {
            var len = 0
            while (len < DATE_TIME_SIZE && data[offset + len] != 0.toByte()) len++
            return data.copyOfRange(offset, offset + len)
        }

        private fun encodeDateTimeField(dateTime: ByteArray): ByteArray {
            val buf = ByteArray(DATE_TIME_SIZE)
            val len = minOf(dateTime.size, DATE_TIME_SIZE - 1)
            dateTime.copyInto(buf, 0, 0, len)
            return buf
        }
    }
}