package net.jegor.kmftn.binkpclient

/**
 * CRAM (Challenge-Response Authentication Mechanism) support for Binkp
 * Based on FTS-1027.001 specification
 */

import org.kotlincrypto.hash.md.MD5
import org.kotlincrypto.hash.sha1.SHA1

/**
 * Supported hash algorithms for CRAM
 */
internal enum class CramHashAlgorithm(val alias: String) {
    MD5("MD5"),
    SHA1("SHA1");

    companion object {
        fun fromAlias(alias: String): CramHashAlgorithm? {
            return entries.find { it.alias.equals(alias, ignoreCase = true) }
        }
    }
}

/**
 * Multiplatform digest calculator using KotlinCrypto
 */
internal class DigestCalculator(private val algorithm: CramHashAlgorithm) {
    private val digest = when (algorithm) {
        CramHashAlgorithm.MD5 -> MD5()
        CramHashAlgorithm.SHA1 -> SHA1()
    }

    fun update(data: ByteArray) {
        digest.update(data)
    }

    fun digest(): ByteArray {
        return digest.digest()
    }

    fun reset() {
        digest.reset()
    }
}

/**
 * Parse CRAM challenge from M_NUL OPT string
 * Format: "OPT ... CRAM-MD5/SHA1-hexchallenge ..."
 *
 * @return Pair of (list of supported hash algorithms, challenge data) or null if not a CRAM option
 */
internal fun parseCramChallenge(optString: String): Pair<List<CramHashAlgorithm>, ByteArray>? {
    // Split by spaces to get individual options
    val options = optString.split(" ").filter { it.isNotBlank() }

    for (option in options) {
        if (option.startsWith("CRAM-", ignoreCase = true)) {
            // Extract the part after "CRAM-"
            val cramPart = option.substring(5)

            // Find the last dash which separates hash list from challenge
            val lastDashIndex = cramPart.lastIndexOf('-')
            if (lastDashIndex < 0) continue

            val hashListStr = cramPart.substring(0, lastDashIndex)
            val challengeHex = cramPart.substring(lastDashIndex + 1)

            // Parse hash algorithm list (delimited by slashes)
            val hashAlgorithms = hashListStr.split("/")
                .mapNotNull { CramHashAlgorithm.fromAlias(it) }

            if (hashAlgorithms.isEmpty()) continue

            // Decode hex challenge
            try {
                val challengeData = hexToBytes(challengeHex)
                if (challengeData.isEmpty()) continue

                return Pair(hashAlgorithms, challengeData)
            } catch (e: Exception) {
                continue
            }
        }
    }

    return null
}

/**
 * Generate CRAM response digest
 * Implements HMAC as per RFC 2104
 *
 * @param password The session password
 * @param challengeData The challenge data from answering side
 * @param algorithm The hash algorithm to use
 * @return The digest encoded as lowercase hexadecimal string
 */
internal fun generateCramDigest(password: String, challengeData: ByteArray, algorithm: CramHashAlgorithm): String {
    val digest = hmac(password.encodeToByteArray(), challengeData, algorithm)
    return bytesToHex(digest)
}

/**
 * HMAC implementation according to RFC 2104
 * HMAC = HASH((secret XOR opad) + HASH((secret XOR ipad) + data))
 */
private fun hmac(key: ByteArray, data: ByteArray, algorithm: CramHashAlgorithm): ByteArray {
    val blockSize = 64 // 64 bytes for MD5 and SHA-1
    val ipad = 0x36.toByte()
    val opad = 0x5C.toByte()

    // Prepare the key
    val keyProcessed = when {
        key.size > blockSize -> {
            // If key is longer than block size, hash it
            val md = DigestCalculator(algorithm)
            md.update(key)
            md.digest()
        }
        key.size < blockSize -> {
            // If key is shorter than block size, pad with zeros
            key + ByteArray(blockSize - key.size)
        }
        else -> key
    }

    // Create padded keys
    val ipadKey = ByteArray(blockSize) { i -> (keyProcessed[i].toInt() xor ipad.toInt()).toByte() }
    val opadKey = ByteArray(blockSize) { i -> (keyProcessed[i].toInt() xor opad.toInt()).toByte() }

    // Inner hash: HASH((key XOR ipad) + data)
    val md = DigestCalculator(algorithm)
    md.update(ipadKey)
    md.update(data)
    val innerHash = md.digest()

    // Outer hash: HASH((key XOR opad) + innerHash)
    md.reset()
    md.update(opadKey)
    md.update(innerHash)
    return md.digest()
}

/**
 * Convert hex string to byte array
 * Accepts both lowercase and uppercase hex characters
 */
internal fun hexToBytes(hex: String): ByteArray {
    require(hex.length % 2 == 0) { "Hex string must have even length" }

    return ByteArray(hex.length / 2) { i ->
        val index = i * 2
        val byte = hex.substring(index, index + 2).toInt(16)
        byte.toByte()
    }
}

/**
 * Convert byte array to lowercase hex string
 */
internal fun bytesToHex(bytes: ByteArray): String {
    return bytes.joinToString("") { byte ->
        val value = byte.toInt() and 0xFF
        if (value < 16) "0${value.toString(16)}" else value.toString(16)
    }
}
