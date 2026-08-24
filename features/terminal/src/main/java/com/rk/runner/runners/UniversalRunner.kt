package com.rk.runner.runners

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Environment
import android.util.Base64
import com.rk.DefaultScope
import com.rk.exec.TerminalCommand
import com.rk.exec.launchTerminal
import com.rk.file.FileObject
import com.rk.file.FileWrapper
import com.rk.file.child
import com.rk.file.localBinDir
import com.rk.icons.Icon
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.runner.FileRunner
import com.rk.settings.Settings
import com.rk.terminal.setupAssetFile
import com.rk.utils.dialogRes
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets

object UniversalRunner : FileRunner() {

    override val id = "universal"
    override val label = strings.universal_runner.getString()
    override val description = strings.universal_runner_desc.getString()

    override fun matcher(fileObject: FileObject): Boolean {
        return Regex(
                ".*\\.(py|js|ts|java|kt|rs|rb|php|c|cpp|cc|cxx|cs|sh|bash|zsh|fish|pl|lua|r|R|hs|f90|f95|f03|f08|pas|tcl|elm|fsx|fs|go)$"
            )
            .matches(fileObject.getName())
    }

    @SuppressLint("SdCardPath")
    override suspend fun run(activity: Activity, fileObject: FileObject) {
        if (Settings.use_ssh_terminal) {
            launchUniversalRunner(activity, fileObject)
            return
        }

        setupAssetFile("universal_runner")

        if (fileObject !is FileWrapper) {
            dialogRes(title = strings.attention.getString(), msg = strings.non_native_filetype.getString(), onOk = {})
            return
        }

        val path = fileObject.getAbsolutePath()
        if (
            path.startsWith("/sdcard") ||
                path.startsWith("/storage/") ||
                path.startsWith(Environment.getExternalStorageDirectory().absolutePath)
        ) {
            dialogRes(
                title = strings.attention.getString(),
                msg = strings.sdcard_filetype.getString(),
                okRes = strings.continue_action,
                onCancel = {},
                onOk = { DefaultScope.launch { launchUniversalRunner(activity, fileObject) } },
            )
            return
        }

        launchUniversalRunner(activity, fileObject)
    }

    suspend fun launchUniversalRunner(activity: Activity, fileObject: FileObject) {
        val isSSH = Settings.use_ssh_terminal
        val sshCmd = if (isSSH) getSSHRunCommand(fileObject) else null

        launchTerminal(
            activity = activity,
            terminalCommand =
                TerminalCommand(
                    sandbox = !isSSH,
                    exe = "/bin/bash",
                    args = arrayOf(localBinDir().child("universal_runner").absolutePath, fileObject.getAbsolutePath()),
                    id = strings.universal_runner.getString(),
                    terminatePreviousSession = true,
                    workingDir = if (isSSH) "~/.xed_runner" else (fileObject.getParentFile()?.getAbsolutePath() ?: "/"),
                    sshCommand = sshCmd,
                ),
        )
    }

    private fun getSSHRunCommand(fileObject: FileObject): String {
        val fileName = fileObject.getName()
        val ext = fileObject.getExtension().lowercase()
        val baseName = fileName.substringBeforeLast('.')

        val runCmd =
            when (ext) {
                "py" -> "python3 $fileName 2>/dev/null || python $fileName"
                "js" -> "node $fileName"
                "ts" -> "npx ts-node $fileName 2>/dev/null || (tsc $fileName && node $baseName.js)"
                "java" -> "java $fileName"
                "kt" -> "kotlinc $fileName -include-runtime -d temp.jar && java -jar temp.jar"
                "rs" -> "rustc $fileName -o temp.out && ./temp.out"
                "go" -> "go run $fileName"
                "rb" -> "ruby $fileName"
                "php" -> "php $fileName"
                "c" -> "gcc $fileName -o temp.out && ./temp.out"
                "cpp", "cc", "cxx" -> "g++ $fileName -o temp.out && ./temp.out"
                "cs" -> "dotnet run || (csc $fileName && mono $baseName.exe)"
                "sh", "bash" -> "bash $fileName"
                "zsh" -> "zsh $fileName"
                "fish" -> "fish $fileName"
                "pl" -> "perl $fileName"
                "lua" -> "lua $fileName"
                "r" -> "Rscript $fileName"
                "f90", "f95", "f03", "f08" -> "gfortran $fileName -o temp.out && ./temp.out"
                "pas" -> "fpc $fileName && ./$baseName"
                "tcl" -> "tclsh $fileName"
                "fsx", "fs" -> "dotnet fsi $fileName"
                else -> "chmod +x $fileName && ./$fileName"
            }

        val content =
            runCatching {
                fileObject.getInputStream().bufferedReader().use { it.readText() }
            }.getOrDefault("")
        val base64 = Base64.encodeToString(content.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)

        return "mkdir -p ~/.xed_runner && echo \"$base64\" | base64 -d > ~/.xed_runner/$fileName && cd ~/.xed_runner && echo -e \"\\e[32;1m[Running $fileName on SSH Remote]\\e[0m\" && ($runCmd)"
    }

    override fun getIcon(context: Context): Icon {
        return Icon.ResourceIcon(drawables.run)
    }
}
