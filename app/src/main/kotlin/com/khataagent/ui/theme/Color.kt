package com.khataagent.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * KhataAgent palette — a ledger book, not a chatbot.
 * Warm paper/ivory surfaces, warm-brown ink text, ONE accent (deep indigo).
 * Credit (udhaar) is always red, payment is always green — that mapping is sacred,
 * never reused for anything else in the app.
 */

// ---- Paper (light — the hero scheme, optimized for daylight/outdoor use) ----
val PaperBackground = Color(0xFFFBF2E1)
val PaperSurface = Color(0xFFFFFCF5)
val PaperSurfaceVariant = Color(0xFFF3E6C9)
val PaperSurfaceRaised = Color(0xFFFFFEFA)

// ---- Ink (warm brown text, not pure black — reads like fountain-pen ink on paper) ----
val InkPrimary = Color(0xFF3A2C1D)
val InkSecondary = Color(0xFF7A6650)
val InkFaint = Color(0xFFA9977E)

// ---- Ledger rule lines (the subtle horizontal ruling texture) ----
val RuleLine = Color(0xFFDCC9A3)
val RuleLineFaint = Color(0xFFEADFC4)

// ---- Accent — deep indigo (the ONE brand color; never purple-default) ----
val AccentIndigo = Color(0xFF352F73)
val AccentIndigoLight = Color(0xFF5B54A6)
val AccentIndigoContainer = Color(0xFFE3E0F7)
val OnAccentIndigo = Color(0xFFFCFAFF)

// ---- Semantic: credit (udhaar) = red, payment = green. Never reassign. ----
val CreditRed = Color(0xFFB3261E)
val CreditRedContainer = Color(0xFFF8DEDA)
val PaymentGreen = Color(0xFF1E7B4D)
val PaymentGreenContainer = Color(0xFFDCEEE0)
val SaleGold = Color(0xFF8A6A18)
val SaleGoldContainer = Color(0xFFF3E3BC)

// ---- Status pill dots ----
val StatusOnDevice = PaymentGreen
val StatusOffline = InkFaint
val StatusSyncing = AccentIndigoLight

// ---- Dark variant (secondary — app is optimized for light/daylight, this is a fallback) ----
val DarkBackground = Color(0xFF201A10)
val DarkSurface = Color(0xFF2B2417)
val DarkSurfaceVariant = Color(0xFF3A311F)
val DarkInkPrimary = Color(0xFFF1E7D4)
val DarkInkSecondary = Color(0xFFC7B693)
val DarkRuleLine = Color(0xFF4A3F29)
val DarkAccentIndigo = Color(0xFFACA3E8)
val DarkAccentIndigoContainer = Color(0xFF433C86)
val DarkCreditRed = Color(0xFFF2B8B2)
val DarkCreditRedContainer = Color(0xFF7A3230)
val DarkPaymentGreen = Color(0xFF9AD6AF)
val DarkPaymentGreenContainer = Color(0xFF215837)
val DarkSaleGold = Color(0xFFE3C878)
val DarkSaleGoldContainer = Color(0xFF5A480F)
