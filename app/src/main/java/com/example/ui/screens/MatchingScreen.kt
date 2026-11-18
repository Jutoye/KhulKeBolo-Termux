package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.model.UserRole
import com.example.ui.viewmodel.MatchUiState

@Composable
fun MatchingScreen(
    role: UserRole,
    uiState: MatchUiState,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onMatched: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onCancel() }

    LaunchedEffect(uiState) {
        if (uiState is MatchUiState.Matched) {
            onMatched(uiState.roomId)
        }
    }

    val transition = rememberInfiniteTransition(label = "matching_pulse")
    val ringScale by transition.animateFloat(
        initialValue = 0.72f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring_scale"
    )

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Column(
                modifier = Modifier.widthIn(max = 520.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "${role.romanUrduLabel} (${role.englishLabel})",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                when (uiState) {
                    is MatchUiState.Searching, MatchUiState.Idle -> {
                        val secondsLeft = (uiState as? MatchUiState.Searching)?.secondsRemaining ?: 60
                        val primaryColor = MaterialTheme.colorScheme.primary
                        val secondaryContainer = MaterialTheme.colorScheme.secondaryContainer

                        Box(
                            modifier = Modifier.size(168.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val maxR = size.minDimension / 2f
                                drawCircle(
                                    color = secondaryContainer.copy(alpha = 0.7f),
                                    radius = maxR * ringScale
                                )
                                drawCircle(
                                    color = primaryColor.copy(alpha = 0.22f),
                                    radius = maxR * 0.65f
                                )
                                drawCircle(
                                    color = primaryColor,
                                    radius = maxR * 0.26f
                                )
                            }
                            Text(
                                text = "${secondsLeft}s",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }

                        Text(
                            text = if (role == UserRole.SPEAKER) {
                                "Aap ke liye ek mehfooz listener dhoond rahe hain..."
                            } else {
                                "Kisi aise dost ko dhoond rahe hain jo baat karna chahta hai..."
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "Intezar karne ka shukriya. Zyada se zyada 60 seconds lagenge.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .testTag("cancel_matching_button")
                        ) {
                            Text("Cancel Karein")
                        }
                    }

                    is MatchUiState.TimedOut -> {
                        Icon(
                            imageVector = Icons.Outlined.HourglassEmpty,
                            contentDescription = "No match found",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(56.dp)
                        )
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                            ),
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("matching_timeout_card")
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Abhi koi partner nahi mil saka",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "60 seconds mein koi partner dastiyab nahi tha. Aap dubara koshish kar sakte hain ya kuch der baad aayein.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        Button(
                            onClick = onRetry,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .testTag("retry_matching_button")
                        ) {
                            Text("Dubara Koshish Karein")
                        }

                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .testTag("back_home_button")
                        ) {
                            Text("Wapas Home")
                        }
                    }

                    is MatchUiState.Error -> {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = uiState.message,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(20.dp),
                                textAlign = TextAlign.Center
                            )
                        }

                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .testTag("error_back_home_button")
                        ) {
                            Text("Wapas Home")
                        }
                    }

                    is MatchUiState.Matched -> {
                        Text(
                            text = "Partner mil gaya! Chat khul rahi hai...",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }
}
