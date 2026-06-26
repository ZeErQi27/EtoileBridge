package com.zeerqi27.etoilebridge.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zeerqi27.etoilebridge.model.UiResourceStatus

@Composable
fun ResourceStatusPanel(status: UiResourceStatus, texts: AppText) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(texts.resources, fontWeight = FontWeight.SemiBold)
        KeyValue(texts.audio, status.audioFileName.withManual(status.audioManual, texts) ?: texts.notDetected)
        KeyValue(texts.jacket, status.jacketFileName.withManual(status.jacketManual, texts) ?: texts.notDetected)
        KeyValue(texts.background, status.backgroundFileName.withManual(status.backgroundManual, texts) ?: texts.backgroundNotDetected)
    }
}

private fun String?.withManual(manual: Boolean, texts: AppText): String? =
    this?.let { if (manual) "$it (${texts.manualTag})" else it }
