package net.jegor.kmftn.binkpexample

import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import net.jegor.kmftn.base.FtnAddr
import net.jegor.kmftn.binkpclient.binkpClient

// ============================================================================
// Example Usage
// ============================================================================

private fun printUsage() {
    println("Usage: binkpclient [options]")
    println()
    println("Required options:")
    println("  --local-addr <addr>      Local FTN address (e.g. 2:382/9999)")
    println("  --remote-addr <addr>     Remote FTN address (e.g. 2:382/999)")
    println("  --remote-host <host>     Remote host to connect to")
    println("  --inbound-dir <path>     Directory for received files")
    println()
    println("Additional options:")
    println("  --system-name <name>     Local system name (default: My BBS)")
    println("  --sysop-name <name>      Sysop name (default: John Doe)")
    println("  --location <location>    Location string (default: Planet Earth)")
    println("  --flags <flags>          Node flags (default: CM,IBN)")
    println("  --password <pass>        Session password")
    println("  --remote-port <port>     Remote port (default: 24554)")
    println("  --send-file <path>       File to send")
}

internal fun main(args: Array<String>) = runBlocking {
    if (args.isEmpty()) {
        printUsage()
        return@runBlocking
    }

    val parsed = parseArgs(args)

    fun required(name: String): String =
        parsed[name]?.firstOrNull() ?: error("Required parameter --$name is missing")

    fun optional(name: String, default: String): String =
        parsed[name]?.firstOrNull() ?: default

    val localAddr = required("local-addr")
    val systemName = optional("system-name", "My BBS")
    val sysopName = optional("sysop-name", "John Doe")
    val location = optional("location", "Planet Earth")
    val flags = optional("flags", "CM,IBN")
    val remoteAddr = required("remote-addr")
    val password = parsed["password"]?.firstOrNull()
    val remoteHost = required("remote-host")
    val remotePort = optional("remote-port", "24554").toInt()
    val sendFile = parsed["send-file"]?.firstOrNull()
    val inboundDir = required("inbound-dir")

    val result = binkpClient(
        localAddresses = listOf(FtnAddr.fromString(localAddr)),
        localSystemName = systemName,
        localSysopName = sysopName,
        localLocation = location,
        localFlags = flags,
        remoteAddress = FtnAddr.fromString(remoteAddr),
        sessionPassword = password,
        remoteHost = remoteHost,
        remotePort = remotePort,
        getFilesToSend = { _, _ ->
            if (sendFile != null) listOf(Path(sendFile)) else emptyList()
        },
        receiveDirectorySecure = Path(inboundDir),
        receiveDirectoryInsecure = Path(inboundDir),
        timeout = 120000,
        onLogString = ::println
    )

    println("Session result:")
    println("  Success: ${result.success}")
    println("  Remote addresses: ${result.remoteAddresses}")
    println("  Files received: ${result.filesReceived.map { it.name }}")
    println("  Files sent: ${result.filesSent.map { it.name }}")
    if (result.errorMessage != null) {
        println("  Error: ${result.errorMessage}")
    }
}

private fun parseArgs(args: Array<String>): Map<String, List<String>> {
    val result = mutableMapOf<String, MutableList<String>>()
    var i = 0
    while (i < args.size) {
        val arg = args[i]
        if (arg.startsWith("--")) {
            val key = arg.removePrefix("--")
            val value = args.getOrNull(i + 1)
            if (value != null && !value.startsWith("--")) {
                result.getOrPut(key) { mutableListOf() }.add(value)
                i += 2
            } else {
                result.getOrPut(key) { mutableListOf() }
                i++
            }
        } else {
            i++
        }
    }
    return result
}
