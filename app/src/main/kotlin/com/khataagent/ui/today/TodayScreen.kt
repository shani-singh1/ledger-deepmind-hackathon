package com.khataagent.ui.today

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.khataagent.R
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.khataagent.core.agent.ConfirmCard
import com.khataagent.core.agent.TurnState
import com.khataagent.core.data.LedgerRepository
import com.khataagent.core.model.DailyState
import com.khataagent.core.model.Transaction
import com.khataagent.fake.FakeLedgerRepository
import com.khataagent.ui.SimpleViewModelFactory
import com.khataagent.ui.components.AmountText
import com.khataagent.ui.components.LedgerRuledBackground
import com.khataagent.ui.components.MicButton
import com.khataagent.ui.components.TxnCard
import com.khataagent.ui.confirm.ConfirmSheet
import com.khataagent.ui.theme.KhataTheme
import com.khataagent.ui.theme.KhataThemeExtras
import com.khataagent.ui.theme.MoneyType
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.khataagent.agent.AgentOrchestrator
import com.khataagent.agent.StubInferenceEngine
import com.khataagent.audio.AudioRecorder
import com.khataagent.audio.rememberTtsSpeaker
import java.util.Locale
import com.khataagent.data.StateBlockBuilderImpl
import com.khataagent.validate.KhataValidator
import kotlinx.coroutines.delay

@Composable
fun TodayScreen(
    repository: LedgerRepository,
    orchestrator: AgentOrchestrator,
    audioRecorder: AudioRecorder,
    voiceAvailable: () -> Boolean,
    isOnline: Boolean,
    modifier: Modifier = Modifier,
) {
    val viewModel: TodayViewModel = viewModel(
        factory = SimpleViewModelFactory {
            TodayViewModel(repository, orchestrator, audioRecorder, voiceAvailable)
        },
    )
    val transactions by viewModel.todayTransactions.collectAsState()
    val dailyState by viewModel.dailyState.collectAsState()
    val turnState by viewModel.turnState.collectAsState()

    // Mic: the system speech-recognition activity (offline-capable, big clear "Speak now" UI that
    // non-technical shopkeepers get) -> transcript -> the SAME on-device Gemma turn as typing.
    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val spoken = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        if (result.resultCode == Activity.RESULT_OK && !spoken.isNullOrBlank()) {
            viewModel.onSubmitText(spoken)
        }
    }
    // OFFLINE voice: the on-device speech recognizer (free-form) -> transcript -> local Gemma.
    val startVoiceRecognizer: () -> Unit = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Bolo… speak your entry")
        }
        runCatching { speechLauncher.launch(intent) }
    }

    TodayContent(
        transactions = transactions,
        dailyState = dailyState,
        turnState = turnState,
        voiceEnabled = true,
        // ONLINE: record audio and send it to Gemini's multimodal model (better at Hindi/mixed
        // speech). OFFLINE: use the on-device recognizer -> text -> local Gemma.
        onMicPress = { if (isOnline) viewModel.onMicPress() else startVoiceRecognizer() },
        onMicRelease = { if (isOnline) viewModel.onMicRelease() },
        onCancelListening = { if (isOnline) viewModel.onCancelListening() },
        onSubmitText = viewModel::onSubmitText,
        onAcceptDeferred = viewModel::onAcceptDeferred,
        onRejectDeferred = viewModel::onRejectDeferred,
        onAcknowledge = viewModel::onAcknowledge,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayContent(
    transactions: List<Transaction>,
    dailyState: DailyState,
    turnState: TurnState,
    voiceEnabled: Boolean,
    voiceListening: Boolean = false,
    onMicPress: () -> Unit,
    onMicRelease: () -> Unit,
    onCancelListening: () -> Unit,
    onSubmitText: (String) -> Unit,
    onAcceptDeferred: (ConfirmCard) -> Unit,
    onRejectDeferred: (ConfirmCard) -> Unit,
    onAcknowledge: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isListening = voiceListening || turnState is TurnState.Listening
    val isBusy = turnState is TurnState.Inferring || turnState is TurnState.Validating
    val tts = rememberTtsSpeaker()

    // Speak the confirmation aloud (offline TTS), then auto-dismiss + return the machine to Idle.
    LaunchedEffect(turnState) {
        if (turnState is TurnState.Committed) tts.speak(turnState.spokenConfirmation)
        if (turnState is TurnState.Committed || turnState is TurnState.Rejected) {
            delay(2200)
            onAcknowledge()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LedgerRuledBackground()

        Column(modifier = Modifier.fillMaxSize()) {
            DayTotalsHeader(dailyState)

            Box(modifier = Modifier.weight(1f)) {
                if (transactions.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.today_empty_state),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 240.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(transactions, key = { it.id }) { txn ->
                            AnimatedLedgerEntry {
                                TxnCard(txn)
                            }
                        }
                    }
                }
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .imePadding(),
            color = MaterialTheme.colorScheme.background,
            shadowElevation = 10.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ProcessingBanner(turnState)
                MicButton(
                    isListening = isListening,
                    isBusy = isBusy,
                    onPressStart = onMicPress,
                    onPressEnd = onMicRelease,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        if (voiceEnabled) R.string.today_hint_voice_and_type else R.string.today_hint_type_only,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextEntryBar(enabled = !isBusy, onSubmit = onSubmitText)
            }
        }

        if (turnState is TurnState.Committed) {
            CommittedToast(
                text = turnState.spokenConfirmation,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
            )
        }
        if (turnState is TurnState.Rejected) {
            CommittedToast(
                text = stringResource(R.string.today_rejected_toast),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                isRejection = true,
            )
        }
    }

    if (turnState is TurnState.Deferred) {
        ConfirmSheet(
            card = turnState.card,
            onAccept = { onAcceptDeferred(turnState.card) },
            onReject = { onRejectDeferred(turnState.card) },
            onDismiss = { onRejectDeferred(turnState.card) },
        )
    }
}

@Composable
private fun DayTotalsHeader(dailyState: DailyState) {
    val extras = KhataThemeExtras.colors
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            TotalStat(label = stringResource(R.string.today_total_credit), amount = dailyState.totalCredit, color = extras.credit)
            VerticalRule()
            TotalStat(label = stringResource(R.string.today_total_payments), amount = dailyState.totalPayments, color = extras.payment)
            VerticalRule()
            CountStat(label = stringResource(R.string.today_total_entries), count = dailyState.txnCount)
        }
    }
}

@Composable
private fun VerticalRule() {
    val ruleColor = KhataThemeExtras.colors.ruleLine
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(40.dp),
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(color = ruleColor)
        }
    }
}

@Composable
private fun TotalStat(label: String, amount: Double, color: androidx.compose.ui.graphics.Color) {
    val animated = remember { Animatable(0f) }
    LaunchedEffect(amount) {
        animated.animateTo(amount.toFloat(), animationSpec = tween(700, easing = FastOutSlowInEasing))
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(2.dp))
        AmountText(amount = animated.value.toDouble(), color = color, style = MoneyType.largeAmount)
    }
}

@Composable
private fun CountStat(label: String, count: Int) {
    val animated = remember { Animatable(0f) }
    LaunchedEffect(count) {
        animated.animateTo(count.toFloat(), animationSpec = tween(700, easing = FastOutSlowInEasing))
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = animated.value.toInt().toString(),
            style = MoneyType.largeAmount,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Prominent, clearly-weighted status so the shopkeeper always knows the agent is working. */
@Composable
private fun ProcessingBanner(turnState: TurnState) {
    val extras = KhataThemeExtras.colors
    data class Status(val text: String?, val spinner: Boolean, val error: Boolean)
    val status = when (turnState) {
        is TurnState.Listening -> Status(stringResource(R.string.today_status_listening), false, false)
        is TurnState.Inferring -> Status(stringResource(R.string.today_status_inferring), true, false)
        is TurnState.Retrying -> Status(stringResource(R.string.today_status_retrying), true, false)
        is TurnState.Validating -> Status(stringResource(R.string.today_status_validating), true, false)
        is TurnState.Errored -> Status(turnState.message, false, true)
        else -> Status(null, false, false)
    }
    AnimatedVisibility(visible = status.text != null, enter = fadeIn(), exit = fadeOut()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 10.dp),
        ) {
            if (status.spinner) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.5.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            Text(
                text = status.text ?: "",
                style = MaterialTheme.typography.titleSmall,
                color = if (status.error) extras.credit else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** The typed "demo insurance" path — same pipeline as voice, downstream is identical. */
@Composable
private fun TextEntryBar(enabled: Boolean, onSubmit: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val send = {
        if (text.isNotBlank()) {
            onSubmit(text)
            text = ""
        }
    }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        enabled = enabled,
        singleLine = true,
        placeholder = { Text(stringResource(R.string.today_text_entry_placeholder)) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { send() }),
        trailingIcon = {
            IconButton(onClick = send, enabled = enabled) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.cd_send), tint = MaterialTheme.colorScheme.primary)
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            disabledContainerColor = MaterialTheme.colorScheme.surface,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    )
}

@Composable
private fun CommittedToast(text: String, modifier: Modifier = Modifier, isRejection: Boolean = false) {
    val extras = KhataThemeExtras.colors
    val color = if (isRejection) extras.credit else extras.payment
    Card(
        modifier = modifier.padding(horizontal = 24.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.14f)),
        shape = RoundedCornerShape(50),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = color,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
        )
    }
}

/** Fade + drop-in entrance for a freshly committed ledger line. */
@Composable
private fun AnimatedLedgerEntry(content: @Composable () -> Unit) {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        appear.animateTo(1f, animationSpec = tween(380, easing = FastOutSlowInEasing))
    }
    Box(
        modifier = Modifier.graphicsLayer {
            translationY = (1f - appear.value) * -28f
            alpha = appear.value
            scaleX = 0.97f + 0.03f * appear.value
            scaleY = 0.97f + 0.03f * appear.value
        },
    ) {
        content()
    }
}

@Preview(showBackground = true, name = "Today")
@Composable
private fun TodayScreenPreview() {
    val repo = FakeLedgerRepository()
    KhataTheme {
        TodayScreen(
            repository = repo,
            orchestrator = AgentOrchestrator(
                engine = StubInferenceEngine(),
                validator = KhataValidator(),
                repository = repo,
                stateBlockBuilder = StateBlockBuilderImpl(repo),
            ),
            audioRecorder = AudioRecorder(),
            voiceAvailable = { false },
            isOnline = false,
        )
    }
}
