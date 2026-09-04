package com.example.nearby.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nearby.data.local.entity.BlockedUserEntity
import com.example.nearby.domain.model.DiscoveredPeer
import com.example.nearby.domain.model.LocalReport
import com.example.nearby.domain.model.UserProfile
import com.example.nearby.ui.components.AvatarBadge
import com.example.nearby.ui.components.AvailabilityBadge
import com.example.nearby.ui.components.MatchScoreBadge
import com.example.ui.theme.StatusOnline

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PeerProfileDialog(
    peer: DiscoveredPeer,
    onDismiss: () -> Unit,
    onConnect: () -> Unit,
    onBlock: () -> Unit,
    onReport: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AvatarBadge(avatarId = peer.avatarId, size = 48)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(peer.nickname, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                        Text(peer.userId, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AvailabilityBadge(status = peer.availability)
                    MatchScoreBadge(score = peer.activityMatchScore)
                }

                if (peer.bio.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("About", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    Text(peer.bio, style = MaterialTheme.typography.bodyMedium)
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("Activities", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    peer.activities.forEach { act ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = act,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                if (peer.interests.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Interests", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        peer.interests.forEach { item ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            ) {
                                Text(
                                    text = item,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onBlock,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Block", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = onReport,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Report, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Report", fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConnect,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Connect via Wi-Fi Direct")
            }
        }
    )
}

@Composable
fun ConnectionRequestDialog(
    peer: DiscoveredPeer,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDecline,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AvatarBadge(avatarId = peer.avatarId, size = 44)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("Connection Request", fontWeight = FontWeight.Bold)
                    Text(peer.nickname, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        text = {
            Column {
                Text(
                    text = "${peer.nickname} (${peer.userId}) wants to establish a direct Wi-Fi Direct connection for ${peer.primaryActivity.ifBlank { "nearby activities" }}.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "A secure ECDH handshake will establish an encrypted session key.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onAccept,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("accept_connection_btn")
            ) {
                Text("Accept & Connect")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDecline,
                modifier = Modifier.testTag("decline_connection_btn")
            ) {
                Text("Decline")
            }
        }
    )
}

@Composable
fun SecurityDialog(
    onDismiss: () -> Unit,
    onSetPin: (String) -> Unit
) {
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set or Change Lock PIN") },
        text = {
            Column {
                Text(
                    text = "A 4–6 digit PIN protects local access to your app without sending anything to a server.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) newPin = it },
                    label = { Text("New PIN (4–6 digits)") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) confirmPin = it },
                    label = { Text("Confirm PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorMsg != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(errorMsg!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (newPin.length < 4) {
                        errorMsg = "PIN must be at least 4 digits"
                    } else if (newPin != confirmPin) {
                        errorMsg = "PINs do not match"
                    } else {
                        onSetPin(newPin)
                        onDismiss()
                    }
                }
            ) {
                Text("Save PIN")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun PrivacyDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Privacy Architecture")
            }
        },
        text = {
            Column {
                Text(
                    text = "NexVora Nearby is architected strictly under zero-knowledge principles:\n\n" +
                            "• Zero Cloud Servers: No Firebase, AWS, Supabase, or external API is ever queried.\n" +
                            "• Real P2P: All discovery and chat operate strictly over local Wi-Fi Direct radio.\n" +
                            "• Deterministic Matching: Compatibility scores are computed 100% locally on your phone without AI telemetry.\n" +
                            "• Complete Erasure: All messages and profile data are stored in an encrypted local SQLite database and can be deleted instantly.",
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 22.sp
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Got it") }
        }
    )
}

@Composable
fun BackupDialog(
    onDismiss: () -> Unit,
    onExport: (pin: String) -> Unit,
    onImport: (backupData: String, pin: String) -> Unit,
    lastExportedBackup: String?
) {
    var pin by remember { mutableStateOf("") }
    var importBackupString by remember { mutableStateOf("") }
    var isImportMode by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isImportMode) "Import Encrypted Backup" else "Export Encrypted Backup") },
        text = {
            Column {
                if (!isImportMode) {
                    Text(
                        text = "Your profile, settings, connections, and chat history will be packaged into an AES-GCM encrypted container protected by your PIN.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it },
                        label = { Text("Enter PIN/Passphrase") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (lastExportedBackup != null) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text("Encrypted Backup String:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = lastExportedBackup,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(8.dp),
                                maxLines = 4
                            )
                        }
                    }
                } else {
                    Text(
                        text = "Paste your encrypted backup string and enter the PIN used when exporting.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = importBackupString,
                        onValueChange = { importBackupString = it },
                        label = { Text("Encrypted Backup String") },
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it },
                        label = { Text("Backup PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = { isImportMode = !isImportMode }) {
                    Text(if (isImportMode) "Switch to Export Backup" else "Switch to Import Backup")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isImportMode) {
                        onImport(importBackupString.trim(), pin.trim())
                    } else {
                        onExport(pin.trim())
                    }
                },
                enabled = pin.isNotBlank()
            ) {
                Text(if (isImportMode) "Restore Data" else "Generate Backup")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun BlockedUsersDialog(
    blockedUsers: List<BlockedUserEntity>,
    onDismiss: () -> Unit,
    onUnblock: (peerId: String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Blocked Peers (${blockedUsers.size})") },
        text = {
            if (blockedUsers.isEmpty()) {
                Text("No peers are currently blocked.", style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn(modifier = Modifier.height(200.dp)) {
                    items(blockedUsers, key = { it.peerId }) { b ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(b.peerNickname, fontWeight = FontWeight.Bold)
                                Text(b.peerId, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { onUnblock(b.peerId) }) {
                                Text("Unblock")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Done") }
        }
    )
}

@Composable
fun ReportsDialog(
    reports: List<LocalReport>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Local Privacy Reports (${reports.size})") },
        text = {
            if (reports.isEmpty()) {
                Text("No incident reports on this device.", style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn(modifier = Modifier.height(220.dp)) {
                    items(reports, key = { it.reportId }) { r ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("${r.peerNickname} (${r.peerId})", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Reason: ${r.reason}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                if (r.details.isNotBlank()) {
                                    Text(r.details, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("About NexVora Nearby") },
        text = {
            Column {
                Text("Version: 1.0.0 (Serverless Release)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "NexVora Nearby is designed from the ground up for total privacy and independence.\n\n" +
                            "• Direct Wi-Fi P2P Networking (Port 8988)\n" +
                            "• Cryptographic Handshake (ECDH secp256r1)\n" +
                            "• Symmetric Encryption (AES-256-GCM)\n" +
                            "• Local Room SQLite Persistence\n" +
                            "• Deterministic Activity Matching",
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 20.sp
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("OK") }
        }
    )
}

@Composable
fun DeleteAccountDialog(
    onDismiss: () -> Unit,
    onConfirmDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Local Account?") },
        text = {
            Text(
                "This action will permanently wipe your local profile, private keys, conversations, and settings from this device. This cannot be undone.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirmDelete()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.testTag("confirm_delete_account_button")
            ) {
                Text("Permanently Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
