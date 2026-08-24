package com.rk.editor.intelligent

import android.view.KeyEvent
import com.rk.editor.Editor
import com.rk.settings.Settings
import io.github.rosemoe.sora.event.EditorKeyEvent

/**
 * Built-in zero-install universal code intelligence engine.
 * Provides intelligent indentation, smart block expansion on Enter, colon expansion (Python),
 * auto-closing pair management, and bracket step-over for all major languages:
 * HTML, CSS, JavaScript, TypeScript, Java, C, C++, Rust, Python, Go, Kotlin, C#, Swift, PHP, Ruby, SQL, JSON, YAML, Bash, Lua, etc.
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

    override val triggerCharacters: List<Char> = listOf('{', '(', '[', '"', '\'', '`', ':', '}')

    override fun handleKeyEvent(event: EditorKeyEvent, editor: Editor) {
        if (event.action != KeyEvent.ACTION_DOWN) return

        if (event.keyCode == KeyEvent.KEYCODE_ENTER && event.modifiers == 0) {
            if (handleEnter(editor)) {
                event.markAsConsumed()
            }
        }
    }

    override fun handleInsertChar(triggerCharacter: Char, editor: Editor) {
        if (editor.cursor.isSelected) return
        val lineIdx = editor.cursor.leftLine
        val colIdx = editor.cursor.leftColumn
        val line = editor.text.getLine(lineIdx)

        // Auto-close brackets and quotes if enabled
        if (!Settings.auto_closing_bracket) return

        when (triggerCharacter) {
            '{' -> autoClosePair(editor, lineIdx, colIdx, '}')
            '(' -> autoClosePair(editor, lineIdx, colIdx, ')')
            '[' -> autoClosePair(editor, lineIdx, colIdx, ']')
            '`' -> autoCloseQuote(editor, lineIdx, colIdx, '`', line)
            '"' -> autoCloseQuote(editor, lineIdx, colIdx, '"', line)
            '\'' -> autoCloseQuote(editor, lineIdx, colIdx, '\'', line)
        }
    }

    private fun autoClosePair(editor: Editor, lineIdx: Int, colIdx: Int, closingChar: Char) {
        val line = editor.text.getLine(lineIdx)
        val nextChar = if (colIdx < line.length) line[colIdx] else null

        // Only insert closing pair if at end of line or before whitespace/closing bracket
        if (nextChar == null || nextChar.isWhitespace() || nextChar in ")}];,") {
            editor.text.insert(lineIdx, colIdx, closingChar.toString())
            editor.setSelection(lineIdx, colIdx)
        }
    }

    private fun autoCloseQuote(editor: Editor, lineIdx: Int, colIdx: Int, quoteChar: Char, line: String) {
        val prevChar = if (colIdx >= 2) line[colIdx - 2] else null
        val nextChar = if (colIdx < line.length) line[colIdx] else null

        // Avoid auto-closing if escaped with backslash
        if (prevChar == '\\') return

        // If next char is already the quote (and not preceded by escape), step over
        if (nextChar == quoteChar) {
            // Remove the redundant quote and move cursor forward
            editor.text.delete(lineIdx, colIdx - 1, lineIdx, colIdx)
            editor.setSelection(lineIdx, colIdx)
            return
        }

        // Only auto-close if at end of line or before delimiter
        if (nextChar == null || nextChar.isWhitespace() || nextChar in ")}];,+-*/%=") {
            editor.text.insert(lineIdx, colIdx, quoteChar.toString())
            editor.setSelection(lineIdx, colIdx)
        }
    }

    private fun handleEnter(editor: Editor): Boolean {
        if (editor.isTextSelected) return false
        val lineIdx = editor.cursor.leftLine
        val colIdx = editor.cursor.leftColumn

        val line = editor.text.getLine(lineIdx)
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

        // Case 2: Enter after line ending with { or : (Python, C, C++, Java, JS, Rust, Go, etc.)
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
