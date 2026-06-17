package net.jegor.kmftn.base

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FtnWildcardTest {

    // 21:2/3.4
    private val addr21_2_3_4 = FtnAddr(zone = 21, net = 2, node = 3, point = 4)

    // 21:5/6
    private val addr21_5_6 = FtnAddr(zone = 21, net = 5, node = 6, point = 0)

    // 2:382/1
    private val addr2_382_1 = FtnAddr(zone = 2, net = 382, node = 1, point = 0)

    // 2:382/2.3
    private val addr2_382_2_3 = FtnAddr(zone = 2, net = 382, node = 2, point = 3)

    // 2:5020/736
    private val addr2_5020_736 = FtnAddr(zone = 2, net = 5020, node = 736, point = 0)

    // 2:5020/736.1
    private val addr2_5020_736_1 = FtnAddr(zone = 2, net = 5020, node = 736, point = 1)

    // 2:5020/737
    private val addr2_5020_737 = FtnAddr(zone = 2, net = 5020, node = 737, point = 0)

    @Test
    fun fullWildcardMatchesEverything() {
        val wildcard = FtnWildcard.fromString("*")
        assertTrue(wildcard.matches(addr21_2_3_4))
        assertTrue(wildcard.matches(addr2_5020_736_1))
        assertTrue(wildcard.matches(FtnAddr(zone = 0, net = 0, node = 0, point = 0)))
    }

    @Test
    fun zoneWildcardMatchesAnyAddressInZone() {
        val wildcard = FtnWildcard.fromString("21:*")
        assertTrue(wildcard.matches(addr21_2_3_4))
        assertTrue(wildcard.matches(addr21_5_6))
        assertFalse(wildcard.matches(addr2_382_1))
    }

    @Test
    fun netWildcardMatchesAnyNodeAndPointInNet() {
        val wildcard = FtnWildcard.fromString("2:382/*")
        assertTrue(wildcard.matches(addr2_382_1))
        assertTrue(wildcard.matches(addr2_382_2_3))
        assertFalse(wildcard.matches(FtnAddr(zone = 2, net = 383, node = 1, point = 0)))
        assertFalse(wildcard.matches(addr21_5_6))
    }

    @Test
    fun nodeWildcardMatchesBossNodeAndItsPoints() {
        val wildcard = FtnWildcard.fromString("2:5020/736.*")
        assertTrue(wildcard.matches(addr2_5020_736))
        assertTrue(wildcard.matches(addr2_5020_736_1))
        assertFalse(wildcard.matches(addr2_5020_737))
    }

    @Test
    fun exactAddressMatchesOnlyThatAddress() {
        val wildcard = FtnWildcard.fromString("2:5020/736")
        assertTrue(wildcard.matches(addr2_5020_736))
        assertFalse(wildcard.matches(addr2_5020_736_1))
        assertFalse(wildcard.matches(addr2_5020_737))
    }

    @Test
    fun exactAddressWithPointMatchesOnlyThatPoint() {
        val wildcard = FtnWildcard.fromString("2:5020/736.1")
        assertTrue(wildcard.matches(addr2_5020_736_1))
        assertFalse(wildcard.matches(addr2_5020_736))
    }

    @Test
    fun matchingIgnoresDomain() {
        val wildcard = FtnWildcard.fromString("21:*")
        assertTrue(wildcard.matches(FtnAddr(zone = 21, net = 9, node = 9, point = 9, domain = "fidonet")))
        assertTrue(wildcard.matches(FtnAddr(zone = 21, net = 9, node = 9, point = 9, domain = null)))
    }

    @Test
    fun fromStringParsesFullWildcard() {
        val wildcard = FtnWildcard.fromString("*")
        assertNull(wildcard.zone)
        assertNull(wildcard.net)
        assertNull(wildcard.node)
        assertNull(wildcard.point)
    }

    @Test
    fun fromStringParsesZoneWildcard() {
        val wildcard = FtnWildcard.fromString("21:*")
        assertEquals(21.toShort(), wildcard.zone)
        assertNull(wildcard.net)
        assertNull(wildcard.node)
        assertNull(wildcard.point)
    }

    @Test
    fun fromStringParsesNetWildcard() {
        val wildcard = FtnWildcard.fromString("2:382/*")
        assertEquals(2.toShort(), wildcard.zone)
        assertEquals(382.toShort(), wildcard.net)
        assertNull(wildcard.node)
        assertNull(wildcard.point)
    }

    @Test
    fun fromStringParsesNodeWildcard() {
        val wildcard = FtnWildcard.fromString("2:5020/736.*")
        assertEquals(2.toShort(), wildcard.zone)
        assertEquals(5020.toShort(), wildcard.net)
        assertEquals(736.toShort(), wildcard.node)
        assertNull(wildcard.point)
    }

    @Test
    fun fromStringParsesExactAddressWithoutPoint() {
        val wildcard = FtnWildcard.fromString("2:5020/736")
        assertEquals(2.toShort(), wildcard.zone)
        assertEquals(5020.toShort(), wildcard.net)
        assertEquals(736.toShort(), wildcard.node)
        assertEquals(0.toShort(), wildcard.point)
    }

    @Test
    fun fromStringParsesExactAddressWithPoint() {
        val wildcard = FtnWildcard.fromString("2:5020/736.1")
        assertEquals(2.toShort(), wildcard.zone)
        assertEquals(5020.toShort(), wildcard.net)
        assertEquals(736.toShort(), wildcard.node)
        assertEquals(1.toShort(), wildcard.point)
    }

    @Test
    fun fromStringTrimsWhitespace() {
        assertEquals(
            FtnWildcard.fromString("21:*"),
            FtnWildcard.fromString("  21:*  "),
        )
    }

    @Test
    fun fromStringRoundTripsToString() {
        listOf(
            "*",
            "21:*",
            "2:382/*",
            "2:5020/736.*",
            "2:5020/736",
            "2:5020/736.1",
        ).forEach { raw ->
            assertEquals(raw, FtnWildcard.fromString(raw).toString())
        }
    }

    @Test
    fun fromStringRejectsBlank() {
        assertFailsWith<IllegalArgumentException> { FtnWildcard.fromString("") }
        assertFailsWith<IllegalArgumentException> { FtnWildcard.fromString("   ") }
    }

    @Test
    fun fromStringRejectsDomain() {
        assertFailsWith<IllegalArgumentException> { FtnWildcard.fromString("21:*@fidonet") }
    }

    @Test
    fun fromStringRejectsMissingColon() {
        assertFailsWith<IllegalArgumentException> { FtnWildcard.fromString("21") }
    }

    @Test
    fun fromStringRejectsMissingSlash() {
        assertFailsWith<IllegalArgumentException> { FtnWildcard.fromString("2:382") }
    }

    @Test
    fun fromStringRejectsNonNumericComponents() {
        assertFailsWith<IllegalArgumentException> { FtnWildcard.fromString("abc:*") }
        assertFailsWith<IllegalArgumentException> { FtnWildcard.fromString("2:xx/*") }
        assertFailsWith<IllegalArgumentException> { FtnWildcard.fromString("2:382/yy.*") }
        assertFailsWith<IllegalArgumentException> { FtnWildcard.fromString("2:382/736.zz") }
    }

    @Test
    fun fromStringRejectsOutOfShortRange() {
        // 70000 doesn't fit in Short
        assertFailsWith<IllegalArgumentException> { FtnWildcard.fromString("70000:*") }
    }

    @Test
    fun equalsComparesAllComponents() {
        val wildcard = FtnWildcard.fromString("2:382/*")
        assertEquals(wildcard, FtnWildcard.fromString("2:382/*"))
        assertEquals(wildcard.hashCode(), FtnWildcard.fromString("2:382/*").hashCode())
        assertNotEquals(wildcard, FtnWildcard.fromString("2:383/*"))
        assertNotEquals(wildcard, FtnWildcard.fromString("2:382/1.*"))
        assertFalse(wildcard.equals("2:382/*"))
        assertFalse(wildcard.equals(null))
    }
}
