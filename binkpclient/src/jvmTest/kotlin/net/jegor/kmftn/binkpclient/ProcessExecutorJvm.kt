package net.jegor.kmftn.binkpclient

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path
import java.io.File

actual class ProcessHandle(private val process: Process) {
    actual suspend fun waitFor(): Int = withContext(Dispatchers.IO) {
        process.waitFor()
    }

    actual suspend fun kill() {
        process.destroy()
    }

    actual fun isRunning(): Boolean = process.isAlive
}

actual fun executeProcess(
    command: String,
    args: List<String>,
    workingDirectory: Path?,
    environment: Map<String, String>
): ProcessHandle {
    val builder = ProcessBuilder(listOf(command) + args)

    if (workingDirectory != null) {
        builder.directory(File(workingDirectory.toString()))
    }

    if (environment.isNotEmpty()) {
        builder.environment().putAll(environment)
    }

    val process = builder.start()
    return ProcessHandle(process)
}
