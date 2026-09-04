package com.example.nearby.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val userId: String,
    val nickname: String,
    val avatarId: String = "avatar_1",
    val bio: String = "",
    val interestsJson: String = "[]",
    val activitiesJson: String = "[]",
    val languagesJson: String = "[]",
    val availabilityStatus: String = "AVAILABLE",
    val currentStatusMessage: String = "",
    val pinSaltAndHash: String? = null,
    val isLocked: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val conversationId: String,
    val peerId: String,
    val peerNickname: String,
    val peerAvatarId: String,
    val lastMessage: String = "",
    val lastTimestamp: Long = System.currentTimeMillis(),
    val unreadCount: Int = 0,
    val isGroup: Boolean = false,
    val groupName: String? = null
)

@Serializable
@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["conversationId"]),
        Index(value = ["timestamp"])
    ]
)
data class MessageEntity(
    @PrimaryKey val messageId: String,
    val conversationId: String,
    val senderId: String,
    val receiverId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val messageType: String = "TEXT", // TEXT, FILE, SYSTEM
    val content: String,
    val status: String = "DELIVERED", // SENDING, DELIVERED, READ, FAILED
    val mediaName: String? = null,
    val mediaSize: Long? = null,
    val mediaUri: String? = null
)

@Serializable
@Entity(tableName = "connections")
data class ConnectionEntity(
    @PrimaryKey val connectionId: String,
    val peerId: String,
    val peerNickname: String,
    val deviceAddress: String,
    val status: String = "CONNECTED",
    val connectedAt: Long = System.currentTimeMillis(),
    val lastSeen: Long = System.currentTimeMillis()
)

@Serializable
@Entity(tableName = "activities")
data class ActivityEntity(
    @PrimaryKey val activityId: String,
    val name: String,
    val category: String,
    val iconName: String,
    val isCustom: Boolean = false
)

@Serializable
@Entity(tableName = "blocked_users")
data class BlockedUserEntity(
    @PrimaryKey val peerId: String,
    val peerNickname: String,
    val reason: String = "",
    val blockedAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val key: String,
    val value: String
)

@Serializable
@Entity(tableName = "backup_metadata")
data class BackupMetadataEntity(
    @PrimaryKey val backupId: String,
    val createdAt: Long = System.currentTimeMillis(),
    val version: Int = 1,
    val checksum: String = ""
)

@Serializable
@Entity(tableName = "reports")
data class ReportEntity(
    @PrimaryKey val reportId: String,
    val peerId: String,
    val peerNickname: String,
    val reason: String,
    val details: String,
    val timestamp: Long = System.currentTimeMillis()
)
