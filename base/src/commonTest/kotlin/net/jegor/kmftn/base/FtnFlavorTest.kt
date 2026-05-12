package net.jegor.kmftn.base

import kotlin.test.Test
import kotlin.test.assertEquals

class FtnFlavorTest {

    @Test
    fun testFlowCodes() {
        assertEquals('i', FtnFlavor.IMMEDIATE.flowCode)
        assertEquals('c', FtnFlavor.CRASH.flowCode)
        assertEquals('d', FtnFlavor.DIRECT.flowCode)
        assertEquals('f', FtnFlavor.NORMAL.flowCode)
        assertEquals('h', FtnFlavor.HOLD.flowCode)
    }

    @Test
    fun testNetmailCodes() {
        assertEquals('i', FtnFlavor.IMMEDIATE.netmailCode)
        assertEquals('c', FtnFlavor.CRASH.netmailCode)
        assertEquals('d', FtnFlavor.DIRECT.netmailCode)
        assertEquals('o', FtnFlavor.NORMAL.netmailCode)
        assertEquals('h', FtnFlavor.HOLD.netmailCode)
    }
}
