package com.example.nearby.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Games
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nearby.domain.model.AvailabilityStatus
import com.example.nearby.network.wifi.WifiDirectState
import com.example.ui.theme.StatusBusy
import com.example.ui.theme.StatusConnecting
import com.example.ui.theme.StatusDnd
import com.example.ui.theme.StatusOnline

@Composable
fun AvailabilityBadge(
    status: AvailabilityStatus,
    modifier: Modifier = Modifier
) {
    val pair: Pair<Color, String> = when (status) {
        AvailabilityStatus.AVAILABLE -> Pair(StatusOnline, "Available")
        AvailabilityStatus.BUSY -> Pair(StatusBusy, "Busy")
        AvailabilityStatus.DO_NOT_DISTURB -> Pair(StatusDnd, "Do Not Disturb")
        AvailabilityStatus.OFFLINE -> Pair(Color.Gray, "Offline")
    }
    val color = pair.first
    val text = pair.second

    Surface(
        modifier = modifier.testTag("availability_badge_${status.name}"),
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color, CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                color = color,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun MatchScoreBadge(
    score: Int,
    modifier: Modifier = Modifier
) {
    val badgeColor = when {
        score >= 80 -> StatusOnline
        score >= 50 -> StatusConnecting
        else -> StatusBusy
    }

    Surface(
        modifier = modifier.testTag("match_score_badge"),
        shape = RoundedCornerShape(8.dp),
        color = badgeColor.copy(alpha = 0.16f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Match $score%",
                color = badgeColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun AvatarBadge(
    avatarId: String,
    modifier: Modifier = Modifier,
    size: Int = 40,
    backgroundColor: Color = MaterialTheme.colorScheme.primaryContainer,
    iconTint: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    val icon: ImageVector = when (avatarId) {
        "avatar_1" -> Icons.Default.Person
        "avatar_2" -> Icons.Default.Code
        "avatar_3" -> Icons.Default.Games
        "avatar_4" -> Icons.Default.School
        "avatar_5" -> Icons.Default.ColorLens
        "avatar_6" -> Icons.Default.FitnessCenter
        "avatar_7" -> Icons.Default.Translate
        "avatar_8" -> Icons.Default.Groups
        else -> Icons.Default.AccountCircle
    }

    Box(
        modifier = modifier
            .size(size.dp)
            .background(backgroundColor, CircleShape)
            .testTag("avatar_$avatarId"),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "Avatar",
            modifier = Modifier.size((size * 0.58f).dp),
            tint = iconTint
        )
    }
}

@Composable
fun WifiStatusCard(
    state: WifiDirectState,
    isSandbox: Boolean,
    onFindNearbyClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (statusTitle, statusColor, isConnected) = when (state) {
        is WifiDirectState.Connected -> Triple(
            if (state.isGroupOwner) "Connected (Group Owner)" else "Connected (Peer Client)",
            StatusOnline,
            true
        )
        is WifiDirectState.Connecting -> Triple("Connecting...", StatusConnecting, false)
        is WifiDirectState.Searching -> Triple("Searching for peers...", StatusConnecting, false)
        is WifiDirectState.DevicesFound -> Triple("${state.count} device(s) found", StatusOnline, false)
        is WifiDirectState.Disabled -> Triple("Wi-Fi Direct Disabled", StatusDnd, false)
        is WifiDirectState.Unavailable -> Triple("Wi-Fi Direct Unavailable", Color.Gray, false)
        is WifiDirectState.Error -> Triple("Discovery Alert: ${state.message}", StatusBusy, false)
        else -> Triple("Nearby Discovery Ready", StatusOnline, false)
    }

    Surface(
        modifier = modifier.testTag("wifi_status_card"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(statusColor.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (state is WifiDirectState.Disabled) Icons.Default.WifiOff else Icons.Default.Wifi,
                    contentDescription = "Wi-Fi Status",
                    tint = statusColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(statusColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isSandbox) "Interactive Test Sandbox Active" else statusTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = if (isSandbox) "Simulated peer networking enabled for instant testing" else "Direct P2P socket • Serverless & Encrypted",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
