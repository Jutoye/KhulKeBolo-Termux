package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.AddAlarm
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.config.HelplineConfig
import com.example.data.model.ChatMessage
import com.example.data.model.UserRole
import com.example.ui.components.AppGuidelines
import com.example.ui.components.CrisisHelpCard
import com.example.ui.theme.BodyFontFamily
import com.example.ui.theme.GentleWarningBgDark
import com.example.ui.theme.GentleWarningBgLight
import com.example.ui.theme.GentleWarningTextDark
import com.example.ui.theme.GentleWarningTextLight
import com.example.ui.theme.UrduFontFamily
import com.example.ui.theme.containsUrduScript
import com.example.ui.viewmodel.ChatUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    currentUserId: String,
    uiState: ChatUiState,
    onInputChanged: (String) -> Unit,
    onSendClick: () -> Unit,
    onQuickPhraseClick: (String) -> Unit,
    onRequestExtension: () -> Unit,
    onEndSession: () -> Unit,
    onReportSubmit: (reason: String, includeLastMessages: Boolean) -> Unit,
    onBlockPartner: () -> Unit,
    onClearFeedback: () -> Unit,
    onNavigateToEndScreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val room = uiState.room
    val myRole = room?.roleFor(currentUserId)
    val partnerNickname = room?.partnerNicknameFor(currentUserId) ?: "Dost"

    var showMenu by remember { mutableStateOf(false) }
    var showEndConfirmDialog by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var showBlockDialog by remember { mutableStateOf(false) }
    var guidelinesExpanded by remember { mutableStateOf(true) }

    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    BackHandler { showEndConfirmDialog = true }

    LaunchedEffect(uiState.sessionEnded) {
        if (uiState.sessionEnded) {
            onNavigateToEndScreen()
        }
    }

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.lastIndex)
        }
    }

    LaunchedEffect(uiState.actionFeedback, uiState.errorMessage) {
        val msg = uiState.actionFeedback ?: uiState.errorMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            onClearFeedback()
        }
    }

    val partnerRequestedExtension = room != null && !room.extendedApplied && (
        (myRole == UserRole.SPEAKER && room.extendRequestedByListener && !room.extendRequestedBySpeaker) ||
            (myRole == UserRole.LISTENER && room.extendRequestedBySpeaker && !room.extendRequestedByListener)
        )

    val iAlreadyRequestedExtension = room != null && (
        (myRole == UserRole.SPEAKER && room.extendRequestedBySpeaker) ||
            (myRole == UserRole.LISTENER && room.extendRequestedByListener)
        )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = partnerNickname,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Timer,
                                contentDescription = "Time remaining",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = formatCountdown(uiState.remainingSeconds),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (myRole != null) {
                                Text(
                                    text = "• ${if (myRole == UserRole.SPEAKER) "Speaker" else "Listener"}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                actions = {
                    if (room != null && !room.extendedApplied && !iAlreadyRequestedExtension) {
                        TextButton(
                            onClick = onRequestExtension,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("extend_session_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AddAlarm,
                                contentDescription = "Extend 10 minutes",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("+10m")
                        }
                    }

                    TextButton(
                        onClick = { showEndConfirmDialog = true },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("end_session_button")
                    ) {
                        Text(
                            text = "Khatam Karein",
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("chat_menu_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.MoreVert,
                                contentDescription = "More options"
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Report Partner") },
                                onClick = {
                                    showMenu = false
                                    showReportDialog = true
                                },
                                modifier = Modifier.testTag("menu_report_item")
                            )
                            DropdownMenuItem(
                                text = { Text("Block Partner") },
                                onClick = {
                                    showMenu = false
                                    showBlockDialog = true
                                },
                                modifier = Modifier.testTag("menu_block_item")
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .imePadding()
        ) {
            if (partnerRequestedExtension) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Partner +10 minute badhana chahte hain",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = onRequestExtension,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("accept_extension_button")
                        ) {
                            Text("Qabool (+10m)")
                        }
                    }
                }
            }

            if (myRole == UserRole.LISTENER) {
                ListenerGuidelinesCard(
                    expanded = guidelinesExpanded,
                    onToggle = { guidelinesExpanded = !guidelinesExpanded },
                    onPhraseTap = onQuickPhraseClick
                )
            }

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (uiState.messages.isEmpty()) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                ),
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (myRole == UserRole.SPEAKER) {
                                        "Aap khul kar apni baat likh sakte hain. Listener bina judge kiye sunne ke liye maujood hain."
                                    } else {
                                        "Aap ek Listener hain. Sirf dhyan se sunein aur narmi se hosla dein. Mashwara tab hi dein jab speaker khud maange."
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }

                    items(uiState.messages, key = { it.id.ifBlank { "${it.senderUid}_${it.text.hashCode()}" } }) { msg ->
                        MessageBubble(message = msg)
                    }
                }
            }

            AnimatedVisibility(visible = uiState.crisisDetected) {
                CrisisHelpCard(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }

            AnimatedVisibility(visible = !uiState.blockedWarning.isNullOrBlank()) {
                val isDark = isSystemInDarkTheme()
                Surface(
                    color = if (isDark) GentleWarningBgDark else GentleWarningBgLight,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                        .testTag("blocked_message_warning")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = if (isDark) GentleWarningTextDark else GentleWarningTextLight,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = uiState.blockedWarning.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isDark) GentleWarningTextDark else GentleWarningTextLight
                        )
                    }
                }
            }

            // Message input row
            Surface(
                tonalElevation = 2.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    OutlinedTextField(
                        value = uiState.inputText,
                        onValueChange = onInputChanged,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_input_field"),
                        placeholder = {
                            Text("Apni baat yahan likhein...")
                        },
                        maxLines = 4,
                        enabled = !uiState.sessionEnded,
                        supportingText = {
                            Text("${uiState.inputText.length}/${HelplineConfig.MAX_MESSAGE_LENGTH}")
                        },
                        shape = MaterialTheme.shapes.large
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    FilledIconButton(
                        onClick = onSendClick,
                        enabled = uiState.inputText.isNotBlank() && !uiState.sessionEnded,
                        modifier = Modifier
                            .padding(bottom = 18.dp)
                            .size(50.dp)
                            .testTag("send_message_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send message"
                        )
                    }
                }
            }
        }
    }

    if (showEndConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showEndConfirmDialog = false },
            title = { Text("Baat Khatam Karein?") },
            text = {
                Text("Session khatam karne par tamam messages delete ho jayenge aur aap End Screen par chale jayenge.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showEndConfirmDialog = false
                        onEndSession()
                    },
                    modifier = Modifier.testTag("confirm_end_session_button")
                ) {
                    Text("Haan, Khatam Karein")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndConfirmDialog = false }) {
                    Text("Nahi, Jari Rakhein")
                }
            }
        )
    }

    if (showBlockDialog) {
        AlertDialog(
            onDismissRequest = { showBlockDialog = false },
            title = { Text("Partner Ko Block Karein?") },
            text = {
                Text("Block karne par yeh session foran khatam ho jayega aur aap dubara is user se match nahi honge.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBlockDialog = false
                        onBlockPartner()
                    },
                    modifier = Modifier.testTag("confirm_block_button")
                ) {
                    Text("Block & Khatam Karein")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showReportDialog) {
        ReportPartnerDialog(
            onDismiss = { showReportDialog = false },
            onSubmit = { reason, includeMessages ->
                showReportDialog = false
                onReportSubmit(reason, includeMessages)
            }
        )
    }
}

@Composable
private fun ListenerGuidelinesCard(
    expanded: Boolean,
    onToggle: () -> Unit,
    onPhraseTap: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .testTag("listener_guidelines_card"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Listener Guidelines — Tap karke bhejein",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "Collapse guidelines" else "Expand guidelines"
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Sirf narmi se sunein. Judge, mazaak, ya zabardasti mashwara na dein.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AppGuidelines.listenerSuggestedPhrases.forEach { phrase ->
                            OutlinedButton(
                                onClick = { onPhraseTap(phrase) },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text(
                                    text = phrase,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    // Requirement 12: speaker messages on the right, listener messages on the left
    val isSpeakerMessage = message.senderRole == UserRole.SPEAKER.name
    val bubbleColor = if (isSpeakerMessage) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val textColor = if (isSpeakerMessage) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }
    val bubbleShape = if (isSpeakerMessage) {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp)
    } else {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 6.dp, bottomEnd = 20.dp)
    }
    val fontFamily = if (containsUrduScript(message.text)) UrduFontFamily else BodyFontFamily

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isSpeakerMessage) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = bubbleColor,
            shape = bubbleShape,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    text = if (isSpeakerMessage) "Speaker" else "Listener",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = textColor.copy(alpha = 0.72f)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyLarge.copy(fontFamily = fontFamily),
                    color = textColor
                )
            }
        }
    }
}

@Composable
private fun ReportPartnerDialog(
    onDismiss: () -> Unit,
    onSubmit: (reason: String, includeLastMessages: Boolean) -> Unit
) {
    val reasons = listOf(
        "Gaali, badtameezi ya dhamki",
        "Flirt ya mulaqat / personal info maangna",
        "Mazaak urana ya judge karna",
        "Koi aur wajah"
    )
    var selectedReason by remember { mutableStateOf(reasons.first()) }
    var includeMessages by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Partner Report Karein") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Wajah muntakhib karein:",
                    style = MaterialTheme.typography.bodyMedium
                )
                reasons.forEach { reason ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedReason = reason },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedReason == reason,
                            onClick = { selectedReason = reason }
                        )
                        Text(
                            text = reason,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { includeMessages = !includeMessages },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = includeMessages,
                        onCheckedChange = { includeMessages = it }
                    )
                    Text(
                        text = "Review ke liye aakhri 5 messages shaamil karein (optional)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(selectedReason, includeMessages) },
                modifier = Modifier.testTag("submit_report_button")
            ) {
                Text("Report Bhejein")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun formatCountdown(totalSeconds: Long): String {
    val mins = totalSeconds / 60
    val secs = totalSeconds % 60
    return "%02d:%02d".format(mins, secs)
}
