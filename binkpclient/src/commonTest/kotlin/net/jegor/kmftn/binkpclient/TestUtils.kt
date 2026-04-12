package net.jegor.kmftn.binkpclient

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.io.writeString
import net.jegor.kmftn.base.FtnAddr
import org.kotlincrypto.hash.md.MD5
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * FTN address pair for testing
 */
data class AddressPair(
    val serverAddress: FtnAddr,
    val clientAddress: FtnAddr
)

/**
 * Utilities for generating random passwords
 */
object PasswordGenerator {
    /**
     * Generate a random password with 6-8 uppercase Latin letters and digits
     */
    fun generateRandomPassword(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val length = Random.nextInt(6, 9) // 6 to 8 characters
        return (1..length)
            .map { chars[Random.nextInt(chars.length)] }
            .joinToString("")
    }
}

/**
 * Utilities for generating FTN addresses
 */
object AddressGenerator {
    /**
     * Generate a pair of unique FTN addresses in format 10:X/Y.Z@testnet
     * where X, Y, Z are random numbers from 0 to 32767
     */
    fun generateAddressPair(): AddressPair {
        var serverAddress: FtnAddr
        var clientAddress: FtnAddr

        do {
            serverAddress = FtnAddr(
                zone = 10,
                net = Random.nextInt(0, 32768).toShort(),
                node = Random.nextInt(0, 32768).toShort(),
                point = Random.nextInt(0, 32768).toShort(),
                domain = "testnet"
            )
            clientAddress = FtnAddr(
                zone = 10,
                net = Random.nextInt(0, 32768).toShort(),
                node = Random.nextInt(0, 32768).toShort(),
                point = Random.nextInt(0, 32768).toShort(),
                domain = "testnet"
            )
        } while (serverAddress == clientAddress)

        return AddressPair(serverAddress, clientAddress)
    }
}

/**
 * Utilities for generating test files
 */
object TestFileGenerator {

    /**
     * Create a test file with random content
     */
    fun createFile(name: String, sizeBytes: Long): Path {
        val tempDir = createTempDirectory()
        val file = Path(tempDir.toString(), "test-$name")

        SystemFileSystem.sink(file).buffered().use { sink ->
            val bufferSize = minOf(sizeBytes, 8192).toInt()
            val buffer = ByteArray(bufferSize)
            var remaining = sizeBytes

            while (remaining > 0) {
                val toWrite = minOf(remaining, bufferSize.toLong()).toInt()
                Random.nextBytes(buffer, 0, toWrite)
                sink.write(buffer, 0, toWrite)
                remaining -= toWrite
            }
        }

        return file
    }

    /**
     * Create a file with specific text content
     */
    fun createTextFile(name: String, content: String): Path {
        val tempDir = createTempDirectory()
        val file = Path(tempDir.toString(), "test-$name")

        SystemFileSystem.sink(file).buffered().use { sink ->
            sink.writeString(content)
        }

        return file
    }

    /**
     * Create a simple FTN packet file (for testing)
     */
    fun createPktFile(from: String, to: String): Path {
        val tempDir = createTempDirectory()
        val file = Path(tempDir.toString(), "test.pkt")

        SystemFileSystem.sink(file).buffered().use { sink ->
            // Write minimal PKT 2.2 header (58 bytes)
            val header = ByteArray(58)
            header[0] = 0x02 // origNode low
            header[1] = 0x00 // origNode high
            // ... rest would be proper PKT format, but for testing just some data
            Random.nextBytes(header, 2, 56)
            sink.write(header)

            // Write a message
            val message = "This is a test message from $from to $to".encodeToByteArray()
            sink.write(message)

            // PKT terminator
            sink.writeByte(0)
            sink.writeByte(0)
        }

        return file
    }

    /**
     * Create a file with special characters in name
     */
    fun createFileWithSpecialName(baseName: String, sizeBytes: Long): Path {
        return createFile(baseName, sizeBytes)
    }
}

/**
 * Test assertion helpers
 */
object TestAssertions {

    /**
     * Assert two files have identical content
     */
    fun assertFilesEqual(expected: Path, actual: Path, message: String = "") {
        assertTrue(SystemFileSystem.exists(expected), "Expected file does not exist: ${expected}")
        assertTrue(SystemFileSystem.exists(actual), "Actual file does not exist: ${actual}")

        val expectedSize = SystemFileSystem.metadataOrNull(expected)?.size ?: 0L
        val actualSize = SystemFileSystem.metadataOrNull(actual)?.size ?: 0L

        assertEquals(
            expectedSize,
            actualSize,
            "$message\nFile sizes differ: expected=$expectedSize, actual=$actualSize"
        )

        val expectedChecksum = calculateChecksum(expected)
        val actualChecksum = calculateChecksum(actual)

        assertEquals(
            expectedChecksum,
            actualChecksum,
            "$message\nFile checksums differ"
        )
    }

    /**
     * Assert file exists in directory
     */
    fun assertFileInDirectory(dir: Path, fileName: String): Path {
        assertTrue(SystemFileSystem.exists(dir), "Directory does not exist: ${dir}")

        val file = Path(dir.toString(), fileName)
        assertTrue(SystemFileSystem.exists(file), "File '$fileName' not found in directory: ${dir}")

        return file
    }

    /**
     * Assert file exists in directory tree (recursive)
     */
    fun assertFileInDirectoryTree(dir: Path, fileName: String): Path {
        assertTrue(SystemFileSystem.exists(dir), "Directory does not exist: ${dir}")

        val foundFiles = mutableListOf<Path>()

        fun walkDirectory(currentDir: Path) {
            SystemFileSystem.list(currentDir).forEach { path ->
                val metadata = SystemFileSystem.metadataOrNull(path)
                if (metadata?.isRegularFile == true && path.name == fileName) {
                    foundFiles.add(path)
                } else if (metadata?.isDirectory == true) {
                    walkDirectory(path)
                }
            }
        }

        walkDirectory(dir)

        assertTrue(foundFiles.isNotEmpty(), "File '$fileName' not found in directory tree: ${dir}")

        return foundFiles.first()
    }

    /**
     * Calculate MD5 checksum of file
     */
    private fun calculateChecksum(file: Path): String {
        val md5 = MD5()

        SystemFileSystem.source(file).buffered().use { source ->
            val buffer = ByteArray(8192)
            while (true) {
                val bytesRead = source.readAtMostTo(buffer)
                if (bytesRead == -1) break
                md5.update(buffer, 0, bytesRead)
            }
        }

        return md5.digest().joinToString("") { byte ->
            val ubyte = byte.toInt() and 0xFF
            ubyte.toString(16).padStart(2, '0')
        }
    }
}

/**
 * Extension functions for Path
 */
fun Path.checksum(): String {
    val md5 = MD5()

    SystemFileSystem.source(this).buffered().use { source ->
        val buffer = ByteArray(8192)
        while (true) {
            val bytesRead = source.readAtMostTo(buffer)
            if (bytesRead == -1) break
            md5.update(buffer, 0, bytesRead)
        }
    }

    return md5.digest().joinToString("") { byte ->
        val ubyte = byte.toInt() and 0xFF
        ubyte.toString(16).padStart(2, '0')
    }
}

/**
 * Get file length (size in bytes)
 */
fun Path.fileLength(): Long {
    return SystemFileSystem.metadataOrNull(this)?.size ?: 0L
}

/**
 * Get file name (last component of path)
 */
fun Path.fileName(): String {
    return this.name
}

/**
 * Create a temporary directory
 */
expect fun createTempDirectory(prefix: String = "tmp"): Path
