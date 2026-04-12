package net.jegor.kmftn.binkpclient

import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlin.random.Random

/**
 * Native implementation of createTempDirectory
 */
actual fun createTempDirectory(prefix: String): Path {
    // Use /tmp on macOS/Linux, system temp on Windows
    val tempBase = Path("/tmp")

    // Generate random suffix
    val randomSuffix = Random.nextInt(100000, 999999)
    val dirName = "$prefix-$randomSuffix"
    val tempDir = Path(tempBase.toString(), dirName)

    // Create the directory
    SystemFileSystem.createDirectories(tempDir, mustCreate = true)

    return tempDir
}
