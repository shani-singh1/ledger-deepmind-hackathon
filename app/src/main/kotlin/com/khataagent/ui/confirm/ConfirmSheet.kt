package com.khataagent.ui.confirm

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.PersonAddAlt
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.khataagent.R
import com.khataagent.core.agent.ConfirmCard
import com.khataagent.core.agent.DeferKind
import com.khataagent.core.tool.ToolCall
import com.khataagent.ui.theme.KhataTheme
import com.khataagent.ui.theme.KhataThemeExtras
import com.khataagent.ui.theme.MoneyType
import kotlinx.coroutines.delay

/**
 * THE thesis screen. Modal bottom sheet over Today: what the agent understood, why it's
 * unsure (plain shopkeeper language), and a clean accept/reject. The parsed intent "types
 * itself" in before settling — the model just finished thinking, and the UI shows it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmSheet(
    card: ConfirmCard,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        ConfirmSheetContent(card = card, onAccept = onAccept, onReject = onReject)
    }
}

@Composable
private fun deferKindMeta(kind: DeferKind): Triple<String, androidx.compose.ui.graphics.vector.ImageVector, String> =
    when (kind) {
        DeferKind.NEW_CUSTOMER -> Triple(stringResource(R.string.confirm_kind_new_customer), Icons.Filled.PersonAddAlt, "PERSON")
        DeferKind.AMBIGUOUS_CUSTOMER -> Triple(stringResource(R.string.confirm_kind_ambiguous_customer), Icons.Filled.SwapHoriz, "AMBIGUOUS")
        DeferKind.OVER_LIMIT -> Triple(stringResource(R.string.confirm_kind_over_limit), Icons.Filled.PriorityHigh, "LIMIT")
        DeferKind.OVERPAYMENT -> Triple(stringResource(R.string.confirm_kind_overpayment), Icons.Filled.PriorityHigh, "OVERPAY")
        DeferKind.DUPLICATE -> Triple(stringResource(R.string.confirm_kind_duplicate), Icons.Filled.Replay, "DUPLICATE")
        DeferKind.SCHEMA -> Triple(stringResource(R.string.confirm_kind_schema), Icons.AutoMirrored.Filled.Rule, "SCHEMA")
        DeferKind.CLARIFICATION -> Triple(stringResource(R.string.confirm_kind_clarification), Icons.AutoMirrored.Filled.HelpOutline, "ASK")
    }

@Composable
fun ConfirmSheetContent(
    card: ConfirmCard,
    onAccept: () -> Unit,
    onReject: () -> Unit,
) {
    val extras = KhataThemeExtras.colors
    val (kindLabel, kindIcon, _) = deferKindMeta(card.kind)
    val accent = MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(accent.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(kindIcon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.size(10.dp))
            Column {
                Text(
                    text = stringResource(R.string.confirm_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = kindLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // "What I understood" — types itself in, like the model just finished composing it.
        Card(
            colors = CardDefaults.cardColors(containerColor = extras.paperSurfaceRaised),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(0.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.confirm_what_i_understood),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(6.dp))
                TypewriterText(
                    fullText = card.understoodAs.summary(),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (card.understoodAs.amountOrNull() != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = com.khataagent.ui.components.formatRupees(card.understoodAs.amountOrNull()!!),
                        style = MoneyType.largeAmount,
                        color = accent,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = card.humanReason,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.height(14.dp))

        RawOutputDisclosure(card.rawModelOutput)

        Spacer(modifier = Modifier.height(22.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onReject,
                modifier = Modifier.weight(1f).height(52.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = extras.credit),
                border = BorderStroke(1.dp, extras.credit.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.size(6.dp))
                Text(stringResource(R.string.confirm_reject))
            }
            Button(
                onClick = onAccept,
                modifier = Modifier.weight(1f).height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = extras.payment),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.size(6.dp))
                Text(stringResource(R.string.confirm_accept))
            }
        }
    }
}

private fun ToolCall.amountOrNull(): Double? = when (this) {
    is ToolCall.AddCredit -> amount
    is ToolCall.RecordPayment -> amount
    is ToolCall.RecordSale -> amount
    else -> null
}

@Composable
private fun RawOutputDisclosure(rawOutput: String) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(
            text = if (expanded) stringResource(R.string.confirm_hide_raw_output) else stringResource(R.string.confirm_show_raw_output),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(vertical = 2.dp)
                .clickable { expanded = !expanded },
        )
        if (expanded) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(KhataThemeExtras.colors.paperSurfaceRaised, RoundedCornerShape(10.dp))
                    .padding(10.dp),
            ) {
                Text(
                    text = rawOutput,
                    style = MoneyType.monoRaw,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Letter-by-letter reveal — the micro-interaction that sells "the model just wrote this". */
@Composable
private fun TypewriterText(fullText: String, style: androidx.compose.ui.text.TextStyle, color: Color) {
    var shown by remember(fullText) { mutableStateOf("") }
    LaunchedEffect(fullText) {
        shown = ""
        for (i in fullText.indices) {
            shown = fullText.substring(0, i + 1)
            delay(14)
        }
    }
    Text(text = shown, style = style, color = color)
}

@Preview(showBackground = true, name = "Confirm — over limit")
@Composable
private fun ConfirmSheetOverLimitPreview() {
    KhataTheme {
        Box(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
            ConfirmSheetContent(
                card = ConfirmCard(
                    turnId = "preview-1",
                    understoodAs = ToolCall.AddCredit(customer = "ramesh", amount = 12000.0, item = "cement"),
                    humanReason = "₹12,000 is above your usual ₹5,000 limit — confirm?",
                    rawModelOutput = """{"tool":"add_credit","customer":"ramesh","amount":12000,"item":"cement"}""",
                    kind = DeferKind.OVER_LIMIT,
                ),
                onAccept = {},
                onReject = {},
            )
        }
    }
}

@Preview(showBackground = true, name = "Confirm — clarification")
@Composable
private fun ConfirmSheetClarificationPreview() {
    KhataTheme {
        Box(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
            ConfirmSheetContent(
                card = ConfirmCard(
                    turnId = "preview-2",
                    understoodAs = ToolCall.AskClarification("Which Ramesh — the one on MG Road?"),
                    humanReason = "Two customers sound like \"Ramesh\" — which one did you mean?",
                    rawModelOutput = """{"tool":"ask_clarification","question":"Which Ramesh - the one on MG Road?"}""",
                    kind = DeferKind.AMBIGUOUS_CUSTOMER,
                ),
                onAccept = {},
                onReject = {},
            )
        }
    }
}
