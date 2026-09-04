package com.example.nearby.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.nearby.data.local.AppDatabase
import com.example.nearby.data.local.entity.BlockedUserEntity
import com.example.nearby.data.repository.BackupRepository
import com.example.nearby.data.repository.BlockAndReportRepository
import com.example.nearby.data.repository.ChatRepository
import com.example.nearby.data.repository.PeerRepository
import com.example.nearby.data.repository.UserRepository
import com.example.nearby.domain.matcher.ActivityMatcher
import com.example.nearby.domain.model.AvailabilityStatus
import com.example.nearby.domain.model.ChatMessage
import com.example.nearby.domain.model.ConversationSummary
import com.example.nearby.domain.model.DiscoveredPeer
import com.example.nearby.domain.model.LocalReport
import com.example.nearby.domain.model.MessageType
import com.example.nearby.domain.model.PeerConnectionStatus
import com.example.nearby.domain.model.UserProfile
import com.example.nearby.domain.security.CryptoManager
import com.example.nearby.domain.security.IdGenerator
import com.example.nearby.network.protocol.ConnectionRequestPayload
import com.example.nearby.network.protocol.HelloPayload
import com.example.nearby.network.protocol.KeyExchangePayload
import com.example.nearby.network.protocol.MessagePayload
import com.example.nearby.network.protocol.NetworkPacket
import com.example.nearby.network.protocol.PacketType
import com.example.nearby.network.protocol.defaultJson
import com.example.nearby.network.socket.SocketManager
import com.example.nearby.network.wifi.WifiDirectManager
import com.example.nearby.network.wifi.WifiDirectState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import java.security.KeyPair
import javax.crypto.spec.SecretKeySpec

class NexVoraViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    val userRepo = UserRepository(db)
    val chatRepo = ChatRepository(db)
    val peerRepo = PeerRepository(db, viewModelScope)
    val blockRepo = BlockAndReportRepository(db)
    val backupRepo = BackupRepository(db)

    val wifiDirectManager = WifiDirectManager(application, viewModelScope)
    val socketManager = SocketManager(viewModelScope)

    // Current User Profile
    val userProfile: StateFlow<UserProfile?> = userRepo.userFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = null
    )

    // Conversations
    val conversations: StateFlow<List<ConversationSummary>> = chatRepo.conversationsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Discovered Peers
    val discoveredPeers: StateFlow<List<DiscoveredPeer>> = peerRepo.discoveredPeers
    val activeConnectedPeer: StateFlow<DiscoveredPeer?> = peerRepo.activeConnectedPeer
    val incomingConnectionRequest: StateFlow<DiscoveredPeer?> = peerRepo.incomingConnectionRequest
    val isSandboxTestingMode: StateFlow<Boolean> = peerRepo.isSandboxTestingMode

    // Wi-Fi Direct State
    val wifiState: StateFlow<WifiDirectState> = wifiDirectManager.networkState

    // Blocked users & Reports
    val blockedUsers: StateFlow<List<BlockedUserEntity>> = blockRepo.blockedUsersFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
    val localReports: StateFlow<List<LocalReport>> = blockRepo.reportsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Crypto session for connected peer
    private var localEcdhKeyPair: KeyPair? = null
    private var peerSharedKey: SecretKeySpec? = null

    private val _activeSafetyNumber = MutableStateFlow<String?>(null)
    val activeSafetyNumber: StateFlow<String?> = _activeSafetyNumber.asStateFlow()

    private val _isPeerVerified = MutableStateFlow(false)
    val isPeerVerified: StateFlow<Boolean> = _isPeerVerified.asStateFlow()

    // UI Feedback state
    private val _uiNotice = MutableStateFlow<String?>(null)
    val uiNotice: StateFlow<String?> = _uiNotice.asStateFlow()

    // File transfer state (transferId to progress 0-100)
    private val _fileTransferProgress = MutableStateFlow<Map<String, Int>>(emptyMap())
    val fileTransferProgress: StateFlow<Map<String, Int>> = _fileTransferProgress.asStateFlow()

    init {
        wifiDirectManager.initialize()
        observeIncomingPackets()
        observeSocketConnection()
        observeWifiDirectState()
        observeWifiDirectPeers()
    }

    fun clearUiNotice() {
        _uiNotice.value = null
    }

    fun togglePeerVerification() {
        _isPeerVerified.value = !_isPeerVerified.value
        _uiNotice.value = if (_isPeerVerified.value) "Peer identity verified!" else "Peer marked unverified."
    }

    private fun observeWifiDirectState() {
        viewModelScope.launch {
            wifiDirectManager.networkState.collect { state ->
                when (state) {
                    is WifiDirectState.Connected -> {
                        if (state.isGroupOwner) {
                            socketManager.startServer()
                        } else if (!state.groupOwnerAddress.isNullOrBlank()) {
                            socketManager.connectToServer(state.groupOwnerAddress)
                        }
                    }
                    is WifiDirectState.Disconnected -> {
                        socketManager.closeConnection()
                        peerSharedKey = null
                        _activeSafetyNumber.value = null
                        _isPeerVerified.value = false
                    }
                    else -> {}
                }
            }
        }
    }

    private fun observeWifiDirectPeers() {
        viewModelScope.launch {
            wifiDirectManager.rawPeers.collect { devices ->
                if (!isSandboxTestingMode.value) {
                    val myProfile = userProfile.value
                    val mapped = devices.map { dev ->
                        val peer = DiscoveredPeer(
                            deviceAddress = dev.deviceAddress,
                            userId = dev.deviceAddress,
                            nickname = dev.deviceName.ifBlank { "Nearby Device (${dev.deviceAddress.takeLast(5)})" },
                            avatarId = "avatar_1",
                            bio = "Nearby Wi-Fi Direct peer",
                            primaryActivity = "Nearby Social",
                            connectionStatus = when (dev.status) {
                                android.net.wifi.p2p.WifiP2pDevice.CONNECTED -> PeerConnectionStatus.CONNECTED
                                android.net.wifi.p2p.WifiP2pDevice.INVITED -> PeerConnectionStatus.CONNECTION_REQUEST_SENT
                                else -> PeerConnectionStatus.DISCOVERED
                            }
                        )
                        if (myProfile != null) {
                            val match = ActivityMatcher.calculateMatch(
                                myProfile = myProfile,
                                peerPrimaryActivity = peer.primaryActivity,
                                peerActivities = peer.activities,
                                peerInterests = peer.interests,
                                peerLanguages = peer.languages,
                                peerAvailability = peer.availability
                            )
                            peer.copy(activityMatchScore = match.totalScore)
                        } else {
                            peer
                        }
                    }
                    peerRepo.updateRawWifiPeers(mapped)
                }
            }
        }
    }

    private fun observeIncomingPackets() {
        viewModelScope.launch {
            socketManager.incomingPackets.collect { packet ->
                handleIncomingPacket(packet)
            }
        }
    }

    private fun observeSocketConnection() {
        viewModelScope.launch {
            socketManager.connectionState.collect { isConnected ->
                if (isConnected) {
                    // Socket connected! Initiate ECDH key exchange
                    initiateHandshake()
                }
            }
        }
    }

    fun createLocalProfile(
        nickname: String,
        bio: String,
        activities: List<String>,
        interests: List<String>,
        languages: List<String>,
        avatarId: String,
        pin: String?
    ) {
        viewModelScope.launch {
            val newUserId = IdGenerator.generateUserId()
            val profile = UserProfile(
                userId = newUserId,
                nickname = nickname.trim(),
                avatarId = avatarId,
                bio = bio.trim(),
                activities = activities,
                interests = interests,
                languages = languages,
                availability = AvailabilityStatus.AVAILABLE,
                currentStatusMessage = "Looking for nearby activities",
                hasPin = !pin.isNullOrBlank(),
                isLocked = false
            )
            userRepo.saveProfile(profile, pin)
            _uiNotice.value = "Profile created successfully! User ID: $newUserId"
        }
    }

    fun updateProfile(
        nickname: String,
        bio: String,
        activities: List<String>,
        interests: List<String>,
        languages: List<String>,
        avatarId: String
    ) {
        val current = userProfile.value ?: return
        viewModelScope.launch {
            val updated = current.copy(
                nickname = nickname.trim(),
                bio = bio.trim(),
                activities = activities,
                interests = interests,
                languages = languages,
                avatarId = avatarId
            )
            userRepo.saveProfile(updated)
            _uiNotice.value = "Profile updated."
        }
    }

    fun setAvailability(status: AvailabilityStatus, statusMessage: String) {
        viewModelScope.launch {
            userRepo.updateAvailability(status, statusMessage)
            _uiNotice.value = "Status updated to ${status.name}."
        }
    }

    fun lockAccount() {
        viewModelScope.launch {
            userRepo.lockAccount()
        }
    }

    fun unlockAccount(pin: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = userRepo.unlockAccount(pin)
            onResult(success)
        }
    }

    fun setOrChangePin(newPin: String) {
        val current = userProfile.value ?: return
        viewModelScope.launch {
            userRepo.saveProfile(current.copy(hasPin = true), newPin)
            _uiNotice.value = "Security PIN updated."
        }
    }

    fun startPeerDiscovery() {
        wifiDirectManager.startDiscovery()
        if (isSandboxTestingMode.value) {
            peerRepo.setSandboxMode(true, userProfile.value)
        }
    }

    fun stopPeerDiscovery() {
        wifiDirectManager.stopDiscovery()
    }

    fun toggleSandboxMode(enabled: Boolean) {
        peerRepo.setSandboxMode(enabled, userProfile.value)
        if (enabled) {
            _uiNotice.value = "Interactive Test Sandbox active: Discovered 4 test peers."
        } else {
            _uiNotice.value = "Switched to standard Wi-Fi Direct mode."
        }
    }

    fun requestConnection(peer: DiscoveredPeer) {
        peerRepo.requestConnection(peer)
        if (isSandboxTestingMode.value) {
            // Simulate realistic P2P response in sandbox mode
            viewModelScope.launch {
                delay(1200)
                peerRepo.acceptConnection(peer)
                // Set up simulated ECDH session key & Safety Number
                localEcdhKeyPair = CryptoManager.generateEcdhKeyPair()
                val peerKeyBytes = CryptoManager.publicKeyToBase64(localEcdhKeyPair!!)
                val (secretKey, safetyNumber) = CryptoManager.deriveSessionKeyAndSafetyNumber(localEcdhKeyPair!!, peerKeyBytes)
                peerSharedKey = secretKey
                _activeSafetyNumber.value = safetyNumber
                _isPeerVerified.value = false
                chatRepo.createOrGetConversation(peer)
                _uiNotice.value = "Connected securely to ${peer.nickname}!"
            }
        } else {
            // Real Wi-Fi Direct connection
            wifiDirectManager.connectToDevice(
                peer.deviceAddress,
                onSuccess = {
                    _uiNotice.value = "Connection requested to ${peer.nickname}..."
                },
                onFailure = { err ->
                    _uiNotice.value = "Connection failed: $err"
                }
            )
        }
    }

    fun acceptIncomingRequest(peer: DiscoveredPeer) {
        peerRepo.acceptConnection(peer)
        viewModelScope.launch {
            chatRepo.createOrGetConversation(peer)
            _uiNotice.value = "Connected to ${peer.nickname}"
        }
    }

    fun declineIncomingRequest(peer: DiscoveredPeer) {
        peerRepo.declineConnection(peer)
        _uiNotice.value = "Declined connection request"
    }

    fun disconnectActivePeer() {
        val peer = activeConnectedPeer.value ?: return
        peerRepo.disconnectPeer(peer.userId)
        socketManager.closeConnection(sendDisconnectNotice = true)
        wifiDirectManager.disconnect()
        peerSharedKey = null
        _activeSafetyNumber.value = null
        _isPeerVerified.value = false
        _uiNotice.value = "Disconnected from ${peer.nickname}."
    }

    private fun initiateHandshake() {
        viewModelScope.launch(Dispatchers.IO) {
            val keyPair = CryptoManager.generateEcdhKeyPair()
            localEcdhKeyPair = keyPair
            val myPublicKeyBase64 = CryptoManager.publicKeyToBase64(keyPair)
            val packet = NetworkPacket(
                packetId = IdGenerator.generatePacketId(),
                packetType = PacketType.KEY_EXCHANGE,
                senderId = userProfile.value?.userId ?: "UNKNOWN",
                payload = defaultJson.encodeToString(KeyExchangePayload(myPublicKeyBase64))
            )
            socketManager.sendPacket(packet)
        }
    }

    private fun handleIncomingPacket(packet: NetworkPacket) {
        viewModelScope.launch(Dispatchers.IO) {
            when (packet.packetType) {
                PacketType.KEY_EXCHANGE -> {
                    try {
                        val payload = defaultJson.decodeFromString<KeyExchangePayload>(packet.payload)
                        val myKeyPair = localEcdhKeyPair ?: CryptoManager.generateEcdhKeyPair().also { localEcdhKeyPair = it }
                        val (secretKey, safetyNumber) = CryptoManager.deriveSessionKeyAndSafetyNumber(myKeyPair, payload.ecdhPublicKeyBase64)
                        peerSharedKey = secretKey
                        _activeSafetyNumber.value = safetyNumber
                        _isPeerVerified.value = false

                        // Send our profile via HELLO packet
                        val profile = userProfile.value
                        if (profile != null) {
                            val helloPacket = NetworkPacket(
                                packetId = IdGenerator.generatePacketId(),
                                packetType = PacketType.HELLO,
                                senderId = profile.userId,
                                payload = defaultJson.encodeToString(
                                    HelloPayload.serializer(),
                                    HelloPayload(
                                        userId = profile.userId,
                                        nickname = profile.nickname,
                                        avatarId = profile.avatarId,
                                        primaryActivity = profile.activities.firstOrNull() ?: "Nearby Social",
                                        activities = profile.activities,
                                        interests = profile.interests,
                                        languages = profile.languages,
                                        availabilityStatus = profile.availability.name,
                                        currentStatusMessage = profile.currentStatusMessage
                                    )
                                )
                            )
                            socketManager.sendPacket(helloPacket)
                        }
                    } catch (_: Exception) {}
                }
                PacketType.HELLO -> {
                    try {
                        val hello = defaultJson.decodeFromString<HelloPayload>(packet.payload)
                        val myProfile = userProfile.value
                        val peerAvail = try {
                            AvailabilityStatus.valueOf(hello.availabilityStatus)
                        } catch (_: Exception) {
                            AvailabilityStatus.AVAILABLE
                        }
                        val score = if (myProfile != null) {
                            ActivityMatcher.calculateMatch(
                                myProfile = myProfile,
                                peerPrimaryActivity = hello.primaryActivity,
                                peerActivities = hello.activities,
                                peerInterests = hello.interests,
                                peerLanguages = hello.languages,
                                peerAvailability = peerAvail
                            ).totalScore
                        } else 0

                        val updatedPeer = DiscoveredPeer(
                            deviceAddress = "WIFI_DIRECT",
                            userId = hello.userId,
                            nickname = hello.nickname,
                            avatarId = hello.avatarId,
                            bio = hello.currentStatusMessage,
                            primaryActivity = hello.primaryActivity,
                            activities = hello.activities,
                            interests = hello.interests,
                            languages = hello.languages,
                            availability = peerAvail,
                            connectionStatus = PeerConnectionStatus.CONNECTED,
                            activityMatchScore = score
                        )
                        peerRepo.updateDiscoveredPeer(updatedPeer, myProfile)
                        chatRepo.createOrGetConversation(updatedPeer)
                        _uiNotice.value = "Handshake complete with ${hello.nickname} (Match: $score%)"
                    } catch (_: Exception) {}
                }
                PacketType.CONNECTION_REQUEST -> {
                    try {
                        val payload = defaultJson.decodeFromString<ConnectionRequestPayload>(packet.payload)
                        val peer = DiscoveredPeer(
                            deviceAddress = "WIFI_DIRECT",
                            userId = payload.requesterId,
                            nickname = payload.requesterNickname,
                            avatarId = payload.requesterAvatarId,
                            bio = payload.note,
                            primaryActivity = "Nearby Companion",
                            connectionStatus = PeerConnectionStatus.CONNECTION_REQUEST_RECEIVED
                        )
                        peerRepo.receiveConnectionRequest(peer)
                    } catch (_: Exception) {}
                }
                PacketType.MESSAGE -> {
                    try {
                        val payload = defaultJson.decodeFromString<MessagePayload>(packet.payload)
                        val key = peerSharedKey
                        val decryptedText = if (key != null) {
                            CryptoManager.decryptAesGcm(payload.encryptedText, key)
                        } else {
                            "[Encrypted message]"
                        }

                        val chatMsg = ChatMessage(
                            messageId = payload.messageId,
                            conversationId = payload.conversationId,
                            senderId = payload.senderId,
                            receiverId = payload.receiverId,
                            timestamp = payload.timestamp,
                            content = decryptedText
                        )
                        val senderPeer = discoveredPeers.value.find { it.userId == payload.senderId }
                        chatRepo.saveIncomingMessage(
                            chatMsg,
                            peerNickname = senderPeer?.nickname ?: "Peer ${payload.senderId.takeLast(4)}",
                            peerAvatarId = senderPeer?.avatarId ?: "avatar_1"
                        )
                    } catch (_: Exception) {}
                }
                PacketType.DISCONNECT -> {
                    disconnectActivePeer()
                }
                else -> {}
            }
        }
    }

    fun sendChatMessage(conversationId: String, receiverId: String, text: String) {
        if (text.isBlank()) return
        val myUser = userProfile.value ?: return

        viewModelScope.launch {
            val sentMsg = chatRepo.sendMessage(
                conversationId = conversationId,
                senderId = myUser.userId,
                receiverId = receiverId,
                content = text.trim()
            )

            // Encrypt and transmit over TCP socket if connected
            val key = peerSharedKey
            if (key != null && !isSandboxTestingMode.value) {
                try {
                    val encrypted = CryptoManager.encryptAesGcm(text.trim(), key)
                    val packet = NetworkPacket(
                        packetId = IdGenerator.generatePacketId(),
                        packetType = PacketType.MESSAGE,
                        senderId = myUser.userId,
                        payload = defaultJson.encodeToString(
                            MessagePayload(
                                messageId = sentMsg.messageId,
                                conversationId = conversationId,
                                senderId = myUser.userId,
                                receiverId = receiverId,
                                encryptedText = encrypted,
                                timestamp = sentMsg.timestamp
                            )
                        )
                    )
                    socketManager.sendPacket(packet)
                } catch (_: Exception) {}
            }

            // If sandbox mode is on and peer is connected, simulate realistic reply after short pause
            if (isSandboxTestingMode.value) {
                simulateSandboxReply(conversationId, receiverId, text)
            }
        }
    }

    private fun simulateSandboxReply(conversationId: String, receiverId: String, userText: String) {
        viewModelScope.launch {
            delay(1500)
            val peer = discoveredPeers.value.find { it.userId == receiverId }
            val peerName = peer?.nickname ?: "Companion"
            val replyText = when {
                userText.contains("hi", ignoreCase = true) || userText.contains("hello", ignoreCase = true) ->
                    "Hi there! Glad to connect over Wi-Fi Direct. Ready for ${peer?.primaryActivity ?: "our activity"}!"
                userText.contains("study", ignoreCase = true) ->
                    "Sounds awesome! I'm focusing on problem-solving chapters right now."
                userText.contains("game", ignoreCase = true) || userText.contains("play", ignoreCase = true) ->
                    "Count me in! I've got my setup ready for the session."
                else ->
                    "Got your encrypted message! P2P connection latency is under 15ms."
            }

            val replyMsg = ChatMessage(
                messageId = IdGenerator.generateMessageId(),
                conversationId = conversationId,
                senderId = receiverId,
                receiverId = userProfile.value?.userId ?: "",
                timestamp = System.currentTimeMillis(),
                content = replyText
            )
            chatRepo.saveIncomingMessage(
                replyMsg,
                peerNickname = peerName,
                peerAvatarId = peer?.avatarId ?: "avatar_2"
            )
        }
    }

    fun sendSimulatedFile(conversationId: String, receiverId: String, fileName: String, fileSize: Long) {
        val myUser = userProfile.value ?: return
        val transferId = "tx_${System.currentTimeMillis()}"

        viewModelScope.launch {
            // Simulate progress
            for (p in 10..100 step 20) {
                _fileTransferProgress.value = _fileTransferProgress.value + (transferId to p)
                delay(300)
            }
            chatRepo.sendMessage(
                conversationId = conversationId,
                senderId = myUser.userId,
                receiverId = receiverId,
                content = "Sent file: $fileName",
                messageType = MessageType.FILE,
                mediaName = fileName,
                mediaSize = fileSize
            )
            _fileTransferProgress.value = _fileTransferProgress.value - transferId
            _uiNotice.value = "File transfer completed: $fileName (${fileSize / 1024} KB)"
        }
    }

    fun blockUser(peerId: String, peerNickname: String) {
        viewModelScope.launch {
            blockRepo.blockUser(peerId, peerNickname, reason = "Blocked by user")
            peerRepo.disconnectPeer(peerId)
            peerRepo.refreshPeerListWithBlockedFilter()
            _uiNotice.value = "User $peerNickname has been blocked."
        }
    }

    fun clearChat(conversationId: String) {
        viewModelScope.launch {
            chatRepo.clearConversation(conversationId)
            _uiNotice.value = "Chat cleared."
        }
    }

    fun unblockUser(peerId: String) {
        viewModelScope.launch {
            blockRepo.unblockUser(peerId)
            peerRepo.refreshPeerListWithBlockedFilter()
            _uiNotice.value = "User unblocked."
        }
    }

    fun submitReport(peerId: String, peerNickname: String, reason: String, details: String) {
        viewModelScope.launch {
            blockRepo.submitReport(peerId, peerNickname, reason, details)
            _uiNotice.value = "Report recorded locally on this device."
        }
    }

    fun exportEncryptedBackup(pin: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                val backup = backupRepo.exportEncryptedBackup(pin)
                onResult(backup)
                _uiNotice.value = "Encrypted backup created successfully."
            } catch (e: Exception) {
                onResult(null)
                _uiNotice.value = "Backup export failed: ${e.message}"
            }
        }
    }

    fun importEncryptedBackup(backupData: String, pin: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val res = backupRepo.importAndRestoreBackup(backupData, pin)
            if (res.isSuccess) {
                _uiNotice.value = "Account and data restored successfully."
                onResult(true)
            } else {
                _uiNotice.value = "Restore failed: Invalid PIN or corrupted data."
                onResult(false)
            }
        }
    }

    fun deleteAccount() {
        viewModelScope.launch {
            userRepo.deleteAccount()
            peerRepo.disconnectPeer(activeConnectedPeer.value?.userId ?: "")
            socketManager.closeConnection()
            wifiDirectManager.disconnect()
            _uiNotice.value = "Local account and database deleted."
        }
    }

    override fun onCleared() {
        super.onCleared()
        socketManager.closeConnection()
        wifiDirectManager.unregisterReceiver()
    }
}
