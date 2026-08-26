package io.github.rosemoe.sora.langs.textmate

import android.os.Bundle
import com.rk.editor.KeywordManager
import com.rk.editor.snippets.SnippetManager
import io.github.rosemoe.sora.lang.completion.CompletionPublisher
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.ContentReference
import org.eclipse.tm4e.core.grammar.IGrammar
import org.eclipse.tm4e.languageconfiguration.internal.model.LanguageConfiguration

class XedTextMateLanguage(
    grammar: IGrammar,
    languageConfiguration: LanguageConfiguration?,
    grammarRegistry: GrammarRegistry,
    themeRegistry: ThemeRegistry,
    collectIdentifiers: Boolean,
    val textmateScope: String,
) : TextMateLanguage(grammar, languageConfiguration, grammarRegistry, themeRegistry, collectIdentifiers) {

    private var activeKeywords: Array<String>? = null

    override fun setCompleterKeywords(keywords: Array<String>?) {
        super.setCompleterKeywords(keywords)
        activeKeywords = keywords
    }

    override fun requireAutoComplete(
        content: ContentReference,
        position: CharPosition,
        publisher: CompletionPublisher,
        extraArguments: Bundle,
    ) {
        if (!isAutoCompleteEnabled) return

        val userIdentifiers = if (collectIdentifiers && textMateAnalyzer != null) {
            textMateAnalyzer.syncIdentifiers
        } else null

        val keywords = activeKeywords ?: KeywordManager.getKeywordsArrayDirect(textmateScope)

        SnippetManager.provideCompletions(
            scope = textmateScope,
            content = content,
            position = position,
            publisher = publisher,
            userIdentifiers = userIdentifiers,
            keywords = keywords,
        )
    }

    companion object {
        fun create(
            languageScopeName: String,
            grammarRegistry: GrammarRegistry = GrammarRegistry.getInstance(),
            themeRegistry: ThemeRegistry = ThemeRegistry.getInstance(),
            collectIdentifiers: Boolean = true,
        ): XedTextMateLanguage {
            val grammar = grammarRegistry.findGrammar(languageScopeName)
                ?: throw IllegalArgumentException("Language with scope name $languageScopeName not found")
            val languageConfiguration = grammarRegistry.findLanguageConfiguration(grammar.scopeName)
            return XedTextMateLanguage(
                grammar = grammar,
                languageConfiguration = languageConfiguration,
                grammarRegistry = grammarRegistry,
                themeRegistry = themeRegistry,
                collectIdentifiers = collectIdentifiers,
                textmateScope = languageScopeName,
            )
        }
    }
}
