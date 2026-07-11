package com.khataagent.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Non-Material semantic colors (credit/payment/sale/rule-lines) exposed via a CompositionLocal. */
data class KhataExtraColors(
    val credit: Color,
    val creditContainer: Color,
    val payment: Color,
    val paymentContainer: Color,
    val sale: Color,
    val saleContainer: Color,
    val ruleLine: Color,
    val ruleLineFaint: Color,
    val statusOnDevice: Color,
    val statusOffline: Color,
    val statusSyncing: Color,
    val paperSurfaceRaised: Color,
)

private val LightExtraColors = KhataExtraColors(
    credit = CreditRed,
    creditContainer = CreditRedContainer,
    payment = PaymentGreen,
    paymentContainer = PaymentGreenContainer,
    sale = SaleGold,
    saleContainer = SaleGoldContainer,
    ruleLine = RuleLine,
    ruleLineFaint = RuleLineFaint,
    statusOnDevice = StatusOnDevice,
    statusOffline = StatusOffline,
    statusSyncing = StatusSyncing,
    paperSurfaceRaised = PaperSurfaceRaised,
)

private val DarkExtraColors = KhataExtraColors(
    credit = DarkCreditRed,
    creditContainer = DarkCreditRedContainer,
    payment = DarkPaymentGreen,
    paymentContainer = DarkPaymentGreenContainer,
    sale = DarkSaleGold,
    saleContainer = DarkSaleGoldContainer,
    ruleLine = DarkRuleLine,
    ruleLineFaint = DarkRuleLine.copy(alpha = 0.6f),
    statusOnDevice = DarkPaymentGreen,
    statusOffline = DarkInkSecondary,
    statusSyncing = DarkAccentIndigo,
    paperSurfaceRaised = DarkSurfaceVariant,
)

val LocalKhataExtraColors = staticCompositionLocalOf { LightExtraColors }

/** Convenience accessor: `KhataTheme.extraColors.credit` style usage via `MaterialTheme`-like object. */
object KhataThemeExtras {
    val colors: KhataExtraColors
        @Composable get() = LocalKhataExtraColors.current
}

private val LightScheme = lightColorScheme(
    primary = AccentIndigo,
    onPrimary = OnAccentIndigo,
    primaryContainer = AccentIndigoContainer,
    onPrimaryContainer = AccentIndigo,
    secondary = SaleGold,
    onSecondary = Color.White,
    secondaryContainer = SaleGoldContainer,
    onSecondaryContainer = InkPrimary,
    background = PaperBackground,
    onBackground = InkPrimary,
    surface = PaperSurface,
    onSurface = InkPrimary,
    surfaceVariant = PaperSurfaceVariant,
    onSurfaceVariant = InkSecondary,
    outline = RuleLine,
    outlineVariant = RuleLineFaint,
    error = CreditRed,
    onError = Color.White,
    errorContainer = CreditRedContainer,
    onErrorContainer = CreditRed,
)

private val DarkScheme = darkColorScheme(
    primary = DarkAccentIndigo,
    onPrimary = Color(0xFF1D1848),
    primaryContainer = DarkAccentIndigoContainer,
    onPrimaryContainer = Color(0xFFEAE6FF),
    secondary = DarkSaleGold,
    onSecondary = Color(0xFF3A2E00),
    secondaryContainer = DarkSaleGoldContainer,
    onSecondaryContainer = DarkInkPrimary,
    background = DarkBackground,
    onBackground = DarkInkPrimary,
    surface = DarkSurface,
    onSurface = DarkInkPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkInkSecondary,
    outline = DarkRuleLine,
    outlineVariant = DarkRuleLine,
    error = DarkCreditRed,
    onError = Color(0xFF4E0002),
    errorContainer = DarkCreditRedContainer,
    onErrorContainer = Color(0xFFFFDAD4),
)

@Composable
fun KhataTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme = if (darkTheme) DarkScheme else LightScheme
    val extras = if (darkTheme) DarkExtraColors else LightExtraColors
    androidx.compose.runtime.CompositionLocalProvider(LocalKhataExtraColors provides extras) {
        MaterialTheme(
            colorScheme = scheme,
            typography = KhataTypography,
            content = content,
        )
    }
}
