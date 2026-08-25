package com.rk.editor

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.text.font.Font
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object FontCache {
    private val cachedFonts = ConcurrentHashMap<String, CachedFont>()

    data class CachedFont(val typeface: Typeface, val composeFont: Font)

    fun loadFont(context: Context, path: String, isAsset: Boolean) {
        if (cachedFonts.containsKey(path)) return
        doLoadFont(context, path, isAsset).onFailure { it.printStackTrace() }
    }

    private fun doLoadFont(context: Context, path: String, isAsset: Boolean) = runCatching {
        val font =
            if (isAsset) {
                val typeface = Typeface.createFromAsset(context.assets, path)
                val composeFont = Font(path, context.assets)
                CachedFont(typeface, composeFont)
            } else {
                val file = File(path)
                if (!file.exists()) {
                    return@runCatching
                }
                val typeface = Typeface.createFromFile(file)
                val composeFont = Font(file)
                CachedFont(typeface, composeFont)
            }
        cachedFonts[path] = font
    }

    private fun getCachedFont(context: Context, path: String, isAsset: Boolean): CachedFont? {
        cachedFonts[path]?.let { return it }
        doLoadFont(context, path, isAsset).fold(
            onFailure = {
                it.printStackTrace()
                return null
            },
            onSuccess = {
                return cachedFonts[path]
            },
        )
    }

    fun getTypeface(context: Context, path: String, isAsset: Boolean): Typeface? {
        return getCachedFont(context, path, isAsset)?.typeface
    }

    fun getFont(context: Context, path: String, isAsset: Boolean): Font? {
        return getCachedFont(context, path, isAsset)?.composeFont
    }
}
