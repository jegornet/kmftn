package net.jegor.kmftn.binkpclient

/**
 * Binkp command identifiers as per FTS-1026.001 Section 5.5
 */
internal enum class BinkpCommand(val id: Int) {
    M_NUL(0),    // Human-readable information, options
    M_ADR(1),    // List of FTN addresses
    M_PWD(2),    // Session password (originating side only)
    M_FILE(3),   // File header: name, size, unixtime, offset
    M_OK(4),     // Password acknowledgement (answering side only)
    M_EOB(5),    // End of batch
    M_GOT(6),    // File acknowledgement
    M_ERR(7),    // Fatal error
    M_BSY(8),    // Non-fatal error (busy)
    M_GET(9),    // Request to resend file from offset
    M_SKIP(10);  // Non-destructive skip

    companion object {
        fun fromId(id: Int): BinkpCommand? = entries.find { it.id == id }
    }
}