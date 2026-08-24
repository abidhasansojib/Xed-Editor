package com.rk.runner.runners

import android.app.Activity
import android.content.Context
import com.rk.TerminalLauncher
import com.rk.file.FileObject
import com.rk.icons.Icon
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.runner.ProjectRunner
import com.rk.xed.XedManager
import kotlinx.coroutines.runBlocking

object XedProjectRunner : ProjectRunner() {
    override val id: String = "xed_project_runner"
    override val label: String = strings.project_runner.getString()

    override val description = strings.project_runner_desc.getString()

    override fun getIcon(context: Context): Icon {
        return Icon.ResourceIcon(drawables.run)
    }

    override fun matcher(projectRoot: FileObject): Boolean {
        return runBlocking { XedManager.getRunScript(projectRoot) != null }
    }

    override suspend fun run(activity: Activity, projectRoot: FileObject) {
        val runScript = XedManager.getRunScript(projectRoot) ?: return
        val isSSH = com.rk.settings.Settings.use_ssh_terminal
        val sshCmd =
            if (isSSH) {
                val scriptContent =
                    runCatching {
                        runScript.getInputStream().bufferedReader().use { it.readText() }
                    }.getOrDefault("")
                val scriptBase64 =
                    android.util.Base64.encodeToString(
                        scriptContent.toByteArray(java.nio.charset.StandardCharsets.UTF_8),
                        android.util.Base64.NO_WRAP,
                    )
                "mkdir -p ~/.xed_runner && echo \"$scriptBase64\" | base64 -d > ~/.xed_runner/run.sh && cd ~/.xed_runner && chmod +x run.sh && echo -e \"\\e[32;1m[Running Project on SSH Remote]\\e[0m\" && ./run.sh"
            } else {
                null
            }

        TerminalLauncher.launch(
            activity = activity,
            sandbox = !isSSH,
            exe = "/bin/bash",
            args = arrayOf(runScript.getAbsolutePath()),
            id = strings.project_runner.getString(),
            workingDir = if (isSSH) "~/.xed_runner" else projectRoot.getAbsolutePath(),
            sshCommand = sshCmd,
        )
    }
}
