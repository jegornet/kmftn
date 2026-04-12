package net.jegor.kmftn.base

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FtnAddrTest {

    // 198:51/100@testnet2
    private val addrA = FtnAddr(zone = 198, net = 51, node = 100, point = 0, domain = "testnet2")

    // 192:51/100.1@testnet2
    private val addrB = FtnAddr(zone = 192, net = 51, node = 100, point = 1, domain = "testnet2")

    // 192:0/2@testnet1
    private val addrC = FtnAddr(zone = 192, net = 0, node = 2, point = 0, domain = "testnet1")

    @Test
    fun toStringWithoutPoint() {
        assertEquals("198:51/100", addrA.toString())
        assertEquals("192:0/2", addrC.toString())
    }

    @Test
    fun toStringWithPoint() {
        assertEquals("192:51/100.1", addrB.toString())
    }

    @Test
    fun toString2DReturnsNetSlashNode() {
        assertEquals("51/100", addrA.toString2D())
        assertEquals("51/100", addrB.toString2D())
        assertEquals("0/2", addrC.toString2D())
    }

    @Test
    fun toString5DAppendsDomain() {
        assertEquals("198:51/100@testnet2", addrA.toString5D())
        assertEquals("192:51/100.1@testnet2", addrB.toString5D())
        assertEquals("192:0/2@testnet1", addrC.toString5D())
    }

    @Test
    fun toString5DWithoutDomainEqualsToString() {
        val noDomain = FtnAddr(zone = 198, net = 51, node = 100, point = 0)
        assertEquals("198:51/100", noDomain.toString5D())
    }

    @Test
    fun toString5DTreatsEmptyDomainAsAbsent() {
        val emptyDomain = FtnAddr(zone = 198, net = 51, node = 100, point = 0, domain = "")
        assertEquals("198:51/100", emptyDomain.toString5D())
    }

    @Test
    fun equalsIgnoresDomain() {
        val sameNumbersDifferentDomain =
            FtnAddr(zone = 198, net = 51, node = 100, point = 0, domain = "testnet1")
        assertEquals(addrA, sameNumbersDifferentDomain)
        assertEquals(addrA.hashCode(), sameNumbersDifferentDomain.hashCode())
    }

    @Test
    fun equalsComparesZoneNetNodePoint() {
        // addrA and addrB share net/node but differ in zone and point
        assertNotEquals(addrA, addrB)
        // addrB and addrC share zone but differ in net/node/point
        assertNotEquals(addrB, addrC)

        // Vary each field individually starting from addrA
        assertNotEquals(addrA, FtnAddr(192, 51, 100, 0, "testnet2")) // zone
        assertNotEquals(addrA, FtnAddr(198, 52, 100, 0, "testnet2")) // net
        assertNotEquals(addrA, FtnAddr(198, 51, 101, 0, "testnet2")) // node
        assertNotEquals(addrA, FtnAddr(198, 51, 100, 1, "testnet2")) // point
    }

    @Test
    fun equalsRejectsOtherTypes() {
        assertFalse(addrA.equals("198:51/100"))
        assertFalse(addrA.equals(null))
    }

    @Test
    fun equals2DComparesOnlyNetAndNode() {
        // addrA and addrB have the same net/node despite different zone/point/(same)domain
        assertTrue(addrA.equals2D(addrB))
        // addrC has different net/node
        assertFalse(addrA.equals2D(addrC))
        assertFalse(addrB.equals2D(addrC))
    }

    @Test
    fun equals5DComparesAllFieldsIncludingDomain() {
        val addrBCopy = FtnAddr(zone = 192, net = 51, node = 100, point = 1, domain = "testnet2")
        assertTrue(addrB.equals5D(addrBCopy))

        // Different domain
        assertFalse(
            addrB.equals5D(FtnAddr(zone = 192, net = 51, node = 100, point = 1, domain = "testnet1"))
        )
        // Missing domain
        assertFalse(addrB.equals5D(FtnAddr(zone = 192, net = 51, node = 100, point = 1)))
        // Different point
        assertFalse(
            addrB.equals5D(FtnAddr(zone = 192, net = 51, node = 100, point = 2, domain = "testnet2"))
        )
    }

    @Test
    fun equals5DRejectsOtherTypes() {
        assertFalse(addrA.equals5D("198:51/100@testnet2"))
        assertFalse(addrA.equals5D(null))
    }

    @Test
    fun getBossNodeZeroesPointAndKeepsDomain() {
        val boss = addrB.getBossNode()

        assertEquals(192.toShort(), boss.zone)
        assertEquals(51.toShort(), boss.net)
        assertEquals(100.toShort(), boss.node)
        assertEquals(0.toShort(), boss.point)
        assertEquals("testnet2", boss.domain)
        assertEquals("192:51/100", boss.toString())
        assertEquals("192:51/100@testnet2", boss.toString5D())
    }

    @Test
    fun getBossNodeOnAlreadyBossReturnsEqualAddr() {
        assertEquals(addrA, addrA.getBossNode())
        assertEquals(addrC, addrC.getBossNode())
    }

    @Test
    fun fromStringParses3D() {
        val parsed = FtnAddr.fromString("198:51/100")
        assertEquals(198.toShort(), parsed.zone)
        assertEquals(51.toShort(), parsed.net)
        assertEquals(100.toShort(), parsed.node)
        assertEquals(0.toShort(), parsed.point)
        assertNull(parsed.domain)
    }

    @Test
    fun fromStringParses4D() {
        val parsed = FtnAddr.fromString("198:51/100.200")
        assertEquals(198.toShort(), parsed.zone)
        assertEquals(51.toShort(), parsed.net)
        assertEquals(100.toShort(), parsed.node)
        assertEquals(200.toShort(), parsed.point)
        assertNull(parsed.domain)
    }

    @Test
    fun fromStringParses3DWithDomain() {
        val parsed = FtnAddr.fromString("198:51/100@testnet")
        assertEquals(198.toShort(), parsed.zone)
        assertEquals(51.toShort(), parsed.net)
        assertEquals(100.toShort(), parsed.node)
        assertEquals(0.toShort(), parsed.point)
        assertEquals("testnet", parsed.domain)
    }

    @Test
    fun fromStringParsesFull5D() {
        val parsed = FtnAddr.fromString("198:51/100.300@testnet")
        assertEquals(198.toShort(), parsed.zone)
        assertEquals(51.toShort(), parsed.net)
        assertEquals(100.toShort(), parsed.node)
        assertEquals(300.toShort(), parsed.point)
        assertEquals("testnet", parsed.domain)
    }

    @Test
    fun fromStringRoundTripsExampleAddresses() {
        assertEquals(addrA, FtnAddr.fromString("198:51/100@testnet2"))
        assertEquals(addrB, FtnAddr.fromString("192:51/100.1@testnet2"))
        assertEquals(addrC, FtnAddr.fromString("192:0/2@testnet1"))

        // equals5D check, since equals ignores domain
        assertTrue(addrA.equals5D(FtnAddr.fromString("198:51/100@testnet2")))
        assertTrue(addrB.equals5D(FtnAddr.fromString("192:51/100.1@testnet2")))
        assertTrue(addrC.equals5D(FtnAddr.fromString("192:0/2@testnet1")))
    }

    @Test
    fun fromStringRoundTripsToString5D() {
        listOf(
            "198:51/100",
            "198:51/100.200",
            "198:51/100@testnet",
            "198:51/100.300@testnet",
            "192:0/2@testnet1",
        ).forEach { raw ->
            assertEquals(raw, FtnAddr.fromString(raw).toString5D())
        }
    }

    @Test
    fun fromStringTrimsWhitespace() {
        assertEquals(
            FtnAddr.fromString("198:51/100.200@testnet"),
            FtnAddr.fromString("  198:51/100.200@testnet  "),
        )
    }

    @Test
    fun fromStringRejectsBlank() {
        assertFailsWith<IllegalArgumentException> { FtnAddr.fromString("") }
        assertFailsWith<IllegalArgumentException> { FtnAddr.fromString("   ") }
    }

    @Test
    fun fromStringRejectsMalformedStructure() {
        // Missing colon
        assertFailsWith<IllegalArgumentException> { FtnAddr.fromString("51/100") }
        // Missing slash
        assertFailsWith<IllegalArgumentException> { FtnAddr.fromString("198:51100") }
        // Wrong order: slash before colon
        assertFailsWith<IllegalArgumentException> { FtnAddr.fromString("198/51:100") }
        // Empty zone (colon at position 0)
        assertFailsWith<IllegalArgumentException> { FtnAddr.fromString(":51/100") }
    }

    @Test
    fun fromStringRejectsEmptyDomain() {
        assertFailsWith<IllegalArgumentException> { FtnAddr.fromString("198:51/100@") }
        assertFailsWith<IllegalArgumentException> { FtnAddr.fromString("198:51/100.1@") }
    }

    @Test
    fun fromStringRejectsNonNumericComponents() {
        assertFailsWith<IllegalArgumentException> { FtnAddr.fromString("abc:51/100") }
        assertFailsWith<IllegalArgumentException> { FtnAddr.fromString("198:xx/100") }
        assertFailsWith<IllegalArgumentException> { FtnAddr.fromString("198:51/yy") }
        assertFailsWith<IllegalArgumentException> { FtnAddr.fromString("198:51/100.zz") }
    }

    @Test
    fun fromStringRejectsOutOfShortRange() {
        // 70000 doesn't fit in Short
        assertFailsWith<IllegalArgumentException> { FtnAddr.fromString("70000:51/100") }
    }
}
