package net.jegor.kmftn.base

/**
 * A wildcard pattern matching [FtnAddr] instances by their 4D coordinates
 * (zone:net/node.point); the domain is ignored during matching.
 *
 * Each of the four components is either a fixed value or a wildcard. A wildcard
 * always covers a trailing suffix of (zone, net, node, point), so a pattern
 * fixes a prefix and matches every address sharing it. For example:
 *
 * - `*` matches every possible FTN address;
 * - `21:*` matches `21:2/3.4`, `21:5/6` and any other address in zone 21;
 * - a node-level wildcard on net 2:382 matches `2:382/1`, `2:382/2.3` and every
 *   other node of that net;
 * - `2:5020/736.*` matches `2:5020/736` itself and its points such as `2:5020/736.1`;
 * - `2:5020/736` matches only that exact address (point 0).
 *
 * Patterns are parsed with [fromString] and tested against an address with [matches].
 */
public class FtnWildcard(
    public val zone: Short? = null,
    public val net: Short? = null,
    public val node: Short? = null,
    public val point: Short? = null,
) {

    /**
     * Returns `true` when [addr] shares the fixed prefix of this pattern.
     * The address domain never affects the result.
     */
    public fun matches(addr: FtnAddr): Boolean {
        if (zone != null && zone != addr.zone) return false
        if (net != null && net != addr.net) return false
        if (node != null && node != addr.node) return false
        if (point != null && point != addr.point) return false
        return true
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FtnWildcard) return false

        if (zone != other.zone) return false
        if (net != other.net) return false
        if (node != other.node) return false
        if (point != other.point) return false

        return true
    }

    override fun hashCode(): Int {
        var result = zone?.toInt() ?: 0
        result = 31 * result + (net?.toInt() ?: 0)
        result = 31 * result + (node?.toInt() ?: 0)
        result = 31 * result + (point?.toInt() ?: 0)
        return result
    }

    override fun toString(): String = when {
        zone == null -> "*"
        net == null -> "$zone:*"
        node == null -> "$zone:$net/*"
        point == null -> "$zone:$net/$node.*"
        point == 0.toShort() -> "$zone:$net/$node"
        else -> "$zone:$net/$node.$point"
    }

    public companion object {

        /**
         * Parses a wildcard pattern. Each form fixes a prefix of (zone, net,
         * node, point) and wildcards the rest; an asterisk may only appear as
         * the final component, covering itself and everything after it.
         * Accepted forms:
         *
         * - `*` — match every address;
         * - `<zone>:*` — match a whole zone;
         * - `<zone>:<net>` with a trailing node wildcard — match a whole net;
         * - `<zone>:<net>/<node>.*` — match a node and all its points;
         * - `<zone>:<net>/<node>[.<point>]` — match one exact address (point defaults to 0).
         *
         * @throws IllegalArgumentException if [input] is blank, contains a domain,
         * has an invalid structure, or holds a non-numeric or out-of-range component.
         */
        public fun fromString(input: String): FtnWildcard {
            val trimmed = input.trim()
            require(trimmed.isNotEmpty()) { "FTN address wildcard is empty" }
            require('@' !in trimmed) {
                "FTN address wildcard must not contain a domain: '$input'"
            }

            if (trimmed == "*") {
                return FtnWildcard()
            }

            val colonIndex = trimmed.indexOf(':')
            require(colonIndex >= 1) {
                "FTN address wildcard must be '*', '<zone>:*', '<zone>:<net>/*', " +
                    "'<zone>:<net>/<node>.*' or '<zone>:<net>/<node>[.<point>]': '$input'"
            }

            val zone = trimmed.take(colonIndex).toShortOrFail("zone", input)
            val afterColon = trimmed.substring(colonIndex + 1)

            if (afterColon == "*") {
                return FtnWildcard(zone = zone)
            }

            val slashIndex = afterColon.indexOf('/')
            require(slashIndex >= 1) {
                "FTN address wildcard has invalid net/node part in '$input'"
            }

            val net = afterColon.take(slashIndex).toShortOrFail("net", input)
            val afterSlash = afterColon.substring(slashIndex + 1)

            if (afterSlash == "*") {
                return FtnWildcard(zone = zone, net = net)
            }

            val dotIndex = afterSlash.indexOf('.')
            val nodePart: String
            val pointPart: String?
            if (dotIndex >= 0) {
                nodePart = afterSlash.take(dotIndex)
                pointPart = afterSlash.substring(dotIndex + 1)
            } else {
                nodePart = afterSlash
                pointPart = null
            }
            val node = nodePart.toShortOrFail("node", input)

            if (pointPart == "*") {
                return FtnWildcard(zone = zone, net = net, node = node)
            }

            val point = (pointPart ?: "0").toShortOrFail("point", input)
            return FtnWildcard(zone = zone, net = net, node = node, point = point)
        }

        private fun String.toShortOrFail(field: String, original: String): Short =
            toShortOrNull()
                ?: throw IllegalArgumentException(
                    "FTN address wildcard has invalid $field '$this' in '$original'"
                )
    }
}
