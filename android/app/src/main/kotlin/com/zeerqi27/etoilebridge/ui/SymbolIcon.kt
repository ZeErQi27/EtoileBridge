package com.zeerqi27.etoilebridge.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeerqi27.etoilebridge.R

@Immutable
object AppSymbols {
    const val Settings = "settings"
    const val SwitchPage = "swap_horiz"
    const val Archive = "archive"
    const val Folder = "folder"
    const val Search = "search"
    const val Article = "article"
    const val Image = "image"
    const val Palette = "palette"
    const val AccountTree = "account_tree"
    const val Tune = "tune"
    const val Convert = "sync_alt"
    const val Download = "download"
    const val SaveAs = "save_as"
    const val Terminal = "terminal"
    const val Warning = "warning"
    const val Error = "error"
    const val Info = "info"
    const val Check = "check_circle"
    const val Delete = "delete"
    const val Language = "language"
    const val Music = "music_note"
    const val Person = "person"
    const val ExpandMore = "expand_more"
}

private val MaterialSymbolsRoundedFont = FontFamily(
    Font(R.font.material_symbols_rounded, weight = FontWeight.Normal),
)

@Composable
fun SymbolIcon(
    symbol: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    color: Color = LocalContentColor.current,
) {
    val semanticsModifier = Modifier.clearAndSetSemantics {
        if (contentDescription != null) this.contentDescription = contentDescription
    }
    Box(
        modifier = modifier
            .size(size)
            .then(semanticsModifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = symbol,
            color = color,
            maxLines = 1,
            textAlign = TextAlign.Center,
            style = TextStyle(
                fontFamily = MaterialSymbolsRoundedFont,
                fontSize = size.value.sp,
                lineHeight = size.value.sp,
                fontFeatureSettings = "liga",
            ),
        )
    }
}
