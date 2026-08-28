package com.rk.tabs.markdown

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Controller managing smooth scrolling and instant navigation to Markdown headings,
 * anchor targets (#topic), footnotes, and Document Outline items.
 */
class MarkdownScrollController(
    val scrollState: ScrollState,
    val coroutineScope: CoroutineScope,
) {
    var rootCoordinates: LayoutCoordinates? = null

    // Heading index -> LayoutCoordinates
    val headingCoordinates = mutableMapOf<Int, LayoutCoordinates>()

    // Anchor alias / id / slug -> LayoutCoordinates
    val anchorCoordinates = mutableMapOf<String, LayoutCoordinates>()

    // List of all parsed headings in the document for intelligent matching
    var headings: List<MarkdownBlock.Heading> = emptyList()

    fun updateHeadings(newHeadings: List<MarkdownBlock.Heading>) {
        headings = newHeadings
    }

    fun registerRoot(coordinates: LayoutCoordinates) {
        rootCoordinates = coordinates
    }

    fun registerHeading(index: Int, heading: MarkdownBlock.Heading, coordinates: LayoutCoordinates) {
        headingCoordinates[index] = coordinates

        val rawId = heading.id.trim().lowercase()
        if (rawId.isNotBlank()) {
            anchorCoordinates[rawId] = coordinates
            val slug = slugify(heading.id)
            if (slug != rawId) {
                anchorCoordinates[slug] = coordinates
            }
        }

        val textSlug = slugify(heading.text)
        if (textSlug.isNotBlank()) {
            anchorCoordinates[textSlug] = coordinates
        }

        heading.anchorAliases.forEach { alias ->
            val clean = alias.trim().removePrefix("#").lowercase()
            if (clean.isNotBlank()) {
                anchorCoordinates[clean] = coordinates
            }
        }
    }

    fun registerAnchor(anchor: String, coordinates: LayoutCoordinates) {
        val clean = anchor.trim().removePrefix("#").lowercase()
        if (clean.isNotBlank()) {
            anchorCoordinates[clean] = coordinates
        }
    }

    fun scrollToAnchor(rawAnchor: String) {
        val cleanAnchor = rawAnchor.trim().removePrefix("#")
        if (cleanAnchor.isBlank()) return

        coroutineScope.launch {
            // Find target coordinates, retry briefly if layout is currently settling
            var targetCoords = findCoordinatesForAnchor(cleanAnchor)
            if (targetCoords == null || !targetCoords.isAttached) {
                delay(50)
                targetCoords = findCoordinatesForAnchor(cleanAnchor)
            }
            if (targetCoords == null || !targetCoords.isAttached) {
                delay(100)
                targetCoords = findCoordinatesForAnchor(cleanAnchor)
            }

            if (targetCoords != null && targetCoords.isAttached) {
                val root = rootCoordinates
                if (root != null && root.isAttached) {
                    val relativeY = root.localPositionOf(targetCoords, Offset.Zero).y
                    val targetY = (relativeY - 16f).coerceIn(0f, scrollState.maxValue.toFloat())
                    scrollState.animateScrollTo(targetY.toInt())
                }
            }
        }
    }

    fun scrollToHeading(heading: MarkdownBlock.Heading, headingIndex: Int? = null) {
        coroutineScope.launch {
            var targetCoords: LayoutCoordinates? = null
            if (headingIndex != null) {
                targetCoords = headingCoordinates[headingIndex]
            }
            if (targetCoords == null || !targetCoords.isAttached) {
                targetCoords = findCoordinatesForAnchor(heading.id.ifBlank { heading.text })
            }
            if (targetCoords == null || !targetCoords.isAttached) {
                delay(50)
                if (headingIndex != null) {
                    targetCoords = headingCoordinates[headingIndex]
                }
                if (targetCoords == null || !targetCoords.isAttached) {
                    targetCoords = findCoordinatesForAnchor(heading.id.ifBlank { heading.text })
                }
            }

            if (targetCoords != null && targetCoords.isAttached) {
                val root = rootCoordinates
                if (root != null && root.isAttached) {
                    val relativeY = root.localPositionOf(targetCoords, Offset.Zero).y
                    val targetY = (relativeY - 16f).coerceIn(0f, scrollState.maxValue.toFloat())
                    scrollState.animateScrollTo(targetY.toInt())
                }
            }
        }
    }

    fun findCoordinatesForAnchor(anchor: String): LayoutCoordinates? {
        val cleanAnchor = anchor.trim().removePrefix("#").lowercase()
        val slugAnchor = slugify(cleanAnchor)

        // 1. Direct match in anchorCoordinates map
        anchorCoordinates[cleanAnchor]?.let { if (it.isAttached) return it }
        anchorCoordinates[slugAnchor]?.let { if (it.isAttached) return it }

        // 2. Exact match against known headings and aliases
        headings.forEachIndexed { idx, heading ->
            val hSlug = slugify(heading.id.ifBlank { heading.text })
            if (hSlug == slugAnchor || heading.id.equals(cleanAnchor, ignoreCase = true) ||
                heading.anchorAliases.any { it.equals(cleanAnchor, ignoreCase = true) || slugify(it) == slugAnchor }) {
                headingCoordinates[idx]?.let { if (it.isAttached) return it }
            }
        }

        // 3. Prefix/suffix match: e.g. anchor 'non-gki' matching 'non-gki-legacy-kernels'
        headings.forEachIndexed { idx, heading ->
            val hSlug = slugify(heading.id.ifBlank { heading.text })
            if (hSlug.startsWith(slugAnchor) || slugAnchor.startsWith(hSlug) ||
                hSlug.endsWith(slugAnchor) || slugAnchor.endsWith(hSlug)) {
                headingCoordinates[idx]?.let { if (it.isAttached) return it }
            }
        }

        // 4. Substring / multi-word match
        val anchorWords = slugAnchor.split('-').filter { it.length > 2 }
        if (anchorWords.isNotEmpty()) {
            var bestIdx = -1
            var maxMatches = 0
            headings.forEachIndexed { idx, heading ->
                val hSlug = slugify(heading.id.ifBlank { heading.text })
                val headingWords = hSlug.split('-').filter { it.length > 2 }
                val matchCount = anchorWords.count { aw ->
                    headingWords.any { hw ->
                        hw.startsWith(aw) || aw.startsWith(hw) ||
                            (hw.length >= 4 && aw.length >= 4 && (hw.take(4) == aw.take(4)))
                    }
                }
                if (matchCount > maxMatches) {
                    maxMatches = matchCount
                    bestIdx = idx
                }
            }
            if (bestIdx != -1 && maxMatches > 0) {
                headingCoordinates[bestIdx]?.let { if (it.isAttached) return it }
            }
        }

        // 5. Partial fallback in anchorCoordinates map
        anchorCoordinates.entries.firstOrNull { (k, v) ->
            v.isAttached && (k.startsWith(slugAnchor) || slugAnchor.startsWith(k) || k.contains(slugAnchor) || slugAnchor.contains(k))
        }?.value?.let { return it }

        return null
    }

    companion object {
        fun slugify(text: String): String {
            return text.lowercase()
                .replace(Regex("<[^>]+>"), "")
                .replace(Regex("\\{#[^}]+\\}"), "")
                .replace(Regex("[^\\p{L}\\p{N}\\s-_]"), "")
                .trim()
                .replace(Regex("[\\s_]+"), "-")
                .trim('-')
        }
    }
}
