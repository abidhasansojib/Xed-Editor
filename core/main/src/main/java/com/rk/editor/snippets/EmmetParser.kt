package com.rk.editor.snippets

object EmmetParser {

    private val VOID_ELEMENTS = hashSetOf(
        "area", "base", "br", "col", "embed", "hr", "img", "input",
        "link", "meta", "param", "source", "track", "wbr"
    )

    data class EmmetNode(
        var tag: String = "",
        val classes: MutableList<String> = mutableListOf(),
        var id: String? = null,
        val attributes: MutableMap<String, String> = mutableMapOf(),
        var text: String? = null,
        var count: Int = 1,
        val children: MutableList<EmmetNode> = mutableListOf(),
    )

    fun parse(abbreviation: String, scope: String): Snippet? {
        val normalizedScope = scope.lowercase()
        if (!normalizedScope.contains("html") && !normalizedScope.contains("xml") && !normalizedScope.contains("htm")) {
            return null
        }

        val trimmed = abbreviation.trim()
        if (trimmed.length < 2) return null
        if (trimmed.contains(" ") || trimmed.contains("\t") || trimmed.contains("\n")) return null

        // Check if string contains any invalid characters
        val validChars = trimmed.all { ch ->
            ch.isLetterOrDigit() || ch in "-_:#.>+*[]=\"'{}"
        }
        if (!validChars) return null

        return try {
            val root = parseExpression(trimmed) ?: return null
            var tabStopIndex = 1
            val template = renderNodeTree(root, indentLevel = 0, tabStopIndexHolder = intArrayOf(tabStopIndex))
            if (template.isBlank()) return null

            Snippet(
                trigger = trimmed,
                label = trimmed,
                description = "Emmet: $trimmed",
                template = template,
                detail = "Emmet Abbreviation",
                scope = "text.html.basic",
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseExpression(expr: String): List<EmmetNode>? {
        val nodes = mutableListOf<EmmetNode>()
        val siblingParts = splitTopLevel(expr, '+')

        for (part in siblingParts) {
            val childParts = splitTopLevel(part, '>')
            var currentParent: EmmetNode? = null
            var rootOfChain: EmmetNode? = null

            for (childPart in childParts) {
                val node = parseSingleNode(childPart) ?: return null
                if (currentParent == null) {
                    rootOfChain = node
                    currentParent = node
                } else {
                    currentParent.children.add(node)
                    currentParent = node
                }
            }
            if (rootOfChain != null) {
                nodes.add(rootOfChain)
            }
        }
        return nodes.ifEmpty { null }
    }

    private fun parseSingleNode(segment: String): EmmetNode? {
        var str = segment
        var multiplier = 1

        if (str.contains("*")) {
            val multPart = str.substringAfterLast("*")
            multiplier = multPart.toIntOrNull() ?: 1
            str = str.substringBeforeLast("*")
            if (multiplier < 1) multiplier = 1
            if (multiplier > 20) multiplier = 20 // cap for performance and usability
        }

        val node = EmmetNode(count = multiplier)

        // Parse custom text node {Text Content}
        if (str.contains("{") && str.endsWith("}")) {
            val textContent = str.substringAfter("{").substringBeforeLast("}")
            node.text = textContent
            str = str.substringBefore("{")
        }

        // Parse custom attributes [attr=val]
        if (str.contains("[") && str.contains("]")) {
            val attrContent = str.substringAfter("[").substringBefore("]")
            parseAttributes(attrContent, node.attributes)
            str = str.substringBefore("[") + str.substringAfter("]")
        }

        // Parse tag, classes, and ID
        val tokens = tokenizeTagClassesId(str)
        if (tokens.isEmpty()) return null

        for (token in tokens) {
            when {
                token.startsWith(".") -> {
                    val cls = token.substring(1)
                    if (cls.isNotEmpty()) node.classes.add(cls)
                }
                token.startsWith("#") -> {
                    node.id = token.substring(1)
                }
                else -> {
                    if (node.tag.isEmpty()) {
                        node.tag = token
                    }
                }
            }
        }

        if (node.tag.isEmpty()) {
            node.tag = "div"
        }

        return node
    }

    private fun tokenizeTagClassesId(str: String): List<String> {
        val tokens = mutableListOf<String>()
        val sb = StringBuilder()

        for (i in str.indices) {
            val c = str[i]
            if (c == '.' || c == '#') {
                if (sb.isNotEmpty()) {
                    tokens.add(sb.toString())
                    sb.clear()
                }
                sb.append(c)
            } else {
                sb.append(c)
            }
        }
        if (sb.isNotEmpty()) {
            tokens.add(sb.toString())
        }
        return tokens
    }

    private fun parseAttributes(attrString: String, outMap: MutableMap<String, String>) {
        val pairs = attrString.split(" ")
        for (pair in pairs) {
            val trimmed = pair.trim()
            if (trimmed.isEmpty()) continue
            if (trimmed.contains("=")) {
                val k = trimmed.substringBefore("=").trim()
                val v = trimmed.substringAfter("=").trim().removeSurrounding("\"").removeSurrounding("'")
                outMap[k] = v
            } else {
                outMap[trimmed] = ""
            }
        }
    }

    private fun splitTopLevel(str: String, delimiter: Char): List<String> {
        val list = mutableListOf<String>()
        val sb = StringBuilder()
        var insideBracket = false
        var insideBrace = false

        for (c in str) {
            when (c) {
                '[' -> insideBracket = true
                ']' -> insideBracket = false
                '{' -> insideBrace = true
                '}' -> insideBrace = false
            }

            if (c == delimiter && !insideBracket && !insideBrace) {
                if (sb.isNotEmpty()) {
                    list.add(sb.toString())
                    sb.clear()
                }
            } else {
                sb.append(c)
            }
        }
        if (sb.isNotEmpty()) list.add(sb.toString())
        return list
    }

    private fun renderNodeTree(
        nodes: List<EmmetNode>,
        indentLevel: Int,
        tabStopIndexHolder: IntArray
    ): String {
        val sb = StringBuilder()
        val indent = "\t".repeat(indentLevel)

        for (node in nodes) {
            for (count in 1..node.count) {
                val tag = node.tag.ifEmpty { "div" }
                val isVoid = VOID_ELEMENTS.contains(tag.lowercase())

                sb.append(indent).append("<").append(tag)

                if (node.classes.isNotEmpty()) {
                    sb.append(" class=\"").append(node.classes.joinToString(" ")).append("\"")
                }
                if (!node.id.isNullOrEmpty()) {
                    sb.append(" id=\"").append(node.id).append("\"")
                }

                // Default attributes for specific tags
                if (tag == "a" && !node.attributes.containsKey("href")) {
                    sb.append(" href=\"\${").append(tabStopIndexHolder[0]++).append(":#}\"")
                } else if (tag == "img" && !node.attributes.containsKey("src")) {
                    sb.append(" src=\"\${").append(tabStopIndexHolder[0]++).append("}\" alt=\"\${").append(tabStopIndexHolder[0]++).append("}\"")
                } else if (tag == "button" && !node.attributes.containsKey("type")) {
                    sb.append(" type=\"\${").append(tabStopIndexHolder[0]++).append(":button}\"")
                }

                for ((k, v) in node.attributes) {
                    if (v.isEmpty()) {
                        sb.append(" ").append(k)
                    } else {
                        sb.append(" ").append(k).append("=\"").append(v).append("\"")
                    }
                }

                if (isVoid) {
                    sb.append(">\n")
                } else {
                    sb.append(">")
                    if (node.children.isNotEmpty()) {
                        sb.append("\n")
                        sb.append(renderNodeTree(node.children, indentLevel + 1, tabStopIndexHolder))
                        sb.append(indent)
                    } else if (!node.text.isNullOrEmpty()) {
                        sb.append(node.text)
                    } else {
                        sb.append("$0")
                    }
                    sb.append("</").append(tag).append(">\n")
                }
            }
        }
        return sb.toString().trimEnd('\n')
    }
}
