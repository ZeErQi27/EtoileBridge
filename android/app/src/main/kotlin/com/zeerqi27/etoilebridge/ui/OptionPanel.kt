package com.zeerqi27.etoilebridge.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zeerqi27.etoilebridge.model.UiConvertOptions

@Composable
fun OptionPanel(
    options: UiConvertOptions,
    texts: AppText,
    onOptionsChange: (UiConvertOptions) -> Unit,
    enabled: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(texts.preprocessOptions, fontWeight = FontWeight.SemiBold)
        OptionSwitch(texts.deleteDesignant, options.enableDeleteDesignantLine, enabled) {
            onOptionsChange(options.copy(enableDeleteDesignantLine = it))
        }
        OptionSwitch(texts.fixZeroArcTap, options.enableFixZeroDurationArcTap, enabled) {
            onOptionsChange(options.copy(enableFixZeroDurationArcTap = it))
        }
        OptionSwitch(texts.fixReversedArc, options.enableFixReversedArcTime, enabled) {
            onOptionsChange(options.copy(enableFixReversedArcTime = it))
        }
        OptionSwitch(texts.expandArcResolution, options.enableExpandArcResolution, enabled) {
            onOptionsChange(options.copy(enableExpandArcResolution = it))
        }
    }
}

@Composable
private fun OptionSwitch(
    text: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text)
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
