package net.jegor.kmftn.binkpclient

import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.FileSystem
import kotlinx.io.RawSource
import kotlinx.io.RawSink

/**
 * Helper functions for file operations using kotlinx-io
 */
internal object FileSystemHelper {
    val fs: FileSystem = SystemFileSystem

    /**
     * Get file size
     */
    fun fileSize(path: Path): Long {
        return fs.metadataOrNull(path)?.size ?: 0L
    }

    /**
     * Check if file exists
     */
    fun exists(path: Path): Boolean {
        return fs.exists(path)
    }

    /**
     * Get last modified time in Unix timestamp (seconds)
     */
    fun lastModifiedTime(path: Path): Long {
        return actualLastModifiedTime(path)
    }

    /**
     * Open file for reading
     */
    fun openForReading(path: Path): RawSource {
        return fs.source(path)
    }

    /**
     * Open file for writing (create or overwrite)
     */
    fun openForWriting(path: Path): RawSink {
        return fs.sink(path)
    }

    /**
     * Open file for appending
     */
    fun openForAppending(path: Path): RawSink {
        return fs.sink(path, append = true)
    }

    /**
     * Delete file
     */
    fun delete(path: Path) {
        fs.delete(path)
    }

    /**
     * Get file name from path
     */
    fun fileName(path: Path): String {
        return path.name
    }

    /**
     * Create path from parent and child
     */
    fun resolve(parent: Path, child: String): Path {
        return Path(parent, child)
    }
}

/**
 * Platform-specific implementation for getting last modified time
 * Returns Unix timestamp in seconds
 */
internal expect fun actualLastModifiedTime(path: Path): Long
