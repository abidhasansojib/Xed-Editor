package com.rk.settings.editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rk.settings.Settings

const val DEFAULT_TERMINAL_FONT_PATH = "fonts/SourceCodePro-Medium.ttf"

@Composable
fun TerminalFontScreen(modifier: Modifier = Modifier) {
    val selectedFontPath = Settings.terminal_font_path

    FontScreen(modifier, selectedFontPath, DEFAULT_TERMINAL_FONT_PATH, null) { font ->
        Settings.terminal_font_path = font.pathOrAsset
        Settings.is_terminal_font_asset = font.isAsset
    }
}
