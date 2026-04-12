package net.jegor.kmftn.binkpclient

/**
 * Transmit state machine states
 */
internal enum class TxState {
    TxGNF,      // Get next file
    TxTryR,     // Try reading/check queue
    TxReadS,    // Read and send
    TxWLA,      // Wait for last acknowledgement
    TxDone      // Done
}