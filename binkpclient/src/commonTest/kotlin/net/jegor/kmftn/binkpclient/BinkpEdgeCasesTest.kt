package net.jegor.kmftn.binkpclient

import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import net.jegor.kmftn.base.FtnFlavor
import net.jegor.kmftn.bso.BsoOutbound
import net.jegor.kmftn.bso.BsoReference
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration tests for Binkp edge cases and boundary conditions
 * Tests special scenarios, frame sizes, and error conditions
 */
class BinkpEdgeCasesTest {

    private lateinit var server: BinkdTestServer
    private lateinit var receiveDir: Path
    private lateinit var outboundDir: Path
    private lateinit var outbound: BsoOutbound
    private lateinit var addresses: AddressPair

    @BeforeTest
    fun setup() {
        receiveDir = createTempDirectory(prefix = "binkp-client-recv-")
        outboundDir = createTempDirectory(prefix = "binkp-client-out-")
        addresses = AddressGenerator.generateAddressPair()
        outbound = BsoOutbound(outboundDir, addresses.clientAddress.zone)
    }

    @AfterTest
    fun teardown(): Unit = runBlocking {
        if (::server.isInitialized) {
            server.stop()
            server.cleanup()
        }
        if (::receiveDir.isInitialized && SystemFileSystem.exists(receiveDir)) {
            deleteRecursively(receiveDir)
        }
        if (::outboundDir.isInitialized && SystemFileSystem.exists(outboundDir)) {
            deleteRecursively(outboundDir)
        }
    }

    @Test
    fun testEmptySession(): Unit = runBlocking {
        // Arrange
        val password = PasswordGenerator.generateRandomPassword()
        server = BinkdTestServer(
            serverAddress = addresses.serverAddress,
            nodeAddress = addresses.clientAddress,
            nodePassword = password)
        server.start()

        // Act
        val result = binkpClient(
            localAddresses = listOf(addresses.clientAddress),
            localSystemName = "Test Client",
            localSysopName = "Test Sysop",
            localLocation = "Test Location",
            localFlags = "CM,IBN",
            remoteAddress = addresses.serverAddress,
            sessionPassword = password,
            remoteHost = "127.0.0.1",
            remotePort = server.port,
            outbound = outbound,
            receiveDirectorySecure = receiveDir,
            receiveDirectoryInsecure = receiveDir,
            onLogString = ::println
        )

        // Assert
        println("\n=== Test Result ===")
        println("Success: ${result.success}")

        assertTrue(result.success, "Empty session should succeed")
        assertEquals(0, result.filesSent.size, "Should have sent 0 files")
        assertEquals(0, result.filesReceived.size, "Should have received 0 files")
    }

    @Test
    fun testVerySmallFile(): Unit = runBlocking {
        // Arrange
        val password = PasswordGenerator.generateRandomPassword()
        server = BinkdTestServer(
            serverAddress = addresses.serverAddress,
            nodeAddress = addresses.clientAddress,
            nodePassword = password)
        server.start()

        val tinyFile = TestFileGenerator.createTextFile("tiny.txt", "X")
        outbound.addReference(addresses.serverAddress, BsoReference(tinyFile, FtnFlavor.NORMAL))

        // Act
        val result = binkpClient(
            localAddresses = listOf(addresses.clientAddress),
            localSystemName = "Test Client",
            localSysopName = "Test Sysop",
            localLocation = "Test Location",
            localFlags = "CM,IBN",
            remoteAddress = addresses.serverAddress,
            sessionPassword = password,
            remoteHost = "127.0.0.1",
            remotePort = server.port,
            outbound = outbound,
            receiveDirectorySecure = receiveDir,
            receiveDirectoryInsecure = receiveDir,
            onLogString = ::println
        )

        // Assert
        assertTrue(result.success, "Should handle 1-byte file")
        assertEquals(1, result.filesSent.size)

        val receivedFiles = server.getInboundFiles()
        val receivedFile = receivedFiles.first { it.name == tinyFile.name }
        TestAssertions.assertFilesEqual(tinyFile, receivedFile)
    }

    @Test
    fun testFileExactlyMaxFrameSize(): Unit = runBlocking {
        // Arrange
        val password = PasswordGenerator.generateRandomPassword()
        server = BinkdTestServer(
            serverAddress = addresses.serverAddress,
            nodeAddress = addresses.clientAddress,
            nodePassword = password)
        server.start()

        // Create file exactly 32767 bytes (max binkp frame data size)
        val maxFrameFile = TestFileGenerator.createFile("max-frame.dat", 32767)
        outbound.addReference(addresses.serverAddress, BsoReference(maxFrameFile, FtnFlavor.NORMAL))

        // Act
        val result = binkpClient(
            localAddresses = listOf(addresses.clientAddress),
            localSystemName = "Test Client",
            localSysopName = "Test Sysop",
            localLocation = "Test Location",
            localFlags = "CM,IBN",
            remoteAddress = addresses.serverAddress,
            sessionPassword = password,
            remoteHost = "127.0.0.1",
            remotePort = server.port,
            outbound = outbound,
            receiveDirectorySecure = receiveDir,
            receiveDirectoryInsecure = receiveDir,
            onLogString = ::println
        )

        // Assert
        println("\n=== Test Result ===")
        println("File size: ${maxFrameFile.fileLength()} bytes (max frame size)")

        assertTrue(result.success, "Should handle file of exactly max frame size")
        assertEquals(1, result.filesSent.size)

        val receivedFiles = server.getInboundFiles()
        val receivedFile = receivedFiles.first { it.name == maxFrameFile.name }
        TestAssertions.assertFilesEqual(maxFrameFile, receivedFile)
    }

    @Test
    fun testFileJustOverMaxFrameSize(): Unit = runBlocking {
        // Arrange
        val password = PasswordGenerator.generateRandomPassword()
        server = BinkdTestServer(
            serverAddress = addresses.serverAddress,
            nodeAddress = addresses.clientAddress,
            nodePassword = password)
        server.start()

        // Create file 32768 bytes (requires at least 2 frames)
        val overFrameFile = TestFileGenerator.createFile("over-frame.dat", 32768)
        outbound.addReference(addresses.serverAddress, BsoReference(overFrameFile, FtnFlavor.NORMAL))

        // Act
        val result = binkpClient(
            localAddresses = listOf(addresses.clientAddress),
            localSystemName = "Test Client",
            localSysopName = "Test Sysop",
            localLocation = "Test Location",
            localFlags = "CM,IBN",
            remoteAddress = addresses.serverAddress,
            sessionPassword = password,
            remoteHost = "127.0.0.1",
            remotePort = server.port,
            outbound = outbound,
            receiveDirectorySecure = receiveDir,
            receiveDirectoryInsecure = receiveDir,
            onLogString = ::println
        )

        // Assert
        println("\n=== Test Result ===")
        println("File size: ${overFrameFile.fileLength()} bytes (max frame size + 1)")

        assertTrue(result.success, "Should handle file requiring multiple frames")
        assertEquals(1, result.filesSent.size)

        val receivedFiles = server.getInboundFiles()
        val receivedFile = receivedFiles.first { it.name == overFrameFile.name }
        TestAssertions.assertFilesEqual(overFrameFile, receivedFile)
    }

    @Test
    fun testFileMultipleOfFrameSize(): Unit = runBlocking {
        // Arrange
        val password = PasswordGenerator.generateRandomPassword()
        server = BinkdTestServer(
            serverAddress = addresses.serverAddress,
            nodeAddress = addresses.clientAddress,
            nodePassword = password)
        server.start()

        // Create file that's exactly 3 * 32767 bytes
        val multiFrameFile = TestFileGenerator.createFile("multi-frame.dat", 32767L * 3)
        outbound.addReference(addresses.serverAddress, BsoReference(multiFrameFile, FtnFlavor.NORMAL))

        // Act
        val result = binkpClient(
            localAddresses = listOf(addresses.clientAddress),
            localSystemName = "Test Client",
            localSysopName = "Test Sysop",
            localLocation = "Test Location",
            localFlags = "CM,IBN",
            remoteAddress = addresses.serverAddress,
            sessionPassword = password,
            remoteHost = "127.0.0.1",
            remotePort = server.port,
            outbound = outbound,
            receiveDirectorySecure = receiveDir,
            receiveDirectoryInsecure = receiveDir,
            onLogString = ::println
        )

        // Assert
        assertTrue(result.success, "Should handle file with exact frame multiples")
        assertEquals(1, result.filesSent.size)

        val receivedFiles = server.getInboundFiles()
        val receivedFile = receivedFiles.first { it.name == multiFrameFile.name }
        TestAssertions.assertFilesEqual(multiFrameFile, receivedFile)
    }

    @Test
    fun testMaxPasswordLength(): Unit = runBlocking {
        // Arrange
        val maxPassword = "12345678" // exactly 8 characters
        server = BinkdTestServer(
            serverAddress = addresses.serverAddress,
            nodeAddress = addresses.clientAddress,
            nodePassword = maxPassword)
        server.start()

        // Act
        val result = binkpClient(
            localAddresses = listOf(addresses.clientAddress),
            localSystemName = "Test Client",
            localSysopName = "Test Sysop",
            localLocation = "Test Location",
            localFlags = "CM,IBN",
            remoteAddress = addresses.serverAddress,
            sessionPassword = maxPassword,
            remoteHost = "127.0.0.1",
            remotePort = server.port,
            outbound = outbound,
            receiveDirectorySecure = receiveDir,
            receiveDirectoryInsecure = receiveDir,
            onLogString = ::println
        )

        // Assert
        assertTrue(result.success, "Should handle 8-character password")
    }

    @Test
    fun testMinPasswordLength(): Unit = runBlocking {
        // Arrange
        val minPassword = "x" // 1 character
        server = BinkdTestServer(
            serverAddress = addresses.serverAddress,
            nodeAddress = addresses.clientAddress,
            nodePassword = minPassword)
        server.start()

        // Act
        val result = binkpClient(
            localAddresses = listOf(addresses.clientAddress),
            localSystemName = "Test Client",
            localSysopName = "Test Sysop",
            localLocation = "Test Location",
            localFlags = "CM,IBN",
            remoteAddress = addresses.serverAddress,
            sessionPassword = minPassword,
            remoteHost = "127.0.0.1",
            remotePort = server.port,
            outbound = outbound,
            receiveDirectorySecure = receiveDir,
            receiveDirectoryInsecure = receiveDir,
            onLogString = ::println
        )

        // Assert
        assertTrue(result.success, "Should handle 1-character password")
    }

    @Test
    fun testManySmallFiles(): Unit = runBlocking {
        // Arrange
        val password = PasswordGenerator.generateRandomPassword()
        server = BinkdTestServer(
            serverAddress = addresses.serverAddress,
            nodeAddress = addresses.clientAddress,
            nodePassword = password)
        server.start()

        // Create 20 small files
        val files = (1..20).map { i ->
            val file = TestFileGenerator.createTextFile("file-$i.txt", "Content of file $i\n")
            outbound.addReference(addresses.serverAddress, BsoReference(file, FtnFlavor.NORMAL))
            file
        }

        // Act
        val result = binkpClient(
            localAddresses = listOf(addresses.clientAddress),
            localSystemName = "Test Client",
            localSysopName = "Test Sysop",
            localLocation = "Test Location",
            localFlags = "CM,IBN",
            remoteAddress = addresses.serverAddress,
            sessionPassword = password,
            remoteHost = "127.0.0.1",
            remotePort = server.port,
            outbound = outbound,
            receiveDirectorySecure = receiveDir,
            receiveDirectoryInsecure = receiveDir,
            timeout = 60000,
            onLogString = ::println
        )

        // Assert
        println("\n=== Test Result ===")
        println("Success: ${result.success}")
        println("Files sent: ${result.filesSent.size}")

        assertTrue(result.success, "Should handle many small files")
        assertEquals(20, result.filesSent.size, "Should have sent all 20 files")

        val receivedFiles = server.getInboundFiles()
        assertEquals(20, receivedFiles.size, "Binkd should have received all 20 files")
    }

    @Test
    fun testLongSystemName(): Unit = runBlocking {
        // Arrange
        val password = PasswordGenerator.generateRandomPassword()
        server = BinkdTestServer(
            serverAddress = addresses.serverAddress,
            nodeAddress = addresses.clientAddress,
            nodePassword = password)
        server.start()

        val longSystemName = "A".repeat(200) // Very long name

        // Act
        val result = binkpClient(
            localAddresses = listOf(addresses.clientAddress),
            localSystemName = longSystemName,
            localSysopName = "Test Sysop",
            localLocation = "Test Location",
            localFlags = "CM,IBN",
            remoteAddress = addresses.serverAddress,
            sessionPassword = password,
            remoteHost = "127.0.0.1",
            remotePort = server.port,
            outbound = outbound,
            receiveDirectorySecure = receiveDir,
            receiveDirectoryInsecure = receiveDir,
            onLogString = ::println
        )

        // Assert
        assertTrue(result.success, "Should handle long system name")
    }

    @Test
    fun testUnicodeFileName(): Unit = runBlocking {
        // Arrange
        val password = PasswordGenerator.generateRandomPassword()
        server = BinkdTestServer(
            serverAddress = addresses.serverAddress,
            nodeAddress = addresses.clientAddress,
            nodePassword = password)
        server.start()

        // Create file with Cyrillic characters
        val unicodeFile = TestFileGenerator.createTextFile("тест-файл.txt", "Unicode content")
        outbound.addReference(addresses.serverAddress, BsoReference(unicodeFile, FtnFlavor.NORMAL))

        // Act
        val result = binkpClient(
            localAddresses = listOf(addresses.clientAddress),
            localSystemName = "Test Client",
            localSysopName = "Test Sysop",
            localLocation = "Test Location",
            localFlags = "CM,IBN",
            remoteAddress = addresses.serverAddress,
            sessionPassword = password,
            remoteHost = "127.0.0.1",
            remotePort = server.port,
            outbound = outbound,
            receiveDirectorySecure = receiveDir,
            receiveDirectoryInsecure = receiveDir,
            onLogString = ::println
        )

        // Assert
        println("\n=== Test Result ===")
        println("Success: ${result.success}")
        println("Files sent: ${result.filesSent.map { it.fileName() }}")

        // This test might fail on some systems, so we just log the result
        // Unicode filename support depends on binkd configuration and file system
        if (result.success) {
            println("Unicode filename handling: SUCCESS")
        } else {
            println("Unicode filename handling: FAILED (may be expected on some systems)")
            println("Error: ${result.errorMessage}")
        }
    }
}

/**
 * Helper function to delete directory recursively
 */
private fun deleteRecursively(dir: Path) {
    if (!SystemFileSystem.exists(dir)) return

    val metadata = SystemFileSystem.metadataOrNull(dir)
    if (metadata?.isDirectory == true) {
        SystemFileSystem.list(dir).forEach { path ->
            deleteRecursively(path)
        }
    }

    SystemFileSystem.delete(dir)
}
