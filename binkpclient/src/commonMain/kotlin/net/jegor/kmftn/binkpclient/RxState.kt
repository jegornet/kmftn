package net.jegor.kmftn.binkpclient

/**
 * Receive state machine states
 */
internal enum class RxState {
    RxWaitF,    // Waiting for file
    RxAccF,     // Accepting file decision
    RxReceD,    // Receiving data
    RxWriteD,   // Writing data
    RxEOB,      // End of batch received
    RxDone      // Done
}