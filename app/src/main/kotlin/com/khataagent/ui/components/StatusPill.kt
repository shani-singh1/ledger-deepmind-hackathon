package com.khataagent.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.khataagent.core.AgentStatus
import com.khataagent.ui.theme.KhataThemeExtras

/**
 * Top-bar status honesty: `● on-device` / `○ offline` / `↑ syncing`.
 * Quietly reinforces the whole pitch — the local loop never depends on the network.
 */
@Composable
fun StatusPill(status: AgentStatus, modifier: Modifier = Modifier) {
    val extras = KhataThemeExtras.colors
    val (dotColor, label) = when (status) {
        AgentStatus.ON_DEVICE -> extras.statusOnDevice to "on-device"
        AgentStatus.OFFLINE -> extras.statusOffline to "offline"
        AgentStatus.SYNCING -> extras.statusSyncing to "syncing"
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(dotColor.copy(alpha = 0.12f))
            .padding(PaddingValues(horizontal = 10.dp, vertical = 5.dp)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when (status) {
            AgentStatus.SYNCING -> {
                val infinite = rememberInfiniteTransition(label = "syncing-arrow")
                val offsetY by infinite.animateFloat(
                    initialValue = 0f,
                    targetValue = -4f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(600, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "syncing-arrow-offset",
                )
                Icon(
                    imageVector = Icons.Filled.ArrowUpward,
                    contentDescription = null,
                    tint = dotColor,
                    modifier = Modifier
                        .size(12.dp)
                        .graphicsLayer { translationY = offsetY },
                )
            }
            AgentStatus.ON_DEVICE -> {
                val infinite = rememberInfiniteTransition(label = "on-device-pulse")
                val alpha by infinite.animateFloat(
                    initialValue = 0.55f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1100, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "on-device-pulse-alpha",
                )
                // ● filled dot, gently pulsing — "the model is here, always answering"
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .graphicsLayer { this.alpha = alpha }
                        .background(dotColor, CircleShape),
                )
            }
            AgentStatus.OFFLINE -> {
                // ○ hollow ring — deliberately calm, not an error state
                Canvas(modifier = Modifier.size(8.dp)) {
                    drawCircle(color = dotColor, style = Stroke(width = 1.4.dp.toPx()))
                }
            }
        }
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = dotColor)
    }
}
