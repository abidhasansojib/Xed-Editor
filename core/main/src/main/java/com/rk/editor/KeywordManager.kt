package com.rk.editor

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStreamReader
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object KeywordManager {
    private val keywordRegistryInitialized = CompletableDeferred<Unit>()
    private var keywords: Map<String, List<String>> = emptyMap()

    // Built-in keyword sets for modern languages when JSON lacks them
    private val BUILTIN_KEYWORDS: Map<String, List<String>> = mapOf(
        "source.js" to listOf(
            "async", "await", "break", "case", "catch", "class", "const", "continue", "debugger",
            "default", "delete", "do", "else", "export", "extends", "finally", "for", "function",
            "if", "import", "in", "instanceof", "let", "new", "null", "return", "super", "switch",
            "this", "throw", "true", "false", "try", "typeof", "undefined", "var", "void", "while",
            "with", "yield", "console", "log", "document", "window", "Promise", "Array", "Object",
            "String", "Number", "Boolean", "Math", "JSON", "Map", "Set", "fetch", "setTimeout"
        ),
        "source.ts" to listOf(
            "async", "await", "break", "case", "catch", "class", "const", "continue", "debugger",
            "default", "delete", "do", "else", "enum", "export", "extends", "finally", "for",
            "function", "if", "implements", "import", "in", "instanceof", "interface", "let", "new",
            "null", "package", "private", "protected", "public", "readonly", "return", "static",
            "super", "switch", "this", "throw", "true", "false", "try", "type", "typeof", "undefined",
            "var", "void", "while", "with", "yield", "any", "boolean", "number", "string", "symbol",
            "unknown", "never", "keyof", "as", "is", "namespace", "declare", "module"
        ),
        "source.python" to listOf(
            "False", "None", "True", "and", "as", "assert", "async", "await", "break", "class",
            "continue", "def", "del", "elif", "else", "except", "finally", "for", "from", "global",
            "if", "import", "in", "is", "lambda", "nonlocal", "not", "or", "pass", "raise", "return",
            "try", "while", "with", "yield", "print", "len", "range", "list", "dict", "set", "str",
            "int", "float", "bool", "type", "super", "self", "cls", "__init__", "__main__", "__name__"
        ),
        "source.c" to listOf(
            "auto", "break", "case", "char", "const", "continue", "default", "do", "double", "else",
            "enum", "extern", "float", "for", "goto", "if", "inline", "int", "long", "register",
            "restrict", "return", "short", "signed", "sizeof", "static", "struct", "switch", "typedef",
            "union", "unsigned", "void", "volatile", "while", "NULL", "true", "false", "size_t",
            "printf", "scanf", "malloc", "free", "memcpy", "memset", "include", "define"
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
            "endl", "vector", "string", "map", "unordered_map", "set", "unique_ptr", "shared_ptr"
        ),
        "source.rust" to listOf(
            "as", "async", "await", "break", "const", "continue", "crate", "dyn", "else", "enum",
            "extern", "false", "fn", "for", "if", "impl", "in", "let", "loop", "match", "mod",
            "move", "mut", "pub", "ref", "return", "self", "Self", "static", "struct", "super",
            "trait", "true", "type", "unsafe", "use", "where", "while", "i8", "i16", "i32", "i64",
            "i128", "isize", "u8", "u16", "u32", "u64", "u128", "usize", "f32", "f64", "bool",
            "char", "str", "String", "Vec", "Option", "Some", "None", "Result", "Ok", "Err",
            "println!", "format!", "panic!", "vec!"
        ),
        "source.go" to listOf(
            "break", "default", "func", "interface", "select", "case", "defer", "go", "map",
            "struct", "chan", "else", "goto", "package", "switch", "const", "fallthrough", "if",
            "range", "type", "continue", "for", "import", "return", "var", "append", "cap", "close",
            "complex", "copy", "delete", "imag", "len", "make", "new", "panic", "print", "println",
            "real", "recover", "bool", "byte", "complex64", "complex128", "error", "float32",
            "float64", "int", "int8", "int16", "int32", "int64", "rune", "string", "uint", "uint8",
            "uint16", "uint32", "uint64", "uintptr", "true", "false", "iota", "nil"
        ),
        "source.java" to listOf(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
            "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
            "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
            "interface", "long", "native", "new", "package", "private", "protected", "public",
            "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
            "throw", "throws", "transient", "try", "void", "volatile", "while", "true", "false",
            "null", "String", "System", "out", "println", "Override"
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
            "Long", "List", "Map", "Set", "println"
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
            "transition", "transform", "animation", "important", "!important"
        ),
        "source.sql" to listOf(
            "SELECT", "FROM", "WHERE", "INSERT", "INTO", "VALUES", "UPDATE", "SET", "DELETE",
            "CREATE", "TABLE", "DATABASE", "ALTER", "DROP", "INDEX", "VIEW", "JOIN", "INNER",
            "LEFT", "RIGHT", "FULL", "OUTER", "ON", "GROUP", "BY", "ORDER", "ASC", "DESC",
            "HAVING", "LIMIT", "OFFSET", "DISTINCT", "AS", "AND", "OR", "NOT", "IN", "BETWEEN",
            "LIKE", "IS", "NULL", "PRIMARY", "KEY", "FOREIGN", "REFERENCES", "UNIQUE", "CHECK",
            "DEFAULT", "COUNT", "SUM", "AVG", "MIN", "MAX", "UNION", "ALL", "EXISTS", "CASE",
            "WHEN", "THEN", "ELSE", "END"
        )
    )

    private val SCOPE_FALLBACKS: Map<String, String> = mapOf(
        "source.tsx" to "source.ts",
        "source.js.jsx" to "source.js",
        "text.html.htmx" to "text.html.basic",
        "text.html.php" to "text.html.basic",
        "source.css.scss" to "source.css",
        "source.css.less" to "source.css",
    )

    suspend fun initKeywordRegistry(context: Context) {
        if (keywordRegistryInitialized.isCompleted) return

        withContext(Dispatchers.IO) {
            try {
                context.assets.open(TEXTMATE_PREFIX + KEYWORDS_FILE).use {
                    val gson = Gson()
                    val typeToken = object : TypeToken<Map<String, List<String>>>() {}
                    keywords = gson.fromJson(InputStreamReader(it), typeToken) ?: emptyMap()
                }
            } catch (_: Exception) {
                keywords = emptyMap()
            } finally {
                if (!keywordRegistryInitialized.isCompleted) {
                    keywordRegistryInitialized.complete(Unit)
                }
            }
        }
    }

    suspend fun getKeywords(textmateScope: String): List<String>? {
        if (!keywordRegistryInitialized.isCompleted) {
            try {
                keywordRegistryInitialized.await()
            } catch (_: Exception) {}
        }

        // 1. Exact match from keywords.json
        val fromFile = keywords[textmateScope]
        if (!fromFile.isNullOrEmpty()) {
            val builtin = BUILTIN_KEYWORDS[textmateScope] ?: emptyList()
            return if (builtin.isNotEmpty()) (fromFile + builtin).distinct() else fromFile
        }

        // 2. Direct builtin match
        val builtinDirect = BUILTIN_KEYWORDS[textmateScope]
        if (!builtinDirect.isNullOrEmpty()) return builtinDirect

        // 3. Fallback scope match (e.g. source.tsx -> source.ts)
        val fallbackScope = SCOPE_FALLBACKS[textmateScope]
        if (fallbackScope != null) {
            val fromFallback = keywords[fallbackScope] ?: BUILTIN_KEYWORDS[fallbackScope]
            if (!fromFallback.isNullOrEmpty()) return fromFallback
        }

        return null
    }
}
