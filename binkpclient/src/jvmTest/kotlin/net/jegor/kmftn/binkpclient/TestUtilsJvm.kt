package net.jegor.kmftn.binkpclient

import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

/**
 * JVM implementation of createTempDirectory
 */
actual fun createTempDirectory(prefix: String): Path {
    val tmpDir = Path(System.getProperty("java.io.tmpdir") ?: "/tmp")
    val dirName = "$prefix-${kotlin.random.Random.nextInt(100000, 999999)}"
    val tempPath = Path(tmpDir.toString(), dirName)
    SystemFileSystem.createDirectories(tempPath, mustCreate = true)
    return tempPath
}
