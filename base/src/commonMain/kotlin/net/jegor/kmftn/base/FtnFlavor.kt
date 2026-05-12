package net.jegor.kmftn.base

/**
 * Flavors of flow files and messages as defined in FTS-5005.
 */
public enum class FtnFlavor {
    /**
     * Immediate flavor (code 'i').
     * Poll remote system without taking in consideration any restrictions.
     */
    IMMEDIATE,

    /**
     * Crash (Continuous) flavor (code 'c').
     * Poll remote system taking in consideration internal restrictions but not external ones.
     */
    CRASH,

    /**
     * Direct flavor (code 'd').
     * Poll remote system taking into consideration both external and internal restrictions.
     */
    DIRECT,

    /**
     * Normal flavor (codes 'f' for flow files, 'o' for netmail).
     * Poll remote system taking into consideration both external and internal restrictions.
     * May be rerouted.
     */
    NORMAL,

    /**
     * Hold flavor (code 'h').
     * Wait for a poll from the remote system.
     */
    HOLD;

    /**
     * The character code used for this flavor in flow file extensions (.?lo).
     */
    public val flowCode: Char
        get() = when (this) {
            IMMEDIATE -> 'i'
            CRASH -> 'c'
            DIRECT -> 'd'
            NORMAL -> 'f'
            HOLD -> 'h'
        }

    /**
     * The character code used for this flavor in netmail packet extensions (.?ut).
     */
    public val netmailCode: Char
        get() = when (this) {
            IMMEDIATE -> 'i'
            CRASH -> 'c'
            DIRECT -> 'd'
            NORMAL -> 'o'
            HOLD -> 'h'
        }
}
