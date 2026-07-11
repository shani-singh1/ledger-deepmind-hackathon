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
import androidx.compose.ui.tooling.preview.Preview
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
import kotlinx.coroutines.delay

@Composable
fun TodayScreen(repository: LedgerRepository, modifier: Modifier = Modifier) {
    val viewModel: TodayViewModel = viewModel(factory = SimpleViewModelFactory { TodayViewModel(repository) })
    val transactions by viewModel.todayTransactions.collectAsState()
    val dailyState by viewModel.dailyState.collectAsState()
    val turnState by viewModel.turnState.collectAsState()

    TodayContent(
        transactions = transactions,
        dailyState = dailyState,
        turnState = turnState,
        onMicPress = viewModel::onMicPress,
        onMicRelease = viewModel::onMicRelease,
        onCancelListening = viewModel::onCancelListening,
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
    onMicPress: () -> Unit,
    onMicRelease: () -> Unit,
    onCancelListening: () -> Unit,
    onAcceptDeferred: (ConfirmCard) -> Unit,
    onRejectDeferred: (ConfirmCard) -> Unit,
    onAcknowledge: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isListening = turnState is TurnState.Listening
    val isBusy = turnState is TurnState.Inferring || turnState is TurnState.Validating

    // Auto-dismiss the committed/rejected toast and return the turn machine to Idle.
    LaunchedEffect(turnState) {
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
                            text = "No entries yet today — hold the mic to speak one in.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 140.dp),
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

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TurnStatusHint(turnState)
            Spacer(modifier = Modifier.height(6.dp))
            MicButton(
                isListening = isListening,
                isBusy = isBusy,
                onPressStart = onMicPress,
                onPressEnd = onMicRelease,
            )
        }

        if (turnState is TurnState.Committed) {
            CommittedToast(
                text = turnState.spokenConfirmation,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
            )
        }
        if (turnState is TurnState.Rejected) {
            CommittedToast(
                text = "Rejected — nothing was written to the khata.",
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
            TotalStat(label = "Credit", amount = dailyState.totalCredit, color = extras.credit)
            VerticalRule()
            TotalStat(label = "Payments", amount = dailyState.totalPayments, color = extras.payment)
            VerticalRule()
            CountStat(label = "Entries", count = dailyState.txnCount)
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

@Composable
private fun TurnStatusHint(turnState: TurnState) {
    val text = when (turnState) {
        is TurnState.Listening -> "Listening…"
        is TurnState.Inferring -> "Thinking…"
        is TurnState.Validating -> "Checking…"
        else -> null
    }
    AnimatedVisibility(visible = text != null, enter = fadeIn(), exit = fadeOut()) {
        Text(
            text = text ?: "",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
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
    KhataTheme {
        TodayScreen(repository = FakeLedgerRepository())
    }
}
