package com.example.nearby.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nearby.domain.model.UserProfile
import com.example.nearby.ui.components.AvatarBadge

@Composable
fun OnboardingScreen(
    onGetStarted: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 40.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Wifi,
                    contentDescription = "NexVora Logo",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(54.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "NexVora Nearby",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Connect Nearby. Chat Directly.",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Discover nearby people through direct Wi-Fi Direct. No server, no accounts, no cloud database. Completely offline-first and end-to-end encrypted.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(36.dp))

            // Pillars
            PillarRow(
                icon = Icons.Default.Security,
                title = "Privacy-First Architecture",
                desc = "No email, phone number, or cloud profile required."
            )
            Spacer(modifier = Modifier.height(16.dp))
            PillarRow(
                icon = Icons.Default.Wifi,
                title = "100% Direct Wi-Fi Direct",
                desc = "Device-to-device encrypted TCP sockets without internet."
            )
            Spacer(modifier = Modifier.height(16.dp))
            PillarRow(
                icon = Icons.Default.Lock,
                title = "Local Cryptographic Keys",
                desc = "ECDH secp256r1 key agreement and AES-GCM encryption."
            )
        }

        Button(
            onClick = onGetStarted,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("get_started_button"),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Create Local Profile", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PillarRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    desc: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateProfileScreen(
    onProfileCreated: (
        nickname: String,
        bio: String,
        activities: List<String>,
        interests: List<String>,
        languages: List<String>,
        avatarId: String,
        pin: String?
    ) -> Unit
) {
    var nickname by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var selectedAvatar by remember { mutableStateOf("avatar_1") }
    var optionalPin by remember { mutableStateOf("") }

    val availableActivities = listOf(
        "Study Buddy", "Gaming", "Programming", "Language Practice",
        "Sports", "Board Games", "Creative Hobby", "Group Activity"
    )
    val selectedActivities = remember { mutableStateListOf("Study Buddy", "Programming") }

    val availableInterests = listOf(
        "Android", "Kotlin", "AI / ML", "Chess", "Books",
        "Music", "Coffee", "Fitness", "Design", "Open Source"
    )
    val selectedInterests = remember { mutableStateListOf("Android", "Kotlin") }

    val availableLanguages = listOf("English", "Spanish", "French", "German", "Japanese", "Arabic", "Bengali")
    val selectedLanguages = remember { mutableStateListOf("English") }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(scrollState)
            .padding(24.dp)
    ) {
        Text(
            text = "Create Local Profile",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Stored entirely on this device. No remote server ever touches your data.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Avatar selector
        Text(
            text = "Choose Avatar",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val avatars = listOf("avatar_1", "avatar_2", "avatar_3", "avatar_4", "avatar_5", "avatar_6")
            avatars.forEach { av ->
                val isSelected = av == selectedAvatar
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable { selectedAvatar = av }
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    AvatarBadge(avatarId = av, size = 42)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Nickname
        OutlinedTextField(
            value = nickname,
            onValueChange = { if (it.length <= 30) nickname = it },
            label = { Text("Nickname *") },
            placeholder = { Text("e.g. Alex or NexCoder") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("nickname_input")
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Short Bio
        OutlinedTextField(
            value = bio,
            onValueChange = { if (it.length <= 120) bio = it },
            label = { Text("Short Bio") },
            placeholder = { Text("What are you working on or looking for?") },
            maxLines = 3,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("bio_input")
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Preferred Activities
        Text(
            text = "Preferred Activities (for compatibility matching)",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            availableActivities.forEach { act ->
                val isSelected = selectedActivities.contains(act)
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        if (isSelected) selectedActivities.remove(act) else selectedActivities.add(act)
                    },
                    label = { Text(act) },
                    leadingIcon = if (isSelected) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Interests
        Text(
            text = "Interests & Topics",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            availableInterests.forEach { interest ->
                val isSelected = selectedInterests.contains(interest)
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        if (isSelected) selectedInterests.remove(interest) else selectedInterests.add(interest)
                    },
                    label = { Text(interest) }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Languages
        Text(
            text = "Languages",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            availableLanguages.forEach { lang ->
                val isSelected = selectedLanguages.contains(lang)
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        if (isSelected) selectedLanguages.remove(lang) else selectedLanguages.add(lang)
                    },
                    label = { Text(lang) }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Optional PIN
        OutlinedTextField(
            value = optionalPin,
            onValueChange = { if (it.length <= 6 && it.all { char -> char.isDigit() }) optionalPin = it },
            label = { Text("Optional 4–6 Digit Lock PIN") },
            placeholder = { Text("Leave blank for no PIN") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            leadingIcon = { Icon(Icons.Default.Pin, contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("pin_setup_input")
        )

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = {
                val cleanNick = if (nickname.isBlank()) "User_${(1000..9999).random()}" else nickname.trim()
                onProfileCreated(
                    cleanNick,
                    bio.trim(),
                    selectedActivities.toList(),
                    selectedInterests.toList(),
                    selectedLanguages.toList(),
                    selectedAvatar,
                    if (optionalPin.length in 4..6) optionalPin else null
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("save_profile_button"),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Complete Setup", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun PinUnlockScreen(
    userProfile: UserProfile,
    onUnlock: (String) -> Unit,
    errorMessage: String? = null
) {
    var pin by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AvatarBadge(avatarId = userProfile.avatarId, size = 72)
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Welcome Back",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = userProfile.nickname,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "ID: ${userProfile.userId}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) pin = it },
            label = { Text("Enter PIN") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("pin_unlock_input")
        )

        if (!errorMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { onUnlock(pin) },
            enabled = pin.length >= 4,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("unlock_button"),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Unlock Account", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}
