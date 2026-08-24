package com.rk.runner

import android.app.Activity
import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.rk.DefaultScope
import com.rk.TerminalLauncher
import com.rk.file.FileObject
import com.rk.file.child
import com.rk.file.createFileIfNot
import com.rk.file.localDir
import com.rk.icons.Icon
import com.rk.resources.drawables
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.random.Random

object ShellBasedRunners {
    private val _runners = MutableStateFlow<List<ShellBasedRunner>>(emptyList())
    val runners = _runners.asStateFlow()

    init {
        DefaultScope.launch { indexRunners() }
    }

    suspend fun newRunner(runner: ShellBasedRunner): Boolean {
        return withContext(Dispatchers.IO) {
            if (_runners.value.find { it.label == runner.label } == null) {
                _runners.update { it + runner }
                runnerDir()
                    .child("${runner.label}.sh")
                    .createFileIfNot()
                    .writeText("echo \"This runner has no implementation. Click the runner and add your own script.\"")
                saveRunners()
                true
            } else {
                false
            }
        }
    }

    suspend fun saveRunners() {
        val json = Gson().toJson(_runners.value)
        localDir().child("runners.json").writeText(json)
    }

    suspend fun deleteRunner(runner: ShellBasedRunner) {
        _runners.update { it - runner }
        saveRunners()
        runnerDir().child("${runner.label}.sh").createFileIfNot().delete()
    }

    suspend fun indexRunners() {
        withContext(Dispatchers.IO) {
            val file = localDir().child("runners.json")
            if (file.exists()) {
                val content = file.readText()
                val type = object : TypeToken<List<ShellBasedRunner>>() {}.type
                _runners.value = Gson().fromJson<List<ShellBasedRunner>>(content, type)
            }
        }
    }
}

data class ShellBasedRunner(override val label: String, val regex: String) : FileRunner() {

    override val id = Random.nextInt().toString()

    override fun matcher(fileObject: FileObject): Boolean {
        return Regex(regex).matches(fileObject.getName())
    }

    override suspend fun run(activity: Activity, fileObject: FileObject) {
        val script = runnerDir().child("${label}.sh").createFileIfNot()
        val isSSH = com.rk.settings.Settings.use_ssh_terminal
        val sshCmd =
            if (isSSH) {
                val scriptContent = runCatching { script.readText() }.getOrDefault("")
                val scriptBase64 =
                    android.util.Base64.encodeToString(
                        scriptContent.toByteArray(java.nio.charset.StandardCharsets.UTF_8),
                        android.util.Base64.NO_WRAP,
                    )
                val fileContent =
                    runCatching {
                        fileObject.getInputStream().bufferedReader().use { it.readText() }
                    }.getOrDefault("")
                val fileBase64 =
                    android.util.Base64.encodeToString(
                        fileContent.toByteArray(java.nio.charset.StandardCharsets.UTF_8),
                        android.util.Base64.NO_WRAP,
                    )
                val fileName = fileObject.getName()
                "mkdir -p ~/.xed_runner && echo \"$scriptBase64\" | base64 -d > ~/.xed_runner/runner.sh && echo \"$fileBase64\" | base64 -d > ~/.xed_runner/$fileName && cd ~/.xed_runner && chmod +x runner.sh && echo -e \"\\e[32;1m[Running $label on SSH Remote]\\e[0m\" && ./runner.sh ~/.xed_runner/$fileName"
            } else {
                null
            }

        TerminalLauncher.launch(
            activity = activity,
            sandbox = !isSSH,
            exe = "/bin/bash",
            args = arrayOf(script.absolutePath, fileObject.getAbsolutePath()),
            id = label,
            sshCommand = sshCmd,
        )
    }

    fun getScript(): File {
        return runnerDir().child("$label.sh").createFileIfNot()
    }

    override fun getIcon(context: Context): Icon {
        return Icon.ResourceIcon(drawables.bash)
    }
}
