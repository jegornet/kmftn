package net.jegor.kmftn.binkpclient

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for CRAM (Challenge-Response Authentication Mechanism) implementation
 * Based on FTS-1027.001 specification
 */
class BinkpCramTest {

    @Test
    fun testCramDigestFromSpec(): Unit = runBlocking {
        // Example from FTS-1027.001 section 1.7:
        // Password: tanstaaftanstaaf
        // Challenge (hex): f0315b074d728d483d6887d0182fc328
        // Expected digest (hex): 56be002162a4a15ba7a9064f0c93fd00

        val password = "tanstaaftanstaaf"
        val challengeHex = "f0315b074d728d483d6887d0182fc328"
        val expectedDigestHex = "56be002162a4a15ba7a9064f0c93fd00"

        // Convert hex string to bytes
        val challengeData = hexToBytes(challengeHex)

        // Generate digest
        val actualDigest = generateCramDigest(password, challengeData, CramHashAlgorithm.MD5)

        assertEquals(expectedDigestHex, actualDigest,
            "HMAC-MD5 digest should match FTS-1027.001 example")
    }

    @Test
    fun testParseCramChallenge(): Unit = runBlocking {
        // Example: M_NUL "OPT xx xx CRAM-MD5-f0315b074d728d483d6887d0182fc328"
        val optString = "OPT EXTCMD CRAM-MD5-f0315b074d728d483d6887d0182fc328"

        val result = parseCramChallenge(optString)

        assertNotNull(result, "Should parse CRAM challenge")
        result!!

        assertEquals(1, result.first.size, "Should have one hash algorithm")
        assertEquals(CramHashAlgorithm.MD5, result.first[0], "Should be MD5")

        val expectedChallenge = hexToBytes("f0315b074d728d483d6887d0182fc328")
        assertTrue(expectedChallenge.contentEquals(result.second), "Challenge data should match")
    }

    @Test
    fun testParseCramChallengeMultipleAlgorithms(): Unit = runBlocking {
        val optString = "OPT CRAM-MD5/SHA1-abcdef1234567890"

        val result = parseCramChallenge(optString)

        assertNotNull(result, "Should parse CRAM challenge")
        result!!

        assertEquals(2, result.first.size, "Should have two hash algorithms")
        assertEquals(CramHashAlgorithm.MD5, result.first[0], "First should be MD5")
        assertEquals(CramHashAlgorithm.SHA1, result.first[1], "Second should be SHA1")

        val expectedChallenge = hexToBytes("abcdef1234567890")
        assertTrue(expectedChallenge.contentEquals(result.second), "Challenge data should match")
    }

    @Test
    fun testParseNonCramString(): Unit = runBlocking {
        val optString = "OPT EXTCMD NR"

        val result = parseCramChallenge(optString)

        assertNull(result, "Should return null for non-CRAM option")
    }

    @Test
    fun testParseCramChallengeCaseInsensitive(): Unit = runBlocking {
        // Test lowercase
        val optStringLower = "OPT cram-md5-aabbccdd"
        val resultLower = parseCramChallenge(optStringLower)
        assertNotNull(resultLower, "Should parse lowercase CRAM")

        // Test uppercase challenge hex (should also work per spec)
        val optStringUpper = "OPT CRAM-MD5-AABBCCDD"
        val resultUpper = parseCramChallenge(optStringUpper)
        assertNotNull(resultUpper, "Should parse uppercase hex CRAM")

        // Both should produce same result
        assertTrue(resultLower!!.second.contentEquals(resultUpper!!.second),
            "Lowercase and uppercase hex should produce same challenge data")
    }

    @Test
    fun testParseCramChallengePosition(): Unit = runBlocking {
        // CRAM at beginning
        val optString1 = "OPT CRAM-MD5-aabbccdd"
        assertNotNull(parseCramChallenge(optString1))

        // CRAM in middle
        val optString2 = "OPT EXTCMD CRAM-MD5-aabbccdd NR"
        assertNotNull(parseCramChallenge(optString2))

        // CRAM at end
        val optString3 = "OPT EXTCMD NR CRAM-MD5-aabbccdd"
        assertNotNull(parseCramChallenge(optString3))
    }

    @Test
    fun testCramDigestDifferentPasswords(): Unit = runBlocking {
        val challenge = hexToBytes("f0315b074d728d483d6887d0182fc328")

        val digest1 = generateCramDigest("password1", challenge, CramHashAlgorithm.MD5)
        val digest2 = generateCramDigest("password2", challenge, CramHashAlgorithm.MD5)

        assertNotEquals(digest1, digest2, "Different passwords should produce different digests")
    }

    @Test
    fun testCramDigestDifferentChallenges(): Unit = runBlocking {
        val password = PasswordGenerator.generateRandomPassword()
        val challenge1 = hexToBytes("f0315b074d728d483d6887d0182fc328")
        val challenge2 = hexToBytes("aabbccddeeff00112233445566778899")

        val digest1 = generateCramDigest(password, challenge1, CramHashAlgorithm.MD5)
        val digest2 = generateCramDigest(password, challenge2, CramHashAlgorithm.MD5)

        assertNotEquals(digest1, digest2, "Different challenges should produce different digests")
    }

    @Test
    fun testCramDigestDeterministic(): Unit = runBlocking {
        val password = PasswordGenerator.generateRandomPassword()
        val challenge = hexToBytes("f0315b074d728d483d6887d0182fc328")

        val digest1 = generateCramDigest(password, challenge, CramHashAlgorithm.MD5)
        val digest2 = generateCramDigest(password, challenge, CramHashAlgorithm.MD5)

        assertEquals(digest1, digest2, "Same inputs should produce same digest")
    }

    @Test
    fun testCramDigestLowercaseHex(): Unit = runBlocking {
        val password = "test"
        val challenge = hexToBytes("aabbccdd")

        val digest = generateCramDigest(password, challenge, CramHashAlgorithm.MD5)

        assertTrue(digest.all { it.isLowerCase() || it.isDigit() },
            "Digest should be lowercase hex")
    }

    @Test
    fun testCramDigestMD5Length(): Unit = runBlocking {
        val password = "test"
        val challenge = hexToBytes("aabbccdd")

        val digest = generateCramDigest(password, challenge, CramHashAlgorithm.MD5)

        assertEquals(32, digest.length, "MD5 digest should be 32 hex characters (16 bytes)")
    }

    /**
     * Helper function to convert hex string to byte array
     */
    private fun hexToBytes(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "Hex string must have even length" }

        return ByteArray(hex.length / 2) { i ->
            val index = i * 2
            val byte = hex.substring(index, index + 2).toInt(16)
            byte.toByte()
        }
    }
}
