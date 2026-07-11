package com.khataagent.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/**
 * The giant mic FAB. Press and hold: a live amplitude ring jitters around the button like a
 * waveform, released on lift with an instant freeze (perceived-latency trick from BUILD.md —
 * never a frozen screen, the ring itself communicates "I heard you, thinking now").
 */
@Composable
fun MicButton(
    isListening: Boolean,
    isBusy: Boolean,
    onPressStart: () -> Unit,
    onPressEnd: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 96.dp,
) {
    val ringScale = remember { Animatable(1f) }
    var pressed by remember { mutableStateOf(false) }

    val buttonScale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = tween(120),
        label = "mic-press-scale",
    )

    // Live amplitude jitter while held — random-walk target radius, like a waveform ring.
    LaunchedEffect(isListening) {
        if (isListening) {
            while (true) {
                val target = 1.15f + Random.nextFloat() * 0.55f
                ringScale.animateTo(target, animationSpec = tween(140))
            }
        } else {
            ringScale.animateTo(1f, animationSpec = tween(220))
        }
    }

    Box(
        modifier = modifier
            .size(size + 56.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (isListening) {
            val ringColor = MaterialTheme.colorScheme.primary
            Box(
                modifier = Modifier
                    .size(size)
                    .scale(ringScale.value)
                    .background(color = ringColor.copy(alpha = 0.16f), shape = CircleShape),
            )
        }
        Box(
            modifier = Modifier
                .size(size)
                .scale(buttonScale)
                .background(
                    color = if (isBusy) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                )
                .pointerInput(isBusy) {
                    if (isBusy) return@pointerInput
                    awaitEachGesture {
                        awaitFirstDown()
                        pressed = true
                        onPressStart()
                        waitForUpOrCancellation()
                        pressed = false
                        onPressEnd()
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Mic,
                contentDescription = "Hold to speak a transaction",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(size * 0.42f),
            )
        }
    }
}
