package com.example.nearby.data.repository

import com.example.nearby.data.local.AppDatabase
import com.example.nearby.data.local.entity.ActivityEntity
import com.example.nearby.data.local.entity.BackupMetadataEntity
import com.example.nearby.data.local.entity.BlockedUserEntity
import com.example.nearby.data.local.entity.ConnectionEntity
import com.example.nearby.data.local.entity.ConversationEntity
import com.example.nearby.data.local.entity.MessageEntity
import com.example.nearby.data.local.entity.ReportEntity
import com.example.nearby.data.local.entity.SettingsEntity
import com.example.nearby.data.local.entity.UserEntity
import com.example.nearby.domain.matcher.ActivityMatcher
import com.example.nearby.domain.model.AvailabilityStatus
import com.example.nearby.domain.model.ChatMessage
import com.example.nearby.domain.model.ConversationSummary
import com.example.nearby.domain.model.DiscoveredPeer
import com.example.nearby.domain.model.LocalReport
import com.example.nearby.domain.model.MessageDeliveryStatus
import com.example.nearby.domain.model.MessageType
import com.example.nearby.domain.model.PeerConnectionStatus
import com.example.nearby.domain.model.UserProfile
import com.example.nearby.domain.security.CryptoManager
import com.example.nearby.network.protocol.defaultJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

class UserRepository(private val db: AppDatabase) {

    val userFlow: Flow<UserProfile?> = db.userDao().getUserFlow().map { entity ->
        entity?.toDomain()
    }

    suspend fun getUser(): UserProfile? = withContext(Dispatchers.IO) {
        db.userDao().getUser()?.toDomain()
    }

    suspend fun saveProfile(profile: UserProfile, pin: String? = null) = withContext(Dispatchers.IO) {
        val existing = db.userDao().getUser()
        val pinHash = if (!pin.isNullOrBlank()) {
            CryptoManager.hashPin(pin)
        } else {
            existing?.pinSaltAndHash
        }

        val entity = UserEntity(
            userId = profile.userId,
            nickname = profile.nickname,
            avatarId = profile.avatarId,
            bio = profile.bio,
            interestsJson = defaultJson.encodeToString(profile.interests),
            activitiesJson = defaultJson.encodeToString(profile.activities),
            languagesJson = defaultJson.encodeToString(profile.languages),
            availabilityStatus = profile.availability.name,
            currentStatusMessage = profile.currentStatusMessage,
            pinSaltAndHash = pinHash,
            isLocked = false,
            createdAt = existing?.createdAt ?: profile.createdAt
        )
        db.userDao().insertUser(entity)
    }

    suspend fun lockAccount() = withContext(Dispatchers.IO) {
        val user = db.userDao().getUser() ?: return@withContext
        db.userDao().updateUser(user.copy(isLocked = true))
    }

    suspend fun unlockAccount(enteredPin: String): Boolean = withContext(Dispatchers.IO) {
        val user = db.userDao().getUser() ?: return@withContext false
        val storedHash = user.pinSaltAndHash
        if (storedHash.isNullOrBlank()) {
            db.userDao().updateUser(user.copy(isLocked = false))
            return@withContext true
        }
        val isValid = CryptoManager.verifyPin(enteredPin, storedHash)
        if (isValid) {
            db.userDao().updateUser(user.copy(isLocked = false))
            return@withContext true
        }
        return@withContext false
    }

    suspend fun updateAvailability(status: AvailabilityStatus, message: String) = withContext(Dispatchers.IO) {
        val user = db.userDao().getUser() ?: return@withContext
        db.userDao().updateUser(
            user.copy(
                availabilityStatus = status.name,
                currentStatusMessage = message
            )
        )
    }

    suspend fun deleteAccount() = withContext(Dispatchers.IO) {
        db.userDao().deleteAllUsers()
        db.conversationDao().deleteAllConversations()
        db.messageDao().deleteAllMessages()
        db.connectionDao().deleteAllConnections()
        db.blockedUserDao().deleteAllBlocked()
        db.settingsDao().deleteAllSettings()
        db.reportDao().deleteAllReports()
    }

    private fun UserEntity.toDomain(): UserProfile {
        val interestsList = try {
            defaultJson.decodeFromString<List<String>>(interestsJson)
        } catch (_: Exception) { emptyList() }
        val activitiesList = try {
            defaultJson.decodeFromString<List<String>>(activitiesJson)
        } catch (_: Exception) { emptyList() }
        val languagesList = try {
            defaultJson.decodeFromString<List<String>>(languagesJson)
        } catch (_: Exception) { emptyList() }

        val avail = try {
            AvailabilityStatus.valueOf(availabilityStatus)
        } catch (_: Exception) { AvailabilityStatus.AVAILABLE }

        return UserProfile(
            userId = userId,
            nickname = nickname,
            avatarId = avatarId,
            bio = bio,
            interests = interestsList,
            activities = activitiesList,
            languages = languagesList,
            availability = avail,
            currentStatusMessage = currentStatusMessage,
            hasPin = !pinSaltAndHash.isNullOrBlank(),
            isLocked = isLocked,
            createdAt = createdAt
        )
    }
}

class ChatRepository(private val db: AppDatabase) {

    val conversationsFlow: Flow<List<ConversationSummary>> =
        db.conversationDao().getAllConversationsFlow().map { list ->
            list.map { it.toSummary() }
        }

    fun getMessagesFlow(conversationId: String): Flow<List<ChatMessage>> =
        db.messageDao().getMessagesForConversationFlow(conversationId).map { list ->
            list.map { it.toDomain() }
        }

    suspend fun sendMessage(
        conversationId: String,
        senderId: String,
        receiverId: String,
        content: String,
        messageType: MessageType = MessageType.TEXT,
        mediaName: String? = null,
        mediaSize: Long? = null,
        mediaUri: String? = null
    ): ChatMessage = withContext(Dispatchers.IO) {
        val msgId = "msg_${System.currentTimeMillis()}_${(1000..9999).random()}"
        val entity = MessageEntity(
            messageId = msgId,
            conversationId = conversationId,
            senderId = senderId,
            receiverId = receiverId,
            timestamp = System.currentTimeMillis(),
            messageType = messageType.name,
            content = content,
            status = "DELIVERED",
            mediaName = mediaName,
            mediaSize = mediaSize,
            mediaUri = mediaUri
        )
        db.messageDao().insertMessage(entity)
        db.conversationDao().updateLastMessage(conversationId, content, entity.timestamp)
        entity.toDomain()
    }

    suspend fun saveIncomingMessage(message: ChatMessage, peerNickname: String, peerAvatarId: String) =
        withContext(Dispatchers.IO) {
            // Check if user is blocked
            if (db.blockedUserDao().isBlocked(message.senderId)) {
                return@withContext
            }

            // Ensure conversation exists
            val existing = db.conversationDao().getConversationById(message.conversationId)
            if (existing == null) {
                db.conversationDao().insertConversation(
                    ConversationEntity(
                        conversationId = message.conversationId,
                        peerId = message.senderId,
                        peerNickname = peerNickname,
                        peerAvatarId = peerAvatarId,
                        lastMessage = message.content,
                        lastTimestamp = message.timestamp,
                        unreadCount = 1
                    )
                )
            } else {
                db.conversationDao().updateLastMessage(
                    message.conversationId,
                    message.content,
                    message.timestamp
                )
            }

            db.messageDao().insertMessage(
                MessageEntity(
                    messageId = message.messageId,
                    conversationId = message.conversationId,
                    senderId = message.senderId,
                    receiverId = message.receiverId,
                    timestamp = message.timestamp,
                    messageType = message.messageType.name,
                    content = message.content,
                    status = "DELIVERED",
                    mediaName = message.mediaName,
                    mediaSize = message.mediaSize,
                    mediaUri = message.mediaUri
                )
            )
        }

    suspend fun createOrGetConversation(peer: DiscoveredPeer): String = withContext(Dispatchers.IO) {
        val convId = "conv_${peer.userId}"
        val existing = db.conversationDao().getConversationById(convId)
        if (existing == null) {
            db.conversationDao().insertConversation(
                ConversationEntity(
                    conversationId = convId,
                    peerId = peer.userId,
                    peerNickname = peer.nickname,
                    peerAvatarId = peer.avatarId,
                    lastMessage = "Started direct chat",
                    lastTimestamp = System.currentTimeMillis(),
                    unreadCount = 0
                )
            )
        }
        convId
    }

    suspend fun markAsRead(conversationId: String) = withContext(Dispatchers.IO) {
        db.conversationDao().markAsRead(conversationId)
    }

    suspend fun clearConversation(conversationId: String) = withContext(Dispatchers.IO) {
        db.messageDao().deleteMessagesForConversation(conversationId)
        db.conversationDao().deleteConversation(conversationId)
    }

    private fun ConversationEntity.toSummary() = ConversationSummary(
        conversationId = conversationId,
        peerId = peerId,
        peerNickname = peerNickname,
        peerAvatarId = peerAvatarId,
        lastMessage = lastMessage,
        lastTimestamp = lastTimestamp,
        unreadCount = unreadCount,
        isConnected = false,
        isGroup = isGroup,
        groupName = groupName
    )

    private fun MessageEntity.toDomain() = ChatMessage(
        messageId = messageId,
        conversationId = conversationId,
        senderId = senderId,
        receiverId = receiverId,
        timestamp = timestamp,
        messageType = try { MessageType.valueOf(messageType) } catch (_: Exception) { MessageType.TEXT },
        content = content,
        status = try { MessageDeliveryStatus.valueOf(status) } catch (_: Exception) { MessageDeliveryStatus.DELIVERED },
        mediaName = mediaName,
        mediaSize = mediaSize,
        mediaUri = mediaUri
    )
}

class PeerRepository(
    private val db: AppDatabase,
    private val scope: CoroutineScope
) {
    private val _discoveredPeers = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
    val discoveredPeers: StateFlow<List<DiscoveredPeer>> = _discoveredPeers.asStateFlow()

    private val _activeConnectedPeer = MutableStateFlow<DiscoveredPeer?>(null)
    val activeConnectedPeer: StateFlow<DiscoveredPeer?> = _activeConnectedPeer.asStateFlow()

    private val _incomingConnectionRequest = MutableStateFlow<DiscoveredPeer?>(null)
    val incomingConnectionRequest: StateFlow<DiscoveredPeer?> = _incomingConnectionRequest.asStateFlow()

    private val _isSandboxTestingMode = MutableStateFlow(false)
    val isSandboxTestingMode: StateFlow<Boolean> = _isSandboxTestingMode.asStateFlow()

    init {
        // Observe and filter blocked peers
        scope.launch(Dispatchers.IO) {
            refreshPeerListWithBlockedFilter()
        }
    }

    fun setSandboxMode(enabled: Boolean, myProfile: UserProfile?) {
        _isSandboxTestingMode.value = enabled
        if (enabled && myProfile != null) {
            populateSandboxPeers(myProfile)
        } else if (!enabled) {
            _discoveredPeers.value = emptyList()
        }
    }

    private fun populateSandboxPeers(myProfile: UserProfile) {
        val sandboxProfiles = listOf(
            DiscoveredPeer(
                deviceAddress = "02:00:00:00:01:00",
                userId = "NV-4A12-8E31",
                nickname = "Alex",
                avatarId = "avatar_2",
                bio = "Kotlin enthusiast & coffee brewer. Up for code pairing.",
                primaryActivity = "Programming",
                activities = listOf("Programming", "Study Buddy", "Gaming"),
                interests = listOf("Android", "Kotlin", "Open Source", "Algorithms"),
                languages = listOf("English", "Spanish"),
                availability = AvailabilityStatus.AVAILABLE,
                connectionStatus = PeerConnectionStatus.DISCOVERED,
                activityMatchScore = 90
            ),
            DiscoveredPeer(
                deviceAddress = "02:00:00:00:02:00",
                userId = "NV-8D99-2B71",
                nickname = "Rahim",
                avatarId = "avatar_3",
                bio = "Competitive board gamer and basketball player.",
                primaryActivity = "Gaming",
                activities = listOf("Gaming", "Board Games", "Sports"),
                interests = listOf("Chess", "Strategy", "Fitness"),
                languages = listOf("English", "Bengali"),
                availability = AvailabilityStatus.AVAILABLE,
                connectionStatus = PeerConnectionStatus.DISCOVERED,
                activityMatchScore = 70
            ),
            DiscoveredPeer(
                deviceAddress = "02:00:00:00:03:00",
                userId = "NV-1C55-7F22",
                nickname = "Hasan",
                avatarId = "avatar_4",
                bio = "Preparing for IELTS & tech certifications.",
                primaryActivity = "Study Buddy",
                activities = listOf("Study Buddy", "Language Practice"),
                interests = listOf("Language", "Reading", "Certifications"),
                languages = listOf("English", "Arabic"),
                availability = AvailabilityStatus.AVAILABLE,
                connectionStatus = PeerConnectionStatus.DISCOVERED,
                activityMatchScore = 80
            ),
            DiscoveredPeer(
                deviceAddress = "02:00:00:00:04:00",
                userId = "NV-9B34-6E11",
                nickname = "Sam",
                avatarId = "avatar_5",
                bio = "Acoustic guitarist and watercolor sketcher.",
                primaryActivity = "Creative Hobby",
                activities = listOf("Creative Hobby", "Group Activity"),
                interests = listOf("Art", "Music", "Acoustic", "Design"),
                languages = listOf("English"),
                availability = AvailabilityStatus.BUSY,
                connectionStatus = PeerConnectionStatus.DISCOVERED,
                activityMatchScore = 50
            )
        )

        // Calculate deterministic score for each against myProfile
        _discoveredPeers.value = sandboxProfiles.map { peer ->
            val match = ActivityMatcher.calculateMatch(
                myProfile = myProfile,
                peerPrimaryActivity = peer.primaryActivity,
                peerActivities = peer.activities,
                peerInterests = peer.interests,
                peerLanguages = peer.languages,
                peerAvailability = peer.availability
            )
            peer.copy(activityMatchScore = match.totalScore)
        }
    }

    suspend fun updateDiscoveredPeer(peer: DiscoveredPeer, myProfile: UserProfile?) = withContext(Dispatchers.IO) {
        if (db.blockedUserDao().isBlocked(peer.userId)) return@withContext

        val score = if (myProfile != null) {
            ActivityMatcher.calculateMatch(
                myProfile = myProfile,
                peerPrimaryActivity = peer.primaryActivity,
                peerActivities = peer.activities,
                peerInterests = peer.interests,
                peerLanguages = peer.languages,
                peerAvailability = peer.availability
            ).totalScore
        } else {
            0
        }

        val updated = peer.copy(activityMatchScore = score)
        val current = _discoveredPeers.value.toMutableList()
        val index = current.indexOfFirst { it.userId == peer.userId || it.deviceAddress == peer.deviceAddress }
        if (index >= 0) {
            current[index] = updated
        } else {
            current.add(updated)
        }
        _discoveredPeers.value = current
    }

    fun requestConnection(peer: DiscoveredPeer) {
        updatePeerStatus(peer.userId, PeerConnectionStatus.CONNECTION_REQUEST_SENT)
    }

    fun receiveConnectionRequest(peer: DiscoveredPeer) {
        _incomingConnectionRequest.value = peer
        updatePeerStatus(peer.userId, PeerConnectionStatus.CONNECTION_REQUEST_RECEIVED)
    }

    fun acceptConnection(peer: DiscoveredPeer) {
        _incomingConnectionRequest.value = null
        updatePeerStatus(peer.userId, PeerConnectionStatus.CONNECTED)
        _activeConnectedPeer.value = peer.copy(connectionStatus = PeerConnectionStatus.CONNECTED)
    }

    fun declineConnection(peer: DiscoveredPeer) {
        _incomingConnectionRequest.value = null
        updatePeerStatus(peer.userId, PeerConnectionStatus.DISCONNECTED)
    }

    fun disconnectPeer(peerId: String) {
        updatePeerStatus(peerId, PeerConnectionStatus.DISCONNECTED)
        if (_activeConnectedPeer.value?.userId == peerId) {
            _activeConnectedPeer.value = null
        }
    }

    private fun updatePeerStatus(userId: String, status: PeerConnectionStatus) {
        val current = _discoveredPeers.value.toMutableList()
        val index = current.indexOfFirst { it.userId == userId }
        if (index >= 0) {
            current[index] = current[index].copy(connectionStatus = status)
            _discoveredPeers.value = current
        }
    }

    suspend fun refreshPeerListWithBlockedFilter() = withContext(Dispatchers.IO) {
        val blockedIds = db.blockedUserDao().getBlockedUsers().map { it.peerId }.toSet()
        _discoveredPeers.value = _discoveredPeers.value.filterNot { blockedIds.contains(it.userId) }
    }

    suspend fun updateRawWifiPeers(peers: List<DiscoveredPeer>) = withContext(Dispatchers.IO) {
        val blockedIds = db.blockedUserDao().getBlockedUsers().map { it.peerId }.toSet()
        _discoveredPeers.value = peers.filterNot { blockedIds.contains(it.userId) }
    }
}

class BlockAndReportRepository(private val db: AppDatabase) {

    val blockedUsersFlow: Flow<List<BlockedUserEntity>> = db.blockedUserDao().getBlockedUsersFlow()
    val reportsFlow: Flow<List<LocalReport>> = db.reportDao().getAllReportsFlow().map { list ->
        list.map {
            LocalReport(
                reportId = it.reportId,
                peerId = it.peerId,
                peerNickname = it.peerNickname,
                reason = it.reason,
                details = it.details,
                timestamp = it.timestamp
            )
        }
    }

    suspend fun blockUser(peerId: String, peerNickname: String, reason: String = "") =
        withContext(Dispatchers.IO) {
            db.blockedUserDao().blockUser(
                BlockedUserEntity(
                    peerId = peerId,
                    peerNickname = peerNickname,
                    reason = reason,
                    blockedAt = System.currentTimeMillis()
                )
            )
            // Disconnect peer connection record if present
            db.connectionDao().deleteConnection(peerId)
        }

    suspend fun unblockUser(peerId: String) = withContext(Dispatchers.IO) {
        db.blockedUserDao().unblockUser(peerId)
    }

    suspend fun submitReport(peerId: String, peerNickname: String, reason: String, details: String) =
        withContext(Dispatchers.IO) {
            val reportId = "rep_${System.currentTimeMillis()}_${(1000..9999).random()}"
            db.reportDao().insertReport(
                ReportEntity(
                    reportId = reportId,
                    peerId = peerId,
                    peerNickname = peerNickname,
                    reason = reason,
                    details = details,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
}

@Serializable
data class BackupContainer(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val user: UserEntity?,
    val conversations: List<ConversationEntity>,
    val messages: List<MessageEntity>,
    val activities: List<ActivityEntity>,
    val blockedUsers: List<BlockedUserEntity>,
    val reports: List<ReportEntity>
)

class BackupRepository(private val db: AppDatabase) {

    suspend fun exportEncryptedBackup(pin: String): String = withContext(Dispatchers.IO) {
        val user = db.userDao().getUser()
        val allConversations = db.conversationDao().getAllConversations()
        val allMessages = db.messageDao().getAllMessages()
        val container = BackupContainer(
            user = user,
            conversations = allConversations,
            messages = allMessages,
            activities = db.activityDao().getAllActivities(),
            blockedUsers = db.blockedUserDao().getBlockedUsers(),
            reports = db.reportDao().getAllReports()
        )

        val jsonString = defaultJson.encodeToString(container)
        val encryptedBackupString = CryptoManager.encryptBackup(jsonString, pin)

        db.backupMetadataDao().insertBackup(
            BackupMetadataEntity(
                backupId = "bkp_${System.currentTimeMillis()}",
                createdAt = System.currentTimeMillis(),
                version = 1,
                checksum = "${jsonString.hashCode()}"
            )
        )

        encryptedBackupString
    }

    suspend fun importAndRestoreBackup(backupString: String, pin: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val decryptedJson = CryptoManager.decryptBackup(backupString, pin)
                val container = defaultJson.decodeFromString<BackupContainer>(decryptedJson)

                if (container.user != null) {
                    db.userDao().insertUser(container.user)
                }
                if (container.conversations.isNotEmpty()) {
                    db.conversationDao().insertConversations(container.conversations)
                }
                if (container.messages.isNotEmpty()) {
                    db.messageDao().insertMessages(container.messages)
                }
                for (b in container.blockedUsers) {
                    db.blockedUserDao().blockUser(b)
                }
                for (r in container.reports) {
                    db.reportDao().insertReport(r)
                }
                Result.success(true)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
