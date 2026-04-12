package net.jegor.kmftn.binkpclient

import kotlinx.io.files.Path
import net.jegor.kmftn.base.FtnAddr

/**
 * Session result information
 */
public data class BinkpSessionResult(
    val success: Boolean,
    val remoteAddresses: List<FtnAddr>,
    val filesReceived: List<Path>,
    val filesSent: List<Path>,
    val passwordProtected: Boolean,
    val errorMessage: String? = null
)
