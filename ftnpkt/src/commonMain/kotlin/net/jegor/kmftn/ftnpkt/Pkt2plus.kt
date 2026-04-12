package net.jegor.kmftn.ftnpkt

/**
 * FSC-0048 Type-2+ Packet Header (58 bytes).
 *
 * Same layout as Type-2e (FSC-0039) with the following difference:
 * - When origPoint != 0, origNet is set to 0xFFFF (-1)
 *   and the real origNet is stored in auxNet (offset 0x26).
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
 *   0x14  origNet        2   (0xFFFF when origPoint != 0)
 *   0x16  destNet        2
 *   0x18  prodCodeLo     1
 *   0x19  prodRevMajor   1
 *   0x1A  password       8   null-padded
 *   0x22  qOrigZone      2   (QMail-style zone)
 *   0x24  qDestZone      2   (QMail-style zone)
 *   0x26  auxNet         2   (real origNet when origPoint != 0)
 *   0x28  capValid       2   (CW byte-swapped validation copy)
 *   0x2A  prodCodeHi     1
 *   0x2B  prodRevMinor   1
 *   0x2C  capWord        2   (Capability Word)
 *   0x2E  origZone       2
 *   0x30  destZone       2
 *   0x32  origPoint      2
 *   0x34  destPoint      2
 *   0x36  prodData       4
 *   0x3A  messages...
 *         0x0000 terminator
 */
public class Pkt2plus(
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
    public val qOrigZone: UShort,
    public val qDestZone: UShort,
    public val auxNet: UShort,
    public val capValid: UShort,
    public val prodCodeHi: UByte,
    public val prodRevMinor: UByte,
    public val capWord: UShort,
    override val origZone: UShort,
    override val destZone: UShort,
    public val origPoint: UShort,
    public val destPoint: UShort,
    public val prodData: ByteArray,
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
        // When origPoint != 0, write 0xFFFF as origNet
        val wireOrigNet = if (origPoint != 0.toUShort()) 0xFFFFu.toUShort() else origNet
        pos = writeLeUShort(buf, pos, wireOrigNet)
        pos = writeLeUShort(buf, pos, destNet)
        buf[pos++] = prodCodeLo.toByte()
        buf[pos++] = prodRevMajor.toByte()
        pos = writeFixedBytes(buf, pos, password, PASSWORD_SIZE)
        pos = writeLeUShort(buf, pos, qOrigZone)
        pos = writeLeUShort(buf, pos, qDestZone)
        // When origPoint != 0, auxNet holds real origNet
        val wireAuxNet = if (origPoint != 0.toUShort()) origNet else auxNet
        pos = writeLeUShort(buf, pos, wireAuxNet)
        pos = writeLeUShort(buf, pos, capValid)
        buf[pos++] = prodCodeHi.toByte()
        buf[pos++] = prodRevMinor.toByte()
        pos = writeLeUShort(buf, pos, capWord)
        pos = writeLeUShort(buf, pos, origZone)
        pos = writeLeUShort(buf, pos, destZone)
        pos = writeLeUShort(buf, pos, origPoint)
        pos = writeLeUShort(buf, pos, destPoint)
        pos = writeFixedBytes(buf, pos, prodData, PROD_DATA_SIZE)

        msgBytes.copyInto(buf, pos)
        pos += msgBytes.size
        buf[pos] = 0
        buf[pos + 1] = 0

        return buf
    }

    override fun toString(): String =
        "Pkt2plus(from=$origZone:$origNet/$origNode.$origPoint to=$destZone:$destNet/$destNode.$destPoint)"

    public companion object {
        internal const val HEADER_SIZE: Int = 58
        internal const val PASSWORD_SIZE: Int = 8
        internal const val PROD_DATA_SIZE: Int = 4
        internal val PKT_VER: UShort = 2u

        public fun fromByteArray(data: ByteArray, offset: Int = 0): Pkt2plus {
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
            val rawOrigNet = readLeUShort(data, pos); pos += 2
            val destNet = readLeUShort(data, pos); pos += 2
            val prodCodeLo = data[pos].toUByte(); pos++
            val prodRevMajor = data[pos].toUByte(); pos++
            val password = readFixedBytes(data, pos, PASSWORD_SIZE); pos += PASSWORD_SIZE
            val qOrigZone = readLeUShort(data, pos); pos += 2
            val qDestZone = readLeUShort(data, pos); pos += 2
            val auxNet = readLeUShort(data, pos); pos += 2
            val capValid = readLeUShort(data, pos); pos += 2
            val prodCodeHi = data[pos].toUByte(); pos++
            val prodRevMinor = data[pos].toUByte(); pos++
            val capWord = readLeUShort(data, pos); pos += 2
            val origZone = readLeUShort(data, pos); pos += 2
            val destZone = readLeUShort(data, pos); pos += 2
            val origPoint = readLeUShort(data, pos); pos += 2
            val destPoint = readLeUShort(data, pos); pos += 2
            val prodData = readFixedBytes(data, pos, PROD_DATA_SIZE); pos += PROD_DATA_SIZE

            // Resolve origNet: if origNet == 0xFFFF and origPoint != 0, real origNet is in auxNet
            val origNet = if (rawOrigNet == 0xFFFFu.toUShort() && origPoint != 0.toUShort()) {
                auxNet
            } else {
                rawOrigNet
            }

            val messages = parseMessages(data, pos)

            return Pkt2plus(
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
                qOrigZone = qOrigZone,
                qDestZone = qDestZone,
                auxNet = auxNet,
                capValid = capValid,
                prodCodeHi = prodCodeHi,
                prodRevMinor = prodRevMinor,
                capWord = capWord,
                origZone = origZone,
                destZone = destZone,
                origPoint = origPoint,
                destPoint = destPoint,
                prodData = prodData,
                messages = messages,
            )
        }
    }
}
