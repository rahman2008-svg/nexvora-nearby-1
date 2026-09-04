package com.example.nearby.network.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class PacketType {
    HELLO,
    PROFILE,
    CONNECTION_REQUEST,
    CONNECTION_ACCEPT,
    CONNECTION_DECLINE,
    KEY_EXCHANGE,
    MESSAGE,
    MESSAGE_ACK,
    MESSAGE_READ,
    PING,
    PONG,
    DISCONNECT,
    FILE_REQUEST,
    FILE_CHUNK,
    FILE_COMPLETE
}

@Serializable
data class NetworkPacket(
    val protocolVersion: Int = CURRENT_PROTOCOL_VERSION,
    val packetId: String,
    val packetType: PacketType,
    val senderId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val payload: String = "",
    val integritySignature: String? = null
) {
    companion object {
        const val CURRENT_PROTOCOL_VERSION = 1
        const val MAX_PACKET_SIZE_BYTES = 64 * 1024 // 64 KB limit per text packet
        const val MAX_FILE_CHUNK_SIZE_BYTES = 32 * 1024 // 32 KB chunk for file transfer
    }
}

// Payload models for typed packet payloads
@Serializable
data class HelloPayload(
    val userId: String,
    val nickname: String,
    val avatarId: String,
    val primaryActivity: String,
    val activities: List<String> = emptyList(),
    val interests: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
    val availabilityStatus: String,
    val currentStatusMessage: String = ""
)

@Serializable
data class ConnectionRequestPayload(
    val requesterId: String,
    val requesterNickname: String,
    val requesterAvatarId: String,
    val note: String = "Wants to connect for shared activity"
)

@Serializable
data class KeyExchangePayload(
    val ecdhPublicKeyBase64: String
)

@Serializable
data class MessagePayload(
    val messageId: String,
    val conversationId: String,
    val senderId: String,
    val receiverId: String,
    val encryptedText: String, // Encrypted with session key
    val timestamp: Long
)

@Serializable
data class MessageAckPayload(
    val originalMessageId: String,
    val conversationId: String,
    val status: String // DELIVERED, READ
)

@Serializable
data class FileRequestPayload(
    val transferId: String,
    val fileName: String,
    val fileSize: Long,
    val mimeType: String
)

@Serializable
data class FileChunkPayload(
    val transferId: String,
    val chunkIndex: Int,
    val totalChunks: Int,
    val chunkDataBase64: String
)

val defaultJson = Json {
    ignoreUnknownKeys = true
    isLenient = false
    encodeDefaults = true
}
