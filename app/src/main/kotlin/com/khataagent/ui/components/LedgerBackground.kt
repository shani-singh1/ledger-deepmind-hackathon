package com.khataagent.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.khataagent.ui.theme.KhataThemeExtras

/**
 * The subtle ruled-line ledger-book texture behind the Today feed — a faint horizontal
 * rule every 28dp, like feint-ruled account-book paper. Deliberately understated.
 */
@Composable
fun LedgerRuledBackground(modifier: Modifier = Modifier) {
    val ruleColor = KhataThemeExtras.colors.ruleLineFaint
    val bg = MaterialTheme.colorScheme.background
    Canvas(modifier = modifier.fillMaxSize().background(bg)) {
        val step = 28.dp.toPx()
        var y = step
        while (y < size.height) {
            drawLine(
                color = ruleColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f,
            )
            y += step
        }
        // a faint left margin rule, like a khata's account column
        drawLine(
            color = ruleColor,
            start = Offset(56.dp.toPx(), 0f),
            end = Offset(56.dp.toPx(), size.height),
            strokeWidth = 1f,
        )
    }
}
