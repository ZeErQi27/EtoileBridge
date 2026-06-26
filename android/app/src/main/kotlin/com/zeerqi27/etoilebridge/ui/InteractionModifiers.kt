package com.zeerqi27.etoilebridge.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown

fun Modifier.tapFeedbackOnly(
    enabled: Boolean = true,
    overlayColor: Color? = null,
): Modifier = composed {
    val color = overlayColor ?: MaterialTheme.colorScheme.onSurface
    var pressed by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (pressed) 0.06f else 0f,
        animationSpec = tween(durationMillis = if (pressed) 70 else 180, easing = FastOutSlowInEasing),
        label = "tapFeedbackAlpha",
    )
    this
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = true, pass = PointerEventPass.Final)
                if (down.isConsumed) return@awaitEachGesture
                val start = down.position
                val pointerId = down.id
                val touchSlop = viewConfiguration.touchSlop
                pressed = true
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                    val change = event.changes.firstOrNull { it.id == pointerId } ?: event.changes.firstOrNull()
                    if (change == null || change.isConsumed) {
                        pressed = false
                        break
                    }
                    if ((change.position - start).getDistance() > touchSlop) {
                        pressed = false
                        break
                    }
                    if (!change.pressed) {
                        pressed = false
                        break
                    }
                }
            }
        }
        .drawWithContent {
            drawContent()
            if (alpha > 0f) drawRect(color.copy(alpha = alpha))
        }
}

fun Modifier.pulseHighlight(
    active: Boolean,
    overlayColor: Color? = null,
): Modifier = composed {
    val color = overlayColor ?: MaterialTheme.colorScheme.primary
    val alpha by animateFloatAsState(
        targetValue = if (active) 0.12f else 0f,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "pulseHighlightAlpha",
    )
    drawWithContent {
        drawContent()
        if (alpha > 0f) drawRect(color.copy(alpha = alpha))
    }
}
