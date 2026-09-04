package com.example.nearby.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.nearby.domain.model.DiscoveredPeer
import com.example.nearby.ui.NexVoraViewModel
import com.example.nearby.ui.screens.AboutDialog
import com.example.nearby.ui.screens.ActivitiesScreen
import com.example.nearby.ui.screens.BackupDialog
import com.example.nearby.ui.screens.BlockedUsersDialog
import com.example.nearby.ui.screens.ChatDetailScreen
import com.example.nearby.ui.screens.ChatListScreen
import com.example.nearby.ui.screens.ConnectionRequestDialog
import com.example.nearby.ui.screens.CreateProfileScreen
import com.example.nearby.ui.screens.DeleteAccountDialog
import com.example.nearby.ui.screens.HomeScreen
import com.example.nearby.ui.screens.NearbyScreen
import com.example.nearby.ui.screens.OnboardingScreen
import com.example.nearby.ui.screens.PeerProfileDialog
import com.example.nearby.ui.screens.PinUnlockScreen
import com.example.nearby.ui.screens.PrivacyDialog
import com.example.nearby.ui.screens.ProfileScreen
import com.example.nearby.ui.screens.ReportsDialog
import com.example.nearby.ui.screens.SecurityDialog

enum class AppDestination(val route: String, val title: String, val icon: ImageVector) {
    HOME("home", "Home", Icons.Default.Home),
    NEARBY("nearby", "Nearby", Icons.Default.Radar),
    CHATS("chats", "Chats", Icons.Default.Chat),
    ACTIVITIES("activities", "Activities", Icons.Default.SportsEsports),
    PROFILE("profile", "Profile", Icons.Default.Person)
}

@Composable
fun NexVoraApp(
    viewModel: NexVoraViewModel = viewModel()
) {
    val navController = rememberNavController()
    val userProfile by viewModel.userProfile.collectAsState()
    val uiNotice by viewModel.uiNotice.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiNotice) {
        uiNotice?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearUiNotice()
        }
    }

    // Check lock state & determine start destination
    val startDestination = remember(userProfile) {
        when {
            userProfile == null -> "onboarding"
            userProfile!!.isLocked && userProfile!!.hasPin -> "unlock"
            else -> "main"
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable("onboarding") {
            OnboardingScreen(
                onGetStarted = {
                    navController.navigate("create_profile") {
                        popUpTo("onboarding") { inclusive = true }
                    }
                }
            )
        }

        composable("create_profile") {
            CreateProfileScreen(
                onProfileCreated = { nick, bio, act, interests, lang, avatar, pin ->
                    viewModel.createLocalProfile(nick, bio, act, interests, lang, avatar, pin)
                    navController.navigate("main") {
                        popUpTo("create_profile") { inclusive = true }
                    }
                }
            )
        }

        composable("unlock") {
            var unlockError by remember { mutableStateOf<String?>(null) }
            val currentProfile = userProfile
            if (currentProfile != null) {
                PinUnlockScreen(
                    userProfile = currentProfile,
                    onUnlock = { pin ->
                        viewModel.unlockAccount(pin) { success ->
                            if (success) {
                                navController.navigate("main") {
                                    popUpTo("unlock") { inclusive = true }
                                }
                            } else {
                                unlockError = "Incorrect PIN"
                            }
                        }
                    },
                    errorMessage = unlockError
                )
            }
        }

        composable("main") {
            MainTabsScaffold(
                viewModel = viewModel,
                onOpenChatDetail = { conversationId ->
                    navController.navigate("chat_detail/$conversationId")
                },
                onLockTriggered = {
                    navController.navigate("unlock") {
                        popUpTo("main") { inclusive = true }
                    }
                },
                snackbarHostState = snackbarHostState
            )
        }

        composable("chat_detail/{conversationId}") { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getString("conversationId") ?: ""
            val activePeer by viewModel.activeConnectedPeer.collectAsState()
            val conversations by viewModel.conversations.collectAsState()
            val targetConv = conversations.find { it.conversationId == conversationId }

            val peerName = targetConv?.peerNickname
                ?: activePeer?.nickname
                ?: if (conversationId == "conv_group_activity") "Nearby Activity Group" else "Nearby Peer"
            val peerAvatar = targetConv?.peerAvatarId ?: activePeer?.avatarId ?: "avatar_1"
            val receiverId = targetConv?.peerId ?: activePeer?.userId ?: "GROUP"

            val messages by viewModel.chatRepo.getMessagesFlow(conversationId).collectAsState(initial = emptyList())

            val activeSafetyNumber by viewModel.activeSafetyNumber.collectAsState()
            val isPeerVerified by viewModel.isPeerVerified.collectAsState()

            ChatDetailScreen(
                conversationId = conversationId,
                peerNickname = peerName,
                peerAvatarId = peerAvatar,
                messages = messages,
                myUserId = userProfile?.userId ?: "",
                safetyNumber = activeSafetyNumber,
                isPeerVerified = isPeerVerified,
                onTogglePeerVerification = { viewModel.togglePeerVerification() },
                onSendMessage = { text ->
                    viewModel.sendChatMessage(conversationId, receiverId, text)
                },
                onSendFile = { fileName, size ->
                    viewModel.sendSimulatedFile(conversationId, receiverId, fileName, size)
                },
                onBlockPeer = {
                    if (receiverId != "GROUP") {
                        viewModel.blockUser(receiverId, peerName)
                        navController.popBackStack()
                    }
                },
                onClearChat = {
                    viewModel.clearChat(conversationId)
                },
                onBack = { navController.popBackStack() }
            )
        }
    }
}

@Composable
fun MainTabsScaffold(
    viewModel: NexVoraViewModel,
    onOpenChatDetail: (String) -> Unit,
    onLockTriggered: () -> Unit,
    snackbarHostState: SnackbarHostState
) {
    var selectedTab by remember { mutableStateOf(AppDestination.HOME) }

    val userProfile by viewModel.userProfile.collectAsState()
    val wifiState by viewModel.wifiState.collectAsState()
    val peers by viewModel.discoveredPeers.collectAsState()
    val activeConnectedPeer by viewModel.activeConnectedPeer.collectAsState()
    val incomingRequest by viewModel.incomingConnectionRequest.collectAsState()
    val isSandboxMode by viewModel.isSandboxTestingMode.collectAsState()
    val conversations by viewModel.conversations.collectAsState()
    val blockedUsers by viewModel.blockedUsers.collectAsState()
    val localReports by viewModel.localReports.collectAsState()

    // Dialog states
    var selectedPeerForDetail by remember { mutableStateOf<DiscoveredPeer?>(null) }
    var showSecurityDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var showBlockedDialog by remember { mutableStateOf(false) }
    var showReportsDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var lastExportedBackup by remember { mutableStateOf<String?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.testTag("bottom_navigation_bar")
            ) {
                AppDestination.values().forEach { destination ->
                    val isSelected = selectedTab == destination
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selectedTab = destination },
                        icon = { Icon(destination.icon, contentDescription = destination.title) },
                        label = { Text(destination.title, fontSize = 11.sp) },
                        modifier = Modifier.testTag("nav_item_${destination.route}")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                AppDestination.HOME -> {
                    HomeScreen(
                        userProfile = userProfile,
                        wifiState = wifiState,
                        activeConnectedPeer = activeConnectedPeer,
                        isSandboxMode = isSandboxMode,
                        onToggleSandbox = { viewModel.toggleSandboxMode(it) },
                        onNavigateToNearby = { selectedTab = AppDestination.NEARBY },
                        onNavigateToChat = onOpenChatDetail,
                        onDisconnectPeer = { viewModel.disconnectActivePeer() },
                        onUpdateAvailability = { status, msg -> viewModel.setAvailability(status, msg) }
                    )
                }
                AppDestination.NEARBY -> {
                    NearbyScreen(
                        peers = peers,
                        wifiState = wifiState,
                        isSandboxMode = isSandboxMode,
                        onStartDiscovery = { viewModel.startPeerDiscovery() },
                        onStopDiscovery = { viewModel.stopPeerDiscovery() },
                        onConnectPeer = { peer -> viewModel.requestConnection(peer) },
                        onSelectPeer = { peer -> selectedPeerForDetail = peer },
                        onToggleSandbox = { viewModel.toggleSandboxMode(it) }
                    )
                }
                AppDestination.CHATS -> {
                    ChatListScreen(
                        conversations = conversations,
                        activePeer = activeConnectedPeer,
                        onConversationClick = onOpenChatDetail
                    )
                }
                AppDestination.ACTIVITIES -> {
                    ActivitiesScreen(
                        userProfile = userProfile,
                        onUpdateActivities = { newActivities ->
                            val p = userProfile
                            if (p != null) {
                                viewModel.updateProfile(
                                    nickname = p.nickname,
                                    bio = p.bio,
                                    activities = newActivities,
                                    interests = p.interests,
                                    languages = p.languages,
                                    avatarId = p.avatarId
                                )
                            }
                        },
                        onOpenGroupChat = { onOpenChatDetail("conv_group_activity") }
                    )
                }
                AppDestination.PROFILE -> {
                    ProfileScreen(
                        userProfile = userProfile,
                        onUpdateAvailability = { status, msg -> viewModel.setAvailability(status, msg) },
                        onLockAccount = {
                            viewModel.lockAccount()
                            onLockTriggered()
                        },
                        onOpenSecurity = { showSecurityDialog = true },
                        onOpenPrivacy = { showPrivacyDialog = true },
                        onOpenBackup = { showBackupDialog = true },
                        onOpenBlocked = { showBlockedDialog = true },
                        onOpenReports = { showReportsDialog = true },
                        onOpenAbout = { showAboutDialog = true },
                        onDeleteAccount = { showDeleteAccountDialog = true }
                    )
                }
            }
        }
    }

    // Active incoming connection request alert
    incomingRequest?.let { peer ->
        ConnectionRequestDialog(
            peer = peer,
            onAccept = {
                viewModel.acceptIncomingRequest(peer)
                onOpenChatDetail("conv_${peer.userId}")
            },
            onDecline = {
                viewModel.declineIncomingRequest(peer)
            }
        )
    }

    // Peer Profile Details dialog
    selectedPeerForDetail?.let { peer ->
        PeerProfileDialog(
            peer = peer,
            onDismiss = { selectedPeerForDetail = null },
            onConnect = {
                selectedPeerForDetail = null
                viewModel.requestConnection(peer)
            },
            onBlock = {
                selectedPeerForDetail = null
                viewModel.blockUser(peer.userId, peer.nickname)
            },
            onReport = {
                selectedPeerForDetail = null
                viewModel.submitReport(peer.userId, peer.nickname, "User report", "Reported from peer detail")
            }
        )
    }

    // Security Dialog
    if (showSecurityDialog) {
        SecurityDialog(
            onDismiss = { showSecurityDialog = false },
            onSetPin = { pin -> viewModel.setOrChangePin(pin) }
        )
    }

    // Privacy Dialog
    if (showPrivacyDialog) {
        PrivacyDialog(onDismiss = { showPrivacyDialog = false })
    }

    // Encrypted Backup Dialog
    if (showBackupDialog) {
        BackupDialog(
            onDismiss = { showBackupDialog = false },
            onExport = { pin ->
                viewModel.exportEncryptedBackup(pin) { result ->
                    lastExportedBackup = result
                }
            },
            onImport = { data, pin ->
                viewModel.importEncryptedBackup(data, pin) { success ->
                    if (success) showBackupDialog = false
                }
            },
            lastExportedBackup = lastExportedBackup
        )
    }

    // Blocked Users Dialog
    if (showBlockedDialog) {
        BlockedUsersDialog(
            blockedUsers = blockedUsers,
            onDismiss = { showBlockedDialog = false },
            onUnblock = { peerId -> viewModel.unblockUser(peerId) }
        )
    }

    // Local Reports Dialog
    if (showReportsDialog) {
        ReportsDialog(
            reports = localReports,
            onDismiss = { showReportsDialog = false }
        )
    }

    // About Dialog
    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false })
    }

    // Delete Account Confirmation Dialog
    if (showDeleteAccountDialog) {
        DeleteAccountDialog(
            onDismiss = { showDeleteAccountDialog = false },
            onConfirmDelete = {
                viewModel.deleteAccount()
            }
        )
    }
}
