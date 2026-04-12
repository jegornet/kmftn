package net.jegor.kmftn.binkpclient

import kotlinx.io.files.Path

/**
 * Platform-specific process execution
 */
expect class ProcessHandle {
    suspend fun waitFor(): Int
    suspend fun kill()
    fun isRunning(): Boolean
}

expect fun executeProcess(
    command: String,
    args: List<String> = emptyList(),
    workingDirectory: Path? = null,
    environment: Map<String, String> = emptyMap()
): ProcessHandle
