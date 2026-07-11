package com.khataagent.ui.live

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.khataagent.BuildConfig
import com.khataagent.core.data.LedgerRepository
import com.khataagent.core.escalate.ConnectivityMonitor
import com.khataagent.fake.FakeConnectivityMonitor
import com.khataagent.fake.FakeLedgerRepository
import com.khataagent.live.GeminiLiveClient.LiveStatus
import com.khataagent.ui.SimpleViewModelFactory
import com.khataagent.ui.theme.KhataTheme

/**
 * Full-screen live voice chat with Gemini: the shopkeeper taps the orb and talks; the AI has this
 * shop's ledger as context (see [com.khataagent.live.LiveContextBuilder]) and answers out loud.
 * Online-only. Nothing here touches the offline local agent loop.
 *
 * Entry point for the integrator: add a "Talk to AI (live)" button on the Reports screen that
 * navigates to this screen, e.g. inside [com.khataagent.ui.nav.KhataNav]:
 * ```
 * composable("live_chat") {
 *     LiveChatScreen(
 *         repository = repository,
 *         connectivityMonitor = connectivityMonitor,
 *         onBack = { navController.popBackStack() },
 *     )
 * }
 * ```
 * and, in ReportsScreen's action row, a button with `onClick = { navController.navigate("live_chat") }`.
 */
@Composable
fun LiveChatScreen(
    repository: LedgerRepository,
    connectivityMonitor: ConnectivityMonitor,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    apiKey: String = BuildConfig.GEMINI_API_KEY,
) {
    val isOnline by connectivityMonitor.isOnline.collectAsState()

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when {
            !isOnline -> LiveChatMessageState(
                icon = Icons.Filled.CloudOff,
                title = "Live chat needs internet",
                message = "Go online to talk to the AI about your books — everything else in " +
                    "KhataAgent keeps working offline.",
                onBack = onBack,
            )
            apiKey.isBlank() -> LiveChatMessageState(
                icon = Icons.Filled.Key,
                title = "No Gemini key configured",
                message = "Add a Gemini API key to enable live voice chat (set GEMINI_API_KEY or " +
                    "-Pgemini.key when building).",
                onBack = onBack,
            )
            else -> LiveChatConnectedScreen(repository = repository, apiKey = apiKey, onBack = onBack)
        }
    }
}

/** Friendly full-screen state for offline / no-API-key — with a way back out. */
@Composable
private fun LiveChatMessageState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    message: String,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LiveChatTopBar(onBack = onBack)
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, shape = CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(44.dp),
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun LiveChatConnectedScreen(
    repository: LedgerRepository,
    apiKey: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: LiveChatViewModel = viewModel(
        factory = SimpleViewModelFactory { LiveChatViewModel(repository = repository, apiKey = apiKey) },
    )
    val status by viewModel.status.collectAsState()
    val talking by viewModel.talking.collectAsState()

    var micGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> micGranted = granted }

    LaunchedEffect(Unit) {
        if (!micGranted) permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LiveChatTopBar(onBack = onBack)
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            LiveOrb(status = status, talking = talking)
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = statusHeadline(status, micGranted),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = statusHint(status, talking, micGranted),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(40.dp))
            TalkButton(
                status = status,
                talking = talking,
                enabled = micGranted && (status == LiveStatus.Idle || status == LiveStatus.Listening || status == LiveStatus.Speaking),
                onClick = {
                    if (!micGranted) {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    } else {
                        viewModel.toggleTalking()
                    }
                },
            )
        }
    }
}

@Composable
private fun LiveChatTopBar(onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(4.dp)) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
        Text(
            text = "Talk to AI",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

/** Big animated mic/orb: breathes when idle, pulses fast while listening, glows while Gemini speaks. */
@Composable
private fun LiveOrb(status: LiveStatus, talking: Boolean, size: androidx.compose.ui.unit.Dp = 176.dp) {
    val infinite = rememberInfiniteTransition(label = "live-orb")
    val breathe by infinite.animateFloat(
        initialValue = 0.97f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Reverse),
        label = "breathe",
    )
    val fastPulse by infinite.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    val spin by infinite.animateFloat(
        initialValue = 0.85f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
        label = "connecting",
    )

    val (color, ringScale) = when (status) {
        is LiveStatus.Error -> MaterialTheme.colorScheme.error to 1f
        LiveStatus.Connecting -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) to spin
        LiveStatus.Speaking -> MaterialTheme.colorScheme.secondary to fastPulse
        LiveStatus.Listening -> MaterialTheme.colorScheme.primary to fastPulse
        LiveStatus.Idle -> MaterialTheme.colorScheme.primary.copy(alpha = 0.85f) to breathe
    }

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size + 48.dp)) {
        Box(
            modifier = Modifier
                .size(size)
                .scale(ringScale)
                .background(color = color.copy(alpha = 0.18f), shape = CircleShape),
        )
        Box(
            modifier = Modifier
                .size(size * 0.72f)
                .scale(if (status == LiveStatus.Listening) fastPulse else breathe)
                .background(color = color, shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (status is LiveStatus.Error && !talking) Icons.Filled.MicOff else Icons.Filled.Mic,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(size * 0.3f),
            )
        }
    }
}

@Composable
private fun TalkButton(status: LiveStatus, talking: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val label = if (talking) "Tap to stop" else "Tap to talk"
    Box(
        modifier = Modifier
            .size(width = 200.dp, height = 56.dp)
            .background(
                color = if (enabled) {
                    if (talking) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                },
                shape = CircleShape,
            )
            .then(if (enabled) Modifier else Modifier)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.TextButton(onClick = onClick, enabled = enabled) {
            Icon(
                imageVector = if (talking) Icons.Filled.Stop else Icons.Filled.Mic,
                contentDescription = null,
                tint = Color.White,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = label, color = Color.White, fontWeight = FontWeight.Medium)
        }
    }
}

private fun statusHeadline(status: LiveStatus, micGranted: Boolean): String = when {
    !micGranted -> "Microphone access needed"
    status is LiveStatus.Error -> "Live chat hit a snag"
    status == LiveStatus.Connecting -> "Connecting…"
    status == LiveStatus.Listening -> "Listening…"
    status == LiveStatus.Speaking -> "Speaking…"
    else -> "Ready when you are"
}

private fun statusHint(status: LiveStatus, talking: Boolean, micGranted: Boolean): String = when {
    !micGranted -> "Allow microphone access, then tap the button below to talk."
    status is LiveStatus.Error -> status.message
    status == LiveStatus.Connecting -> "Setting up the call with Gemini…"
    status == LiveStatus.Listening -> "Ask about balances, this week's sales, or low stock."
    status == LiveStatus.Speaking -> "Gemini is answering out loud."
    talking -> "Go ahead, ask your question."
    else -> "Tap the button and ask about your books — who owes money, how business was this week, or what's low on stock."
}

@Preview(showBackground = true, name = "Live chat — ready")
@Composable
private fun LiveChatScreenPreview() {
    KhataTheme {
        LiveChatScreen(
            repository = FakeLedgerRepository(),
            connectivityMonitor = FakeConnectivityMonitor(initiallyOnline = true),
            onBack = {},
            apiKey = "preview-key",
        )
    }
}

@Preview(showBackground = true, name = "Live chat — offline")
@Composable
private fun LiveChatScreenOfflinePreview() {
    KhataTheme {
        LiveChatScreen(
            repository = FakeLedgerRepository(),
            connectivityMonitor = FakeConnectivityMonitor(initiallyOnline = false),
            onBack = {},
            apiKey = "preview-key",
        )
    }
}
