package com.rk.editor

import android.content.Context
import com.rk.file.FileTypeManager
import io.github.rosemoe.sora.lang.styling.color.ColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.lsp.editor.text.MarkdownCodeHighlighterRegistry
import io.github.rosemoe.sora.lsp.editor.text.withEditorHighlighter
import java.util.concurrent.ConcurrentHashMap

object CodeHighlighter {
    private val highlighterCache = ConcurrentHashMap<String, Pair<TextMateLanguage, ColorScheme>>()

    fun registerMarkdownCodeHighlighter(context: Context) {
        MarkdownCodeHighlighterRegistry.global.withEditorHighlighter { languageName ->
            val textmateScope =
                FileTypeManager.fromMarkdownName(languageName).textmateScope ?: return@withEditorHighlighter null

            highlighterCache.getOrPut(textmateScope) {
                val language = LanguageManager.createLanguageBlocking(textmateScope)
                val colorScheme = ThemeManager.createColorSchemeBlocking(context, null)
                language to colorScheme
            }
        }
    }
}
