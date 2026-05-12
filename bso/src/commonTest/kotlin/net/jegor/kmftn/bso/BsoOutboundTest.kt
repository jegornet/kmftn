package net.jegor.kmftn.bso

import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import net.jegor.kmftn.base.FtnAddr
import net.jegor.kmftn.base.FtnFlavor
import kotlin.test.*
import kotlinx.coroutines.test.runTest

class BsoOutboundTest {
    private val testRoot = Path("test-outbound")

    @BeforeTest
    fun setup() {
        if (SystemFileSystem.exists(testRoot)) {
            deleteRecursive(testRoot)
        }
        SystemFileSystem.createDirectories(testRoot)
    }

    @AfterTest
    fun tearDown() {
        if (SystemFileSystem.exists(testRoot)) {
            deleteRecursive(testRoot)
        }
    }

    private fun deleteRecursive(path: Path) {
        if (SystemFileSystem.metadataOrNull(path)?.isDirectory == true) {
            SystemFileSystem.list(path).forEach { deleteRecursive(it) }
        }
        SystemFileSystem.delete(path)
    }

    @Test
    fun testDirectoryResolution() {
        val outbound = BsoOutbound(testRoot, 2)
        
        // Default zone
        assertEquals(testRoot, outbound.getDirectoryForAddr(FtnAddr(2, 5020, 10, 0)))
        
        // Other zone
        assertEquals(Path("$testRoot.001"), outbound.getDirectoryForAddr(FtnAddr(1, 5020, 10, 0)))
        assertEquals(Path("$testRoot.02e"), outbound.getDirectoryForAddr(FtnAddr(46, 5020, 10, 0)))
        
        // Point
        val pointAddr = FtnAddr(2, 5020, 10, 5)
        val expectedPointDir = Path(testRoot, "139c000a.pnt")
        assertEquals(expectedPointDir, outbound.getDirectoryForAddr(pointAddr))
    }

    @Test
    fun testAddReference() = runTest {
        val outbound = BsoOutbound(testRoot, 2)
        val addr = FtnAddr(2, 5020, 10, 0)
        val fileRef = Path("/tmp/test.zip")
        
        outbound.addReference(addr, BsoReference(fileRef, FtnFlavor.NORMAL, BsoDirective.TRUNCATE))
        
        val link = outbound.getLink(addr)
        assertEquals(1, link.references.size)
        assertEquals(fileRef, link.references[0].path)
        assertEquals(FtnFlavor.NORMAL, link.references[0].flavor)
        assertEquals(BsoDirective.TRUNCATE, link.references[0].directive)
    }

    @Test
    fun testListLinks() = runTest {
        val outbound = BsoOutbound(testRoot, 2)
        
        // Add some files manually
        val addr1 = FtnAddr(2, 5020, 10, 0) // 139c000a
        val addr2 = FtnAddr(3, 100, 1, 0)   // 00640001 in outbound.003
        
        outbound.addReference(addr1, BsoReference(Path("f1"), FtnFlavor.NORMAL))
        
        val zone3Dir = Path("$testRoot.003")
        SystemFileSystem.createDirectories(zone3Dir)
        outbound.addReference(addr2, BsoReference(Path("f2"), FtnFlavor.IMMEDIATE))
        
        val links = outbound.listLinks()
        assertEquals(2, links.size)
        assertTrue(links.contains(addr1))
        assertTrue(links.contains(addr2))
    }

    @Test
    fun testBusyLock() = runTest {
        val outbound = BsoOutbound(testRoot, 2)
        val addr = FtnAddr(2, 5020, 10, 0)
        
        // Create a .bsy file
        val dir = outbound.getDirectoryForAddr(addr)
        SystemFileSystem.createDirectories(dir)
        val bsyPath = Path(dir, "139c000a.bsy")
        SystemFileSystem.sink(bsyPath).close()
        
        assertFailsWith<IllegalStateException> {
            outbound.addReference(addr, BsoReference(Path("fail"), FtnFlavor.NORMAL))
        }
    }
}
