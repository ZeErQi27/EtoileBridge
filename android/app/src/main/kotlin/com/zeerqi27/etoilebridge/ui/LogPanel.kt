package com.zeerqi27.etoilebridge.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun LogPanel(title: String, lines: List<String>, texts: AppText) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold)
        if (lines.isEmpty()) {
            Text(texts.none)
        } else {
            lines.takeLast(80).forEach { Text(it) }
        }
    }
}
