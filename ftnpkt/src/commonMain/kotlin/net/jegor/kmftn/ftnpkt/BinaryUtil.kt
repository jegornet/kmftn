package net.jegor.kmftn.ftnpkt

internal fun readLeUShort(data: ByteArray, offset: Int): UShort =
    ((data[offset].toInt() and 0xFF) or
        ((data[offset + 1].toInt() and 0xFF) shl 8)).toUShort()

internal fun writeLeUShort(buf: ByteArray, offset: Int, value: UShort): Int {
    val v = value.toInt()
    buf[offset] = (v and 0xFF).toByte()
    buf[offset + 1] = ((v shr 8) and 0xFF).toByte()
    return offset + 2
}

internal fun readNullTerminated(data: ByteArray, offset: Int): Pair<ByteArray, Int> {
    var end = offset
    while (end < data.size && data[end] != 0.toByte()) end++
    val bytes = data.copyOfRange(offset, end)
    return bytes to (end + 1)
}

internal fun readFixedBytes(data: ByteArray, offset: Int, size: Int): ByteArray =
    data.copyOfRange(offset, offset + size)

internal fun writeFixedBytes(buf: ByteArray, offset: Int, src: ByteArray, size: Int): Int {
    val len = minOf(src.size, size)
    src.copyInto(buf, offset, 0, len)
    return offset + size
}

internal fun parseMessages(data: ByteArray, offset: Int): List<PackedMsg> {
    val messages = mutableListOf<PackedMsg>()
    var pos = offset
    while (pos + 1 < data.size) {
        val type = readLeUShort(data, pos)
        if (type == 0.toUShort()) break
        val msg = PackedMsg.fromByteArray(data, pos)
        messages.add(msg)
        pos += msg.toByteArray().size
    }
    return messages
}

internal fun serializeMessages(messages: List<PackedMsg>): ByteArray {
    val parts = messages.map { it.toByteArray() }
    val totalSize = parts.sumOf { it.size }
    val buf = ByteArray(totalSize)
    var pos = 0
    for (part in parts) {
        part.copyInto(buf, pos)
        pos += part.size
    }
    return buf
}