package net.jegor.kmftn.binkpclient

import kotlinx.coroutines.*
import kotlinx.io.files.Path
import net.jegor.kmftn.base.FtnAddr

/**
 * Binkp/1.0 Protocol Client Implementation
 * Based on FTS-1026.001 specification
 *
 * Implements a Fidonet Technology Network (FTN) session layer protocol
 * for reliable connections (TCP/IP).
 */

/**
 * Establishes a binkp client session with a remote FTN system and performs file transfer.
 *
 * @param localAddresses FTN addresses of the calling (local) system (e.g., listOf("2:5020/9999@fidonet"))
 * @param localSystemName Name of the calling system
 * @param localSysopName Name of the sysop of the calling system
 * @param localLocation Location of the calling system
 * @param localFlags Nodelist flags of the calling system
 * @param remoteAddress FTN address of the called (remote) system
 * @param sessionPassword Password for the binkp session (null for password-less session)
 * @param remoteHost Internet hostname or IP address of the remote system
 * @param remotePort Port number (default 24554 as per IANA registration)
 * @param getFilesToSend Function that takes remote FTN addresses and password-protected flag, returns list of files to send
 * @param receiveDirectorySecure Directory to save received files for password-protected sessions
 * @param receiveDirectoryInsecure Directory to save received files for sessions without a password
 * @param onSessionStarted Callback invoked after session setup with remote addresses (e.g., to mark them as busy)
 * @param enableResume Enable file resume/continuation support using M_GET (default false).
 *                     WARNING: Enabling this is potentially dangerous as the client currently does not
 *                     track whether a file has been modified between sessions. Attempting to resume
 *                     a file that was updated on the remote side may result in corrupted data!
 * @param requireCram Require CRAM authentication as per FTS-1027.001 (default false)
 * @param timeout Connection and read timeout in milliseconds (default 120000 = 2 minutes)
 *
 * @return BinkpSessionResult containing session outcome and file transfer results
 */
public suspend fun binkpClient(
    localAddresses: List<FtnAddr>,
    localSystemName: String,
    localSysopName: String,
    localLocation: String,
    localFlags: String,
    remoteAddress: FtnAddr,
    sessionPassword: String?,
    remoteHost: String,
    remotePort: Int = 24554,
    getFilesToSend: (List<FtnAddr>, Boolean) -> List<Path>,
    receiveDirectorySecure: Path,
    receiveDirectoryInsecure: Path,
    onSessionStarted: (List<FtnAddr>, Boolean) -> Unit = { _, _ -> },
    enableResume: Boolean = false,
    requireCram: Boolean = false,
    timeout: Int = 120000,
    onLogString: (String) -> Unit,
): BinkpSessionResult = coroutineScope {

    // Validate inputs
    require(remoteHost.isNotBlank()) { "Remote host must not be blank" }
    require(remotePort in 1..65535) { "Port must be between 1 and 65535" }
    require(!requireCram || sessionPassword != null) { "requireCram needs sessionPassword" }

    // Select receive directory based on password protection
    val passwordProtected = sessionPassword != null && sessionPassword != "-"
    val receiveDirectory = if (passwordProtected) receiveDirectorySecure else receiveDirectoryInsecure

    // Ensure receive directory exists
    val fs = FileSystemHelper.fs
    if (!fs.exists(receiveDirectory)) {
        fs.createDirectories(receiveDirectory)
    }

    onLogString("+ Starting Binkp session to $remoteHost:$remotePort")
    onLogString("+ Local: ${localAddresses.joinToString(" ") { it.toString5D() }}, Remote: $remoteAddress")

    try {
        // Establish connection
        onLogString("+ Connecting to $remoteHost:$remotePort...")
        val connection = connectToBinkpServer(remoteHost, remotePort, timeout)
        onLogString("+ Connected successfully")

        try {
            val frameIO = BinkpFrameIO(connection.input, connection.output)

            val session = BinkpSessionHandler(
                frameIO = frameIO,
                localAddresses = localAddresses,
                localSystemName = localSystemName,
                localSysopName = localSysopName,
                localLocation = localLocation,
                localFlags = localFlags,
                remoteAddress = remoteAddress,
                sessionPassword = sessionPassword,
                getFilesToSend = getFilesToSend,
                receiveDirectory = receiveDirectory,
                enableResume = enableResume,
                requireCram = requireCram,
                onSessionStarted = onSessionStarted,
                onLogString = onLogString
            )

            return@coroutineScope session.run()

        } finally {
            connection.close()
            onLogString("+ Connection closed")
        }

    } catch (e: Exception) {
        onLogString("- Connection failed: ${e.message}")
        return@coroutineScope BinkpSessionResult(
            success = false,
            remoteAddresses = emptyList(),
            filesReceived = emptyList(),
            filesSent = emptyList(),
            passwordProtected = false,
            errorMessage = "Connection failed: ${e.message}"
        )
    }
}
