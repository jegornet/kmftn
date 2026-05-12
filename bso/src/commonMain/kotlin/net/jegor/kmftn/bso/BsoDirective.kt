package net.jegor.kmftn.bso

/**
 * Directives in BSO reference flow files (.?lo) as defined in FTS-5005.
 */
public enum class BsoDirective(public val code: Char?) {
    /**
     * Truncate the file to zero-length after successful transfer.
     */
    TRUNCATE('#'),

    /**
     * Delete the file after successful transfer.
     */
    DELETE('^'),

    /**
     * Alternative delete directive.
     */
    DELETE_ALT('-'),

    /**
     * Skip the line from treatment.
     */
    SKIP('~'),

    /**
     * Alternative skip directive.
     */
    SKIP_ALT('!'),

    /**
     * Send file, do not truncate or delete.
     */
    SEND_ONLY('@'),

    /**
     * No directive, standard behavior (send and leave).
     */
    NONE(null);

    public companion object {
        public fun fromCode(code: Char): BsoDirective =
            entries.find { it.code == code } ?: NONE
    }
}
