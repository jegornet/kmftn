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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration tests for Binkp file transfer
 * Tests sending files, receiving files, and bidirectional transfer
 */
class BinkpFileTransferTest {

    private lateinit var server: BinkdTestServer
    private lateinit var receiveDir: Path
    private lateinit var addresses: AddressPair

    @BeforeTest
    fun setup() {
        receiveDir = createTempDirectory(prefix = "binkp-client-recv-")
        addresses = AddressGenerator.generateAddressPair()
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
    }

    @Test
    fun testSendSingleSmallFile(): Unit = runBlocking {
        // Arrange
        val password = PasswordGenerator.generateRandomPassword()
        server = BinkdTestServer(
            serverAddress = addresses.serverAddress,
            nodeAddress = addresses.clientAddress,
            nodePassword = password)
        server.start()

        val outboundDir = createTempDirectory(prefix = "binkp-client-out-")
        val outbound = BsoOutbound(outboundDir, addresses.clientAddress.zone)

        val testFile = TestFileGenerator.createTextFile(
            "test-small.txt",
            "This is a test file with some content.\nLine 2\nLine 3\n"
        )
        outbound.addReference(addresses.serverAddress, BsoReference(testFile, FtnFlavor.NORMAL))

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
        println("Files sent: ${result.filesSent.map { it.name }}")

        assertTrue(result.success, "Session should succeed")
        assertNull(result.errorMessage)
        assertEquals(1, result.filesSent.size, "Should have sent 1 file")
        assertEquals(testFile.name, result.filesSent[0].name)

        // Verify file was received by binkd
        val receivedFiles = server.getInboundFiles()
        assertEquals(1, receivedFiles.size, "Binkd should have received 1 file")

        val receivedFile = receivedFiles.first { it.name == testFile.name }
        TestAssertions.assertFilesEqual(testFile, receivedFile, "Sent file should match received file")
    }

    @Test
    fun testSendMultipleFiles(): Unit = runBlocking {
        // Arrange
        val password = PasswordGenerator.generateRandomPassword()
        server = BinkdTestServer(
            serverAddress = addresses.serverAddress,
            nodeAddress = addresses.clientAddress,
            nodePassword = password)
        server.start()

        val outboundDir = createTempDirectory(prefix = "binkp-client-out-")
        val outbound = BsoOutbound(outboundDir, addresses.clientAddress.zone)

        val file1 = TestFileGenerator.createTextFile("file1.txt", "Content of file 1")
        val file2 = TestFileGenerator.createFile("file2.dat", 1024) // 1 KB
        val file3 = TestFileGenerator.createTextFile("file3.pkt", "Packet data here")

        outbound.addReference(addresses.serverAddress, BsoReference(file1, FtnFlavor.NORMAL))
        outbound.addReference(addresses.serverAddress, BsoReference(file2, FtnFlavor.NORMAL))
        outbound.addReference(addresses.serverAddress, BsoReference(file3, FtnFlavor.NORMAL))

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
        println("Files sent: ${result.filesSent.map { it.name }}")

        assertTrue(result.success, "Session should succeed")
        assertEquals(3, result.filesSent.size, "Should have sent 3 files")

        // Verify all files were received by binkd
        val receivedFiles = server.getInboundFiles()
        assertEquals(3, receivedFiles.size, "Binkd should have received 3 files")

        // Verify content of each file
        TestAssertions.assertFilesEqual(file1, receivedFiles.first { it.name == file1.name })
        TestAssertions.assertFilesEqual(file2, receivedFiles.first { it.name == file2.name })
        TestAssertions.assertFilesEqual(file3, receivedFiles.first { it.name == file3.name })
    }

    @Test
    fun testSendLargeFile(): Unit = runBlocking {
        // Arrange
        val password = PasswordGenerator.generateRandomPassword()
        server = BinkdTestServer(
            serverAddress = addresses.serverAddress,
            nodeAddress = addresses.clientAddress,
            nodePassword = password)
        server.start()

        val outboundDir = createTempDirectory(prefix = "binkp-client-out-")
        val outbound = BsoOutbound(outboundDir, addresses.clientAddress.zone)

        // Create a 1 MB file
        val largeFile = TestFileGenerator.createFile("large-file.bin", 1024 * 1024)
        outbound.addReference(addresses.serverAddress, BsoReference(largeFile, FtnFlavor.NORMAL))

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
            timeout = 60000, // 1 minute timeout for large file
            onLogString = ::println
        )

        // Assert
        println("\n=== Test Result ===")
        println("Success: ${result.success}")
        println("Files sent: ${result.filesSent.map { "${it.name} (${it.fileLength()} bytes)" }}")

        assertTrue(result.success, "Session should succeed")
        assertEquals(1, result.filesSent.size, "Should have sent 1 file")

        // Verify file was received correctly
        val receivedFiles = server.getInboundFiles()
        assertEquals(1, receivedFiles.size, "Binkd should have received 1 file")

        val receivedFile = receivedFiles.first()
        TestAssertions.assertFilesEqual(largeFile, receivedFile, "Large file should be transferred correctly")
    }

    @Test
    fun testReceiveSingleFile(): Unit = runBlocking {
        // Arrange
        val password = PasswordGenerator.generateRandomPassword()
        server = BinkdTestServer(
            serverAddress = addresses.serverAddress,
            nodeAddress = addresses.clientAddress,
            nodePassword = password)
        server.start()

        val outboundDir = createTempDirectory(prefix = "binkp-client-out-")
        val outbound = BsoOutbound(outboundDir, addresses.clientAddress.zone)

        // Put file in binkd outbound for our node
        val testFile = TestFileGenerator.createTextFile("incoming.txt", "This file comes from binkd")
        val outboundFile = server.putFileInOutbound(testFile)

        // Create .flo file to trigger sending
        server.createFloFile(listOf(outboundFile))

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
        println("Files received: ${result.filesReceived.map { it.name }}")

        // Debug: check binkd log
        println("\n=== Binkd Log ===")
        println(server.getLog())

        assertTrue(result.success, "Session should succeed")
        assertEquals(1, result.filesReceived.size, "Should have received 1 file")

        val receivedFile = result.filesReceived[0]
        assertEquals(testFile.name, receivedFile.fileName())
        TestAssertions.assertFilesEqual(testFile, receivedFile, "Received file should match original")
    }

    @Test
    fun testReceiveMultipleFiles(): Unit = runBlocking {
        // Arrange
        val password = PasswordGenerator.generateRandomPassword()
        server = BinkdTestServer(
            serverAddress = addresses.serverAddress,
            nodeAddress = addresses.clientAddress,
            nodePassword = password)
        server.start()

        val outboundDir = createTempDirectory(prefix = "binkp-client-out-")
        val outbound = BsoOutbound(outboundDir, addresses.clientAddress.zone)

        // Create multiple files in binkd outbound
        val file1 = TestFileGenerator.createTextFile("recv1.txt", "File 1 from binkd")
        val file2 = TestFileGenerator.createFile("recv2.dat", 2048)
        val file3 = TestFileGenerator.createPktFile("198:51/100", "198:51/100.1")

        val outFile1 = server.putFileInOutbound(file1)
        val outFile2 = server.putFileInOutbound(file2)
        val outFile3 = server.putFileInOutbound(file3)

        server.createFloFile(listOf(outFile1, outFile2, outFile3))

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
        println("Files received: ${result.filesReceived.map { it.name }}")

        assertTrue(result.success, "Session should succeed")
        assertEquals(3, result.filesReceived.size, "Should have received 3 files")

        // Verify all files
        TestAssertions.assertFilesEqual(file1, result.filesReceived.first { it.fileName() == file1.name })
        TestAssertions.assertFilesEqual(file2, result.filesReceived.first { it.fileName() == file2.name })
        TestAssertions.assertFilesEqual(file3, result.filesReceived.first { it.fileName() == file3.name })
    }

    @Test
    fun testBidirectionalTransfer(): Unit = runBlocking {
        // Arrange
        val password = PasswordGenerator.generateRandomPassword()
        server = BinkdTestServer(
            serverAddress = addresses.serverAddress,
            nodeAddress = addresses.clientAddress,
            nodePassword = password
        ) // with password
        server.start()

        val outboundDir = createTempDirectory(prefix = "binkp-client-out-")
        val outbound = BsoOutbound(outboundDir, addresses.clientAddress.zone)

        // Files to send
        val sendFile1 = TestFileGenerator.createTextFile("send1.txt", "Sending to binkd")
        val sendFile2 = TestFileGenerator.createFile("send2.dat", 4096)
        outbound.addReference(addresses.serverAddress, BsoReference(sendFile1, FtnFlavor.NORMAL))
        outbound.addReference(addresses.serverAddress, BsoReference(sendFile2, FtnFlavor.NORMAL))

        // Files to receive from binkd
        val recvFile1 = TestFileGenerator.createTextFile("recv1.txt", "Receiving from binkd")
        val recvFile2 = TestFileGenerator.createFile("recv2.dat", 8192)

        val outFile1 = server.putFileInOutbound(recvFile1)
        val outFile2 = server.putFileInOutbound(recvFile2)
        server.createFloFile(listOf(outFile1, outFile2))

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
        println("Files sent: ${result.filesSent.map { it.name }}")
        println("Files received: ${result.filesReceived.map { it.name }}")

        assertTrue(result.success, "Session should succeed")
        assertEquals(2, result.filesSent.size, "Should have sent 2 files")
        assertEquals(2, result.filesReceived.size, "Should have received 2 files")

        // Verify sent files arrived at binkd
        val binkdReceivedFiles = server.getInboundFiles()
        assertTrue(binkdReceivedFiles.size >= 2, "Binkd should have received at least 2 files")

        // Verify received files
        TestAssertions.assertFilesEqual(recvFile1, result.filesReceived.first { it.fileName() == recvFile1.name })
        TestAssertions.assertFilesEqual(recvFile2, result.filesReceived.first { it.fileName() == recvFile2.name })
    }

    @Test
    fun testFileWithSpecialCharacters(): Unit = runBlocking {
        // Arrange
        val password = PasswordGenerator.generateRandomPassword()
        server = BinkdTestServer(
            serverAddress = addresses.serverAddress,
            nodeAddress = addresses.clientAddress,
            nodePassword = password)
        server.start()

        val outboundDir = createTempDirectory(prefix = "binkp-client-out-")
        val outbound = BsoOutbound(outboundDir, addresses.clientAddress.zone)

        // Create file with space in name
        val fileWithSpace = TestFileGenerator.createTextFile("test file.txt", "File with space")
        outbound.addReference(addresses.serverAddress, BsoReference(fileWithSpace, FtnFlavor.NORMAL))

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
        println("Files sent: ${result.filesSent.map { it.name }}")

        assertTrue(result.success, "Session should succeed")
        assertEquals(1, result.filesSent.size, "Should have sent 1 file")

        // Verify file was received by binkd (name might be escaped)
        val receivedFiles = server.getInboundFiles()
        assertTrue(receivedFiles.isNotEmpty(), "Binkd should have received the file")
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
