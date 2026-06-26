package com.zeerqi27.etoilebridge.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

object EtoileShapeTokens {
    val TopBar = 32.dp
    val HeroCard = 28.dp
    val SectionCard = 24.dp
    val InnerCard = 20.dp
    val ImagePreview = 20.dp
    val Dialog = 32.dp
}

private val LocalScrollViewportTopPx = compositionLocalOf { 0f }
private val LocalScrollOffsetPx = compositionLocalOf { 0 }

fun edgeAwareRadiusPx(baseRadiusPx: Float, visibleHeightPx: Float): Float =
    min(baseRadiusPx, max(0f, visibleHeightPx) / 2f)

fun edgeAwareCropTopPx(viewportTopPx: Float, cardTopPx: Float, cardHeightPx: Float): Float =
    (viewportTopPx - cardTopPx).coerceIn(0f, max(0f, cardHeightPx))

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdaptiveActionRow(
    modifier: Modifier = Modifier,
    horizontalSpacing: androidx.compose.ui.unit.Dp = 8.dp,
    verticalSpacing: androidx.compose.ui.unit.Dp = 8.dp,
    content: @Composable () -> Unit,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
        verticalArrangement = Arrangement.spacedBy(verticalSpacing),
        content = { content() },
    )
}

@Composable
fun ScrollContentViewport(
    modifier: Modifier = Modifier,
    scrollState: ScrollState? = null,
    content: @Composable () -> Unit,
) {
    var viewportTopPx by remember { mutableFloatStateOf(0f) }
    // Keep the viewport deliberately unshaped. Cards keep their own shape while scrolling;
    // a shared rounded mask caused the top card edge to be clipped out of alignment.
    Box(
        modifier = modifier.onGloballyPositioned {
            viewportTopPx = it.positionInRoot().y
        },
    ) {
        CompositionLocalProvider(
            LocalScrollViewportTopPx provides viewportTopPx,
            LocalScrollOffsetPx provides (scrollState?.value ?: 0),
        ) {
            content()
        }
    }
}

@Composable
fun EdgeAwareCard(
    modifier: Modifier = Modifier,
    shape: Dp = EtoileShapeTokens.SectionCard,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceContainer,
    border: BorderStroke? = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f)),
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val viewportTopPx = LocalScrollViewportTopPx.current
    val scrollOffsetPx = LocalScrollOffsetPx.current
    var baseTopPx by remember { mutableFloatStateOf(Float.NaN) }
    var heightPx by remember { mutableIntStateOf(0) }
    val currentTopPx = if (baseTopPx.isNaN()) Float.NaN else baseTopPx - scrollOffsetPx
    val cropTopPx by remember(viewportTopPx, currentTopPx, heightPx) {
        derivedStateOf {
            if (currentTopPx.isNaN() || heightPx == 0) {
                0f
            } else {
                edgeAwareCropTopPx(viewportTopPx, currentTopPx, heightPx.toFloat())
            }
        }
    }
    val baseRadiusPx = with(density) { shape.toPx() }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .drawWithContent {
                val visibleHeightPx = max(0f, size.height - cropTopPx)
                if (visibleHeightPx <= 0f) return@drawWithContent
                if (cropTopPx <= 0.5f) {
                    drawContent()
                    return@drawWithContent
                }
                val dynamicRadiusPx = edgeAwareRadiusPx(baseRadiusPx, visibleHeightPx)
                val path = Path().apply {
                    addRoundRect(
                        RoundRect(
                            Rect(0f, cropTopPx, size.width, size.height),
                            CornerRadius(dynamicRadiusPx, dynamicRadiusPx),
                        ),
                    )
                }
                clipPath(path) {
                    this@drawWithContent.drawContent()
                }
            }
            .onGloballyPositioned {
                val nextBaseTopPx = it.positionInRoot().y + scrollOffsetPx
                if (baseTopPx.isNaN() || kotlin.math.abs(baseTopPx - nextBaseTopPx) > 0.5f) {
                    baseTopPx = nextBaseTopPx
                }
                heightPx = it.size.height
            },
        shape = RoundedCornerShape(shape),
        color = containerColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = border,
    ) {
        Box(modifier = Modifier.animateContentSize(), contentAlignment = contentAlignment) {
            content()
        }
    }
}
