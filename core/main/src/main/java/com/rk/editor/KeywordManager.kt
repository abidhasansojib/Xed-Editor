package com.rk.editor

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap

/**
 * High-performance, low-RAM keyword and autocomplete manager for Xed-Editor.
 * Uses immutable pre-indexed keyword arrays and zero-allocation caching.
 */
object KeywordManager {
    private val keywordRegistryInitialized = CompletableDeferred<Unit>()
    private var rawKeywords: Map<String, List<String>> = emptyMap()
    private val cachedMergedKeywords = ConcurrentHashMap<String, Array<String>>()

    // Comprehensive built-in keyword dictionaries for instant, zero-delay autocomplete
    private val BUILTIN_KEYWORDS: Map<String, List<String>> = mapOf(
        "source.js" to listOf(
            "async", "await", "break", "case", "catch", "class", "const", "continue", "debugger",
            "default", "delete", "do", "else", "export", "extends", "finally", "for", "function",
            "if", "import", "in", "instanceof", "let", "new", "null", "return", "super", "switch",
            "this", "throw", "true", "false", "try", "typeof", "undefined", "var", "void", "while",
            "with", "yield", "console", "log", "document", "window", "Promise", "Array", "Object",
            "String", "Number", "Boolean", "Math", "JSON", "Map", "Set", "fetch", "setTimeout",
            "setInterval", "clearTimeout", "clearInterval", "addEventListener", "removeEventListener"
        ),
        "source.ts" to listOf(
            "async", "await", "break", "case", "catch", "class", "const", "continue", "debugger",
            "default", "delete", "do", "else", "enum", "export", "extends", "finally", "for",
            "function", "if", "implements", "import", "in", "instanceof", "interface", "let", "new",
            "null", "package", "private", "protected", "public", "readonly", "return", "static",
            "super", "switch", "this", "throw", "true", "false", "try", "type", "typeof", "undefined",
            "var", "void", "while", "with", "yield", "any", "boolean", "number", "string", "symbol",
            "unknown", "never", "keyof", "as", "is", "namespace", "declare", "module", "override",
            "abstract", "satisfies", "infer", "Record", "Partial", "Required", "Readonly", "Pick", "Omit"
        ),
        "source.python" to listOf(
            "False", "None", "True", "and", "as", "assert", "async", "await", "break", "class",
            "continue", "def", "del", "elif", "else", "except", "finally", "for", "from", "global",
            "if", "import", "in", "is", "lambda", "nonlocal", "not", "or", "pass", "raise", "return",
            "try", "while", "with", "yield", "print", "len", "range", "list", "dict", "set", "str",
            "int", "float", "bool", "type", "super", "self", "cls", "__init__", "__main__", "__name__",
            "enumerate", "zip", "map", "filter", "sorted", "reversed", "open", "isinstance", "issubclass"
        ),
        "source.c" to listOf(
            "auto", "break", "case", "char", "const", "continue", "default", "do", "double", "else",
            "enum", "extern", "float", "for", "goto", "if", "inline", "int", "long", "register",
            "restrict", "return", "short", "signed", "sizeof", "static", "struct", "switch", "typedef",
            "union", "unsigned", "void", "volatile", "while", "NULL", "true", "false", "size_t",
            "uint8_t", "uint16_t", "uint32_t", "uint64_t", "int8_t", "int16_t", "int32_t", "int64_t",
            "printf", "scanf", "malloc", "free", "memcpy", "memset", "strlen", "strcpy", "strcmp"
        ),
        "source.cpp" to listOf(
            "alignas", "alignof", "and", "and_eq", "asm", "auto", "bitand", "bitor", "bool", "break",
            "case", "catch", "char", "char8_t", "char16_t", "char32_t", "class", "compl", "concept",
            "const", "consteval", "constexpr", "constinit", "const_cast", "continue", "co_await",
            "co_return", "co_yield", "decltype", "default", "delete", "do", "double", "dynamic_cast",
            "else", "enum", "explicit", "export", "extern", "false", "float", "for", "friend", "goto",
            "if", "inline", "int", "long", "mutable", "namespace", "new", "noexcept", "not", "not_eq",
            "nullptr", "operator", "or", "or_eq", "private", "protected", "public", "reflexpr",
            "register", "reinterpret_cast", "requires", "return", "short", "signed", "sizeof", "static",
            "static_assert", "static_cast", "struct", "switch", "template", "this", "thread_local",
            "throw", "true", "try", "typedef", "typeid", "typename", "union", "unsigned", "using",
            "virtual", "void", "volatile", "wchar_t", "while", "xor", "xor_eq", "std", "cout", "cin",
            "endl", "vector", "string", "map", "unordered_map", "set", "unique_ptr", "shared_ptr", "make_unique", "make_shared"
        ),
        "source.rust" to listOf(
            "as", "async", "await", "break", "const", "continue", "crate", "dyn", "else", "enum",
            "extern", "false", "fn", "for", "if", "impl", "in", "let", "loop", "match", "mod",
            "move", "mut", "pub", "ref", "return", "self", "Self", "static", "struct", "super",
            "trait", "true", "type", "unsafe", "use", "where", "while", "i8", "i16", "i32", "i64",
            "i128", "isize", "u8", "u16", "u32", "u64", "u128", "usize", "f32", "f64", "bool",
            "char", "str", "String", "Vec", "Option", "Some", "None", "Result", "Ok", "Err",
            "println!", "format!", "panic!", "vec!", "Box", "Rc", "Arc", "Mutex", "Cell", "RefCell"
        ),
        "source.go" to listOf(
            "break", "default", "func", "interface", "select", "case", "defer", "go", "map",
            "struct", "chan", "else", "goto", "package", "switch", "const", "fallthrough", "if",
            "range", "type", "continue", "for", "import", "return", "var", "append", "cap", "close",
            "complex", "copy", "delete", "imag", "len", "make", "new", "panic", "print", "println",
            "real", "recover", "bool", "byte", "complex64", "complex128", "error", "float32",
            "float64", "int", "int8", "int16", "int32", "int64", "rune", "string", "uint", "uint8",
            "uint16", "uint32", "uint64", "uintptr", "true", "false", "iota", "nil", "context", "Context"
        ),
        "source.java" to listOf(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
            "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
            "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
            "interface", "long", "native", "new", "package", "private", "protected", "public",
            "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
            "throw", "throws", "transient", "try", "void", "volatile", "while", "true", "false",
            "null", "String", "System", "out", "println", "Override", "StringBuilder", "List", "Map", "Set"
        ),
        "source.kotlin" to listOf(
            "as", "as?", "break", "class", "continue", "do", "else", "false", "for", "fun", "if",
            "in", "!in", "interface", "is", "!is", "null", "object", "package", "return", "super",
            "this", "throw", "true", "try", "typealias", "typeof", "val", "var", "when", "while",
            "by", "catch", "constructor", "delegate", "dynamic", "field", "file", "finally", "get",
            "import", "init", "param", "property", "receiver", "set", "setparam", "where", "actual",
            "abstract", "annotation", "companion", "const", "crossinline", "data", "enum", "expect",
            "external", "final", "infix", "inline", "inner", "internal", "lateinit", "noinline",
            "open", "operator", "out", "override", "private", "protected", "public", "reified",
            "sealed", "suspend", "tailrec", "vararg", "Int", "String", "Boolean", "Double", "Float",
            "Long", "List", "Map", "Set", "println", "let", "run", "also", "apply", "takeIf"
        ),
        "text.html.basic" to listOf(
            "html", "head", "body", "title", "meta", "link", "style", "script", "div", "span",
            "p", "a", "img", "button", "input", "form", "label", "textarea", "select", "option",
            "ul", "ol", "li", "table", "tr", "th", "td", "thead", "tbody", "tfoot", "h1", "h2",
            "h3", "h4", "h5", "h6", "header", "footer", "nav", "section", "article", "aside",
            "main", "figure", "figcaption", "video", "audio", "source", "canvas", "svg", "path",
            "iframe", "hr", "br", "class", "id", "href", "src", "type", "name", "value", "placeholder"
        ),
        "source.css" to listOf(
            "margin", "margin-top", "margin-bottom", "margin-left", "margin-right",
            "padding", "padding-top", "padding-bottom", "padding-left", "padding-right",
            "width", "height", "min-width", "min-height", "max-width", "max-height",
            "display", "flex", "grid", "block", "inline-block", "none", "flex-direction",
            "justify-content", "align-items", "position", "relative", "absolute", "fixed", "sticky",
            "top", "bottom", "left", "right", "z-index", "background", "background-color",
            "color", "font-family", "font-size", "font-weight", "text-align", "text-decoration",
            "line-height", "border", "border-radius", "box-shadow", "overflow", "cursor", "opacity",
            "transition", "transform", "animation", "important", "!important", "gap", "box-sizing"
        ),
        "source.sql" to listOf(
            "SELECT", "FROM", "WHERE", "INSERT", "INTO", "VALUES", "UPDATE", "SET", "DELETE",
            "CREATE", "TABLE", "DATABASE", "ALTER", "DROP", "INDEX", "VIEW", "JOIN", "INNER",
            "LEFT", "RIGHT", "FULL", "OUTER", "ON", "GROUP", "BY", "ORDER", "ASC", "DESC",
            "HAVING", "LIMIT", "OFFSET", "DISTINCT", "AS", "AND", "OR", "NOT", "IN", "BETWEEN",
            "LIKE", "IS", "NULL", "PRIMARY", "KEY", "FOREIGN", "REFERENCES", "UNIQUE", "CHECK",
            "DEFAULT", "COUNT", "SUM", "AVG", "MIN", "MAX", "UNION", "ALL", "EXISTS", "CASE",
            "WHEN", "THEN", "ELSE", "END"
        ),
        "source.shell" to listOf(
            "if", "then", "else", "elif", "fi", "case", "esac", "for", "while", "until", "do",
            "done", "in", "function", "select", "time", "export", "local", "readonly", "return",
            "exit", "echo", "printf", "cd", "pwd", "mkdir", "rm", "cp", "mv", "touch", "cat",
            "grep", "sed", "awk", "find", "chmod", "chown", "curl", "wget", "tar", "gzip", "source"
        ),
        "source.json" to listOf("true", "false", "null"),
        "source.yaml" to listOf("true", "false", "null", "yes", "no", "on", "off"),
        "source.toml" to listOf("true", "false")
    )

    private val SCOPE_FALLBACKS: Map<String, String> = mapOf(
        "source.tsx" to "source.ts",
        "source.js.jsx" to "source.js",
        "text.html.htmx" to "text.html.basic",
        "text.html.php" to "text.html.basic",
        "source.css.scss" to "source.css",
        "source.css.less" to "source.css",
        "source.shell.bash" to "source.shell",
        "source.shell.zsh" to "source.shell",
    )

    suspend fun initKeywordRegistry(context: Context) {
        if (keywordRegistryInitialized.isCompleted) return

        withContext(Dispatchers.IO) {
            try {
                context.assets.open(TEXTMATE_PREFIX + KEYWORDS_FILE).use { stream ->
                    val gson = Gson()
                    val typeToken = object : TypeToken<Map<String, List<String>>>() {}
                    rawKeywords = gson.fromJson(InputStreamReader(stream), typeToken) ?: emptyMap()
                }
            } catch (_: Exception) {
                rawKeywords = emptyMap()
            } finally {
                if (!keywordRegistryInitialized.isCompleted) {
                    keywordRegistryInitialized.complete(Unit)
                }
            }
        }
    }

    /**
     * Returns pre-indexed, zero-allocation cached array of keywords for the given TextMate scope.
     */
    suspend fun getKeywordsArray(textmateScope: String): Array<String>? {
        cachedMergedKeywords[textmateScope]?.let { return it }

        if (!keywordRegistryInitialized.isCompleted) {
            try {
                keywordRegistryInitialized.await()
            } catch (_: Exception) {}
        }

        // Fast-path lookup
        val set = LinkedHashSet<String>()

        // 1. Built-in keywords
        BUILTIN_KEYWORDS[textmateScope]?.let { set.addAll(it) }

        // 2. Fallback scope keywords
        val fallbackScope = SCOPE_FALLBACKS[textmateScope]
        if (fallbackScope != null) {
            BUILTIN_KEYWORDS[fallbackScope]?.let { set.addAll(it) }
            rawKeywords[fallbackScope]?.let { set.addAll(it) }
        }

        // 3. Keywords from file
        rawKeywords[textmateScope]?.let { set.addAll(it) }

        if (set.isEmpty()) {
            return null
        }

        val array = set.toTypedArray()
        cachedMergedKeywords[textmateScope] = array
        return array
    }

    suspend fun getKeywords(textmateScope: String): List<String>? {
        return getKeywordsArray(textmateScope)?.asList()
    }
}
