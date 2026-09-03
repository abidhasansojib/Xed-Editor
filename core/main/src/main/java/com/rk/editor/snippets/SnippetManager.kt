package com.rk.editor.snippets

import com.rk.editor.KeywordManager
import io.github.rosemoe.sora.lang.completion.CompletionItem
import io.github.rosemoe.sora.lang.completion.CompletionItemKind
import io.github.rosemoe.sora.lang.completion.CompletionPublisher
import io.github.rosemoe.sora.lang.completion.IdentifierAutoComplete.Identifiers
import io.github.rosemoe.sora.lang.completion.SimpleCompletionItem
import io.github.rosemoe.sora.lang.completion.SimpleSnippetCompletionItem
import io.github.rosemoe.sora.lang.completion.SnippetDescription
import io.github.rosemoe.sora.lang.completion.snippet.CodeSnippet
import io.github.rosemoe.sora.lang.completion.snippet.parser.CodeSnippetParser
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.ContentReference
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.launch

data class Snippet(
    val trigger: String,
    val label: String = trigger,
    val description: String,
    val template: String,
    val detail: String? = null,
    val scope: String? = null,
) {
    val parsedCodeSnippet: CodeSnippet by lazy {
        CodeSnippetParser.parse(template)
    }
}

object SnippetManager {

    private val snippetRegistry = ConcurrentHashMap<String, List<Snippet>>()

    init {
        registerBuiltinSnippets()
        loadCustomSnippetsAsync()
    }

    fun registerSnippet(scope: String, snippet: Snippet) {
        val normalized = scope.lowercase()
        snippetRegistry.compute(normalized) { _, current ->
            val list = current?.toMutableList() ?: mutableListOf()
            list.removeAll { it.trigger == snippet.trigger }
            list.add(0, snippet)
            list
        }
    }

    fun registerSnippets(scope: String, snippets: List<Snippet>) {
        val normalized = scope.lowercase()
        snippetRegistry.compute(normalized) { _, current ->
            val list = current?.toMutableList() ?: mutableListOf()
            for (snip in snippets) {
                list.removeAll { it.trigger == snip.trigger }
                list.add(snip)
            }
            list
        }
    }

    fun loadCustomSnippetsAsync() {
        try {
            val app = com.rk.utils.application ?: return
            val snippetsDir = app.filesDir.resolve("snippets")
            if (!snippetsDir.exists()) {
                snippetsDir.mkdirs()
                return
            }
            if (!snippetsDir.isDirectory) return

            com.rk.DefaultScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val jsonFiles = snippetsDir.listFiles { file ->
                    file.isFile && (file.extension.equals("json", ignoreCase = true) || file.name.endsWith(".code-snippets", ignoreCase = true))
                } ?: return@launch

                for (file in jsonFiles) {
                    val snippets = VsCodeSnippetLoader.parseFile(file)
                    for (snippet in snippets) {
                        val targetScope = snippet.scope ?: file.nameWithoutExtension.lowercase()
                        registerSnippet(targetScope, snippet)
                    }
                }
            }
        } catch (_: Exception) {}
    }

    fun getSnippetsForScope(scope: String): List<Snippet> {
        val normalized = scope.lowercase()
        val direct = snippetRegistry[normalized] ?: emptyList()
        val baseScope = normalized.substringBeforeLast(".")
        val base = if (baseScope != normalized) snippetRegistry[baseScope] ?: emptyList() else emptyList()

        val htmlAliases = if (normalized.contains("html") || normalized.contains("htm")) {
            snippetRegistry["text.html.basic"] ?: emptyList()
        } else emptyList()

        val jsAliases = if (normalized.contains("javascript") || normalized.contains("js") || normalized.contains("typescript") || normalized.contains("ts")) {
            snippetRegistry["source.js"] ?: emptyList()
        } else emptyList()

        val cssAliases = if (normalized.contains("css") || normalized.contains("scss") || normalized.contains("less")) {
            snippetRegistry["source.css"] ?: emptyList()
        } else emptyList()

        val ktAliases = if (normalized.contains("kotlin") || normalized.contains("kt")) {
            snippetRegistry["source.kotlin"] ?: emptyList()
        } else emptyList()

        val javaAliases = if (normalized.contains("java") && !normalized.contains("javascript")) {
            snippetRegistry["source.java"] ?: emptyList()
        } else emptyList()

        val cAliases = if (normalized.contains("cpp") || normalized.contains("c++") || normalized.endsWith(".c") || normalized.contains("source.c")) {
            (snippetRegistry["source.cpp"] ?: emptyList()) + (snippetRegistry["source.c"] ?: emptyList())
        } else emptyList()

        val pyAliases = if (normalized.contains("python") || normalized.contains("py")) {
            snippetRegistry["source.python"] ?: emptyList()
        } else emptyList()

        val rustAliases = if (normalized.contains("rust") || normalized.contains("rs")) {
            snippetRegistry["source.rust"] ?: emptyList()
        } else emptyList()

        val goAliases = if (normalized.contains("golang") || normalized.contains("go")) {
            snippetRegistry["source.go"] ?: emptyList()
        } else emptyList()

        val shAliases = if (normalized.contains("shell") || normalized.contains("bash") || normalized.contains("sh")) {
            snippetRegistry["source.shell"] ?: emptyList()
        } else emptyList()

        val all = (direct + base + htmlAliases + jsAliases + cssAliases + ktAliases + javaAliases + cAliases + pyAliases + rustAliases + goAliases + shAliases).distinctBy { it.trigger }
        return all
    }

    fun computePrefix(content: ContentReference, position: CharPosition): String {
        val line = content.getLine(position.line)
        val col = position.column
        if (col <= 0 || col > line.length) return ""

        var start = col - 1
        while (start >= 0) {
            val ch = line[start]
            // Allow identifier chars and tags/emmet trigger symbols (:, -, ., #, !)
            // Break on operators like =, +, *, <, >, [, ], {, }, (, ), ;, ,
            if (ch.isLetterOrDigit() || ch == '_' || ch == '$' || ch == ':' || ch == '-' || ch == '.' || ch == '#' || ch == '!') {
                start--
            } else {
                break
            }
        }
        return line.substring(start + 1, col)
    }

    fun computeIdentifierPrefix(content: ContentReference, position: CharPosition): String {
        val line = content.getLine(position.line)
        val col = position.column
        if (col <= 0 || col > line.length) return ""

        var start = col - 1
        while (start >= 0) {
            val ch = line[start]
            if (ch.isLetterOrDigit() || ch == '_' || ch == '$') {
                start--
            } else {
                break
            }
        }
        return line.substring(start + 1, col)
    }

    fun parseDynamicEmmet(prefix: String, scope: String): Snippet? {
        val parsed = EmmetParser.parse(prefix, scope)
        if (parsed != null) return parsed

        val normalized = scope.lowercase()
        if (!normalized.contains("html") && !normalized.contains("xml") && !normalized.contains("htm")) {
            return null
        }
        if (prefix.isBlank() || prefix.length < 2) return null

        // 1. .class or #id (e.g. .container -> <div class="container">$0</div>)
        if (prefix.startsWith(".")) {
            val cls = prefix.removePrefix(".")
            if (cls.isNotEmpty() && cls.all { it.isLetterOrDigit() || it == '-' || it == '_' }) {
                return Snippet(prefix, prefix, "Emmet: <div class=\"$cls\">", "<div class=\"$cls\">$0</div>", "Emmet")
            }
        }
        if (prefix.startsWith("#")) {
            val id = prefix.removePrefix("#")
            if (id.isNotEmpty() && id.all { it.isLetterOrDigit() || it == '-' || it == '_' }) {
                return Snippet(prefix, prefix, "Emmet: <div id=\"$id\">", "<div id=\"$id\">$0</div>", "Emmet")
            }
        }

        // 2. tag.class or tag#id (e.g. div.card -> <div class="card">$0</div>)
        if (prefix.contains(".")) {
            val tag = prefix.substringBefore(".")
            val cls = prefix.substringAfter(".")
            if (tag.isNotEmpty() && cls.isNotEmpty() &&
                tag.all { it.isLetterOrDigit() || it == '-' } &&
                cls.all { it.isLetterOrDigit() || it == '-' || it == '_' }
            ) {
                return Snippet(prefix, prefix, "Emmet: <$tag class=\"$cls\">", "<$tag class=\"$cls\">$0</$tag>", "Emmet")
            }
        }
        if (prefix.contains("#")) {
            val tag = prefix.substringBefore("#")
            val id = prefix.substringAfter("#")
            if (tag.isNotEmpty() && id.isNotEmpty() &&
                tag.all { it.isLetterOrDigit() || it == '-' } &&
                id.all { it.isLetterOrDigit() || it == '-' || it == '_' }
            ) {
                return Snippet(prefix, prefix, "Emmet: <$tag id=\"$id\">", "<$tag id=\"$id\">$0</$tag>", "Emmet")
            }
        }

        // 3. Any arbitrary tag (e.g. dialog -> <dialog>$0</dialog>)
        if (prefix.length >= 2 && prefix.all { it.isLetterOrDigit() || it == '-' }) {
            return Snippet(prefix, prefix, "Emmet: <$prefix>", "<$prefix>$0</$prefix>", "Emmet")
        }

        return null
    }

    fun provideCompletions(
        scope: String,
        content: ContentReference,
        position: CharPosition,
        publisher: CompletionPublisher,
        userIdentifiers: Identifiers?,
        keywords: Array<String>?,
    ) {
        val rawPrefix = computePrefix(content, position)
        val idPrefix = computeIdentifierPrefix(content, position)

        if (rawPrefix.isEmpty() && idPrefix.isEmpty()) return

        val items = ArrayList<CompletionItem>()
        val seenLabels = HashSet<String>()

        // 1. Snippets (highest ranking & priority)
        val snippets = getSnippetsForScope(scope)
        val prefixToMatch = if (rawPrefix.isNotEmpty() && snippets.any { it.trigger.startsWith(rawPrefix, ignoreCase = true) }) {
            rawPrefix.lowercase()
        } else {
            idPrefix.lowercase()
        }

        if (snippets.isNotEmpty() && prefixToMatch.isNotEmpty()) {
            for (snip in snippets) {
                val triggerLower = snip.trigger.lowercase()
                val labelLower = snip.label.lowercase()
                val matches = triggerLower.startsWith(prefixToMatch) ||
                        labelLower.startsWith(prefixToMatch) ||
                        (prefixToMatch.length >= 2 && triggerLower.contains(prefixToMatch))

                if (matches) {
                    val prefixLen = if (rawPrefix.isNotEmpty() && (triggerLower.startsWith(rawPrefix.lowercase()) || rawPrefix == "!")) {
                        rawPrefix.length
                    } else if (idPrefix.isNotEmpty()) {
                        idPrefix.length
                    } else {
                        rawPrefix.length
                    }

                    val isExact = triggerLower == prefixToMatch || labelLower == prefixToMatch
                    val rank = if (isExact) "00" else if (triggerLower.startsWith(prefixToMatch)) "01" else "02"

                    val snippetDesc = SnippetDescription(prefixLen, snip.parsedCodeSnippet, true)
                    val item = SimpleSnippetCompletionItem(snip.label, snip.description, snippetDesc).apply {
                        this.prefixLength = prefixLen
                        kind(CompletionItemKind.Snippet)
                        this.detail = snip.detail ?: "Snippet"
                        this.sortText = "${rank}_${snip.trigger}"
                    }
                    items.add(item)
                    seenLabels.add(snip.label.lowercase())
                }
            }
        }

        // 1.5 Dynamic Emmet expansion
        val dynamicEmmet = parseDynamicEmmet(rawPrefix.ifEmpty { idPrefix }, scope)
        if (dynamicEmmet != null && !seenLabels.contains(dynamicEmmet.label.lowercase())) {
            val prefixLen = rawPrefix.ifEmpty { idPrefix }.length
            val snippetDesc = SnippetDescription(prefixLen, dynamicEmmet.parsedCodeSnippet, true)
            val item = SimpleSnippetCompletionItem(dynamicEmmet.label, dynamicEmmet.description, snippetDesc).apply {
                this.prefixLength = prefixLen
                kind(CompletionItemKind.Snippet)
                this.detail = "Emmet"
                this.sortText = "01_${dynamicEmmet.trigger}"
            }
            items.add(item)
            seenLabels.add(dynamicEmmet.label.lowercase())
        }

        // 2. Keywords
        val matchId = idPrefix.lowercase()
        if (keywords != null && idPrefix.isNotEmpty()) {
            for (kw in keywords) {
                val kwLower = kw.lowercase()
                if (kwLower.startsWith(matchId)) {
                    if (!seenLabels.contains(kwLower)) {
                        val isExact = kwLower == matchId
                        val rank = if (isExact) "10" else "11"
                        val item = SimpleCompletionItem(kw, "Keyword", idPrefix.length, kw).apply {
                            kind(CompletionItemKind.Keyword)
                            this.sortText = "${rank}_$kw"
                        }
                        items.add(item)
                        seenLabels.add(kwLower)
                    }
                }
            }
        }

        // 3. User Document Identifiers
        if (userIdentifiers != null && idPrefix.isNotEmpty()) {
            val identifierList = ArrayList<String>()
            userIdentifiers.filterIdentifiers(idPrefix, identifierList)
            for (id in identifierList) {
                val idLower = id.lowercase()
                if (!seenLabels.contains(idLower)) {
                    val isExact = idLower == matchId
                    val rank = if (isExact) "20" else "21"
                    val item = SimpleCompletionItem(id, "Identifier", idPrefix.length, id).apply {
                        kind(CompletionItemKind.Identifier)
                        this.sortText = "${rank}_$id"
                    }
                    items.add(item)
                    seenLabels.add(idLower)
                }
            }
        }

        if (items.isNotEmpty()) {
            items.sortWith { a, b ->
                val sa = a.sortText ?: a.label.toString()
                val sb = b.sortText ?: b.label.toString()
                sa.compareTo(sb, ignoreCase = true)
            }
            publisher.setComparator(null)
            publisher.addItems(items)
        }
    }

    private fun registerBuiltinSnippets() {
        // ==========================================
        // HTML SNIPPETS (Emmet & VS Code standard)
        // ==========================================
        val htmlSnippets = listOf(
            Snippet(
                trigger = "!",
                label = "!",
                description = "HTML5 Boilerplate",
                template = "<!DOCTYPE html>\n<html lang=\"\${1:en}\">\n<head>\n\t<meta charset=\"UTF-8\">\n\t<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n\t<title>\${2:Document}</title>\n</head>\n<body>\n\t$0\n</body>\n</html>",
                detail = "HTML5 template",
            ),
            Snippet(
                trigger = "html:5",
                label = "html:5",
                description = "HTML5 Boilerplate Document",
                template = "<!DOCTYPE html>\n<html lang=\"\${1:en}\">\n<head>\n\t<meta charset=\"UTF-8\">\n\t<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n\t<title>\${2:Document}</title>\n</head>\n<body>\n\t$0\n</body>\n</html>",
                detail = "HTML5 template",
            ),
            Snippet(
                trigger = "html",
                label = "html",
                description = "HTML5 Document skeleton",
                template = "<!DOCTYPE html>\n<html lang=\"\${1:en}\">\n<head>\n\t<meta charset=\"UTF-8\">\n\t<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n\t<title>\${2:Document}</title>\n</head>\n<body>\n\t$0\n</body>\n</html>",
                detail = "HTML5 template",
            ),
            Snippet("h1", "h1", "Heading 1", "<h1>$0</h1>"),
            Snippet("h2", "h2", "Heading 2", "<h2>$0</h2>"),
            Snippet("h3", "h3", "Heading 3", "<h3>$0</h3>"),
            Snippet("h4", "h4", "Heading 4", "<h4>$0</h4>"),
            Snippet("h5", "h5", "Heading 5", "<h5>$0</h5>"),
            Snippet("h6", "h6", "Heading 6", "<h6>$0</h6>"),
            Snippet("p", "p", "Paragraph", "<p>$0</p>"),
            Snippet("div", "div", "Division element", "<div\${1: class=\"\${2:container}\"}>\n\t$0\n</div>"),
            Snippet("div.", "div.class", "Division with class", "<div class=\"\${1:className}\">$0</div>"),
            Snippet("div#", "div#id", "Division with id", "<div id=\"\${1:idName}\">$0</div>"),
            Snippet("span", "span", "Span inline element", "<span>$0</span>"),
            Snippet("a", "a", "Hyperlink anchor", "<a href=\"\${1:#}\">$0</a>"),
            Snippet("a:link", "a:link", "External target hyperlink", "<a href=\"http://\${1:url}\" target=\"_blank\" rel=\"noopener noreferrer\">$0</a>"),
            Snippet("img", "img", "Image tag", "<img src=\"\${1:image.jpg}\" alt=\"\${2:description}\">"),
            Snippet("button", "button", "Button element", "<button type=\"\${1:button}\">$0</button>"),
            Snippet("button:submit", "button:submit", "Submit button", "<button type=\"submit\">$0</button>"),
            Snippet("input", "input", "Input element", "<input type=\"\${1:text}\" name=\"\${2}\" placeholder=\"\${3}\">"),
            Snippet("input:text", "input:text", "Text input", "<input type=\"text\" name=\"\${1}\" id=\"\${1}\">"),
            Snippet("input:password", "input:password", "Password input", "<input type=\"password\" name=\"\${1:password}\" id=\"\${1:password}\">"),
            Snippet("input:email", "input:email", "Email input", "<input type=\"email\" name=\"\${1:email}\" id=\"\${1:email}\">"),
            Snippet("input:number", "input:number", "Number input", "<input type=\"number\" name=\"\${1}\" id=\"\${1}\">"),
            Snippet("input:checkbox", "input:checkbox", "Checkbox input", "<input type=\"checkbox\" name=\"\${1}\" id=\"\${1}\">"),
            Snippet("input:radio", "input:radio", "Radio input", "<input type=\"radio\" name=\"\${1}\" id=\"\${1}\">"),
            Snippet("input:submit", "input:submit", "Submit input", "<input type=\"submit\" value=\"\${1:Submit}\">"),
            Snippet("form", "form", "Form element", "<form action=\"\${1}\" method=\"\${2:POST}\">\n\t$0\n</form>"),
            Snippet("label", "label", "Label element", "<label for=\"\${1}\">$0</label>"),
            Snippet("select", "select", "Select dropdown", "<select name=\"\${1}\" id=\"\${1}\">\n\t<option value=\"\${2:val}\">\${3:Option}</option>\n\t$0\n</select>"),
            Snippet("option", "option", "Select option", "<option value=\"\${1:val}\">$0</option>"),
            Snippet("textarea", "textarea", "Textarea element", "<textarea name=\"\${1}\" id=\"\${1}\" rows=\"\${2:4}\" cols=\"\${3:50}\">$0</textarea>"),
            Snippet("ul", "ul", "Unordered list", "<ul>\n\t<li>$0</li>\n</ul>"),
            Snippet("ol", "ol", "Ordered list", "<ol>\n\t<li>$0</li>\n</ol>"),
            Snippet("li", "li", "List item", "<li>$0</li>"),
            Snippet("table", "table", "HTML Table structure", "<table>\n\t<thead>\n\t\t<tr>\n\t\t\t<th>\${1:Header}</th>\n\t\t</tr>\n\t</thead>\n\t<tbody>\n\t\t<tr>\n\t\t\t<td>$0</td>\n\t\t</tr>\n\t</tbody>\n</table>"),
            Snippet("tr", "tr", "Table row", "<tr>\n\t<td>$0</td>\n</tr>"),
            Snippet("td", "td", "Table data cell", "<td>$0</td>"),
            Snippet("th", "th", "Table header cell", "<th>$0</th>"),
            Snippet("link:css", "link:css", "Link stylesheet", "<link rel=\"stylesheet\" href=\"\${1:style.css}\">"),
            Snippet("link", "link", "Link stylesheet", "<link rel=\"stylesheet\" href=\"\${1:style.css}\">"),
            Snippet("link:favicon", "link:favicon", "Favicon link", "<link rel=\"shortcut icon\" href=\"\${1:favicon.ico}\" type=\"image/x-icon\">"),
            Snippet("script", "script", "Script block", "<script>\n\t$0\n</script>"),
            Snippet("script:src", "script:src", "Script external source", "<script src=\"\${1:script.js}\"></script>"),
            Snippet("style", "style", "Style block", "<style>\n\t$0\n</style>"),
            Snippet("header", "header", "Header semantic tag", "<header>\n\t$0\n</header>"),
            Snippet("footer", "footer", "Footer semantic tag", "<footer>\n\t$0\n</footer>"),
            Snippet("nav", "nav", "Navigation semantic tag", "<nav>\n\t$0\n</nav>"),
            Snippet("main", "main", "Main content tag", "<main>\n\t$0\n</main>"),
            Snippet("section", "section", "Section tag", "<section>\n\t$0\n</section>"),
            Snippet("article", "article", "Article tag", "<article>\n\t$0\n</article>"),
            Snippet("aside", "aside", "Aside tag", "<aside>\n\t$0\n</aside>"),
            Snippet("meta:vp", "meta:vp", "Viewport meta tag", "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">"),
            Snippet("meta:utf", "meta:utf", "Charset UTF-8 meta tag", "<meta charset=\"UTF-8\">"),
            Snippet("video", "video", "Video player tag", "<video controls src=\"\${1:video.mp4}\">\n\t$0\n</video>"),
            Snippet("audio", "audio", "Audio player tag", "<audio controls src=\"\${1:audio.mp3}\">\n\t$0\n</audio>"),
            Snippet("iframe", "iframe", "Inline iframe", "<iframe src=\"\${1:url}\" title=\"\${2:title}\" frameborder=\"0\"></iframe>"),
            Snippet("canvas", "canvas", "Canvas element", "<canvas id=\"\${1:canvas}\" width=\"\${2:300}\" height=\"\${3:150}\">$0</canvas>"),
            Snippet("svg", "svg", "SVG element", "<svg width=\"\${1:100}\" height=\"\${2:100}\" xmlns=\"http://www.w3.org/2000/svg\">\n\t$0\n</svg>")
        )
        snippetRegistry["text.html.basic"] = htmlSnippets
        snippetRegistry["text.html.derivative"] = htmlSnippets

        // ==========================================
        // JAVASCRIPT & TYPESCRIPT SNIPPETS
        // ==========================================
        val jsSnippets = listOf(
            Snippet("clg", "clg", "console.log", "console.log($0);"),
            Snippet("log", "log", "console.log", "console.log($0);"),
            Snippet("cerr", "cerr", "console.error", "console.error($0);"),
            Snippet("cwarn", "cwarn", "console.warn", "console.warn($0);"),
            Snippet("fn", "fn", "Function declaration", "function \${1:name}(\${2:params}) {\n\t$0\n}"),
            Snippet("function", "function", "Function declaration", "function \${1:name}(\${2:params}) {\n\t$0\n}"),
            Snippet("afn", "afn", "Arrow function", "(\${1:params}) => {\n\t$0\n}"),
            Snippet("cafn", "cafn", "Const arrow function", "const \${1:name} = (\${2:params}) => {\n\t$0\n};"),
            Snippet("if", "if", "If statement", "if (\${1:condition}) {\n\t$0\n}"),
            Snippet("ife", "ife", "If-Else statement", "if (\${1:condition}) {\n\t\${2}\n} else {\n\t$0\n}"),
            Snippet("for", "for", "For loop", "for (let \${1:i} = 0; \${1:i} < \${2:array}.length; \${1:i}++) {\n\t$0\n}"),
            Snippet("forof", "forof", "For-Of loop", "for (const \${1:item} of \${2:iterable}) {\n\t$0\n}"),
            Snippet("forin", "forin", "For-In loop", "for (const \${1:key} in \${2:object}) {\n\t$0\n}"),
            Snippet("while", "while", "While loop", "while (\${1:condition}) {\n\t$0\n}"),
            Snippet("switch", "switch", "Switch statement", "switch (\${1:key}) {\n\tcase \${2:value}:\n\t\t$0\n\t\tbreak;\n\tdefault:\n\t\tbreak;\n}"),
            Snippet("try", "try", "Try-Catch block", "try {\n\t$0\n} catch (\${1:error}) {\n\tconsole.error(\${1:error});\n}"),
            Snippet("prom", "prom", "New Promise", "new Promise((resolve, reject) => {\n\t$0\n});"),
            Snippet("asyncfn", "asyncfn", "Async function", "async function \${1:name}(\${2:params}) {\n\t$0\n}"),
            Snippet("imp", "imp", "Import default", "import \${1:module} from '\${2:package}';"),
            Snippet("imd", "imd", "Import destructured", "import { \${1:members} } from '\${2:package}';"),
            Snippet("exp", "exp", "Export default", "export default \${1:name};"),
            Snippet("expc", "expc", "Export const", "export const \${1:name} = $0;"),
            Snippet("fe", "fe", "Array forEach", "\${1:array}.forEach((\${2:item}) => {\n\t$0\n});"),
            Snippet("map", "map", "Array map", "\${1:array}.map((\${2:item}) => \${0:\$2})"),
            Snippet("filter", "filter", "Array filter", "\${1:array}.filter((\${2:item}) => \${0:true})"),
            Snippet("reduce", "reduce", "Array reduce", "\${1:array}.reduce((\${2:acc}, \${3:cur}) => {\n\t$0\n}, \${4:initial})"),
            Snippet("st", "st", "setTimeout", "setTimeout(() => {\n\t$0\n}, \${1:1000});"),
            Snippet("si", "si", "setInterval", "setInterval(() => {\n\t$0\n}, \${1:1000});"),
            Snippet("class", "class", "Class declaration", "class \${1:ClassName} {\n\tconstructor(\${2:params}) {\n\t\t$0\n\t}\n}"),
            Snippet("fetch", "fetch", "Fetch API call", "fetch('\${1:https://api.example.com/data}')\n\t.then(res => res.json())\n\t.then(data => {\n\t\t$0\n\t})\n\t.catch(err => console.error(err));")
        )
        snippetRegistry["source.js"] = jsSnippets
        snippetRegistry["source.ts"] = jsSnippets
        snippetRegistry["source.jsx"] = jsSnippets
        snippetRegistry["source.tsx"] = jsSnippets

        // ==========================================
        // PYTHON SNIPPETS
        // ==========================================
        val pySnippets = listOf(
            Snippet("main", "main", "if __name__ == '__main__'", "if __name__ == '__main__':\n\t\${1:main()}"),
            Snippet("def", "def", "Function definition", "def \${1:function_name}(\${2:args}):\n\t\${0:pass}"),
            Snippet("class", "class", "Class definition", "class \${1:ClassName}:\n\tdef __init__(self\${2:, args}):\n\t\t$0"),
            Snippet("for", "for", "For in loop", "for \${1:item} in \${2:iterable}:\n\t$0"),
            Snippet("fori", "fori", "For range loop", "for \${1:i} in range(\${2:n}):\n\t$0"),
            Snippet("while", "while", "While loop", "while \${1:condition}:\n\t$0"),
            Snippet("if", "if", "If statement", "if \${1:condition}:\n\t$0"),
            Snippet("ife", "ife", "If-Else statement", "if \${1:condition}:\n\t\${2:pass}\nelse:\n\t$0"),
            Snippet("try", "try", "Try-Except block", "try:\n\t\${1:pass}\nexcept \${2:Exception} as \${3:e}:\n\t$0"),
            Snippet("with", "with", "With file open", "with open('\${1:filename}', '\${2:r}') as \${3:f}:\n\t$0"),
            Snippet("pr", "pr", "Print statement", "print($0)"),
            Snippet("print", "print", "Print statement", "print($0)"),
            Snippet("prf", "prf", "Print formatted f-string", "print(f\"{$0}\")"),
            Snippet("lambda", "lambda", "Lambda expression", "lambda \${1:x}: $0"),
            Snippet("lc", "lc", "List comprehension", "[\${1:x} for \${1:x} in \${2:iterable}]"),
            Snippet("dc", "dc", "Dict comprehension", "{\${1:k}: \${2:v} for \${1:k}, \${2:v} in \${3:iterable}}")
        )
        snippetRegistry["source.python"] = pySnippets

        // ==========================================
        // C & C++ SNIPPETS
        // ==========================================
        val cSnippets = listOf(
            Snippet("main", "main", "Main function", "int main(int argc, char *argv[]) {\n\t$0\n\treturn 0;\n}"),
            Snippet("inc", "inc", "#include <system>", "#include <\${1:stdio.h}>"),
            Snippet("include", "include", "#include <system>", "#include <\${1:stdio.h}>"),
            Snippet("inc2", "inc2", "#include \"local\"", "#include \"\${1:header.h}\""),
            Snippet("def", "def", "#define constant", "#define \${1:NAME} \${2:VALUE}"),
            Snippet("for", "for", "For loop", "for (int \${1:i} = 0; \${1:i} < \${2:n}; \${1:i}++) {\n\t$0\n}"),
            Snippet("while", "while", "While loop", "while (\${1:condition}) {\n\t$0\n}"),
            Snippet("if", "if", "If condition", "if (\${1:condition}) {\n\t$0\n}"),
            Snippet("struct", "struct", "Struct declaration", "struct \${1:Name} {\n\t$0\n};"),
            Snippet("pf", "pf", "printf", "printf(\"\${1:%s}\\n\", $0);"),
            Snippet("printf", "printf", "printf", "printf(\"\${1:%s}\\n\", $0);")
        )
        val cppSnippets = cSnippets + listOf(
            Snippet("cout", "cout", "std::cout <<", "std::cout << $0 << std::endl;"),
            Snippet("cin", "cin", "std::cin >>", "std::cin >> $0;"),
            Snippet("class", "class", "C++ Class", "class \${1:ClassName} {\npublic:\n\t\${1:ClassName}();\n\t~\${1:ClassName}();\n\nprivate:\n\t$0\n};"),
            Snippet("vec", "vec", "std::vector", "std::vector<\${1:int}> \${2:v};"),
            Snippet("str", "str", "std::string", "std::string \${1:s};"),
            Snippet("ns", "ns", "namespace", "namespace \${1:name} {\n\t$0\n}")
        )
        snippetRegistry["source.c"] = cSnippets
        snippetRegistry["source.cpp"] = cppSnippets

        // ==========================================
        // JAVA SNIPPETS
        // ==========================================
        val javaSnippets = listOf(
            Snippet("main", "main", "public static void main", "public static void main(String[] args) {\n\t$0\n}"),
            Snippet("psvm", "psvm", "public static void main", "public static void main(String[] args) {\n\t$0\n}"),
            Snippet("sout", "sout", "System.out.println", "System.out.println($0);"),
            Snippet("souf", "souf", "System.out.printf", "System.out.printf(\"\${1:%s}\\n\", $0);"),
            Snippet("class", "class", "Class declaration", "public class \${1:ClassName} {\n\tpublic \${1:ClassName}() {\n\t\t$0\n\t}\n}"),
            Snippet("iface", "iface", "Interface declaration", "public interface \${1:InterfaceName} {\n\t$0\n}"),
            Snippet("for", "for", "For index loop", "for (int \${1:i} = 0; \${1:i} < \${2:n}; \${1:i}++) {\n\t$0\n}"),
            Snippet("fore", "fore", "For-each loop", "for (\${1:Object} \${2:item} : \${3:collection}) {\n\t$0\n}"),
            Snippet("while", "while", "While loop", "while (\${1:condition}) {\n\t$0\n}"),
            Snippet("try", "try", "Try-catch block", "try {\n\t$0\n} catch (Exception e) {\n\te.printStackTrace();\n}"),
            Snippet("func", "func", "Method declaration", "public \${1:void} \${2:methodName}(\${3:params}) {\n\t$0\n}")
        )
        snippetRegistry["source.java"] = javaSnippets

        // ==========================================
        // KOTLIN SNIPPETS
        // ==========================================
        val ktSnippets = listOf(
            Snippet("main", "main", "fun main", "fun main(args: Array<String>) {\n\t$0\n}"),
            Snippet("pr", "pr", "println", "println($0)"),
            Snippet("println", "println", "println", "println($0)"),
            Snippet("fun", "fun", "Function declaration", "fun \${1:name}(\${2:params}): \${3:Unit} {\n\t$0\n}"),
            Snippet("class", "class", "Class declaration", "class \${1:ClassName}(\${2:params}) {\n\t$0\n}"),
            Snippet("data", "data", "Data class", "data class \${1:ClassName}(val \${2:param}: \${3:String})"),
            Snippet("for", "for", "For item in collection", "for (\${1:item} in \${2:collection}) {\n\t$0\n}"),
            Snippet("fori", "fori", "For range loop", "for (\${1:i} in 0 until \${2:n}) {\n\t$0\n}"),
            Snippet("if", "if", "If statement", "if (\${1:condition}) {\n\t$0\n}"),
            Snippet("when", "when", "When expression", "when (\${1:x}) {\n\t\${2:value} -> $0\n\telse -> {}\n}"),
            Snippet("comp", "comp", "Companion object", "companion object {\n\t$0\n}"),
            Snippet("lazy", "lazy", "by lazy delegate", "val \${1:name} by lazy { $0 }")
        )
        snippetRegistry["source.kotlin"] = ktSnippets

        // ==========================================
        // RUST SNIPPETS
        // ==========================================
        val rustSnippets = listOf(
            Snippet("main", "main", "fn main()", "fn main() {\n\t$0\n}"),
            Snippet("fn", "fn", "Function definition", "fn \${1:name}(\${2:args}) -> \${3:()} {\n\t$0\n}"),
            Snippet("pr", "pr", "println!", "println!(\"\${1:{}}\", $0);"),
            Snippet("println", "println", "println!", "println!(\"\${1:{}}\", $0);"),
            Snippet("struct", "struct", "Struct definition", "struct \${1:Name} {\n\t\${2:field}: \${3:Type},\n}"),
            Snippet("enum", "enum", "Enum definition", "enum \${1:Name} {\n\t$0\n}"),
            Snippet("impl", "impl", "Impl block", "impl \${1:Name} {\n\t$0\n}"),
            Snippet("for", "for", "For loop", "for \${1:item} in \${2:iterable} {\n\t$0\n}"),
            Snippet("match", "match", "Match expression", "match \${1:val} {\n\t\${2:pattern} => $0,\n}"),
            Snippet("vec", "vec", "vec! macro", "vec![$0]"),
            Snippet("test", "test", "Test function", "#[test]\nfn \${1:test_name}() {\n\t$0\n}")
        )
        snippetRegistry["source.rust"] = rustSnippets

        // ==========================================
        // GO SNIPPETS
        // ==========================================
        val goSnippets = listOf(
            Snippet("main", "main", "Package main & func main", "package main\n\nfunc main() {\n\t$0\n}"),
            Snippet("fn", "fn", "Function", "func \${1:name}(\${2:params}) \${3:error} {\n\t$0\n}"),
            Snippet("func", "func", "Function", "func \${1:name}(\${2:params}) \${3:error} {\n\t$0\n}"),
            Snippet("meth", "meth", "Method receiver", "func (\${1:r} *\${2:Type}) \${3:name}(\${4:params}) \${5:error} {\n\t$0\n}"),
            Snippet("pl", "pl", "fmt.Println", "fmt.Println($0)"),
            Snippet("pf", "pf", "fmt.Printf", "fmt.Printf(\"\${1:%s}\\n\", $0)"),
            Snippet("for", "for", "For loop", "for \${1:i} := 0; \${1:i} < \${2:n}; \${1:i}++ {\n\t$0\n}"),
            Snippet("forr", "forr", "For range loop", "for \${1:k}, \${2:v} := range \${3:collection} {\n\t$0\n}"),
            Snippet("if", "if", "If statement", "if \${1:condition} {\n\t$0\n}"),
            Snippet("ife", "ife", "If err != nil", "if err != nil {\n\treturn \${1:err}\n}"),
            Snippet("struct", "struct", "Type struct", "type \${1:Name} struct {\n\t$0\n}"),
            Snippet("iface", "iface", "Type interface", "type \${1:Name} interface {\n\t$0\n}"),
            Snippet("go", "go", "Goroutine", "go func() {\n\t$0\n}()")
        )
        snippetRegistry["source.go"] = goSnippets

        // ==========================================
        // CSS / SCSS SNIPPETS
        // ==========================================
        val cssSnippets = listOf(
            Snippet("flex", "flex", "Display flex center", "display: flex;\njustify-content: \${1:center};\nalign-items: \${2:center};"),
            Snippet("grid", "grid", "Display grid auto-fit", "display: grid;\ngrid-template-columns: \${1:repeat(auto-fit, minmax(200px, 1fr))};"),
            Snippet("bg", "bg", "Background", "background: \${1:#ffffff};"),
            Snippet("bgc", "bgc", "Background color", "background-color: \${1:#ffffff};"),
            Snippet("color", "color", "Text color", "color: \${1:#333333};"),
            Snippet("m", "m", "Margin", "margin: \${1:0};"),
            Snippet("p", "p", "Padding", "padding: \${1:0};"),
            Snippet("w", "w", "Width", "width: \${1:100%};"),
            Snippet("h", "h", "Height", "height: \${1:100%};"),
            Snippet("b", "b", "Border", "border: \${1:1px solid #cccccc};"),
            Snippet("br", "br", "Border radius", "border-radius: \${1:8px};"),
            Snippet("fs", "fs", "Font size", "font-size: \${1:16px};"),
            Snippet("fw", "fw", "Font weight", "font-weight: \${1:bold};"),
            Snippet("ta", "ta", "Text align", "text-align: \${1:center};"),
            Snippet("pos", "pos", "Position", "position: \${1:relative};"),
            Snippet("box", "box", "Box sizing border-box", "box-sizing: border-box;"),
            Snippet("media", "media", "@media screen query", "@media (max-width: \${1:768px}) {\n\t$0\n}")
        )
        snippetRegistry["source.css"] = cssSnippets
        snippetRegistry["source.scss"] = cssSnippets
        snippetRegistry["source.less"] = cssSnippets

        // ==========================================
        // MARKDOWN SNIPPETS
        // ==========================================
        val mdSnippets = listOf(
            Snippet("link", "link", "Markdown link", "[\${1:text}](\${2:url})"),
            Snippet("img", "img", "Markdown image", "![\${1:alt}](\${2:url})"),
            Snippet("code", "code", "Fenced code block", "```\${1:lang}\n$0\n```"),
            Snippet("table", "table", "Markdown table", "| \${1:Header 1} | \${2:Header 2} |\n| --- | --- |\n| \${3:Row 1} | \${4:Row 2} |"),
            Snippet("details", "details", "Collapsible details", "<details>\n<summary>\${1:Summary}</summary>\n$0\n</details>"),
            Snippet("h1", "h1", "Heading 1", "# $0"),
            Snippet("h2", "h2", "Heading 2", "## $0"),
            Snippet("h3", "h3", "Heading 3", "### $0"),
            Snippet("todo", "todo", "Task item", "- [ ] $0")
        )
        snippetRegistry["text.html.markdown"] = mdSnippets
        snippetRegistry["source.gfm"] = mdSnippets

        // ==========================================
        // SHELL / BASH SNIPPETS
        // ==========================================
        val shSnippets = listOf(
            Snippet("sh", "sh", "Bash shebang", "#!/bin/bash\n\n$0"),
            Snippet("if", "if", "If condition block", "if [[ \${1:condition} ]]; then\n\t$0\nfi"),
            Snippet("for", "for", "For in loop", "for \${1:i} in \${2:list}; do\n\t$0\ndone"),
            Snippet("while", "while", "While loop", "while \${1:condition}; do\n\t$0\ndone"),
            Snippet("fn", "fn", "Function definition", "\${1:func_name}() {\n\t$0\n}"),
            Snippet("echo", "echo", "Echo statement", "echo \"$0\"")
        )
        snippetRegistry["source.shell"] = shSnippets
        snippetRegistry["source.bash"] = shSnippets

        // ==========================================
        // JAVA SNIPPETS
        // ==========================================
        val javaSnippets = listOf(
            Snippet("main", "main", "main method", "public static void main(String[] args) {\n\t$0\n}"),
            Snippet("sout", "sout", "System.out.println", "System.out.println($0);"),
            Snippet("serr", "serr", "System.err.println", "System.err.println($0);"),
            Snippet("for", "for", "For loop", "for (int \${1:i} = 0; \${1:i} < \${2:n}; \${1:i}++) {\n\t$0\n}"),
            Snippet("fore", "fore", "For-each loop", "for (\${1:Object} \${2:item} : \${3:collection}) {\n\t$0\n}"),
            Snippet("if", "if", "If statement", "if (\${1:condition}) {\n\t$0\n}"),
            Snippet("ife", "ife", "If-Else statement", "if (\${1:condition}) {\n\t\${2}\n} else {\n\t$0\n}"),
            Snippet("try", "try", "Try-Catch block", "try {\n\t$0\n} catch (\${1:Exception} \${2:e}) {\n\t\${2:e}.printStackTrace();\n}"),
            Snippet("class", "class", "Class declaration", "public class \${1:ClassName} {\n\t$0\n}"),
            Snippet("iface", "iface", "Interface declaration", "public interface \${1:InterfaceName} {\n\t$0\n}"),
            Snippet("psf", "psf", "public static final", "public static final \${1:String} \${2:NAME} = \${3:value};"),
            Snippet("psvm", "psvm", "main method", "public static void main(String[] args) {\n\t$0\n}")
        )
        snippetRegistry["source.java"] = javaSnippets

        // ==========================================
        // C & C++ SNIPPETS
        // ==========================================
        val cSnippets = listOf(
            Snippet("main", "main", "int main", "int main(int argc, char *argv[]) {\n\t$0\n\treturn 0;\n}"),
            Snippet("inc", "inc", "#include", "#include <\${1:stdio.h}>"),
            Snippet("inci", "inci", "#include <iostream>", "#include <iostream>\n"),
            Snippet("pr", "pr", "printf", "printf(\"\${1:%s}\\n\", $0);"),
            Snippet("cout", "cout", "std::cout", "std::cout << $0 << std::endl;"),
            Snippet("cin", "cin", "std::cin", "std::cin >> $0;"),
            Snippet("for", "for", "For loop", "for (int \${1:i} = 0; \${1:i} < \${2:n}; \${1:i}++) {\n\t$0\n}"),
            Snippet("if", "if", "If statement", "if (\${1:condition}) {\n\t$0\n}"),
            Snippet("ife", "ife", "If-Else statement", "if (\${1:condition}) {\n\t\${2}\n} else {\n\t$0\n}"),
            Snippet("struct", "struct", "Struct declaration", "struct \${1:Name} {\n\t$0\n};"),
            Snippet("class", "class", "Class declaration", "class \${1:ClassName} {\npublic:\n\t\${1:ClassName}();\n\t~$0();\n};"),
            Snippet("vec", "vec", "std::vector", "std::vector<\${1:int}> \${2:v};")
        )
        snippetRegistry["source.c"] = cSnippets
        snippetRegistry["source.cpp"] = cSnippets
        snippetRegistry["source.c++"] = cSnippets
    }
}
