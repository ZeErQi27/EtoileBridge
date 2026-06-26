package com.zeerqi27.etoilebridge.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zeerqi27.etoilebridge.model.UiMissingMetadata

@Composable
fun MissingMetadataPanel(missingMetadata: UiMissingMetadata?, texts: AppText) {
    if (missingMetadata == null) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(texts.needMetadata, fontWeight = FontWeight.SemiBold)
        Text(missingMetadata.reason)
        ListSection(texts.missingFields, missingMetadata.requiredFields, texts)
        ListSection(texts.optionalFields, missingMetadata.optionalFields, texts)
        ListSection(texts.candidateSongIds, missingMetadata.candidateSongIds, texts)
        ListSection(texts.scannedAff, missingMetadata.affFiles, texts)
        ListSection(texts.scannedResources, missingMetadata.resourceFiles, texts)
        Text(texts.pendingMetadata)
    }
}
