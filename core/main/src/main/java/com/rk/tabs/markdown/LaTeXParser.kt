package com.rk.tabs.markdown

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp

/**
 * Robust LaTeX and Math expression parser for Jetpack Compose Markdown preview.
 * Converts LaTeX math expressions into rich [AnnotatedString]s with styles,
 * Greek letters, math symbols, superscripts, subscripts, fractions, and formatting.
 */
object LaTeXParser {

    private val GREEK_LETTERS =
        mapOf(
            "alpha" to "α", "beta" to "β", "gamma" to "γ", "Gamma" to "Γ",
            "delta" to "δ", "Delta" to "Δ", "epsilon" to "ε", "varepsilon" to "ε",
            "zeta" to "ζ", "eta" to "η", "theta" to "θ", "Theta" to "Θ", "vartheta" to "ϑ",
            "iota" to "ι", "kappa" to "κ", "varkappa" to "ϰ", "lambda" to "λ", "Lambda" to "Λ",
            "mu" to "μ", "nu" to "ν", "xi" to "ξ", "Xi" to "Ξ",
            "pi" to "π", "Pi" to "Π", "varpi" to "ϖ", "rho" to "ρ", "varrho" to "ϱ",
            "sigma" to "σ", "Sigma" to "Σ", "varsigma" to "ς", "tau" to "τ",
            "upsilon" to "υ", "Upsilon" to "Υ", "phi" to "φ", "varphi" to "ϕ", "Phi" to "Φ",
            "chi" to "χ", "psi" to "ψ", "Psi" to "Ψ", "omega" to "ω", "Omega" to "Ω",
        )

    private val MATH_SYMBOLS =
        mapOf(
            "times" to "×", "cdot" to "·", "div" to "÷", "pm" to "±", "mp" to "∓",
            "circ" to "∘", "bullet" to "•", "ast" to "*", "star" to "⋆",
            "leq" to "≤", "le" to "≤", "geq" to "≥", "ge" to "≥", "neq" to "≠", "ne" to "≠",
            "approx" to "≈", "equiv" to "≡", "sim" to "~", "simeq" to "≃", "cong" to "≅", "propto" to "∝",
            "in" to "∈", "notin" to "∉", "subset" to "⊂", "subseteq" to "⊆", "supset" to "⊃", "supseteq" to "⊇",
            "cup" to "∪", "cap" to "∩", "setminus" to "\\", "emptyset" to "∅", "varnothing" to "∅",
            "forall" to "∀", "exists" to "∃", "nexists" to "∄", "partial" to "∂", "nabla" to "∇", "infty" to "∞",
            "leftarrow" to "←", "gets" to "←", "rightarrow" to "→", "to" to "→", "leftrightarrow" to "↔",
            "Leftarrow" to "⇐", "Rightarrow" to "⇒", "implies" to "⇒", "Leftrightarrow" to "⇔", "iff" to "⇔", "mapsto" to "↦",
            "uparrow" to "↑", "downarrow" to "↓", "Uparrow" to "⇑", "Downarrow" to "⇓",
            "sum" to "∑", "prod" to "∏", "coprod" to "∐", "int" to "∫", "iint" to "∬", "iiint" to "∭", "oint" to "∮",
            "dots" to "…", "ldots" to "…", "cdots" to "…", "vdots" to "⋮", "ddots" to "⋱",
            "angle" to "∠", "triangle" to "△", "perp" to "⊥", "parallel" to "∥",
            "oplus" to "⊕", "ominus" to "⊖", "otimes" to "⊗", "oslash" to "⊘", "odot" to "⊙",
            "land" to "∧", "wedge" to "∧", "lor" to "∨", "vee" to "∨", "neg" to "¬", "lnot" to "¬",
            "hbar" to "ℏ", "ell" to "ℓ", "Re" to "ℜ", "Im" to "ℑ", "aleph" to "ℵ",
            "sin" to "sin", "cos" to "cos", "tan" to "tan", "sec" to "sec", "csc" to "csc", "cot" to "cot",
            "arcsin" to "arcsin", "arccos" to "arccos", "arctan" to "arctan",
            "sinh" to "sinh", "cosh" to "cosh", "tanh" to "tanh",
            "ln" to "ln", "log" to "log", "exp" to "exp", "det" to "det",
            "gcd" to "gcd", "deg" to "deg", "dim" to "dim", "ker" to "ker",
            "min" to "min", "max" to "max", "sup" to "sup", "inf" to "inf", "lim" to "lim",
            "quad" to "  ", "qquad" to "    ", "," to " ", ";" to " ", ":" to " ", "!" to "", " " to " ",
        )

    private val COLOR_MAP =
        mapOf(
            "red" to Color(0xFFE53935),
            "blue" to Color(0xFF1E88E5),
            "green" to Color(0xFF43A047),
            "orange" to Color(0xFFFB8C00),
            "purple" to Color(0xFF8E24AA),
            "yellow" to Color(0xFFFDD835),
            "cyan" to Color(0xFF00ACC1),
            "magenta" to Color(0xFFD81B60),
            "gray" to Color(0xFF757575),
            "grey" to Color(0xFF757575),
        )

    private data class MathCacheKey(val expr: String, val color: ULong?, val isBlock: Boolean)

    private val MATH_CACHE =
        object : java.util.LinkedHashMap<MathCacheKey, AnnotatedString>(128, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<MathCacheKey, AnnotatedString>?): Boolean {
                return size > 400
            }
        }

    fun parse(
        expression: String,
        primaryColor: Color? = null,
        isBlock: Boolean = false,
    ): AnnotatedString {
        val trimmed = expression.trim()
        val key = MathCacheKey(trimmed, primaryColor?.value, isBlock)
        synchronized(MATH_CACHE) {
            val cached = MATH_CACHE[key]
            if (cached != null) return cached
        }

        val result = buildAnnotatedString {
            renderTo(this, trimmed, primaryColor)
        }

        synchronized(MATH_CACHE) {
            MATH_CACHE[key] = result
        }
        return result
    }

    fun renderTo(
        builder: AnnotatedString.Builder,
        latex: String,
        primaryColor: Color? = null,
    ) {
        val trimmed = latex.trim()
        if (trimmed.isEmpty()) return
        val parser = StringParser(trimmed, builder, primaryColor)
        parser.parseAll()
    }

    private class StringParser(
        private val input: String,
        private val builder: AnnotatedString.Builder,
        private val primaryColor: Color?,
    ) {
        private var pos = 0

        fun parseAll() {
            while (pos < input.length) {
                parseNext()
            }
        }

        private fun parseNext() {
            if (pos >= input.length) return
            val ch = input[pos]

            when {
                ch == '\\' -> {
                    pos++
                    parseCommandOrEscape()
                }
                ch == '_' -> {
                    pos++
                    val subContent = readArgOrSingleChar()
                    builder.pushStyle(SpanStyle(baselineShift = BaselineShift.Subscript, fontSize = 10.sp))
                    val subParser = StringParser(subContent, builder, primaryColor)
                    subParser.parseAll()
                    builder.pop()
                }
                ch == '^' -> {
                    pos++
                    val supContent = readArgOrSingleChar()
                    builder.pushStyle(SpanStyle(baselineShift = BaselineShift.Superscript, fontSize = 10.sp))
                    val supParser = StringParser(supContent, builder, primaryColor)
                    supParser.parseAll()
                    builder.pop()
                }
                ch == '{' -> {
                    val groupContent = readBracedContent()
                    val groupParser = StringParser(groupContent, builder, primaryColor)
                    groupParser.parseAll()
                }
                ch == '}' -> {
                    pos++ // Skip unmatched closing brace
                }
                else -> {
                    builder.append(ch)
                    pos++
                }
            }
        }

        private fun parseCommandOrEscape() {
            if (pos >= input.length) return
            val firstChar = input[pos]

            // Single special character escapes: \\, \{, \}, \_, \%, \$, \&, \#, \ , \;, \,, \:, \!
            if (firstChar == '\\') {
                builder.append("\n")
                pos++
                return
            }
            if (firstChar in listOf('{', '}', '_', '%', '$', '&', '#')) {
                builder.append(firstChar)
                pos++
                return
            }
            if (firstChar in listOf(' ', ';', ',', ':', '!')) {
                builder.append(if (firstChar == '!') "" else " ")
                pos++
                return
            }

            // Command name: [a-zA-Z]+ and optional *
            val startPos = pos
            while (pos < input.length && input[pos].isLetter()) {
                pos++
            }
            if (pos < input.length && input[pos] == '*') {
                pos++
            }

            val cmd = input.substring(startPos, pos)
            skipWhitespace()

            when (cmd) {
                // Bold styles: \mathbf, \textbf, \boldsymbol, \bm
                "mathbf", "textbf", "boldsymbol", "bm" -> {
                    val content = readBracedContent()
                    builder.pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    val p = StringParser(content, builder, primaryColor)
                    p.parseAll()
                    builder.pop()
                }

                // Italic styles: \mathit, \textit, \emph
                "mathit", "textit", "emph" -> {
                    val content = readBracedContent()
                    builder.pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    val p = StringParser(content, builder, primaryColor)
                    p.parseAll()
                    builder.pop()
                }

                // Normal upright font: \mathrm, \text, \textnormal, \operatorname
                "mathrm", "text", "textnormal", "operatorname", "operatorname*" -> {
                    val content = readBracedContent()
                    builder.pushStyle(SpanStyle(fontStyle = FontStyle.Normal, fontWeight = FontWeight.Normal))
                    val p = StringParser(content, builder, primaryColor)
                    p.parseAll()
                    builder.pop()
                }

                // Monospace: \mathtt, \texttt
                "mathtt", "texttt" -> {
                    val content = readBracedContent()
                    builder.pushStyle(SpanStyle(fontFamily = FontFamily.Monospace))
                    val p = StringParser(content, builder, primaryColor)
                    p.parseAll()
                    builder.pop()
                }

                // Sans-serif: \mathsf, \textsf
                "mathsf", "textsf" -> {
                    val content = readBracedContent()
                    builder.pushStyle(SpanStyle(fontFamily = FontFamily.SansSerif))
                    val p = StringParser(content, builder, primaryColor)
                    p.parseAll()
                    builder.pop()
                }

                // Underline
                "underline" -> {
                    val content = readBracedContent()
                    builder.pushStyle(SpanStyle(textDecoration = TextDecoration.Underline))
                    val p = StringParser(content, builder, primaryColor)
                    p.parseAll()
                    builder.pop()
                }

                // Boxed: \boxed{x} -> [ x ]
                "boxed" -> {
                    val content = readBracedContent()
                    builder.append("[ ")
                    val p = StringParser(content, builder, primaryColor)
                    p.parseAll()
                    builder.append(" ]")
                }

                // Vectors / Accents: \vec{v}, \hat{x}, \bar{x}, \dot{x}, \ddot{x}, \tilde{x}, \overline{x}
                "vec" -> {
                    val content = readBracedContent()
                    val p = StringParser(content, builder, primaryColor)
                    p.parseAll()
                    builder.append("⃗")
                }
                "hat" -> {
                    val content = readBracedContent()
                    val p = StringParser(content, builder, primaryColor)
                    p.parseAll()
                    builder.append("̂")
                }
                "bar", "overline" -> {
                    val content = readBracedContent()
                    val p = StringParser(content, builder, primaryColor)
                    p.parseAll()
                    builder.append("̅")
                }
                "dot" -> {
                    val content = readBracedContent()
                    val p = StringParser(content, builder, primaryColor)
                    p.parseAll()
                    builder.append("̇")
                }
                "ddot" -> {
                    val content = readBracedContent()
                    val p = StringParser(content, builder, primaryColor)
                    p.parseAll()
                    builder.append("̈")
                }
                "tilde" -> {
                    val content = readBracedContent()
                    val p = StringParser(content, builder, primaryColor)
                    p.parseAll()
                    builder.append("̃")
                }

                // Colors: \color{red}{...}, \textcolor{blue}{...}
                "color", "textcolor" -> {
                    val colorName = readBracedContent().lowercase()
                    skipWhitespace()
                    val content = readBracedContent()
                    val resolvedColor = COLOR_MAP[colorName] ?: primaryColor ?: Color.Unspecified
                    builder.pushStyle(SpanStyle(color = resolvedColor))
                    val p = StringParser(content, builder, primaryColor)
                    p.parseAll()
                    builder.pop()
                }

                // Binomial: \binom{n}{k} -> C(n, k)
                "binom" -> {
                    val n = readBracedContent()
                    skipWhitespace()
                    val k = readBracedContent()
                    builder.append("C(")
                    val np = StringParser(n, builder, primaryColor)
                    np.parseAll()
                    builder.append(", ")
                    val kp = StringParser(k, builder, primaryColor)
                    kp.parseAll()
                    builder.append(")")
                }

                // Blackboard bold: \mathbb{R} -> ℝ
                "mathbb" -> {
                    val content = readBracedContent()
                    builder.append(toBlackboardBold(content))
                }

                // Calligraphic / Script: \mathcal{L} -> ℒ
                "mathcal", "mathscr" -> {
                    val content = readBracedContent()
                    builder.append(toCalligraphic(content))
                }

                // Fraktur: \mathfrak{g} -> 𝔤
                "mathfrak" -> {
                    val content = readBracedContent()
                    builder.append(toFraktur(content))
                }

                // Fractions: \frac{a}{b} -> (a) / (b)
                "frac", "dfrac", "tfrac" -> {
                    val num = readBracedContent()
                    skipWhitespace()
                    val den = readBracedContent()

                    val isSimpleNum = num.all { it.isLetterOrDigit() }
                    val isSimpleDen = den.all { it.isLetterOrDigit() }

                    if (!isSimpleNum) builder.append("(")
                    val numParser = StringParser(num, builder, primaryColor)
                    numParser.parseAll()
                    if (!isSimpleNum) builder.append(")")

                    builder.append(" / ")

                    if (!isSimpleDen) builder.append("(")
                    val denParser = StringParser(den, builder, primaryColor)
                    denParser.parseAll()
                    if (!isSimpleDen) builder.append(")")
                }

                // Square roots: \sqrt[n]{x} -> ⁿ√(x) or √(x)
                "sqrt" -> {
                    val rootDegree =
                        if (pos < input.length && input[pos] == '[') {
                            readBracketedContent()
                        } else {
                            null
                        }
                    skipWhitespace()
                    val content = readBracedContent()

                    if (rootDegree != null) {
                        builder.append(toSuperscriptString(rootDegree))
                    }
                    builder.append("√(")
                    val p = StringParser(content, builder, primaryColor)
                    p.parseAll()
                    builder.append(")")
                }

                // Left/Right delimiters: \left( ... \right)
                "left", "right" -> {
                    if (pos < input.length) {
                        val delim = input[pos]
                        pos++
                        if (delim != '.') {
                            if (delim == '\\') {
                                if (pos < input.length && (input[pos] == '{' || input[pos] == '}')) {
                                    builder.append(input[pos])
                                    pos++
                                }
                            } else {
                                builder.append(delim)
                            }
                        }
                    }
                }

                // Environments: \begin{matrix} ... \end{matrix}
                "begin" -> {
                    val env = readBracedContent()
                    val endTag = "\\end{$env}"
                    val endIdx = input.indexOf(endTag, pos)
                    val envBody =
                        if (endIdx != -1) {
                            val body = input.substring(pos, endIdx)
                            pos = endIdx + endTag.length
                            body
                        } else {
                            val body = input.substring(pos)
                            pos = input.length
                            body
                        }

                    renderEnvironment(env, envBody)
                }

                "end" -> {
                    readBracedContent() // Consume end tag if reached directly
                }

                else -> {
                    // Check Greek letters
                    if (GREEK_LETTERS.containsKey(cmd)) {
                        builder.append(GREEK_LETTERS[cmd])
                    } else if (MATH_SYMBOLS.containsKey(cmd)) {
                        builder.append(MATH_SYMBOLS[cmd])
                    } else {
                        // Check if followed by braced argument: \cmd{arg}
                        if (pos < input.length && input[pos] == '{') {
                            val content = readBracedContent()
                            val p = StringParser(content, builder, primaryColor)
                            p.parseAll()
                        } else {
                            // Plain command name
                            builder.append(cmd)
                        }
                    }
                }
            }
        }

        private fun renderEnvironment(env: String, body: String) {
            val openParen =
                when (env) {
                    "pmatrix" -> "("
                    "bmatrix" -> "["
                    "Bmatrix" -> "{"
                    "vmatrix" -> "|"
                    "Vmatrix" -> "‖"
                    "cases" -> "{\n"
                    else -> ""
                }
            val closeParen =
                when (env) {
                    "pmatrix" -> ")"
                    "bmatrix" -> "]"
                    "Bmatrix" -> "}"
                    "vmatrix" -> "|"
                    "Vmatrix" -> "‖"
                    "cases" -> ""
                    else -> ""
                }

            if (openParen.isNotEmpty()) builder.append(openParen)

            val rows = body.split("\\\\")
            rows.forEachIndexed { rowIdx, row ->
                val cols = row.split("&")
                cols.forEachIndexed { colIdx, col ->
                    val p = StringParser(col.trim(), builder, primaryColor)
                    p.parseAll()
                    if (colIdx < cols.lastIndex) {
                        builder.append("   ")
                    }
                }
                if (rowIdx < rows.lastIndex) {
                    builder.append("\n")
                }
            }

            if (closeParen.isNotEmpty()) builder.append(closeParen)
        }

        private fun readBracedContent(): String {
            skipWhitespace()
            if (pos >= input.length) return ""
            if (input[pos] != '{') {
                return readSingleToken()
            }
            pos++ // skip '{'
            val start = pos
            var depth = 1
            while (pos < input.length && depth > 0) {
                val c = input[pos]
                if (c == '\\') {
                    pos += 2 // skip escaped char
                    continue
                }
                if (c == '{') depth++
                else if (c == '}') depth--
                if (depth > 0) pos++
            }
            val content = input.substring(start, pos)
            if (pos < input.length && input[pos] == '}') {
                pos++ // skip closing '}'
            }
            return content
        }

        private fun readBracketedContent(): String {
            skipWhitespace()
            if (pos >= input.length || input[pos] != '[') return ""
            pos++ // skip '['
            val start = pos
            var depth = 1
            while (pos < input.length && depth > 0) {
                val c = input[pos]
                if (c == '\\') {
                    pos += 2
                    continue
                }
                if (c == '[') depth++
                else if (c == ']') depth--
                if (depth > 0) pos++
            }
            val content = input.substring(start, pos)
            if (pos < input.length && input[pos] == ']') {
                pos++
            }
            return content
        }

        private fun readArgOrSingleChar(): String {
            skipWhitespace()
            if (pos >= input.length) return ""
            if (input[pos] == '{') {
                return readBracedContent()
            }
            if (input[pos] == '\\') {
                pos++
                val start = pos
                while (pos < input.length && input[pos].isLetter()) {
                    pos++
                }
                val cmd = input.substring(start, pos)
                return "\\" + cmd
            }
            val ch = input[pos].toString()
            pos++
            return ch
        }

        private fun readSingleToken(): String {
            if (pos >= input.length) return ""
            val ch = input[pos].toString()
            pos++
            return ch
        }

        private fun skipWhitespace() {
            while (pos < input.length && input[pos].isWhitespace()) {
                pos++
            }
        }
    }

    private val BLACKBOARD_BOLD_MAP =
        mapOf(
            'A' to "𝔸", 'B' to "𝔹", 'C' to "ℂ", 'D' to "𝔻", 'E' to "𝔼", 'F' to "𝔽",
            'G' to "𝔾", 'H' to "ℍ", 'I' to "𝕀", 'J' to "𝕁", 'K' to "𝕂", 'L' to "𝕃",
            'M' to "𝕄", 'N' to "ℕ", 'O' to "𝕆", 'P' to "ℙ", 'Q' to "ℚ", 'R' to "ℝ",
            'S' to "𝕊", 'T' to "𝕋", 'U' to "𝕌", 'V' to "𝕍", 'W' to "𝕎", 'X' to "𝕏",
            'Y' to "𝕐", 'Z' to "ℤ",
            'a' to "𝕒", 'b' to "𝕓", 'c' to "𝕔", 'd' to "𝕕", 'e' to "𝕖", 'f' to "𝗑",
            'g' to "𝕘", 'h' to "𝕙", 'i' to "𝕚", 'j' to "𝕛", 'k' to "𝕜", 'l' to "𝕝",
            'm' to "𝕞", 'n' to "𝕟", 'o' to "𝕠", 'p' to "𝕡", 'q' to "𝕢", 'r' to "𝕣",
            's' to "𝕤", 't' to "𝕥", 'u' to "𝕦", 'v' to "𝕧", 'w' to "𝕨", 'x' to "𝕩",
            'y' to "𝪰", 'z' to "𝕫",
            '0' to "𝟘", '1' to "𝟙", '2' to "𝟚", '3' to "𝟛", '4' to "𝟜",
            '5' to "𝟝", '6' to "𝟞", '7' to "𝟟", '8' to "𝟠", '9' to "𝟡",
        )

    private val CALLIGRAPHIC_MAP =
        mapOf(
            'A' to "𝒜", 'B' to "ℬ", 'C' to "𝒞", 'D' to "𝒟", 'E' to "ℰ", 'F' to "ℱ",
            'G' to "𝒢", 'H' to "ℋ", 'I' to "ℐ", 'J' to "𝒥", 'K' to "𝒦", 'L' to "ℒ",
            'M' to "ℳ", 'N' to "𝒩", 'O' to "𝒪", 'P' to "𝒫", 'Q' to "𝒬", 'R' to "ℛ",
            'S' to "𝒮", 'T' to "𝒯", 'U' to "𝒰", 'V' to "𝒱", 'W' to "𝒲", 'X' to "𝒳",
            'Y' to "𝒴", 'Z' to "𝒵",
        )

    private val FRAKTUR_MAP =
        mapOf(
            'A' to "𝔄", 'B' to "𝔅", 'C' to "ℭ", 'D' to "𝔇", 'E' to "𝔈", 'F' to "𝔉",
            'G' to "𝔊", 'H' to "ℌ", 'I' to "ℑ", 'J' to "𝔍", 'K' to "𝔎", 'L' to "𝔏",
            'M' to "𝔐", 'N' to "𝔑", 'O' to "𝔒", 'P' to "𝔓", 'Q' to "𝔔", 'R' to "ℜ",
            'S' to "𝔖", 'T' to "𝔗", 'U' to "𝔘", 'V' to "𝔙", 'W' to "𝔚", 'X' to "𝔛",
            'Y' to "𝔜", 'Z' to "ℨ",
        )

    private val SUPERSCRIPT_MAP =
        mapOf(
            '0' to "⁰", '1' to "¹", '2' to "²", '3' to "³", '4' to "⁴",
            '5' to "⁵", '6' to "⁶", '7' to "⁷", '8' to "⁸", '9' to "⁹",
            '+' to "⁺", '-' to "⁻", '=' to "⁼", '(' to "⁽", ')' to "⁾",
            'n' to "ⁿ", 'i' to "ⁱ", 'x' to "ˣ", 'y' to "ʸ", 'a' to "ᵃ", 'b' to "ᵇ",
        )

    private fun toBlackboardBold(text: String): String {
        return text.map { BLACKBOARD_BOLD_MAP[it] ?: it.toString() }.joinToString("")
    }

    private fun toCalligraphic(text: String): String {
        return text.map { CALLIGRAPHIC_MAP[it] ?: it.toString() }.joinToString("")
    }

    private fun toFraktur(text: String): String {
        return text.map { FRAKTUR_MAP[it] ?: it.toString() }.joinToString("")
    }

    private fun toSuperscriptString(text: String): String {
        return text.map { SUPERSCRIPT_MAP[it] ?: it.toString() }.joinToString("")
    }
}
