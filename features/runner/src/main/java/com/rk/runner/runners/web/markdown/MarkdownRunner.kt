package com.rk.runner.runners.web.markdown

import android.app.Activity
import android.content.Context
import com.rk.activities.main.MainActivity
import com.rk.file.BuiltinFileType
import com.rk.file.FileObject
import com.rk.icons.Icon
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.runner.FileRunner
import com.rk.tabs.editor.EditorTab
import com.rk.tabs.markdown.MarkdownTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object MarkdownRunner : FileRunner() {

    override val id = "markdown_preview"
    override val label = strings.markdown_preview.getString()
    override val description = strings.markdown_preview_desc.getString()

    override fun matcher(fileObject: FileObject): Boolean {
        val markdownExtensions = BuiltinFileType.MARKDOWN.extensions.joinToString("|")
        return Regex(".*\\.($markdownExtensions)$").matches(fileObject.getName())
    }

    override suspend fun run(activity: Activity, fileObject: FileObject) {
        val mainActivity = MainActivity.instance ?: (activity as? MainActivity) ?: return
        val viewModel = mainActivity.viewModel

        withContext(Dispatchers.Main) {
            val currentTab = viewModel.tabManager.currentTab
            val projectRoot = (currentTab as? EditorTab)?.projectRoot
            val markdownTab = MarkdownTab(fileObject, projectRoot, viewModel)

            if (currentTab is EditorTab && currentTab.file?.getAbsolutePath() == fileObject.getAbsolutePath()) {
                currentTab.quickSave()
                viewModel.tabManager.replaceTab(currentTab, markdownTab)
            } else {
                viewModel.tabManager.openTab(markdownTab)
            }
        }
    }

    override fun getIcon(context: Context): Icon? {
        return BuiltinFileType.MARKDOWN.icon
    }
}
