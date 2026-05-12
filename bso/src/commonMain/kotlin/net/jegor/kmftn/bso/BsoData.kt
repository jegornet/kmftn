package net.jegor.kmftn.bso

import kotlinx.io.files.Path
import net.jegor.kmftn.base.FtnFlavor

/**
 * A reference to a file in a flow file.
 */
public data class BsoReference(
    val path: Path,
    val flavor: FtnFlavor,
    val directive: BsoDirective = BsoDirective.NONE
)

/**
 * Netmail information for a link.
 */
public data class BsoNetmail(
    val path: Path,
    val flavor: FtnFlavor
)

/**
 * Information about an FTN link in the outbound.
 */
public data class BsoLink(
    val addr: net.jegor.kmftn.base.FtnAddr,
    val netmail: BsoNetmail?,
    val references: List<BsoReference>,
    val hasRequest: Boolean,
    val isBusy: Boolean,
    val isCalling: Boolean,
    val isHold: Boolean
)
