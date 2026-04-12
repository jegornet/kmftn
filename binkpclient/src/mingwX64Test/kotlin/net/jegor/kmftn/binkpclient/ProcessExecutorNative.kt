package net.jegor.kmftn.binkpclient

import kotlinx.cinterop.*
import kotlinx.coroutines.delay
import kotlinx.io.files.Path
import platform.posix.*
import platform.windows.*

@OptIn(ExperimentalForeignApi::class)
actual class ProcessHandle(
    private val hProcess: HANDLE?,
    private val hThread: HANDLE?
) {
    private var exitCode: Int? = null

    actual suspend fun waitFor(): Int {
        if (exitCode != null) return exitCode!!

        WaitForSingleObject(hProcess, INFINITE)
        memScoped {
            val code = alloc<DWORDVar>()
            GetExitCodeProcess(hProcess, code.ptr)
            exitCode = code.value.toInt()
        }
        CloseHandle(hProcess)
        CloseHandle(hThread)
        return exitCode!!
    }

    actual suspend fun kill() {
        TerminateProcess(hProcess, 1u)
        CloseHandle(hProcess)
        CloseHandle(hThread)
    }

    actual fun isRunning(): Boolean {
        memScoped {
            val code = alloc<DWORDVar>()
            GetExitCodeProcess(hProcess, code.ptr)
            return code.value == STILL_ACTIVE
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
actual fun executeProcess(
    command: String,
    args: List<String>,
    workingDirectory: Path?,
    environment: Map<String, String>
): ProcessHandle {
    memScoped {
        val cmdLine = (listOf(command) + args).joinToString(" ") {
            if (it.contains(" ")) "\"$it\"" else it
        }

        // Set environment variables
        environment.forEach { (key, value) ->
            SetEnvironmentVariableW(key, value)
        }

        val si = alloc<STARTUPINFOW>()
        si.cb = sizeOf<STARTUPINFOW>().toUInt()
        val pi = alloc<PROCESS_INFORMATION>()

        val result = CreateProcessW(
            null,
            cmdLine.wcstr.ptr,
            null,
            null,
            FALSE,
            0u,
            null,
            workingDirectory?.toString(),
            si.ptr,
            pi.ptr
        )

        if (result == FALSE) {
            error("Failed to create process: $cmdLine")
        }

        return ProcessHandle(pi.hProcess, pi.hThread)
    }
}
