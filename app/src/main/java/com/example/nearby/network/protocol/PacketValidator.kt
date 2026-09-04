package com.example.nearby.network.protocol

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

object PacketValidator {
    const val MAX_NICKNAME_LENGTH = 32
    const val MAX_STATUS_MESSAGE_LENGTH = 120
    const val MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024L // 10 MB limit
    private const val MAX_REQUESTS_PER_MINUTE = 60
    private const val MAX_INVALID_PACKETS_BEFORE_BLOCK = 5
    private const val MAX_CLOCK_DRIFT_MS = 300_000L // 5 minutes drift allowed
    private const val MAX_SEEN_PACKET_CACHE = 1000

    private val peerRequestCounts = ConcurrentHashMap<String, MutableList<Long>>()
    private val peerInvalidPacketCounts = ConcurrentHashMap<String, AtomicInteger>()
    private val seenPacketIds = java.util.Collections.synchronizedSet(
        object : java.util.LinkedHashSet<String>() {
            override fun add(element: String): Boolean {
                if (size >= MAX_SEEN_PACKET_CACHE) {
                    val iterator = iterator()
                    if (iterator.hasNext()) {
                        iterator.next()
                        iterator.remove()
                    }
                }
                return super.add(element)
            }
        }
    )

    /**
     * Validates a raw network packet before processing.
     * Returns true if valid, false if malicious/malformed/replayed.
     */
    fun validatePacket(packet: NetworkPacket): Boolean {
        // 1. Version check
        if (packet.protocolVersion != NetworkPacket.CURRENT_PROTOCOL_VERSION) {
            return false
        }

        // 2. Sender ID validation
        if (packet.senderId.isBlank() || packet.senderId.length > 64) {
            return false
        }

        // 3. Timestamp freshness check (reject packets older than 5 mins or in far future)
        val now = System.currentTimeMillis()
        if (Math.abs(now - packet.timestamp) > MAX_CLOCK_DRIFT_MS) {
            return false
        }

        // 4. Replay attack prevention (reject duplicated packetId)
        if (packet.packetId.isBlank() || !seenPacketIds.add(packet.packetId)) {
            return false
        }

        // 5. Payload size check
        if (packet.payload.length > NetworkPacket.MAX_PACKET_SIZE_BYTES) {
            recordInvalidPacket(packet.senderId)
            return false
        }

        // 6. Rate limiting check
        if (!checkRateLimit(packet.senderId)) {
            return false
        }

        return true
    }

    /**
     * Sanitizes user input string (removes control characters, trims, enforces max length).
     */
    fun sanitizeString(input: String, maxLength: Int = 100): String {
        val cleaned = input.replace(Regex("[\\p{Cntrl}&&[^\r\n\t]]"), "").trim()
        return if (cleaned.length > maxLength) cleaned.substring(0, maxLength) else cleaned
    }

    /**
     * Sanitizes received file name to strictly prevent directory traversal (e.g., ../../../etc/passwd).
     */
    fun sanitizeFileName(rawFileName: String): String {
        val fileNameOnly = File(rawFileName).name
        // Replace dangerous characters with underscore
        val cleanName = fileNameOnly.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return if (cleanName.isBlank()) "received_file_${System.currentTimeMillis()}" else cleanName
    }

    /**
     * Checks if peer has exceeded rate limit (max 60 packets per minute).
     */
    private fun checkRateLimit(senderId: String): Boolean {
        val now = System.currentTimeMillis()
        val timestamps = peerRequestCounts.computeIfAbsent(senderId) { mutableListOf() }
        synchronized(timestamps) {
            // Remove older than 1 minute
            timestamps.removeAll { now - it > 60_000 }
            if (timestamps.size >= MAX_REQUESTS_PER_MINUTE) {
                return false
            }
            timestamps.add(now)
            return true
        }
    }

    /**
     * Records an invalid or malformed packet from a peer.
     * Returns true if peer should be temporarily blocked.
     */
    fun recordInvalidPacket(senderId: String): Boolean {
        val counter = peerInvalidPacketCounts.computeIfAbsent(senderId) { AtomicInteger(0) }
        return counter.incrementAndGet() >= MAX_INVALID_PACKETS_BEFORE_BLOCK
    }

    /**
     * Resets invalid packet counter when peer behaves normally.
     */
    fun resetPeerStats(senderId: String) {
        peerInvalidPacketCounts[senderId]?.set(0)
    }
}
