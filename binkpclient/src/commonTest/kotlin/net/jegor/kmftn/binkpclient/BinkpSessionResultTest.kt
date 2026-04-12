package net.jegor.kmftn.binkpclient

import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for BinkpSessionResult.passwordProtected field
 * and receive directory routing based on password protection
 */
class BinkpSessionResultTest {

    private lateinit var server: BinkdTestServer
    private lateinit var receiveDir: Path
    private lateinit var receiveDirSecure: Path
    private lateinit var receiveDirInsecure: Path
    private lateinit var addresses: AddressPair

    @BeforeTest
    fun setup() {
        receiveDir = createTempDirectory(prefix = "binkp-result-test-")
        receiveDirSecure = createTempDirectory(prefix = "binkp-secure-")
        receiveDirInsecure = createTempDirectory(prefix = "binkp-insecure-")
        addresses = AddressGenerator.generateAddressPair()
    }

    @AfterTest
    fun teardown(): Unit = runBlocking {
        if (::server.isInitialized) {
            server.stop()
            server.cleanup()
        }
        listOf(receiveDir, receiveDirSecure, receiveDirInsecure).forEach { dir ->
            if (SystemFileSystem.exists(dir)) {
                deleteRecursively(dir)
            }
        }
    }

    @Test
    fun testPasswordProtectedWhenPasswordSet(): Unit = runBlocking {
        val password = "secret12"
        server = BinkdTestServer(
            serverAddress = addresses.serverAddress,
            nodeAddress = addresses.clientAddress,
            nodePassword = password
        )
        server.start()

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
            onLogString = ::println
        )

        assertTrue(result.success, "Session should succeed: ${result.errorMessage}")
        assertTrue(result.passwordProtected, "Session with password should be marked as password protected")
    }

    @Test
    fun testNotPasswordProtectedWhenNoPassword(): Unit = runBlocking {
        server = BinkdTestServer(
            serverAddress = addresses.serverAddress,
            nodeAddress = addresses.clientAddress,
            nodePassword = null
        )
        server.start()

        val result = binkpClient(
            localAddresses = listOf(addresses.clientAddress),
            localSystemName = "Test Client",
            localSysopName = "Test Sysop",
            localLocation = "Test Location",
            localFlags = "CM,IBN",
            remoteAddress = addresses.serverAddress,
            sessionPassword = null,
            requireCram = false,
            remoteHost = "127.0.0.1",
            remotePort = server.port,
            getFilesToSend = { _, _ -> emptyList() },
            receiveDirectorySecure = receiveDir,
            receiveDirectoryInsecure = receiveDir,
            onLogString = ::println
        )

        assertTrue(result.success, "Session should succeed: ${result.errorMessage}")
        assertFalse(result.passwordProtected, "Session without password should not be marked as password protected")
    }

    @Test
    fun testNotPasswordProtectedOnConnectionFailure(): Unit = runBlocking {
        val nonExistentPort = BinkdTestServer.findFreePort()

        val result = binkpClient(
            localAddresses = listOf(addresses.clientAddress),
            localSystemName = "Test Client",
            localSysopName = "Test Sysop",
            localLocation = "Test Location",
            localFlags = "CM,IBN",
            remoteAddress = addresses.serverAddress,
            sessionPassword = null,
            requireCram = false,
            remoteHost = "127.0.0.1",
            remotePort = nonExistentPort,
            getFilesToSend = { _, _ -> emptyList() },
            receiveDirectorySecure = receiveDir,
            receiveDirectoryInsecure = receiveDir,
            timeout = 5000,
            onLogString = ::println
        )

        assertFalse(result.success, "Session should fail")
        assertFalse(result.passwordProtected, "Failed connection should not be password protected")
    }

    @Test
    fun testFilesReceivedIntoSecureDirectory(): Unit = runBlocking {
        val password = PasswordGenerator.generateRandomPassword()
        server = BinkdTestServer(
            serverAddress = addresses.serverAddress,
            nodeAddress = addresses.clientAddress,
            nodePassword = password
        )
        server.start()

        val testFile = TestFileGenerator.createTextFile("secure-file.txt", "Secure content")
        val outboundFile = server.putFileInOutbound(testFile)
        server.createFloFile(listOf(outboundFile))

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
            receiveDirectorySecure = receiveDirSecure,
            receiveDirectoryInsecure = receiveDirInsecure,
            onLogString = ::println
        )

        assertTrue(result.success, "Session should succeed: ${result.errorMessage}")
        assertTrue(result.passwordProtected, "Session should be password protected")
        assertEquals(1, result.filesReceived.size, "Should have received 1 file")

        // File should be in the secure directory
        val receivedFile = result.filesReceived[0]
        assertTrue(
            receivedFile.toString().startsWith(receiveDirSecure.toString()),
            "Received file should be in secure directory, but was: $receivedFile"
        )

        // Insecure directory should be empty
        val insecureFiles = SystemFileSystem.list(receiveDirInsecure)
        assertTrue(insecureFiles.isEmpty(), "Insecure directory should be empty")
    }

    @Test
    fun testFilesReceivedIntoInsecureDirectory(): Unit = runBlocking {
        server = BinkdTestServer(
            serverAddress = addresses.serverAddress,
            nodeAddress = addresses.clientAddress,
            nodePassword = null
        )
        server.start()

        val testFile = TestFileGenerator.createTextFile("insecure-file.txt", "Insecure content")
        val outboundFile = server.putFileInOutbound(testFile)
        server.createFloFile(listOf(outboundFile))

        val result = binkpClient(
            localAddresses = listOf(addresses.clientAddress),
            localSystemName = "Test Client",
            localSysopName = "Test Sysop",
            localLocation = "Test Location",
            localFlags = "CM,IBN",
            remoteAddress = addresses.serverAddress,
            sessionPassword = null,
            requireCram = false,
            remoteHost = "127.0.0.1",
            remotePort = server.port,
            getFilesToSend = { _, _ -> emptyList() },
            receiveDirectorySecure = receiveDirSecure,
            receiveDirectoryInsecure = receiveDirInsecure,
            onLogString = ::println
        )

        assertTrue(result.success, "Session should succeed: ${result.errorMessage}")
        assertFalse(result.passwordProtected, "Session should not be password protected")
        assertEquals(1, result.filesReceived.size, "Should have received 1 file")

        // File should be in the insecure directory
        val receivedFile = result.filesReceived[0]
        assertTrue(
            receivedFile.toString().startsWith(receiveDirInsecure.toString()),
            "Received file should be in insecure directory, but was: $receivedFile"
        )

        // Secure directory should be empty
        val secureFiles = SystemFileSystem.list(receiveDirSecure)
        assertTrue(secureFiles.isEmpty(), "Secure directory should be empty")
    }

    @Test
    fun testCallbacksReceivePasswordProtectedTrue(): Unit = runBlocking {
        val password = PasswordGenerator.generateRandomPassword()
        server = BinkdTestServer(
            serverAddress = addresses.serverAddress,
            nodeAddress = addresses.clientAddress,
            nodePassword = password
        )
        server.start()

        var getFilesPasswordProtected: Boolean? = null
        var sessionStartedPasswordProtected: Boolean? = null

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
            getFilesToSend = { _, passwordProtected ->
                getFilesPasswordProtected = passwordProtected
                emptyList()
            },
            receiveDirectorySecure = receiveDir,
            receiveDirectoryInsecure = receiveDir,
            onSessionStarted = { _, passwordProtected ->
                sessionStartedPasswordProtected = passwordProtected
            },
            onLogString = ::println
        )

        assertTrue(result.success, "Session should succeed: ${result.errorMessage}")
        assertEquals(true, sessionStartedPasswordProtected, "onSessionStarted should receive passwordProtected=true")
        assertEquals(true, getFilesPasswordProtected, "getFilesToSend should receive passwordProtected=true")
    }

    @Test
    fun testCallbacksReceivePasswordProtectedFalse(): Unit = runBlocking {
        server = BinkdTestServer(
            serverAddress = addresses.serverAddress,
            nodeAddress = addresses.clientAddress,
            nodePassword = null
        )
        server.start()

        var getFilesPasswordProtected: Boolean? = null
        var sessionStartedPasswordProtected: Boolean? = null

        val result = binkpClient(
            localAddresses = listOf(addresses.clientAddress),
            localSystemName = "Test Client",
            localSysopName = "Test Sysop",
            localLocation = "Test Location",
            localFlags = "CM,IBN",
            remoteAddress = addresses.serverAddress,
            sessionPassword = null,
            requireCram = false,
            remoteHost = "127.0.0.1",
            remotePort = server.port,
            getFilesToSend = { _, passwordProtected ->
                getFilesPasswordProtected = passwordProtected
                emptyList()
            },
            receiveDirectorySecure = receiveDir,
            receiveDirectoryInsecure = receiveDir,
            onSessionStarted = { _, passwordProtected ->
                sessionStartedPasswordProtected = passwordProtected
            },
            onLogString = ::println
        )

        assertTrue(result.success, "Session should succeed: ${result.errorMessage}")
        assertEquals(false, sessionStartedPasswordProtected, "onSessionStarted should receive passwordProtected=false")
        assertEquals(false, getFilesPasswordProtected, "getFilesToSend should receive passwordProtected=false")
    }
}

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
