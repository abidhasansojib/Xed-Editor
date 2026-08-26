package com.rk.editor.snippets

import org.json.JSONObject
import java.io.File

object VsCodeSnippetLoader {

    /**
     * Parses standard VS Code snippet JSON string into a list of [Snippet]s.
     */
    fun parseJsonSnippets(jsonString: String, defaultScope: String? = null): List<Snippet> {
        val snippets = ArrayList<Snippet>()
        if (jsonString.isBlank()) return snippets

        try {
            val jsonObject = JSONObject(jsonString)
            val keys = jsonObject.keys()

            while (keys.hasNext()) {
                val name = keys.next()
                val entry = jsonObject.optJSONObject(name) ?: continue

                // 1. Prefix can be a string or JSON array of strings
                val prefixes = ArrayList<String>()
                val prefixOpt = entry.opt("prefix")
                when (prefixOpt) {
                    is String -> prefixes.add(prefixOpt)
                    is org.json.JSONArray -> {
                        for (i in 0 until prefixOpt.length()) {
                            val p = prefixOpt.optString(i)
                            if (p.isNotBlank()) prefixes.add(p)
                        }
                    }
                }

                if (prefixes.isEmpty()) continue

                // 2. Body can be a string or JSON array of strings (lines)
                val bodyStr: String = when (val bodyOpt = entry.opt("body")) {
                    is String -> bodyOpt
                    is org.json.JSONArray -> {
                        val sb = StringBuilder()
                        for (i in 0 until bodyOpt.length()) {
                            if (i > 0) sb.append("\n")
                            sb.append(bodyOpt.optString(i))
                        }
                        sb.toString()
                    }
                    else -> continue
                }

                val description = entry.optString("description", name)
                val scope = entry.optString("scope", defaultScope ?: "").ifBlank { defaultScope }

                for (prefix in prefixes) {
                    snippets.add(
                        Snippet(
                            trigger = prefix,
                            label = prefix,
                            description = description,
                            template = bodyStr,
                            detail = name,
                            scope = scope,
                        )
                    )
                }
            }
        } catch (_: Exception) {}

        return snippets
    }

    /**
     * Parses a VS Code snippet JSON file.
     */
    fun parseFile(file: File, defaultScope: String? = null): List<Snippet> {
        if (!file.exists() || !file.isFile) return emptyList()
        return try {
            val content = file.readText()
            val inferredScope = defaultScope ?: file.nameWithoutExtension.lowercase()
            parseJsonSnippets(content, inferredScope)
        } catch (_: Exception) {
            emptyList()
        }
    }
}
