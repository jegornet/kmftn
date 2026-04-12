package net.jegor.kmftn.binkpclient

import kotlinx.cinterop.*
import kotlinx.coroutines.delay
import kotlinx.io.files.Path
import platform.posix.*

// POSIX macros implementations for Kotlin/Native
@OptIn(ExperimentalForeignApi::class)
private fun WIFEXITED(status: Int): Boolean = ((status and 0x7F) == 0)

@OptIn(ExperimentalForeignApi::class)
private fun WEXITSTATUS(status: Int): Int = ((status and 0xFF00) shr 8)

@OptIn(ExperimentalForeignApi::class)
actual class ProcessHandle(private val pid: Int) {
    private var exitCode: Int? = null

    actual suspend fun waitFor(): Int {
        if (exitCode != null) return exitCode!!

        memScoped {
            val status = alloc<IntVar>()
            while (true) {
                val result = waitpid(pid, status.ptr, WNOHANG)
                when {
                    result == pid -> {
                        exitCode = if (WIFEXITED(status.value)) {
                            WEXITSTATUS(status.value)
                        } else {
                            -1
                        }
                        return exitCode!!
                    }
                    result == -1 -> {
                        exitCode = -1
                        return exitCode!!
                    }
                    else -> delay(100) // Poll every 100ms
                }
            }
        }
    }

    actual suspend fun kill() {
        kill(pid, SIGTERM)
    }

    actual fun isRunning(): Boolean {
        return kill(pid, 0) == 0
    }
}

@OptIn(ExperimentalForeignApi::class)
actual fun executeProcess(
    command: String,
    args: List<String>,
    workingDirectory: Path?,
    environment: Map<String, String>
): ProcessHandle {
    val pid = fork()

    when {
        pid < 0 -> error("Failed to fork process")
        pid == 0 -> {
            // Child process
            if (workingDirectory != null) {
                chdir(workingDirectory.toString())
            }

            // Set environment variables
            environment.forEach { (key, value) ->
                setenv(key, value, 1)
            }

            // Build argv
            memScoped {
                val argv = allocArray<CPointerVar<ByteVar>>(args.size + 2)
                argv[0] = command.cstr.ptr
                args.forEachIndexed { index, arg ->
                    argv[index + 1] = arg.cstr.ptr
                }
                argv[args.size + 1] = null

                execvp(command, argv)
                _exit(127) // If exec fails
            }
            error("execvp failed") // This should never be reached
        }
        else -> {
            // Parent process
            return ProcessHandle(pid)
        }
    }
}
