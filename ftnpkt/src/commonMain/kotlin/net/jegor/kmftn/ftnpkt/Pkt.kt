package net.jegor.kmftn.ftnpkt

/**
 * Common interface for FidoNet Type-2 packet headers.
 *
 * Covers fields shared by FTS-0001 (Stone Age), FSC-0039 (Type-2e),
 * and FSC-0048 (Type-2+).
 */
public interface Pkt {
    public val origNode: UShort
    public val destNode: UShort
    public val year: UShort
    public val month: UShort
    public val day: UShort
    public val hour: UShort
    public val minute: UShort
    public val second: UShort
    public val baud: UShort
    public val origNet: UShort
    public val destNet: UShort
    public val prodCodeLo: UByte
    public val prodRevMajor: UByte
    public val password: ByteArray
    public val origZone: UShort
    public val destZone: UShort
    public val messages: List<PackedMsg>

    public fun toByteArray(): ByteArray
}