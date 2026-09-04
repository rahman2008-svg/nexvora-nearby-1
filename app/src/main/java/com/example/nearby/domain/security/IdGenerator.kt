package com.example.nearby.domain.security

import java.security.SecureRandom

object IdGenerator {
    private val secureRandom = SecureRandom()
    private const val HEX_CHARS = "0123456789ABCDEF"

    /**
     * Generates a unique local user ID in format: NV-XXXX-XXXX
     * using cryptographically secure random bytes.
     */
    fun generateUserId(): String {
        val part1 = generateRandomHex(4)
        val part2 = generateRandomHex(4)
        return "NV-$part1-$part2"
    }

    /**
     * Generates random packet or message ID
     */
    fun generateMessageId(): String {
        return "msg_${System.currentTimeMillis()}_${generateRandomHex(6)}"
    }

    fun generatePacketId(): String {
        return "pkt_${generateRandomHex(8)}"
    }

    private fun generateRandomHex(length: Int): String {
        val sb = StringBuilder(length)
        val bytes = ByteArray(length)
        secureRandom.nextBytes(bytes)
        for (b in bytes) {
            val idx = (b.toInt() and 0xFF) % HEX_CHARS.length
            sb.append(HEX_CHARS[idx])
        }
        return sb.toString()
    }
}
