package net.jegor.kmftn.ftnpkt

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PktRoundtripTest {

    private fun makeMsg(index: Int) = PackedMsg(
        origNode = (100 + index).toUShort(),
        destNode = (200 + index).toUShort(),
        origNet = (300 + index).toUShort(),
        destNet = (400 + index).toUShort(),
        attribute = 0x0001u,
        cost = 0u,
        dateTime = "01 Jan 86  02:34:56".encodeToByteArray(),
        toUserName = "User $index".encodeToByteArray(),
        fromUserName = "Sender $index".encodeToByteArray(),
        subject = "Subject $index".encodeToByteArray(),
        text = "Hello from message $index".encodeToByteArray(),
    )

    private fun messagesForCount(count: Int): List<PackedMsg> =
        (0 until count).map { makeMsg(it) }

    private fun swapBytes(value: UShort): UShort {
        val v = value.toInt()
        return (((v and 0xFF) shl 8) or ((v shr 8) and 0xFF)).toUShort()
    }

    private fun padPassword(pw: String): ByteArray {
        val buf = ByteArray(8)
        pw.encodeToByteArray().copyInto(buf)
        return buf
    }

    private fun assertPktFieldsMatch(expected: Pkt, actual: Pkt) {
        assertEquals(expected.origNode, actual.origNode, "origNode")
        assertEquals(expected.destNode, actual.destNode, "destNode")
        assertEquals(expected.year, actual.year, "year")
        assertEquals(expected.month, actual.month, "month")
        assertEquals(expected.day, actual.day, "day")
        assertEquals(expected.hour, actual.hour, "hour")
        assertEquals(expected.minute, actual.minute, "minute")
        assertEquals(expected.second, actual.second, "second")
        assertEquals(expected.baud, actual.baud, "baud")
        assertEquals(expected.origNet, actual.origNet, "origNet")
        assertEquals(expected.destNet, actual.destNet, "destNet")
        assertEquals(expected.prodCodeLo, actual.prodCodeLo, "prodCodeLo")
        assertEquals(expected.prodRevMajor, actual.prodRevMajor, "prodRevMajor")
        assertContentEquals(expected.password, actual.password, "password")
        assertEquals(expected.origZone, actual.origZone, "origZone")
        assertEquals(expected.destZone, actual.destZone, "destZone")
        assertEquals(expected.messages.size, actual.messages.size, "messages.size")
        for (i in expected.messages.indices) {
            assertEquals(expected.messages[i], actual.messages[i], "messages[$i]")
        }
    }

    // --- Pkt2 ---

    private fun makePkt2(msgCount: Int) = Pkt2(
        origNode = 1u, destNode = 2u,
        year = 2026u, month = 4u, day = 13u,
        hour = 12u, minute = 30u, second = 0u,
        baud = 9600u,
        origNet = 382u, destNet = 381u,
        prodCodeLo = 0xFEu, prodRevMajor = 1u,
        password = padPassword("secret"),
        origZone = 2u, destZone = 2u,
        fill = ByteArray(20),
        messages = messagesForCount(msgCount),
    )

    @Test fun pkt2_0messages() {
        val pkt = makePkt2(0)
        val read = PktReader.fromByteArray(pkt.toByteArray())
        assertIs<Pkt2>(read)
        assertPktFieldsMatch(pkt, read)
    }

    @Test fun pkt2_1message() {
        val pkt = makePkt2(1)
        val read = PktReader.fromByteArray(pkt.toByteArray())
        assertIs<Pkt2>(read)
        assertPktFieldsMatch(pkt, read)
    }

    @Test fun pkt2_2messages() {
        val pkt = makePkt2(2)
        val read = PktReader.fromByteArray(pkt.toByteArray())
        assertIs<Pkt2>(read)
        assertPktFieldsMatch(pkt, read)
    }

    // --- Pkt2e ---

    private val capWord: UShort = 0x0001u

    private fun makePkt2e(msgCount: Int) = Pkt2e(
        origNode = 10u, destNode = 20u,
        year = 2026u, month = 4u, day = 13u,
        hour = 14u, minute = 0u, second = 30u,
        baud = 9600u,
        origNet = 382u, destNet = 381u,
        prodCodeLo = 0xFEu, prodRevMajor = 1u,
        password = padPassword("pass"),
        qOrigZone = 2u, qDestZone = 2u,
        filler = 0u,
        capValid = swapBytes(capWord),
        prodCodeHi = 0u, prodRevMinor = 2u,
        capWord = capWord,
        origZone = 2u, destZone = 2u,
        origPoint = 0u, destPoint = 0u,
        prodData = ByteArray(4),
        messages = messagesForCount(msgCount),
    )

    @Test fun pkt2e_0messages() {
        val pkt = makePkt2e(0)
        val read = PktReader.fromByteArray(pkt.toByteArray())
        assertIs<Pkt2e>(read)
        assertPktFieldsMatch(pkt, read)
    }

    @Test fun pkt2e_1message() {
        val pkt = makePkt2e(1)
        val read = PktReader.fromByteArray(pkt.toByteArray())
        assertIs<Pkt2e>(read)
        assertPktFieldsMatch(pkt, read)
    }

    @Test fun pkt2e_2messages() {
        val pkt = makePkt2e(2)
        val read = PktReader.fromByteArray(pkt.toByteArray())
        assertIs<Pkt2e>(read)
        assertPktFieldsMatch(pkt, read)
    }

    // --- Pkt2plus ---

    private fun makePkt2plus(msgCount: Int) = Pkt2plus(
        origNode = 100u, destNode = 200u,
        year = 2026u, month = 4u, day = 13u,
        hour = 16u, minute = 45u, second = 10u,
        baud = 9600u,
        origNet = 382u, destNet = 381u,
        prodCodeLo = 0xFEu, prodRevMajor = 1u,
        password = padPassword("pw2plus"),
        qOrigZone = 2u, qDestZone = 2u,
        auxNet = 0u,
        capValid = swapBytes(capWord),
        prodCodeHi = 0u, prodRevMinor = 3u,
        capWord = capWord,
        origZone = 2u, destZone = 2u,
        origPoint = 5u, destPoint = 0u,
        prodData = ByteArray(4),
        messages = messagesForCount(msgCount),
    )

    @Test fun pkt2plus_0messages() {
        val pkt = makePkt2plus(0)
        val read = PktReader.fromByteArray(pkt.toByteArray())
        assertIs<Pkt2plus>(read)
        assertPktFieldsMatch(pkt, read)
    }

    @Test fun pkt2plus_1message() {
        val pkt = makePkt2plus(1)
        val read = PktReader.fromByteArray(pkt.toByteArray())
        assertIs<Pkt2plus>(read)
        assertPktFieldsMatch(pkt, read)
    }

    @Test fun pkt2plus_2messages() {
        val pkt = makePkt2plus(2)
        val read = PktReader.fromByteArray(pkt.toByteArray())
        assertIs<Pkt2plus>(read)
        assertPktFieldsMatch(pkt, read)
    }
}