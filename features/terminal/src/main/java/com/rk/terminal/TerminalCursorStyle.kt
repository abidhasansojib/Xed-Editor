package com.rk.terminal

import androidx.annotation.StringRes
import com.rk.resources.strings

enum class TerminalCursorStyle(val value: String, @StringRes val stringRes: Int) {
    BLOCK("block", strings.block),
    UNDERLINE("underline", strings.underline),
    BAR("bar", strings.bar);

    companion object {
        fun fromString(value: String): TerminalCursorStyle {
            return entries.firstOrNull { it.value.equals(value, ignoreCase = true) } ?: BLOCK
        }
    }
}
