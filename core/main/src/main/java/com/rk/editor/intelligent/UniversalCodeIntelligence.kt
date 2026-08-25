package com.rk.editor.intelligent

import android.view.KeyEvent
import com.rk.editor.Editor
import com.rk.settings.Settings
import io.github.rosemoe.sora.event.EditorKeyEvent

/**
 * Built-in zero-install universal code intelligence engine.
 * Provides intelligent indentation, smart block expansion on Enter, and colon expansion (Python)
 * for all major languages without interfering with editor text-change listeners.
 */
object UniversalCodeIntelligence : IntelligentFeature() {
    override val id: String = "universal.code_intelligence"

    override val supportedExtensions: List<String> = listOf(
        // Web
        "html", "htm", "xhtml", "css", "scss", "less", "js", "mjs", "cjs", "jsx",
        "ts", "mts", "cts", "tsx", "json", "jsonc", "yaml", "yml", "xml", "svg",
        // Compiled & Systems
        "c", "h", "cpp", "hpp", "cc", "cxx", "rs", "go", "java", "kt", "kts", "cs", "swift", "dart",
        // Scripting & Data
        "py", "pyw", "rb", "php", "sh", "bash", "zsh", "lua", "r", "sql", "pl", "pm", "groovy"
    )

    override fun handleKeyEvent(event: EditorKeyEvent, editor: Editor) {
        if (event.action != KeyEvent.ACTION_DOWN) return

        if (event.keyCode == KeyEvent.KEYCODE_ENTER && event.modifiers == 0) {
            if (handleEnter(editor)) {
                event.markAsConsumed()
            }
        }
    }

    private fun handleEnter(editor: Editor): Boolean {
        if (editor.isTextSelected) return false
        val lineIdx = editor.cursor.leftLine
        val colIdx = editor.cursor.leftColumn

        if (lineIdx < 0 || lineIdx >= editor.text.lineCount) return false

        val line = editor.text.getLine(lineIdx).toString()
        if (colIdx < 0 || colIdx > line.length) return false

        val textBefore = line.take(colIdx)
        val textAfter = line.substring(colIdx)

        val indentString = getLeadingWhitespace(line)
        val tabIndent = if (Settings.actual_tabs) "\t" else " ".repeat(Settings.tab_size)

        // Case 1: Enter between { and } or ( and ) or [ and ]
        val isBetweenBraces = (textBefore.trimEnd().endsWith("{") && textAfter.trimStart().startsWith("}")) ||
                (textBefore.trimEnd().endsWith("(") && textAfter.trimStart().startsWith(")")) ||
                (textBefore.trimEnd().endsWith("[") && textAfter.trimStart().startsWith("]"))

        if (isBetweenBraces) {
            val content = "\n$indentString$tabIndent\n$indentString"
            editor.text.insert(lineIdx, colIdx, content)
            editor.setSelection(lineIdx + 1, (indentString + tabIndent).length)
            return true
        }

        // Case 2: Enter after line ending with { or : or [ (Python, C, C++, Java, JS, Rust, Go, etc.)
        val trimmedBefore = textBefore.trimEnd()
        if (trimmedBefore.endsWith("{") || trimmedBefore.endsWith(":") || trimmedBefore.endsWith("[")) {
            val content = "\n$indentString$tabIndent"
            editor.text.insert(lineIdx, colIdx, content)
            editor.setSelection(lineIdx + 1, content.length - 1)
            return true
        }

        return false
    }

    private fun getLeadingWhitespace(line: String): String {
        val sb = StringBuilder()
        for (ch in line) {
            if (ch == ' ' || ch == '\t') {
                sb.append(ch)
            } else {
                break
            }
        }
        return sb.toString()
    }

    override fun isEnabled(): Boolean = true
}
