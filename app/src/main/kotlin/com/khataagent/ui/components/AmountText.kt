package com.khataagent.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.khataagent.ui.theme.MoneyType
import java.util.Locale
import kotlin.math.roundToLong

/** Formats a rupee amount the way a shopkeeper's khata does: ₹ prefix, Indian digit grouping. */
fun formatRupees(amount: Double, showDecimals: Boolean = false): String {
    val paise = (amount * 100).roundToLong()
    val whole = paise / 100
    val fraction = kotlin.math.abs(paise % 100)
    val sign = if (whole < 0) "-" else ""
    val grouped = groupIndian(kotlin.math.abs(whole))
    return if (showDecimals) {
        "%s₹%s.%02d".format(sign, grouped, fraction)
    } else {
        "%s₹%s".format(sign, grouped)
    }
}

/** Indian numbering: last 3 digits, then groups of 2 — 1,00,000 not 100,000. */
private fun groupIndian(n: Long): String {
    val s = n.toString()
    if (s.length <= 3) return s
    val head = s.substring(0, s.length - 3)
    val tail = s.substring(s.length - 3)
    val sb = StringBuilder()
    var i = head.length
    var first = true
    while (i > 0) {
        val start = maxOf(0, i - 2)
        val chunk = head.substring(start, i)
        if (!first) sb.insert(0, ",")
        sb.insert(0, chunk)
        first = false
        i = start
    }
    return if (sb.isEmpty()) tail else "$sb,$tail"
}

/**
 * A rupee amount, always set in tabular figures so columns of numbers line up like a real ledger.
 * [color] should be the semantic type color (credit=red, payment=green) or ink for neutral amounts.
 */
@Composable
fun AmountText(
    amount: Double,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    style: TextStyle = MoneyType.bodyAmount,
    showDecimals: Boolean = false,
    prefix: String = "",
) {
    Row(modifier = modifier) {
        Text(
            text = prefix + formatRupees(amount, showDecimals),
            color = color,
            style = style,
        )
    }
}

fun String.capitalizeWords(): String =
    split(" ").joinToString(" ") { w -> w.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() } }
