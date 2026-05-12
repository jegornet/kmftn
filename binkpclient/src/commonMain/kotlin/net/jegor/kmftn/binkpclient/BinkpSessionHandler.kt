package net.jegor.kmftn.binkpclient

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.files.Path
import kotlinx.io.RawSource
import kotlinx.io.RawSink
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlinx.io.files.SystemFileSystem
import net.jegor.kmftn.base.FtnAddr
import net.jegor.kmftn.bso.BsoOutbound

/**
 * Handles the binkp session protocol state machine
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class BinkpSessionHandler(
    private val frameIO: BinkpFrameIO,
    private val localAddresses: List<FtnAddr>,
    private val localSystemName: String,
    private val localSysopName: String,
    private val localLocation: String,
    private val localFlags: String,
    private val remoteAddress: FtnAddr,
    private val sessionPassword: String?,
    private val outbound: BsoOutbound,
    private val receiveDirectory: Path,
    private val enableResume: Boolean,
    private val requireCram: Boolean,
    private val onSessionStarted: (List<FtnAddr>, Boolean) -> Unit,
    private val onLogString: (String) -> Unit
) {
    // Session state
    private val remoteAddresses = mutableListOf<FtnAddr>()
    private val filesReceived = mutableListOf<Path>()
    private val filesSent = mutableListOf<Path>()
    private var errorMessage: String? = null

    // CRAM authentication state
    private var cramHashAlgorithms: List<CramHashAlgorithm>? = null
    private var cramChallengeData: ByteArray? = null

    // File transfer state
    private var outgoingQueue = ArrayDeque<Path>()
    private val pendingFiles = mutableMapOf<String, Path>()
    private val theQueue = Channel<BinkpFrame>(Channel.UNLIMITED)

    // Current receiving file
    private var currentRxFile: FileInfo? = null
    private var currentRxSink: RawSink? = null
    private var currentRxBuffer: Buffer? = null
    private var currentRxBytesReceived: Long = 0
    private var waitingForResumeConfirmation = false

    // Current transmitting file
    private var currentTxFile: Path? = null
    private var currentTxInfo: FileInfo? = null
    private var currentTxSource: RawSource? = null
    private var currentTxBuffer: Buffer? = null

    // State machine states
    private var rxState = RxState.RxWaitF
    private var txState = TxState.TxGNF

    // Session flags
    private var sessionEnded = false
    private var remoteEOBReceived = false
    private var localEOBSent = false

    // Mutex for frame sending
    private val sendMutex = Mutex()

    /**
     * Runs the binkp session
     */
    suspend fun run(): BinkpSessionResult {
        return try {
            onLogString("+ Starting session setup")
            // Phase 1: Session Setup (Originating Side - Table 1)
            if (!sessionSetup()) {
                onLogString("- Session setup failed: ${errorMessage ?: "Unknown error"}")
                return BinkpSessionResult(
                    success = false,
                    remoteAddresses = remoteAddresses,
                    filesReceived = filesReceived,
                    filesSent = filesSent,
                    passwordProtected = sessionPassword != null && sessionPassword != "-",
                    errorMessage = errorMessage ?: "Session setup failed"
                )
            }
            onLogString("+ Session setup completed successfully")

            // Phase 2: File Transfer (Table 3)
            onLogString("+ Starting file transfer phase")
            fileTransfer()

            if (errorMessage == null) {
                onLogString("+ Session completed successfully")
                onLogString("+ Files sent: ${filesSent.size}, Files received: ${filesReceived.size}")
                
                // Delete BSO flow files for successfully processed addresses
                remoteAddresses.forEach { addr ->
                    outbound.deleteFlowFiles(addr)
                }
            } else {
                onLogString("- Session completed with error: $errorMessage")
            }

            BinkpSessionResult(
                success = errorMessage == null,
                remoteAddresses = remoteAddresses,
                filesReceived = filesReceived,
                filesSent = filesSent,
                passwordProtected = sessionPassword != null && sessionPassword != "-",
                errorMessage = errorMessage
            )

        } catch (e: Exception) {
            onLogString("- Session error: ${e.message}")
            errorMessage = e.message
            BinkpSessionResult(
                success = false,
                remoteAddresses = remoteAddresses,
                filesReceived = filesReceived,
                filesSent = filesSent,
                passwordProtected = sessionPassword != null && sessionPassword != "-",
                errorMessage = "Session error: ${e.message}"
            )
        } finally {
            cleanup()
        }
    }

    /**
     * Session setup for originating side (Table 1 in FTS-1026.001)
     * Extended with CRAM support as per FTS-1027.001
     */
    private suspend fun sessionSetup(): Boolean {
        // S1: WaitConn - Connection established, send M_NUL frames with system info
        onLogString("+ Sending system information")
        sendSystemInfo()

        // Send M_ADR with our addresses
        val localAddrStr = localAddresses.joinToString(" ") { it.toString5D() }
        onLogString("< M_ADR $localAddrStr")
        sendCommand(BinkpCommand.M_ADR, localAddrStr)

        // S3: WaitAddr - Wait for M_ADR from remote (and possibly CRAM challenge in M_NUL)
        var gotRemoteAddr = false
        var passwordSent = false
        var gotOk = false

        onLogString("+ Waiting for remote address")
        while (!gotRemoteAddr || !passwordSent || !gotOk) {
            val frame = frameIO.readFrame() ?: return false

            if (!frame.isCommand) {
                continue // Ignore data frames during setup
            }

            when (frame.commandId) {
                BinkpCommand.M_NUL -> {
                    // Parse M_NUL for CRAM challenge
                    parseNulFrame(frame.argument)
                }
                BinkpCommand.M_ADR -> {
                    // Parse remote addresses
                    remoteAddresses.clear()
                    remoteAddresses.addAll(
                        frame.argument.split(" ")
                            .filter { it.isNotBlank() }
                            .map { FtnAddr.fromString(it) }
                    )
                    onLogString("> M_ADR ${remoteAddresses.joinToString(" ") { it.toString5D() }}")

                    // Check if remote presented the address we called
                    if (!validateRemoteAddress()) {
                        onLogString("- Address validation failed")
                        sendCommand(BinkpCommand.M_ERR, "Bad address")
                        errorMessage = "Remote did not present expected address"
                        return false
                    }
                    onLogString("+ Address validated successfully")
                    gotRemoteAddr = true

                    // Notify caller about session start
                    val passwordProtected = sessionPassword != null && sessionPassword != "-"
                    onSessionStarted(remoteAddresses, passwordProtected)

                    // Now that we know remote addresses, get files to send from BSO
                    val filesToSend = mutableListOf<Path>()
                    remoteAddresses.forEach { addr ->
                        val link = outbound.getLink(addr)
                        link.netmail?.let { filesToSend.add(it.path) }
                        link.references.forEach { filesToSend.add(it.path) }
                    }
                    
                    val fs = FileSystemHelper.fs
                    val validFiles = filesToSend.filter { fs.exists(it) }
                    outgoingQueue = ArrayDeque(validFiles)
                    onLogString("+ Files to send: ${validFiles.size}")

                    // S2: SendPasswd - Now that we have the remote address (and possibly CRAM challenge),
                    // send the password
                    if (!passwordSent) {
                        sendPassword()
                        passwordSent = true
                    }
                }
                BinkpCommand.M_OK -> {
                    // Password accepted
                    onLogString("> M_OK - Password accepted")
                    gotOk = true
                }
                BinkpCommand.M_ERR -> {
                    onLogString("> M_ERR ${frame.argument}")
                    errorMessage = "Remote error: ${frame.argument}"
                    return false
                }
                BinkpCommand.M_BSY -> {
                    onLogString("> M_BSY ${frame.argument}")
                    errorMessage = "Remote busy: ${frame.argument}"
                    return false
                }
                else -> {
                    onLogString("> Unexpected frame ${frame.commandId}")
                    // Ignore unknown frames during setup
                }
            }
        }

        return true
    }

    /**
     * Send password using CRAM if available, otherwise plain text
     */
    private suspend fun sendPassword() {
        val password = sessionPassword ?: "-"

        // Check if we have CRAM challenge and password
        if (cramHashAlgorithms != null && cramChallengeData != null && password != "-") {
            // Select the first supported hash algorithm (most preferred)
            val selectedAlgorithm = cramHashAlgorithms!!.firstOrNull()

            if (selectedAlgorithm != null) {
                try {
                    // Generate CRAM digest
                    val digest = generateCramDigest(password, cramChallengeData!!, selectedAlgorithm)
                    val cramResponse = "CRAM-${selectedAlgorithm.alias}-$digest"

                    onLogString("+ Using CRAM-${selectedAlgorithm.alias} authentication")
                    onLogString("< M_PWD CRAM-${selectedAlgorithm.alias}-***")
                    sendCommand(BinkpCommand.M_PWD, cramResponse)
                    return
                } catch (e: Exception) {
                    onLogString("- CRAM digest generation failed: ${e.message}")
                    if (requireCram) {
                        errorMessage = "CRAM authentication required but failed: ${e.message}"
                        sendCommand(BinkpCommand.M_ERR, "CRAM authentication failed")
                        return
                    }
                    // Fall through to plain password
                }
            } else {
                onLogString("- No compatible CRAM hash algorithm found")
                if (requireCram) {
                    errorMessage = "CRAM authentication required, no common hash function"
                    sendCommand(BinkpCommand.M_ERR, "CRAM authentication required, no common hash function")
                    return
                }
                // Fall through to plain password
            }
        } else if (requireCram) {
            // CRAM required but not offered by remote
            errorMessage = "CRAM authentication required but not offered by remote"
            onLogString("- CRAM authentication required but remote doesn't support it")
            sendCommand(BinkpCommand.M_ERR, "You must support CRAM authentication")
            return
        }

        // Fall back to plain password
        onLogString("+ Using plain password authentication")
        onLogString("< M_PWD ${if (password == "-") "(no password)" else "***"}")
        sendCommand(BinkpCommand.M_PWD, password)
    }

    /**
     * Send system information M_NUL frames
     */
    private suspend fun sendSystemInfo() {
        sendCommand(BinkpCommand.M_NUL, "SYS $localSystemName")
        sendCommand(BinkpCommand.M_NUL, "ZYZ $localSysopName")
        sendCommand(BinkpCommand.M_NUL, "LOC $localLocation")
        sendCommand(BinkpCommand.M_NUL, "NDL $localFlags")
        sendCommand(BinkpCommand.M_NUL, "VER kmftn/${BuildConfig.VERSION_NAME} binkp/1.0")

        // Send current time
        val timeStr = DateTimeHelper.formatBinkpTime()
        sendCommand(BinkpCommand.M_NUL, "TIME $timeStr")

        // Traffic prognosis based on called remoteAddress
        val link = outbound.getLink(remoteAddress)
        val estimatedFiles = mutableListOf<Path>()
        link.netmail?.let { estimatedFiles.add(it.path) }
        link.references.forEach { estimatedFiles.add(it.path) }
        
        val pktBytes = estimatedFiles.filter { FileSystemHelper.fileName(it).endsWith(".pkt", ignoreCase = true) }
            .sumOf { FileSystemHelper.fileSize(it) }
        val otherBytes = estimatedFiles.filter { !FileSystemHelper.fileName(it).endsWith(".pkt", ignoreCase = true) }
            .sumOf { FileSystemHelper.fileSize(it) }
        sendCommand(BinkpCommand.M_NUL, "TRF $pktBytes $otherBytes")
    }

    /**
     * Parse M_NUL frame content
     * Looks for CRAM challenge in OPT frames
     */
    private fun parseNulFrame(content: String) {
        // Log M_NUL content
        onLogString("> M_NUL $content")

        // Check if this is an OPT frame with CRAM
        if (content.startsWith("OPT ", ignoreCase = true)) {
            val cramData = parseCramChallenge(content)
            if (cramData != null) {
                cramHashAlgorithms = cramData.first
                cramChallengeData = cramData.second
                onLogString("+ CRAM challenge received: algorithms=${cramHashAlgorithms?.joinToString("/") { it.alias }}, challenge size=${cramChallengeData?.size} bytes")
            }
        }
    }

    /**
     * Validate that remote presented the expected address.
     * Uses FtnAddr.equals() which compares zone/net/node/point (ignoring domain).
     */
    private fun validateRemoteAddress(): Boolean {
        return remoteAddresses.any { it == remoteAddress }
    }

    /**
     * File transfer stage (Table 3 in FTS-1026.001)
     */
    private suspend fun fileTransfer() {
        rxState = RxState.RxWaitF
        txState = TxState.TxGNF

        while (rxState != RxState.RxDone || txState != TxState.TxDone) {
            // Process receive routine
            if (rxState != RxState.RxDone) {
                try {
                    val result = receiveRoutine()
                    if (result == RoutineResult.FAILURE) {
                        break
                    }
                } catch (e: Exception) {
                    errorMessage = "Receive error: ${e.message}"
                    break
                }
            }

            // Transmit routine
            if (txState != TxState.TxDone) {
                val result = transmitRoutine()
                if (result == RoutineResult.FAILURE) {
                    break
                }
            }

            // Check if both sides are done
            if (rxState == RxState.RxDone && txState == TxState.TxDone) {
                break
            }

            // Small delay to prevent busy-waiting
            if (frameIO.available() == 0L) {
                delay(10)
            }
        }
    }

    /**
     * Routine result
     */
    private enum class RoutineResult {
        OK, CONTINUE, FAILURE
    }

    /**
     * Receive routine (Table 4 in FTS-1026.001)
     */
    private suspend fun receiveRoutine(): RoutineResult {
        when (rxState) {
            RxState.RxWaitF -> {
                val frame = frameIO.readFrameNonBlocking() ?: return RoutineResult.OK

                if (!frame.isCommand) {
                    // Data frame while waiting for file - ignore
                    return RoutineResult.OK
                }

                when (frame.commandId) {
                    BinkpCommand.M_ERR -> {
                        errorMessage = "Remote error: ${frame.argument}"
                        rxState = RxState.RxDone
                        return RoutineResult.FAILURE
                    }
                    BinkpCommand.M_GET, BinkpCommand.M_GOT, BinkpCommand.M_SKIP -> {
                        theQueue.send(frame)
                        return RoutineResult.OK
                    }
                    BinkpCommand.M_NUL -> {
                        return RoutineResult.OK
                    }
                    BinkpCommand.M_EOB -> {
                        onLogString("> M_EOB - Remote end of batch")
                        remoteEOBReceived = true
                        rxState = RxState.RxEOB
                        return RoutineResult.OK
                    }
                    BinkpCommand.M_FILE -> {
                        val fileInfo = FileInfo.parse(frame.argument)
                        if (fileInfo != null) {
                            onLogString("> M_FILE ${fileInfo.name} (${fileInfo.size} bytes)")
                            currentRxFile = fileInfo
                            rxState = RxState.RxAccF
                            return RoutineResult.CONTINUE
                        }
                        return RoutineResult.OK
                    }
                    else -> {
                        return RoutineResult.OK
                    }
                }
            }

            RxState.RxAccF -> {
                val fileInfo = currentRxFile ?: run {
                    rxState = RxState.RxWaitF
                    return RoutineResult.OK
                }

                val fs = FileSystemHelper.fs
                val targetPath = FileSystemHelper.resolve(receiveDirectory, fileInfo.name)

                // Check if we have a partial file and resume is enabled
                if (enableResume && fs.exists(targetPath) && FileSystemHelper.fileSize(targetPath) < fileInfo.size) {
                    // Request continuation from offset
                    val offset = FileSystemHelper.fileSize(targetPath)
                    onLogString("+ Resuming file ${fileInfo.name} from offset $offset")
                    currentRxSink = FileSystemHelper.openForAppending(targetPath)
                    currentRxBuffer = Buffer()
                    currentRxBytesReceived = offset
                    val newInfo = fileInfo.copy(offset = offset)
                    sendCommand(BinkpCommand.M_GET, newInfo.toCommandArgs())
                    onLogString("< M_GET ${fileInfo.name} from offset $offset")

                    // Discard any buffered data
                    val bufferedBytes = frameIO.available()
                    if (bufferedBytes > 0) {
                        onLogString("+ Discarding $bufferedBytes buffered bytes")
                        // Skip by discarding available data
                        // Note: We can't directly access frameIO.input, but readFrameNonBlocking will consume data
                    }

                    waitingForResumeConfirmation = true
                } else {
                    // Accept from beginning
                    if (!enableResume && fs.exists(targetPath) && FileSystemHelper.fileSize(targetPath) < fileInfo.size) {
                        val partialSize = FileSystemHelper.fileSize(targetPath)
                        onLogString("+ Resume disabled, deleting partial file ${fileInfo.name} ($partialSize bytes)")
                        FileSystemHelper.delete(targetPath)
                    }
                    onLogString("+ Accepting file ${fileInfo.name}")
                    currentRxSink = FileSystemHelper.openForWriting(targetPath)
                    currentRxBuffer = Buffer()
                    currentRxBytesReceived = 0
                }

                rxState = RxState.RxReceD
                return RoutineResult.OK
            }

            RxState.RxReceD -> {
                val frame = frameIO.readFrameNonBlocking() ?: return RoutineResult.OK

                if (!frame.isCommand) {
                    // Data frame
                    if (waitingForResumeConfirmation) {
                        onLogString("+ Discarding ${frame.data.size} bytes while waiting for resume confirmation")
                        return RoutineResult.OK
                    }
                    rxState = RxState.RxWriteD
                    return writeData(frame.data)
                }

                when (frame.commandId) {
                    BinkpCommand.M_ERR -> {
                        errorMessage = "Remote error: ${frame.argument}"
                        rxState = RxState.RxDone
                        return RoutineResult.FAILURE
                    }
                    BinkpCommand.M_GET, BinkpCommand.M_GOT, BinkpCommand.M_SKIP -> {
                        theQueue.send(frame)
                        return RoutineResult.OK
                    }
                    BinkpCommand.M_NUL -> {
                        return RoutineResult.OK
                    }
                    BinkpCommand.M_FILE -> {
                        val fileInfo = FileInfo.parse(frame.argument)
                        if (fileInfo != null) {
                            if (waitingForResumeConfirmation && currentRxFile?.name == fileInfo.name && currentRxFile?.size == fileInfo.size) {
                                onLogString("+ Got resume confirmation, expecting data from offset ${fileInfo.offset}")
                                waitingForResumeConfirmation = false
                                currentRxFile = fileInfo
                                return RoutineResult.OK
                            }
                            closeCurrentRxFile(partial = true)
                            currentRxFile = fileInfo
                            waitingForResumeConfirmation = false
                            rxState = RxState.RxAccF
                            return RoutineResult.CONTINUE
                        }
                        rxState = RxState.RxWaitF
                        return RoutineResult.OK
                    }
                    else -> {
                        return RoutineResult.OK
                    }
                }
            }

            RxState.RxWriteD -> {
                rxState = RxState.RxReceD
                return RoutineResult.OK
            }

            RxState.RxEOB -> {
                if (pendingFiles.isEmpty() && (txState == TxState.TxDone || localEOBSent)) {
                    rxState = RxState.RxDone
                    return RoutineResult.OK
                }

                val frame = frameIO.readFrameNonBlocking()
                if (frame != null && frame.isCommand) {
                    when (frame.commandId) {
                        BinkpCommand.M_GET, BinkpCommand.M_GOT, BinkpCommand.M_SKIP -> {
                            theQueue.send(frame)
                        }
                        BinkpCommand.M_ERR -> {
                            errorMessage = "Remote error: ${frame.argument}"
                            rxState = RxState.RxDone
                            return RoutineResult.FAILURE
                        }
                        else -> {}
                    }
                }
                return RoutineResult.OK
            }

            RxState.RxDone -> {
                return RoutineResult.OK
            }
        }
    }

    /**
     * Write data to current receiving file
     */
    private suspend fun writeData(data: ByteArray): RoutineResult {
        val sink = currentRxSink ?: return RoutineResult.OK
        val buffer = currentRxBuffer ?: return RoutineResult.OK
        val fileInfo = currentRxFile ?: return RoutineResult.OK

        try {
            buffer.write(data)
            sink.write(buffer, buffer.size)
            currentRxBytesReceived += data.size
            onLogString("D Received ${data.size} bytes, total: $currentRxBytesReceived / ${fileInfo.size}")

            when {
                currentRxBytesReceived > fileInfo.size -> {
                    errorMessage = "Received more data than expected for file ${fileInfo.name}"
                    rxState = RxState.RxDone
                    return RoutineResult.FAILURE
                }
                currentRxBytesReceived == fileInfo.size -> {
                    onLogString("+ Received file ${fileInfo.name} successfully (${fileInfo.size} bytes)")
                    closeCurrentRxFile(partial = false)
                    sendCommand(BinkpCommand.M_GOT, fileInfo.toCommandArgs(withOffset = false))
                    onLogString("< M_GOT ${fileInfo.name}")
                    rxState = RxState.RxWaitF
                    return RoutineResult.OK
                }
                else -> {
                    rxState = RxState.RxReceD
                    return RoutineResult.OK
                }
            }
        } catch (e: Exception) {
            errorMessage = "Write error: ${e.message}"
            rxState = RxState.RxDone
            return RoutineResult.FAILURE
        }
    }

    /**
     * Close current receiving file
     */
    private fun closeCurrentRxFile(partial: Boolean) {
        try {
            currentRxSink?.flush()
            currentRxSink?.close()
        } catch (e: Exception) {
            onLogString("- Failed to close file: ${e.message}")
        }

        if (!partial && currentRxFile != null) {
            val filePath = FileSystemHelper.resolve(receiveDirectory, currentRxFile!!.name)
            if (FileSystemHelper.exists(filePath)) {
                filesReceived.add(filePath)
            }
        }

        currentRxSink = null
        currentRxBuffer = null
        currentRxFile = null
        currentRxBytesReceived = 0
        waitingForResumeConfirmation = false
    }

    /**
     * Transmit routine (Table 5 in FTS-1026.001)
     */
    private suspend fun transmitRoutine(): RoutineResult {
        when (txState) {
            TxState.TxGNF -> {
                processTheQueue()

                val file = outgoingQueue.removeFirstOrNull()
                if (file == null) {
                    if (!localEOBSent) {
                        onLogString("< M_EOB - End of batch")
                        sendCommand(BinkpCommand.M_EOB, "")
                        localEOBSent = true
                    }
                    txState = TxState.TxWLA
                    return RoutineResult.CONTINUE
                }

                try {
                    currentTxFile = file
                    currentTxSource = FileSystemHelper.openForReading(file)
                    currentTxBuffer = Buffer()
                    currentTxInfo = FileInfo(
                        name = FileSystemHelper.fileName(file),
                        size = FileSystemHelper.fileSize(file),
                        unixTime = FileSystemHelper.lastModifiedTime(file)
                    )

                    onLogString("+ Sending file ${FileSystemHelper.fileName(file)} (${FileSystemHelper.fileSize(file)} bytes)")
                    sendCommand(BinkpCommand.M_FILE, currentTxInfo!!.toCommandArgs())
                    onLogString("< M_FILE ${FileSystemHelper.fileName(file)}")
                    pendingFiles[FileSystemHelper.fileName(file)] = file

                    txState = TxState.TxTryR
                    return RoutineResult.CONTINUE
                } catch (e: Exception) {
                    onLogString("- Failed to open file ${FileSystemHelper.fileName(file)}: ${e.message}")
                    errorMessage = "Failed to open file: ${e.message}"
                    txState = TxState.TxDone
                    return RoutineResult.FAILURE
                }
            }

            TxState.TxTryR -> {
                if (!theQueue.isEmpty) {
                    processTheQueue()
                    return RoutineResult.CONTINUE
                }
                txState = TxState.TxReadS
                return RoutineResult.CONTINUE
            }

            TxState.TxReadS -> {
                val source = currentTxSource ?: run {
                    txState = TxState.TxGNF
                    return RoutineResult.OK
                }
                val buffer = currentTxBuffer ?: run {
                    txState = TxState.TxGNF
                    return RoutineResult.OK
                }

                try {
                    val maxRead = minOf(32767, 4096).toLong()
                    val bytesRead = source.readAtMostTo(buffer, maxRead)

                    if (bytesRead == 0L) {
                        closeCurrentTxFile()
                        txState = TxState.TxGNF
                        return RoutineResult.OK
                    }

                    val data = buffer.readByteArray()
                    sendDataFrame(data)
                    txState = TxState.TxTryR
                    return RoutineResult.OK

                } catch (e: Exception) {
                    errorMessage = "Read error: ${e.message}"
                    txState = TxState.TxDone
                    return RoutineResult.FAILURE
                }
            }

            TxState.TxWLA -> {
                if (!theQueue.isEmpty) {
                    processTheQueue()
                    return RoutineResult.CONTINUE
                }

                if (pendingFiles.isEmpty() && remoteEOBReceived) {
                    txState = TxState.TxDone
                }
                return RoutineResult.OK
            }

            TxState.TxDone -> {
                return RoutineResult.OK
            }
        }
    }

    /**
     * Close current transmitting file
     */
    private fun closeCurrentTxFile() {
        try {
            currentTxSource?.close()
        } catch (e: Exception) {
            onLogString("- Failed to close file: ${e.message}")
        }
        currentTxSource = null
        currentTxBuffer = null
    }

    /**
     * Process the queue of M_GET/M_GOT/M_SKIP frames (Table 6 in FTS-1026.001)
     */
    private suspend fun processTheQueue() {
        while (true) {
            val frame = theQueue.tryReceive().getOrNull() ?: break

            when (frame.commandId) {
                BinkpCommand.M_GOT -> {
                    val fileInfo = FileInfo.parse(frame.argument) ?: continue
                    onLogString("> M_GOT ${fileInfo.name}")

                    if (currentTxInfo?.name == fileInfo.name) {
                        closeCurrentTxFile()
                        val file = currentTxFile
                        if (file != null) {
                            onLogString("+ File ${FileSystemHelper.fileName(file)} sent successfully")
                            filesSent.add(file)
                            pendingFiles.remove(FileSystemHelper.fileName(file))
                        }
                        currentTxFile = null
                        currentTxInfo = null
                        txState = TxState.TxGNF
                    } else {
                        val file = pendingFiles.remove(fileInfo.name)
                        if (file != null) {
                            onLogString("+ File ${fileInfo.name} acknowledged")
                            filesSent.add(file)
                        }
                    }
                }

                BinkpCommand.M_GET -> {
                    val fileInfo = FileInfo.parse(frame.argument) ?: continue
                    onLogString("> M_GET ${fileInfo.name} from offset ${fileInfo.offset}")

                    if (currentTxInfo?.name == fileInfo.name) {
                        if (fileInfo.offset >= currentTxInfo!!.size) {
                            onLogString("+ File ${fileInfo.name} already received by remote")
                            closeCurrentTxFile()
                            val file = currentTxFile
                            if (file != null) {
                                filesSent.add(file)
                                pendingFiles.remove(FileSystemHelper.fileName(file))
                            }
                            currentTxFile = null
                            currentTxInfo = null
                            txState = TxState.TxGNF
                        } else {
                            try {
                                onLogString("+ Resuming send from offset ${fileInfo.offset}")
                                currentTxSource?.close()
                                currentTxSource = FileSystemHelper.openForReading(currentTxFile!!)
                                currentTxBuffer = Buffer()

                                // Skip to offset
                                var remaining = fileInfo.offset
                                val tempBuffer = Buffer()
                                while (remaining > 0) {
                                    val transferred = currentTxSource!!.readAtMostTo(tempBuffer, remaining)
                                    if (transferred == 0L) break
                                    tempBuffer.clear()
                                    remaining -= transferred
                                }

                                currentTxInfo = currentTxInfo!!.copy(offset = fileInfo.offset)
                                sendCommand(BinkpCommand.M_FILE, currentTxInfo!!.toCommandArgs())
                            } catch (e: Exception) {
                                onLogString("- Error seeking in file: ${e.message}")
                            }
                        }
                    }
                }

                BinkpCommand.M_SKIP -> {
                    val fileInfo = FileInfo.parse(frame.argument) ?: continue
                    onLogString("> M_SKIP ${fileInfo.name}")

                    if (currentTxInfo?.name == fileInfo.name) {
                        onLogString("+ Skipping file ${fileInfo.name} as requested by remote")
                        closeCurrentTxFile()
                        pendingFiles.remove(fileInfo.name)
                        currentTxFile = null
                        currentTxInfo = null
                        txState = TxState.TxGNF
                    } else {
                        pendingFiles.remove(fileInfo.name)
                    }
                }

                else -> {}
            }
        }
    }

    private suspend fun sendCommand(command: BinkpCommand, argument: String) {
        sendMutex.withLock {
            frameIO.sendCommand(command, argument)
        }
    }

    private suspend fun sendDataFrame(data: ByteArray) {
        sendMutex.withLock {
            frameIO.sendDataFrame(data)
        }
    }

    private fun cleanup() {
        // Cleanup resources
        try {
            currentRxSink?.close()
        } catch (e: Exception) {
            onLogString("- Failed to close Rx stream: ${e.message}")
        }

        try {
            currentTxSource?.close()
        } catch (e: Exception) {
            onLogString("- Failed to close Tx stream: ${e.message}")
        }
    }
}
