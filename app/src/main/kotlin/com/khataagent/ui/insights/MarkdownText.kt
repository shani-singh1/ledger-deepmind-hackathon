package com.khataagent.ui.insights

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * A deliberately small markdown-ish renderer — just enough for the escalation reports'
 * headers/bold/bullets, no dependency needed for a hackathon build.
 */
@Composable
fun MarkdownText(markdown: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        markdown.lines().forEach { rawLine ->
            val line = rawLine.trimEnd()
            when {
                line.isBlank() -> Spacer(modifier = Modifier.height(6.dp))
                line.startsWith("### ") -> Text(
                    text = boldSpans(line.removePrefix("### ")),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                )
                line.startsWith("## ") -> Text(
                    text = boldSpans(line.removePrefix("## ")),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                )
                line.startsWith("- ") -> Text(
                    text = boldSpans("•  " + line.removePrefix("- ")),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 4.dp, top = 1.dp, bottom = 1.dp),
                )
                else -> Text(
                    text = boldSpans(line),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 1.dp),
                )
            }
        }
    }
}

private fun boldSpans(text: String) = buildAnnotatedString {
    val regex = Regex("\\*\\*(.+?)\\*\\*")
    var lastIndex = 0
    for (match in regex.findAll(text)) {
        append(text.substring(lastIndex, match.range.first))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            append(match.groupValues[1])
        }
        lastIndex = match.range.last + 1
    }
    append(text.substring(lastIndex))
}
