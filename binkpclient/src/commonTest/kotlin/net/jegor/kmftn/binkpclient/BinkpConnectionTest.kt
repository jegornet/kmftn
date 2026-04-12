package net.jegor.kmftn.binkpclient

import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import net.jegor.kmftn.base.FtnAddr
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration tests for basic Binkp connection scenarios
 * Tests connection establishment, password authentication, and error handling
 */
class BinkpConnectionTest {

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
    fun testConnectWithoutPassword(): Unit = runBlocking {
        // Arrange
        server = BinkdTestServer(
            serverAddress = addresses.serverAddress,
            nodeAddress = addresses.clientAddress,
            nodePassword = null // no password
        )
        server.start()

        // Act
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

        // Assert
        println("\n=== Test Result ===")
        println("Success: ${result.success}")
        println("Remote addresses: ${result.remoteAddresses}")
        println("Error: ${result.errorMessage}")

        assertTrue(result.success, "Session should succeed")
        assertNull(result.errorMessage, "Should not have error message")
        assertTrue(
            result.remoteAddresses.any { it == addresses.serverAddress },
            "Should receive remote address ${addresses.serverAddress}"
        )
    }

    @Test
    fun testConnectWithPassword(): Unit = runBlocking {
        // Arrange
        val password = "test123" // max 8 chars
        server = BinkdTestServer(
            serverAddress = addresses.serverAddress,
            nodeAddress = addresses.clientAddress,
            nodePassword = password
        )
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
            getFilesToSend = { _, _ -> emptyList() },
            receiveDirectorySecure = receiveDir,
            receiveDirectoryInsecure = receiveDir,
            onLogString = ::println
        )

        // Assert
        println("\n=== Test Result ===")
        println("Success: ${result.success}")
        println("Remote addresses: ${result.remoteAddresses}")
        println("Error: ${result.errorMessage}")

        assertTrue(result.success, "Session should succeed with correct password")
        assertNull(result.errorMessage, "Should not have error message")
    }

    @Test
    fun testRejectIncorrectPassword(): Unit = runBlocking {
        // Arrange
        server = BinkdTestServer(
            serverAddress = addresses.serverAddress,
            nodeAddress = addresses.clientAddress,
            nodePassword = "correct1" // max 8 chars
        )
        server.start()

        // Act
        val result = binkpClient(
            localAddresses = listOf(addresses.clientAddress),
            localSystemName = "Test Client",
            localSysopName = "Test Sysop",
            localLocation = "Test Location",
            localFlags = "CM,IBN",
            remoteAddress = addresses.serverAddress,
            sessionPassword = "wrong123", // incorrect password
            remoteHost = "127.0.0.1",
            remotePort = server.port,
            getFilesToSend = { _, _ -> emptyList() },
            receiveDirectorySecure = receiveDir,
            receiveDirectoryInsecure = receiveDir,
            onLogString = ::println
        )

        // Assert
        println("\n=== Test Result ===")
        println("Success: ${result.success}")
        println("Error: ${result.errorMessage}")

        assertTrue(!result.success, "Session should fail with incorrect password")
        assertNotNull(result.errorMessage, "Should have error message")
    }

    @Test
    fun testConnectionRefused(): Unit = runBlocking {
        // Arrange - no server started
        val nonExistentPort = BinkdTestServer.findFreePort()

        // Act
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
            timeout = 5000, // shorter timeout
            onLogString = ::println
        )

        // Assert
        println("\n=== Test Result ===")
        println("Success: ${result.success}")
        println("Error: ${result.errorMessage}")

        assertTrue(!result.success, "Session should fail when connection refused")
        assertNotNull(result.errorMessage, "Should have error message about connection failure")
    }

    @Test
    fun testOnSessionStartedCallback(): Unit = runBlocking {
        // Arrange
        val password = PasswordGenerator.generateRandomPassword()
        server = BinkdTestServer(
            serverAddress = addresses.serverAddress,
            nodeAddress = addresses.clientAddress,
            nodePassword = password
        )
        server.start()

        val callLog = mutableListOf<String>()
        var sessionStartedAddresses: List<FtnAddr>? = null

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
            getFilesToSend = { addrs, _ ->
                callLog.add("getFilesToSend")
                emptyList()
            },
            receiveDirectorySecure = receiveDir,
            receiveDirectoryInsecure = receiveDir,
            onSessionStarted = { addrs, _ ->
                callLog.add("onSessionStarted")
                sessionStartedAddresses = addrs
            },
            onLogString = ::println
        )

        // Assert
        assertTrue(result.success, "Session should succeed: ${result.errorMessage}")

        // onSessionStarted was called with correct addresses
        assertNotNull(sessionStartedAddresses, "onSessionStarted should have been called")
        assertTrue(
            sessionStartedAddresses!!.any { it == addresses.serverAddress },
            "onSessionStarted should receive remote address ${addresses.serverAddress}"
        )

        // getFilesToSend is called once for TRF estimate, then onSessionStarted, then getFilesToSend again
        assertEquals(
            listOf("getFilesToSend", "onSessionStarted", "getFilesToSend"),
            callLog,
            "onSessionStarted must be called before the second getFilesToSend (after address exchange)"
        )
    }

    @Test
    fun testSystemInformationExchange(): Unit = runBlocking {
        // Arrange
        server = BinkdTestServer(
            serverAddress = addresses.serverAddress,
            nodeAddress = addresses.clientAddress,
            nodePassword = "-"
        )
        server.start()

        val systemName = "My Test BBS"
        val sysopName = "John Doe"
        val location = "Belgrade, Serbia"

        // Act
        val result = binkpClient(
            localAddresses = listOf(addresses.clientAddress),
            localSystemName = systemName,
            localSysopName = sysopName,
            localLocation = location,
            localFlags = "CM,IBN,IFC",
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

        // Assert
        assertTrue(result.success, "Session should succeed")

        // Check that our system info was logged by binkd
        val log = server.getLog()
        println("\n=== Binkd Log ===")
        println(log)

        // binkd should log received M_NUL frames with our system info
        assertTrue(log.contains("SYS") || log.contains("ZYZ") || log.contains("VER"),
            "Binkd log should contain our system information")
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
