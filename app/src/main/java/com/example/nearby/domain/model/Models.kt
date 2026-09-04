package com.example.nearby.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class AvailabilityStatus {
    AVAILABLE,
    BUSY,
    OFFLINE,
    DO_NOT_DISTURB
}

@Serializable
data class UserProfile(
    val userId: String,
    val nickname: String,
    val avatarId: String = "avatar_1",
    val bio: String = "",
    val interests: List<String> = emptyList(),
    val activities: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
    val availability: AvailabilityStatus = AvailabilityStatus.AVAILABLE,
    val currentStatusMessage: String = "",
    val hasPin: Boolean = false,
    val isLocked: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
enum class PeerConnectionStatus {
    DISCOVERED,
    CONNECTING,
    CONNECTION_REQUEST_SENT,
    CONNECTION_REQUEST_RECEIVED,
    CONNECTED,
    DISCONNECTED,
    FAILED,
    BLOCKED
}

@Serializable
data class DiscoveredPeer(
    val deviceAddress: String,
    val userId: String,
    val nickname: String,
    val avatarId: String = "avatar_1",
    val bio: String = "",
    val primaryActivity: String = "",
    val activities: List<String> = emptyList(),
    val interests: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
    val availability: AvailabilityStatus = AvailabilityStatus.AVAILABLE,
    val connectionStatus: PeerConnectionStatus = PeerConnectionStatus.DISCOVERED,
    val activityMatchScore: Int = 0,
    val lastSeenTimestamp: Long = System.currentTimeMillis()
)

@Serializable
enum class MessageType {
    TEXT,
    FILE,
    SYSTEM
}

@Serializable
enum class MessageDeliveryStatus {
    SENDING,
    DELIVERED,
    READ,
    FAILED
}

@Serializable
data class ChatMessage(
    val messageId: String,
    val conversationId: String,
    val senderId: String,
    val receiverId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val messageType: MessageType = MessageType.TEXT,
    val content: String,
    val status: MessageDeliveryStatus = MessageDeliveryStatus.SENDING,
    val mediaName: String? = null,
    val mediaSize: Long? = null,
    val mediaUri: String? = null
)

@Serializable
data class ConversationSummary(
    val conversationId: String,
    val peerId: String,
    val peerNickname: String,
    val peerAvatarId: String,
    val lastMessage: String,
    val lastTimestamp: Long,
    val unreadCount: Int = 0,
    val isConnected: Boolean = false,
    val isGroup: Boolean = false,
    val groupName: String? = null
)

@Serializable
data class ActivityItem(
    val id: String,
    val title: String,
    val category: String,
    val iconName: String,
    val description: String = "",
    val isSelected: Boolean = false
)

@Serializable
data class LocalReport(
    val reportId: String,
    val peerId: String,
    val peerNickname: String,
    val reason: String,
    val details: String,
    val timestamp: Long = System.currentTimeMillis()
)
