package net.jegor.kmftn.binkpclient

import kotlinx.coroutines.delay
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import net.jegor.kmftn.base.FtnAddr
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Manages a binkd test server instance for integration testing
 */
class BinkdTestServer(
    val port: Int = findFreePort(),
    val serverAddress: FtnAddr,
    val nodeAddress: FtnAddr,
    val nodePassword: String? = null // null or "-" for no password, max 8 chars
) {
    private var process: ProcessHandle? = null
    private val testDir: Path = createTempDirectory(prefix = "binkd-test-")
    private val configFile: Path = Path(testDir.toString(), "binkd.conf")

    val inboundDir: Path = Path(testDir.toString(), "inbound", "secure")
    val inboundNonsecureDir: Path = Path(testDir.toString(), "inbound", "nonsecure")
    val tempInboundDir: Path = Path(testDir.toString(), "inbound", "temp")
    val outboundDir: Path = Path(testDir.toString(), "outbound")
    val logDir: Path = Path(testDir.toString(), "log")
    val logFile: Path = Path(logDir.toString(), "binkd.log")

    private val binkdExecutable: String = "/Users/egor/binkd/binkd"

    init {
        require(nodePassword == null || nodePassword == "-" || nodePassword.length <= 8) {
            "Binkp password must be max 8 characters, got: ${nodePassword?.length}"
        }
    }

    /**
     * Start the binkd server
     */
    suspend fun start() {
        // Create directory structure
        SystemFileSystem.createDirectories(inboundDir, mustCreate = false)
        SystemFileSystem.createDirectories(inboundNonsecureDir, mustCreate = false)
        SystemFileSystem.createDirectories(tempInboundDir, mustCreate = false)
        SystemFileSystem.createDirectories(outboundDir, mustCreate = false)
        SystemFileSystem.createDirectories(logDir, mustCreate = false)

        // Generate config from template
        generateConfig()

        // Start binkd process
        val logOutput = Path(testDir.toString(), "binkd-console.log")

        // Create the log file first
        if (!SystemFileSystem.exists(logOutput)) {
            SystemFileSystem.sink(logOutput).use { }
        }

        process = executeProcess(
            command = binkdExecutable,
            args = listOf(configFile.toString())
        )

        // Wait for binkd to start (check if port is listening)
        waitForStartup()

        println("BinkdTestServer started on port $port")
        println("  Test directory: ${testDir}")
        println("  Config: ${configFile}")
        println("  Log: ${logFile}")
    }

    /**
     * Stop the binkd server
     */
    suspend fun stop() {
        process?.let { proc ->
            try {
                proc.kill()
                proc.waitFor()
                println("BinkdTestServer stopped")
            } catch (e: Exception) {
                println("Error stopping binkd: ${e.message}")
            }
        }
        process = null
    }

    /**
     * Cleanup test directory and all files
     */
    fun cleanup() {
        deleteRecursively(testDir)
        println("BinkdTestServer cleanup completed")
    }

    /**
     * Get files from inbound directory
     */
    fun getInboundFiles(): List<Path> {
        val files = mutableListOf<Path>()

        fun collectFiles(dir: Path) {
            if (!SystemFileSystem.exists(dir)) return

            SystemFileSystem.list(dir).forEach { path ->
                val metadata = SystemFileSystem.metadataOrNull(path)
                if (metadata?.isRegularFile == true) {
                    files.add(path)
                } else if (metadata?.isDirectory == true) {
                    collectFiles(path)
                }
            }
        }

        collectFiles(inboundDir)
        collectFiles(inboundNonsecureDir)

        return files
    }

    /**
     * Put file in outbound for the node (FTS-5005 compliant)
     */
    fun putFileInOutbound(file: Path): Path {
        // Parse node address to create proper outbound structure
        // Format: 10:X/Y.Z@testnet -> outbound/XXXXYYYY.pnt/
        val nodeOutbound = getOutboundPath(nodeAddress)
        SystemFileSystem.createDirectories(nodeOutbound, mustCreate = false)

        val targetFile = Path(nodeOutbound.toString(), file.name)
        copyFile(file, targetFile)

        return targetFile
    }

    /**
     * Create a .flo file to trigger file sending
     * Flavor: 'f' = normal, 'h' = hold, 'd' = direct, 'c' = crash
     */
    fun createFloFile(files: List<Path>, flavor: Char = 'f'): Path {
        val nodeOutbound = getOutboundPath(nodeAddress)
        SystemFileSystem.createDirectories(nodeOutbound, mustCreate = false)

        val point = nodeAddress.point.toInt().and(0xFFFF)
        val floFile = Path(nodeOutbound.toString(), "${point.toString(16).padStart(8, '0')}.${flavor}lo")

        SystemFileSystem.sink(floFile).buffered().use { sink ->
            sink.writeString(files.joinToString("\n") { it.toString() })
        }

        println("Created .flo file for node: ${nodeAddress.toString5D()}")
        println("  Path: ${floFile}")
        println("  Files listed in .flo:")
        files.forEach { println("    ${it}") }
        return floFile
    }

    /**
     * Get outbound path for node address according to FTS-5005
     *
     * Format: 10:X/Y.Z@testnet -> outbound/XXXXYYYY.pnt/
     *
     * Where:
     * - outbound is default outbound for zone 10 (no .00a extension)
     * - XXXXYYYY is hex representation of net:node (4 digits each)
     * - .pnt subdirectory for point systems
     * - Files inside named as ZZZZZZZZ.* (point number as 8 hex digits)
     */
    private fun getOutboundPath(address: FtnAddr): Path {
        val netHex = address.net.toInt().and(0xFFFF).toString(16).padStart(4, '0')
        val nodeHex = address.node.toInt().and(0xFFFF).toString(16).padStart(4, '0')
        val baseFileName = "$netHex$nodeHex"

        return Path(outboundDir.toString(), "$baseFileName.pnt")
    }

    /**
     * Get log contents
     */
    fun getLog(): String {
        return if (SystemFileSystem.exists(logFile)) {
            SystemFileSystem.source(logFile).buffered().use { source ->
                source.readString()
            }
        } else {
            ""
        }
    }

    /**
     * Generate binkd config from template
     */
    private fun generateConfig() {
        val template = """
# Binkd test configuration
# Generated automatically for testing

# Logging
log {{TEST_DIR}}/log/binkd.log
loglevel 6
conlog 6

# Domain configuration
domain testnet {{TEST_DIR}}/outbound {{ZONE}}

# Our address
address {{SERVER_ADDRESS}}

# System information
sysname "Binkd Test Server"
location "Test Location"
sysop "Test Sysop"
nodeinfo 115200,TCP,BINKP

# TCP settings
iport {{PORT}}
timeout 1m
connect-timeout 1m

# Limits
maxservers 1000
maxclients 1000

# Retry settings
try 1
hold 10s

# Display settings
percents
printq

# Inbound directories
inbound {{TEST_DIR}}/inbound/secure
inbound-nonsecure {{TEST_DIR}}/inbound/nonsecure
temp-inbound {{TEST_DIR}}/inbound/temp

# Minimum free space
minfree 1024
minfree-nonsecure 1024

# File handling
kill-dup-partial-files
kill-old-partial-files 1h
kill-old-bsy 1h

# Outbound scanning
prescan

# Node definition
node {{NODE_ADDRESS}} - {{NODE_PASSWORD}}
"""

        val password = when (nodePassword) {
            null, "" -> "-"
            else -> nodePassword
        }

        val config = template
            .replace("{{TEST_DIR}}", testDir.toString())
            .replace("{{PORT}}", port.toString())
            .replace("{{SERVER_ADDRESS}}", serverAddress.toString5D())
            .replace("{{NODE_ADDRESS}}", nodeAddress.toString5D())
            .replace("{{NODE_PASSWORD}}", password)
            .replace("{{ZONE}}", serverAddress.zone.toString())

        SystemFileSystem.sink(configFile).buffered().use { sink ->
            sink.writeString(config)
        }

        println("Generated binkd config: ${configFile}")
        println("  Server address: $serverAddress")
        println("  Client address: ${nodeAddress.toString5D()}")
    }

    /**
     * Wait for binkd to start and begin listening on port
     */
    private suspend fun waitForStartup() {
        val maxAttempts = 30
        val delayMs = 100

        repeat(maxAttempts) { attempt ->
            if (isPortListening(port)) {
                println("binkd is listening on port $port (attempt ${attempt + 1})")
                return
            }
            delay(delayMs.milliseconds)
        }

        throw IllegalStateException("binkd failed to start within ${maxAttempts * delayMs}ms")
    }

    /**
     * Check if port is listening
     */
    private suspend fun isPortListening(port: Int): Boolean {
        return try {
            // Try to connect to the port
            val handle = executeProcess(
                command = "nc",
                args = listOf("-z", "127.0.0.1", port.toString())
            )
            val result = handle.waitFor()

            result == 0
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        /**
         * Find a free port for testing
         */
        fun findFreePort(): Int {
            // Use a random port in the ephemeral range
            return (49152..65535).random()
        }
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
