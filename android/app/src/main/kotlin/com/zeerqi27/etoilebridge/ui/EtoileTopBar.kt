package com.zeerqi27.etoilebridge.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zeerqi27.etoilebridge.model.UiAppPage
import com.zeerqi27.etoilebridge.model.UiLanguage

val EtoileAppPages: List<UiAppPage> = listOf(
    UiAppPage.SingleSong,
    UiAppPage.PackBundle,
    UiAppPage.Character,
)

fun appPageIndex(page: UiAppPage): Int =
    EtoileAppPages.indexOf(page).takeIf { it >= 0 } ?: 0

fun pageSwitchDirection(initial: UiAppPage, target: UiAppPage): Int =
    appPageIndex(target).compareTo(appPageIndex(initial))

fun appPageLabel(page: UiAppPage, language: UiLanguage): String =
    when (language) {
        UiLanguage.ZhHans -> when (page) {
            UiAppPage.SingleSong -> "单曲转换"
            UiAppPage.PackBundle -> "曲包编辑"
            UiAppPage.Character -> "搭档编辑"
        }
        UiLanguage.English -> when (page) {
            UiAppPage.SingleSong -> "Single Song"
            UiAppPage.PackBundle -> "Pack Editor"
            UiAppPage.Character -> "Character Editor"
        }
    }

fun appPageIcon(page: UiAppPage): String =
    when (page) {
        UiAppPage.SingleSong -> AppSymbols.Music
        UiAppPage.PackBundle -> AppSymbols.Archive
        UiAppPage.Character -> AppSymbols.Person
    }

@Composable
fun EtoileTopBar(
    currentPage: UiAppPage,
    language: UiLanguage,
    busy: Boolean,
    clearCacheEnabled: Boolean,
    deviceSdkInt: Int,
    deviceRelease: String,
    onPageSelected: (UiAppPage) -> Unit,
    onLanguageChange: (UiLanguage) -> Unit,
    onClearCache: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val texts = textFor(language)
    var pageMenuOpen by rememberSaveable { mutableStateOf(false) }
    var settingsMenuOpen by rememberSaveable { mutableStateOf(false) }
    var languageMenuOpen by rememberSaveable { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(EtoileShapeTokens.TopBar),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    texts.appName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    appPageLabel(currentPage, language),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    IconButton(onClick = { pageMenuOpen = true }) {
                        SymbolIcon(AppSymbols.SwitchPage, contentDescription = texts.switchPage)
                    }
                    DropdownMenu(expanded = pageMenuOpen, onDismissRequest = { pageMenuOpen = false }) {
                        EtoileAppPages.forEach { page ->
                            val selected = page == currentPage
                            DropdownMenuItem(
                                text = { Text(appPageLabel(page, language)) },
                                leadingIcon = { SymbolIcon(appPageIcon(page), contentDescription = null) },
                                trailingIcon = if (selected) {
                                    { SymbolIcon(AppSymbols.Check, contentDescription = texts.currentPage) }
                                } else null,
                                onClick = {
                                    pageMenuOpen = false
                                    if (!selected) onPageSelected(page)
                                },
                            )
                        }
                    }
                }
                Box {
                    IconButton(onClick = { settingsMenuOpen = true }) {
                        SymbolIcon(AppSymbols.Settings, contentDescription = texts.settings)
                    }
                    DropdownMenu(expanded = settingsMenuOpen, onDismissRequest = { settingsMenuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(texts.language) },
                            leadingIcon = { SymbolIcon(AppSymbols.Language, contentDescription = null) },
                            onClick = {
                                settingsMenuOpen = false
                                languageMenuOpen = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(texts.clearCache) },
                            leadingIcon = { SymbolIcon(AppSymbols.Delete, contentDescription = null) },
                            enabled = clearCacheEnabled && !busy,
                            onClick = {
                                settingsMenuOpen = false
                                onClearCache()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(texts.about) },
                            leadingIcon = { SymbolIcon(AppSymbols.Info, contentDescription = null) },
                            onClick = {
                                settingsMenuOpen = false
                                showAbout = true
                            },
                        )
                    }
                    DropdownMenu(expanded = languageMenuOpen, onDismissRequest = { languageMenuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(texts.chinese) },
                            onClick = {
                                languageMenuOpen = false
                                onLanguageChange(UiLanguage.ZhHans)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(texts.english) },
                            onClick = {
                                languageMenuOpen = false
                                onLanguageChange(UiLanguage.English)
                            },
                        )
                    }
                }
            }
        }
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text(texts.appName) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(texts.projectDescription)
                    Text("${texts.versionInfo}: ${texts.debugBuild}")
                    Text("${texts.device}: SDK $deviceSdkInt / Android $deviceRelease")
                }
            },
            confirmButton = {
                TextButton(onClick = { showAbout = false }) { Text(texts.close) }
            },
        )
    }
}
