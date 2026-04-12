package net.jegor.kmftn.base

public class FtnAddr(
    public val zone: Short,
    public val net: Short,
    public val node: Short,
    public val point: Short,
    public val domain: String? = null,
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FtnAddr) return false

        if (zone != other.zone) return false
        if (net != other.net) return false
        if (node != other.node) return false
        if (point != other.point) return false

        return true
    }

    override fun hashCode(): Int {
        var result = zone.toInt()
        result = 31 * result + net
        result = 31 * result + node
        result = 31 * result + point
        return result
    }

    override fun toString(): String =
        if (point == 0.toShort()) {
            "$zone:$net/$node"
        } else {
            "$zone:$net/$node.$point"
        }

    public fun toString2D(): String = "$net/$node"

    public fun toString5D(): String {
        val addr4d = toString()
        return if (domain.isNullOrEmpty()) {
            addr4d
        } else {
            "$addr4d@$domain"
        }
    }

    public fun equals2D(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FtnAddr) return false

        if (net != other.net) return false
        if (node != other.node) return false

        return true
    }

    public fun equals5D(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FtnAddr) return false

        if (zone != other.zone) return false
        if (net != other.net) return false
        if (node != other.node) return false
        if (point != other.point) return false
        if (domain != other.domain) return false

        return true
    }

    public fun getBossNode(): FtnAddr {
        return FtnAddr(
            zone = zone,
            net = net,
            node = node,
            point = 0,
            domain = domain
        )
    }

    public companion object {

        public fun fromString(input: String): FtnAddr {
            val trimmed = input.trim()
            require(trimmed.isNotEmpty()) { "FTN address is empty" }

            val atIndex = trimmed.indexOf('@')
            val addrPart: String
            val domain: String?
            if (atIndex >= 0) {
                addrPart = trimmed.take(atIndex)
                domain = trimmed.substring(atIndex + 1).takeIf { it.isNotEmpty() }
                require(domain != null) { "FTN address has empty domain: '$input'" }
            } else {
                addrPart = trimmed
                domain = null
            }

            val colonIndex = addrPart.indexOf(':')
            val slashIndex = addrPart.indexOf('/')
            require(colonIndex in 1..<slashIndex) {
                "FTN address must be in zone:net/node[.point][@domain] format: '$input'"
            }

            val zonePart = addrPart.take(colonIndex)
            val netPart = addrPart.substring(colonIndex + 1, slashIndex)
            val nodeAndPoint = addrPart.substring(slashIndex + 1)

            val dotIndex = nodeAndPoint.indexOf('.')
            val nodePart: String
            val pointPart: String
            if (dotIndex >= 0) {
                nodePart = nodeAndPoint.take(dotIndex)
                pointPart = nodeAndPoint.substring(dotIndex + 1)
            } else {
                nodePart = nodeAndPoint
                pointPart = "0"
            }

            return FtnAddr(
                zone = zonePart.toShortOrFail("zone", input),
                net = netPart.toShortOrFail("net", input),
                node = nodePart.toShortOrFail("node", input),
                point = pointPart.toShortOrFail("point", input),
                domain = domain,
            )
        }

        private fun String.toShortOrFail(field: String, original: String): Short =
            toShortOrNull()
                ?: throw IllegalArgumentException(
                    "FTN address has invalid $field '$this' in '$original'"
                )
    }
}