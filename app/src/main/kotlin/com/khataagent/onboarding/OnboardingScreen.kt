package com.khataagent.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.khataagent.R
import com.khataagent.ui.components.LedgerRuledBackground
import com.khataagent.ui.theme.KhataTheme

/** One selectable language: its BCP-47 tag and its name written in its own script. */
data class OnboardingLanguage(val tag: String, val nativeName: String, val englishName: String)

val onboardingLanguages = listOf(
    OnboardingLanguage(tag = "en", nativeName = "English", englishName = "English"),
    OnboardingLanguage(tag = "hi", nativeName = "हिंदी", englishName = "Hindi"),
    OnboardingLanguage(tag = "bn", nativeName = "বাংলা", englishName = "Bengali"),
    OnboardingLanguage(tag = "te", nativeName = "తెలుగు", englishName = "Telugu"),
    OnboardingLanguage(tag = "mr", nativeName = "मराठी", englishName = "Marathi"),
    OnboardingLanguage(tag = "ta", nativeName = "தமிழ்", englishName = "Tamil"),
    OnboardingLanguage(tag = "gu", nativeName = "ગુજરાતી", englishName = "Gujarati"),
    OnboardingLanguage(tag = "kn", nativeName = "ಕನ್ನಡ", englishName = "Kannada"),
    OnboardingLanguage(tag = "ml", nativeName = "മലയാളം", englishName = "Malayalam"),
    OnboardingLanguage(tag = "pa", nativeName = "ਪੰਜਾਬੀ", englishName = "Punjabi"),
)

/**
 * Warm first-run screen: a grid of big tappable language cards, each in its own script, so a
 * shopkeeper who can't read English can still recognize their language at a glance. Picking one
 * persists it via [LocaleManager] and hands control back to [onDone] — the caller (MainActivity)
 * is expected to recreate the activity so every screen picks up the new locale.
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val localeManager = remember { LocaleManager(context) }

    Box(modifier = modifier.fillMaxSize()) {
        LedgerRuledBackground()

        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp)) {
                Text(
                    text = stringResource(R.string.onboarding_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.onboarding_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(onboardingLanguages, key = { it.tag }) { language ->
                    LanguageCard(
                        language = language,
                        onClick = {
                            localeManager.setLanguage(language.tag)
                            onDone()
                        },
                    )
                }
            }

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun LanguageCard(language: OnboardingLanguage, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .height(6.dp)
                    .padding(bottom = 8.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), CircleShape),
            )
            Text(
                text = language.nativeName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = language.englishName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview(showBackground = true, name = "Onboarding")
@Composable
private fun OnboardingScreenPreview() {
    KhataTheme {
        OnboardingScreen(onDone = {})
    }
}
