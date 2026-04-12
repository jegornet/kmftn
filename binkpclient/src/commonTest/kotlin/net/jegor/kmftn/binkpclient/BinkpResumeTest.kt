package net.jegor.kmftn.binkpclient

import kotlinx.coroutines.runBlocking
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration tests for Binkp file resume (partial file transfer continuation)
 * Tests M_GET command and offset-based file transfer
 */
class BinkpResumeTest {

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
    fun testResumeReceivingPartialFile(): Unit = runBlocking {
        // Arrange
        val password = PasswordGenerator.generateRandomPassword()
        server = BinkdTestServer(
            serverAddress = addresses.serverAddress,
            nodeAddress = addresses.clientAddress,
            nodePassword = password)
        server.start()

        // Create a test file
        val fullFile = TestFileGenerator.createFile("resume-test.dat", 100 * 1024) // 100 KB
        val outboundFile = server.putFileInOutbound(fullFile)
        server.createFloFile(listOf(outboundFile))

        // Create a partial file in receive directory (first 50 KB)
        val partialFile = Path(receiveDir.toString(), fullFile.name)
        SystemFileSystem.source(fullFile).buffered().use { input ->
            SystemFileSystem.sink(partialFile).buffered().use { output ->
                val buffer = ByteArray(50 * 1024)
                val bytesRead = input.readAtMostTo(buffer)
                output.write(buffer, 0, bytesRead)
            }
        }

        println("Created partial file: ${partialFile} (${partialFile.fileLength()} bytes)")
        println("Full file size: ${fullFile.fileLength()} bytes")

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
            getFilesToSend = { _, _ -> emptyList() },
            receiveDirectorySecure = receiveDir,
            receiveDirectoryInsecure = receiveDir,
            enableResume = true,
            onLogString = ::println
        )

        // Assert
        println("\n=== Test Result ===")
        println("Success: ${result.success}")
        println("Files received: ${result.filesReceived.map { "${it.fileName()} (${it.fileLength()} bytes)" }}")

        assertTrue(result.success, "Session should succeed")
        assertEquals(1, result.filesReceived.size, "Should have received 1 file")

        val receivedFile = result.filesReceived[0]
        assertEquals(fullFile.name, receivedFile.fileName())
        assertEquals(fullFile.fileLength(), receivedFile.fileLength(), "File should be complete after resume")

        // Verify file content is correct
        TestAssertions.assertFilesEqual(fullFile, receivedFile, "Resumed file should match original")
    }

    @Test
    fun testReceiveFileWithExistingCompleteFile(): Unit = runBlocking {
        // Arrange
        val password = PasswordGenerator.generateRandomPassword()
        server = BinkdTestServer(
            serverAddress = addresses.serverAddress,
            nodeAddress = addresses.clientAddress,
            nodePassword = password)
        server.start()

        // Create a test file
        val fullFile = TestFileGenerator.createFile("already-complete.dat", 50 * 1024) // 50 KB
        val outboundFile = server.putFileInOutbound(fullFile)
        server.createFloFile(listOf(outboundFile))

        // Copy the full file to receive directory (already complete)
        val existingFile = Path(receiveDir.toString(), fullFile.name)
        copyFile(fullFile, existingFile)

        println("Created complete file in receive dir: ${existingFile} (${existingFile.fileLength()} bytes)")

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
            getFilesToSend = { _, _ -> emptyList() },
            receiveDirectorySecure = receiveDir,
            receiveDirectoryInsecure = receiveDir,
            enableResume = true,
            onLogString = ::println
        )

        // Assert
        println("\n=== Test Result ===")
        println("Success: ${result.success}")
        println("Files received: ${result.filesReceived.map { "${it.fileName()} (${it.fileLength()} bytes)" }}")

        assertTrue(result.success, "Session should succeed")

        // File should still be there and correct
        assertTrue(SystemFileSystem.exists(existingFile), "File should still exist")
        assertEquals(fullFile.fileLength(), existingFile.fileLength(), "File size should be unchanged")
    }

    @Test
    fun testResumeWithSmallPartialFile(): Unit = runBlocking {
        // Arrange
        val password = PasswordGenerator.generateRandomPassword()
        server = BinkdTestServer(
            serverAddress = addresses.serverAddress,
            nodeAddress = addresses.clientAddress,
            nodePassword = password)
        server.start()

        // Create a larger test file
        val fullFile = TestFileGenerator.createFile("small-partial.dat", 200 * 1024) // 200 KB
        val outboundFile = server.putFileInOutbound(fullFile)
        server.createFloFile(listOf(outboundFile))

        // Create a very small partial file (just 1 KB)
        val partialFile = Path(receiveDir.toString(), fullFile.name)
        SystemFileSystem.source(fullFile).buffered().use { input ->
            SystemFileSystem.sink(partialFile).buffered().use { output ->
                val buffer = ByteArray(1024)
                val bytesRead = input.readAtMostTo(buffer)
                output.write(buffer, 0, bytesRead)
            }
        }

        println("Created small partial file: ${partialFile.fileLength()} bytes of ${fullFile.fileLength()} bytes")

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
            getFilesToSend = { _, _ -> emptyList() },
            receiveDirectorySecure = receiveDir,
            receiveDirectoryInsecure = receiveDir,
            enableResume = true,
            timeout = 60000,
            onLogString = ::println
        )

        // Assert
        println("\n=== Test Result ===")
        println("Success: ${result.success}")

        assertTrue(result.success, "Session should succeed")
        assertEquals(1, result.filesReceived.size, "Should have received 1 file")

        val receivedFile = result.filesReceived[0]
        assertEquals(fullFile.fileLength(), receivedFile.fileLength(), "File should be complete")
        TestAssertions.assertFilesEqual(fullFile, receivedFile, "Resumed file should match original")
    }

    @Test
    fun testResumeWithLargePartialFile(): Unit = runBlocking {
        // Arrange
        val password = PasswordGenerator.generateRandomPassword()
        server = BinkdTestServer(
            serverAddress = addresses.serverAddress,
            nodeAddress = addresses.clientAddress,
            nodePassword = password)
        server.start()

        // Create a test file
        val fullFile = TestFileGenerator.createFile("large-partial.dat", 500 * 1024) // 500 KB
        val outboundFile = server.putFileInOutbound(fullFile)
        server.createFloFile(listOf(outboundFile))

        // Create a partial file that's almost complete (490 KB of 500 KB)
        val partialFile = Path(receiveDir.toString(), fullFile.name)
        SystemFileSystem.source(fullFile).buffered().use { input ->
            SystemFileSystem.sink(partialFile).buffered().use { output ->
                val buffer = ByteArray(490 * 1024)
                val bytesRead = input.readAtMostTo(buffer)
                output.write(buffer, 0, bytesRead)
            }
        }

        println("Created large partial file: ${partialFile.fileLength()} bytes of ${fullFile.fileLength()} bytes (${partialFile.fileLength() * 100 / fullFile.fileLength()}%)")

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
            getFilesToSend = { _, _ -> emptyList() },
            receiveDirectorySecure = receiveDir,
            receiveDirectoryInsecure = receiveDir,
            enableResume = true,
            timeout = 60000,
            onLogString = ::println
        )

        // Assert
        println("\n=== Test Result ===")
        println("Success: ${result.success}")

        assertTrue(result.success, "Session should succeed")
        assertEquals(1, result.filesReceived.size, "Should have received 1 file")

        val receivedFile = result.filesReceived[0]
        assertEquals(fullFile.fileLength(), receivedFile.fileLength(), "File should be complete")
        TestAssertions.assertFilesEqual(fullFile, receivedFile, "Resumed file should match original")
    }

    @Test
    fun testMultipleFilesWithOnePartial(): Unit = runBlocking {
        // Arrange
        val password = PasswordGenerator.generateRandomPassword()
        server = BinkdTestServer(
            serverAddress = addresses.serverAddress,
            nodeAddress = addresses.clientAddress,
            nodePassword = password)
        server.start()

        // Create multiple files
        val file1 = TestFileGenerator.createFile("file1.dat", 30 * 1024)
        val file2 = TestFileGenerator.createFile("file2.dat", 100 * 1024) // This one will be partial
        val file3 = TestFileGenerator.createFile("file3.dat", 50 * 1024)

        val out1 = server.putFileInOutbound(file1)
        val out2 = server.putFileInOutbound(file2)
        val out3 = server.putFileInOutbound(file3)
        server.createFloFile(listOf(out1, out2, out3))

        // Create partial version of file2
        val partialFile2 = Path(receiveDir.toString(), file2.name)
        SystemFileSystem.source(file2).buffered().use { input ->
            SystemFileSystem.sink(partialFile2).buffered().use { output ->
                val buffer = ByteArray(40 * 1024)
                val bytesRead = input.readAtMostTo(buffer)
                output.write(buffer, 0, bytesRead)
            }
        }

        println("Created partial file2: ${partialFile2.fileLength()} of ${file2.fileLength()} bytes")

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
            getFilesToSend = { _, _ -> emptyList() },
            receiveDirectorySecure = receiveDir,
            receiveDirectoryInsecure = receiveDir,
            enableResume = true,
            timeout = 60000,
            onLogString = ::println
        )

        // Assert
        println("\n=== Test Result ===")
        println("Success: ${result.success}")
        println("Files received: ${result.filesReceived.map { "${it.fileName()} (${it.fileLength()} bytes)" }}")

        assertTrue(result.success, "Session should succeed")
        assertEquals(3, result.filesReceived.size, "Should have received 3 files")

        // Verify all files
        TestAssertions.assertFilesEqual(file1, result.filesReceived.first { it.fileName() == file1.name })
        TestAssertions.assertFilesEqual(file2, result.filesReceived.first { it.fileName() == file2.name })
        TestAssertions.assertFilesEqual(file3, result.filesReceived.first { it.fileName() == file3.name })
    }

    @Test
    fun testNoResumeWhenDisabled(): Unit = runBlocking {
        // Arrange
        val password = PasswordGenerator.generateRandomPassword()
        server = BinkdTestServer(
            serverAddress = addresses.serverAddress,
            nodeAddress = addresses.clientAddress,
            nodePassword = password)
        server.start()

        // Create a test file
        val fullFile = TestFileGenerator.createFile("no-resume-test.dat", 100 * 1024) // 100 KB
        val outboundFile = server.putFileInOutbound(fullFile)
        server.createFloFile(listOf(outboundFile))

        // Create a partial file in receive directory (first 50 KB)
        val partialFile = Path(receiveDir.toString(), fullFile.name)
        SystemFileSystem.source(fullFile).buffered().use { input ->
            SystemFileSystem.sink(partialFile).buffered().use { output ->
                val buffer = ByteArray(50 * 1024)
                val bytesRead = input.readAtMostTo(buffer)
                output.write(buffer, 0, bytesRead)
            }
        }

        val partialSize = partialFile.fileLength()
        println("Created partial file: ${partialFile} ($partialSize bytes)")
        println("Full file size: ${fullFile.fileLength()} bytes")

        // Track log messages to verify no M_GET was sent
        val logMessages = mutableListOf<String>()
        val logCapture: (String) -> Unit = { msg ->
            logMessages.add(msg)
            println(msg)
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
            getFilesToSend = { _, _ -> emptyList() },
            receiveDirectorySecure = receiveDir,
            receiveDirectoryInsecure = receiveDir,
            enableResume = false,  // Disable resume
            onLogString = logCapture
        )

        // Assert
        println("\n=== Test Result ===")
        println("Success: ${result.success}")
        println("Files received: ${result.filesReceived.map { "${it.fileName()} (${it.fileLength()} bytes)" }}")

        assertTrue(result.success, "Session should succeed")
        assertEquals(1, result.filesReceived.size, "Should have received 1 file")

        val receivedFile = result.filesReceived[0]
        assertEquals(fullFile.name, receivedFile.name)
        assertEquals(fullFile.fileLength(), receivedFile.fileLength(), "File should be complete")

        // Verify file content is correct
        TestAssertions.assertFilesEqual(fullFile, receivedFile, "Downloaded file should match original")

        // Verify that M_GET was NOT sent (no resume happened)
        val mGetSent = logMessages.any { it.contains("< M_GET") }
        assertTrue(!mGetSent, "M_GET should NOT be sent when enableResume is false")

        // Verify that partial file deletion message was logged
        val partialDeleted = logMessages.any {
            it.contains("Resume disabled, deleting partial file") &&
            it.contains(fullFile.name) &&
            it.contains(partialSize.toString())
        }
        assertTrue(partialDeleted, "Partial file should be deleted when enableResume is false")
    }
}

/**
 * Helper function to copy a file
 */
private fun copyFile(source: Path, destination: Path) {
    SystemFileSystem.source(source).buffered().use { src ->
        SystemFileSystem.sink(destination).buffered().use { dst ->
            val buffer = ByteArray(8192)
            while (true) {
                val bytesRead = src.readAtMostTo(buffer)
                if (bytesRead == -1) break
                dst.write(buffer, 0, bytesRead)
            }
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
