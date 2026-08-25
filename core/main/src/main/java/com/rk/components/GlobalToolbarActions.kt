package com.rk.components

import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.rk.activities.main.MainActivity
import com.rk.activities.main.MainViewModel
import com.rk.activities.main.ui.drawerStateRef
import com.rk.activities.main.ui.fileTreeViewModel
import com.rk.activities.main.ui.searchViewModel
import com.rk.commands.ActionContext
import com.rk.commands.ToolbarConfiguration
import com.rk.drawer.DrawerViewModel
import com.rk.droidspaces.ContainerFileManagerSheet
import com.rk.droidspaces.DroidspacesManager
import com.rk.droidspaces.SelectUserSheet
import com.rk.file.toFileObject
import com.rk.filetree.FileTreeTab
import com.rk.icons.CreateNewFile
import com.rk.icons.Icon
import com.rk.icons.XedIcon
import com.rk.icons.XedIcons
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.search.CodeSearchDialog
import com.rk.search.FileSearchDialog
import com.rk.settings.Settings
import com.rk.utils.dialogRes
import com.rk.utils.application
import com.rk.utils.errorDialog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

object GlobalDialogs {
    val addDialog = MutableStateFlow(false)

    val fileSearchDialog = MutableStateFlow(false)

    val codeSearchDialog = MutableStateFlow(false)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalToolbarActions(viewModel: MainViewModel, drawerViewModel: DrawerViewModel) {
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()

    val commands by ToolbarConfiguration.globalCommands.collectAsStateWithLifecycle()

    val drawerTabs by drawerViewModel.drawerTabs.collectAsStateWithLifecycle()
    val currentDrawerTabIndex by drawerViewModel.currentDrawerTabIndex.collectAsStateWithLifecycle()
    val currentDrawerTab = drawerTabs.getOrNull(currentDrawerTabIndex)

    val addDialog by GlobalDialogs.addDialog.collectAsStateWithLifecycle()
    val fileSearchDialog by GlobalDialogs.fileSearchDialog.collectAsStateWithLifecycle()
    val codeSearchDialog by GlobalDialogs.codeSearchDialog.collectAsStateWithLifecycle()

    var showContainerUserPicker by remember { mutableStateOf(false) }
    var showContainerFileManager by remember { mutableStateOf(false) }
    var containerManagerInitialPath by remember { mutableStateOf("/root") }
    val containerName = remember { Settings.droidspaces_container_name.ifBlank { "Ubuntu" } }

    if (viewModel.tabs.isEmpty() || viewModel.currentTab?.showGlobalActions == true) {
        for (command in commands) {
            if (command.isSupported()) {
                IconButton(
                    enabled = command.isEnabled(),
                    onClick = {
                        activity?.let {
                            command.performCommand(ActionContext(it))
                        }
                    },
                ) {
                    XedIcon(command.getIcon())
                }
            }
        }
    }

    if (fileSearchDialog && currentDrawerTab is FileTreeTab) {
        FileSearchDialog(
            mainViewModel = viewModel,
            searchViewModel = searchViewModel.get()!!,
            projectFile = currentDrawerTab.root,
            onFinish = { GlobalDialogs.fileSearchDialog.value = false },
            onSelect = { projectFile, fileObject ->
                scope.launch {
                    if (fileObject.isFile()) {
                        viewModel.editorManager.openFile(
                            fileObject = fileObject,
                            projectRoot = projectFile,
                            checkDuplicate = true,
                            switchToTab = true,
                        )
                        drawerStateRef.get()?.close()
                    } else {
                        fileTreeViewModel.get()?.goToFolder(projectFile, fileObject)
                        drawerStateRef.get()!!.open()
                    }
                }
            },
        )
    }

    if (codeSearchDialog && currentDrawerTab is FileTreeTab) {
        CodeSearchDialog(
            mainViewModel = viewModel,
            searchViewModel = searchViewModel.get()!!,
            projectFile = currentDrawerTab.root,
            onFinish = { GlobalDialogs.codeSearchDialog.value = false },
        )
    }

    if (addDialog) {
        ModalBottomSheet(onDismissRequest = { GlobalDialogs.addDialog.value = false }) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 0.dp)) {
                AddDialogItem(icon = XedIcons.CreateNewFile, title = stringResource(strings.new_file)) {
                    GlobalDialogs.addDialog.value = false
                    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
                    intent.addCategory(Intent.CATEGORY_OPENABLE)
                    intent.type = "application/octet-stream"
                    intent.putExtra(Intent.EXTRA_TITLE, "newfile.txt")

                    val activities =
                        application!!.packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
                    if (activities.isEmpty()) {
                        errorDialog(strings.unsupported_feature)
                    } else {
                        MainActivity.instance?.apply {
                            fileManager.createNewFile(mimeType = "*/*", title = "newfile.txt") {
                                if (it != null) {
                                    lifecycleScope.launch {
                                        viewModel.editorManager.openFile(
                                            it,
                                            projectRoot = null,
                                            checkDuplicate = true,
                                            switchToTab = true,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                AddDialogItem(resId = drawables.file_symlink, title = stringResource(strings.open_file)) {
                    GlobalDialogs.addDialog.value = false
                    MainActivity.instance?.apply {
                        fileManager.requestOpenFile(mimeType = "*/*") {
                            if (it != null) {
                                lifecycleScope.launch {
                                    viewModel.editorManager.openFile(
                                        it.toFileObject(expectedIsFile = true),
                                        checkDuplicate = true,
                                        projectRoot = null,
                                        switchToTab = true,
                                    )
                                }
                            }
                        }
                    }
                }

                AddDialogItem(
                    icon = Icon.ResourceIcon(drawables.android),
                    title = stringResource(strings.open_container_file),
                    description = stringResource(strings.open_container_file_desc),
                ) {
                    GlobalDialogs.addDialog.value = false
                    scope.launch {
                        val rootOk = DroidspacesManager.checkRootAccess()
                        if (!rootOk) {
                            activity?.let {
                                dialogRes(
                                    activity = it,
                                    title = strings.root_access_required.getString(),
                                    msg = strings.root_access_required_desc.getString(),
                                )
                            }
                            return@launch
                        }

                        val defaultUser = Settings.droidspaces_storage_default_user.trim()
                        if (defaultUser.isNotEmpty()) {
                            val home = DroidspacesManager.getUserHome(containerName, defaultUser)
                            containerManagerInitialPath = home
                            showContainerFileManager = true
                        } else {
                            val users = DroidspacesManager.getContainerUsers(containerName, useCache = false)
                            if (users.size <= 1) {
                                val user = users.firstOrNull()
                                containerManagerInitialPath = user?.homeDir ?: "/root"
                                showContainerFileManager = true
                            } else {
                                showContainerUserPicker = true
                            }
                        }
                    }
                }
            }
        }
    }

    if (showContainerUserPicker) {
        SelectUserSheet(
            containerName = containerName,
            title = stringResource(strings.select_storage_user),
            onDismiss = { showContainerUserPicker = false },
            onUserSelected = { username ->
                scope.launch {
                    val home = DroidspacesManager.getUserHome(containerName, username)
                    containerManagerInitialPath = home
                    showContainerFileManager = true
                }
            },
        )
    }

    if (showContainerFileManager) {
        ContainerFileManagerSheet(
            containerName = containerName,
            initialPath = containerManagerInitialPath,
            onDismiss = { showContainerFileManager = false },
            onOpenInDrawer = { fileObj ->
                scope.launch {
                    drawerViewModel.addFileTreeTab(fileObj, save = true)
                }
            },
        )
    }
}
