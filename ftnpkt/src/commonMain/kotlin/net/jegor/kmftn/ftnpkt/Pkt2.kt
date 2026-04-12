package net.jegor.kmftn.ftnpkt

/**
 * FTS-0001 Type-2 "Stone Age" Packet Header (58 bytes).
 *
 *   0x00  origNode       2
 *   0x02  destNode       2
 *   0x04  year           2
 *   0x06  month          2
 *   0x08  day            2
 *   0x0A  hour           2
 *   0x0C  minute         2
 *   0x0E  second         2
 *   0x10  baud           2
 *   0x12  pktVer         2   (always 2)
 *   0x14  origNet        2
 *   0x16  destNet        2
 *   0x18  prodCode       1
 *   0x19  serialNo       1
 *   0x1A  password       8   null-padded
 *   0x22  origZone       2   (optional)
 *   0x24  destZone       2   (optional)
 *   0x26  fill          20
 *   0x3A  messages...
 *         0x0000 terminator
 */
public class Pkt2(
    override val origNode: UShort,
    override val destNode: UShort,
    override val year: UShort,
    override val month: UShort,
    override val day: UShort,
    override val hour: UShort,
    override val minute: UShort,
    override val second: UShort,
    override val baud: UShort,
    override val origNet: UShort,
    override val destNet: UShort,
    override val prodCodeLo: UByte,
    override val prodRevMajor: UByte,
    override val password: ByteArray,
    override val origZone: UShort,
    override val destZone: UShort,
    public val fill: ByteArray,
    override val messages: List<PackedMsg>,
) : Pkt {

    override fun toByteArray(): ByteArray {
        val msgBytes = serializeMessages(messages)
        val buf = ByteArray(HEADER_SIZE + msgBytes.size + 2)
        var pos = 0

        pos = writeLeUShort(buf, pos, origNode)
        pos = writeLeUShort(buf, pos, destNode)
        pos = writeLeUShort(buf, pos, year)
        pos = writeLeUShort(buf, pos, month)
        pos = writeLeUShort(buf, pos, day)
        pos = writeLeUShort(buf, pos, hour)
        pos = writeLeUShort(buf, pos, minute)
        pos = writeLeUShort(buf, pos, second)
        pos = writeLeUShort(buf, pos, baud)
        pos = writeLeUShort(buf, pos, PKT_VER)
        pos = writeLeUShort(buf, pos, origNet)
        pos = writeLeUShort(buf, pos, destNet)
        buf[pos++] = prodCodeLo.toByte()
        buf[pos++] = prodRevMajor.toByte()
        pos = writeFixedBytes(buf, pos, password, PASSWORD_SIZE)
        pos = writeLeUShort(buf, pos, origZone)
        pos = writeLeUShort(buf, pos, destZone)
        pos = writeFixedBytes(buf, pos, fill, FILL_SIZE)

        msgBytes.copyInto(buf, pos)
        pos += msgBytes.size
        // terminator 0x0000
        buf[pos] = 0
        buf[pos + 1] = 0

        return buf
    }

    override fun toString(): String =
        "Pkt2(from=$origZone:$origNet/$origNode to=$destZone:$destNet/$destNode)"

    public companion object {
        internal const val HEADER_SIZE: Int = 58
        internal const val PASSWORD_SIZE: Int = 8
        internal const val FILL_SIZE: Int = 20
        internal val PKT_VER: UShort = 2u

        public fun fromByteArray(data: ByteArray, offset: Int = 0): Pkt2 {
            var pos = offset

            val origNode = readLeUShort(data, pos); pos += 2
            val destNode = readLeUShort(data, pos); pos += 2
            val year = readLeUShort(data, pos); pos += 2
            val month = readLeUShort(data, pos); pos += 2
            val day = readLeUShort(data, pos); pos += 2
            val hour = readLeUShort(data, pos); pos += 2
            val minute = readLeUShort(data, pos); pos += 2
            val second = readLeUShort(data, pos); pos += 2
            val baud = readLeUShort(data, pos); pos += 2
            val pktVer = readLeUShort(data, pos); pos += 2
            require(pktVer == PKT_VER) {
                "Invalid packet version: $pktVer, expected 2"
            }
            val origNet = readLeUShort(data, pos); pos += 2
            val destNet = readLeUShort(data, pos); pos += 2
            val prodCodeLo = data[pos].toUByte(); pos++
            val prodRevMajor = data[pos].toUByte(); pos++
            val password = readFixedBytes(data, pos, PASSWORD_SIZE); pos += PASSWORD_SIZE
            val origZone = readLeUShort(data, pos); pos += 2
            val destZone = readLeUShort(data, pos); pos += 2
            val fill = readFixedBytes(data, pos, FILL_SIZE); pos += FILL_SIZE

            val messages = parseMessages(data, pos)

            return Pkt2(
                origNode = origNode,
                destNode = destNode,
                year = year,
                month = month,
                day = day,
                hour = hour,
                minute = minute,
                second = second,
                baud = baud,
                origNet = origNet,
                destNet = destNet,
                prodCodeLo = prodCodeLo,
                prodRevMajor = prodRevMajor,
                password = password,
                origZone = origZone,
                destZone = destZone,
                fill = fill,
                messages = messages,
            )
        }
    }
}
