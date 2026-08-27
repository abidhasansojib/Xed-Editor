package com.rk.drawer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.rk.activities.main.MainActivity
import com.rk.components.AddDialogItem
import com.rk.droidspaces.DroidspacesManager
import com.rk.feature.FeatureRegistry
import com.rk.file.FileObject
import com.rk.file.FileWrapper
import com.rk.icons.Icon
import com.rk.project.ProjectCreatorActivity
import com.rk.project.ProjectTemplateRegistry
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Settings
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddProjectSheet(
    onDismiss: () -> Unit,
    onAddProject: (FileObject) -> Unit,
    openFolder: ManagedActivityResultLauncher<Uri?, Uri?>? = null,
    showPrivateFileWarning: (onOK: () -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val activity = context as MainActivity
    val lifecycleScope = remember { activity.lifecycleScope }

    val viewModel = activity.drawerViewModel

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier =
                Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp).verticalScroll(rememberScrollState())
        ) {
            val storageOptions = remember {
                AddProjectRegistry.options.filter { it.category == AddProjectCategory.STORAGE }
            }

            SectionHeader(stringResource(strings.storage))

            var showInternalFileManager by remember { mutableStateOf(false) }
            var internalFileManagerInitialPath by remember { mutableStateOf<String?>(null) }

            AddDialogItem(
                icon = Icon.ResourceIcon(drawables.folder),
                title = stringResource(strings.open_internal_storage),
                description = stringResource(strings.open_internal_storage_desc),
                onClick = {
                    internalFileManagerInitialPath = null
                    showInternalFileManager = true
                },
            )

            var showUserPicker by remember { mutableStateOf(false) }
            var showContainerFileManager by remember { mutableStateOf(false) }
            var containerManagerInitialPath by remember { mutableStateOf("/root") }
            val containerName = remember { Settings.droidspaces_container_name.ifBlank { "Ubuntu" } }

            AddDialogItem(
                icon = Icon.ResourceIcon(drawables.android),
                title = stringResource(strings.container_storage),
                description = stringResource(strings.open_container_storage),
                onClick = {
                    lifecycleScope.launch {
                        val rootOk = DroidspacesManager.checkRootAccess()
                        if (!rootOk) {
                            com.rk.utils.dialogRes(
                                activity = activity,
                                title = strings.root_access_required.getString(),
                                msg = strings.root_access_required_desc.getString(),
                            )
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
                                showUserPicker = true
                            }
                        }
                    }
                },
            )

            if (showInternalFileManager) {
                com.rk.file.InternalFileManagerSheet(
                    initialPath = internalFileManagerInitialPath,
                    onDismiss = {
                        showInternalFileManager = false
                        onDismiss()
                    },
                    onOpenInDrawer = { fileObj ->
                        lifecycleScope.launch {
                            viewModel.addFileTreeTab(fileObj, save = true)
                            onDismiss()
                        }
                    },
                )
            }

            if (showUserPicker) {
                com.rk.droidspaces.SelectUserSheet(
                    containerName = containerName,
                    title = stringResource(strings.select_storage_user),
                    onDismiss = { showUserPicker = false },
                    onUserSelected = { username ->
                        lifecycleScope.launch {
                            val home = DroidspacesManager.getUserHome(containerName, username)
                            containerManagerInitialPath = home
                            showContainerFileManager = true
                        }
                    },
                )
            }

            if (showContainerFileManager) {
                com.rk.droidspaces.ContainerFileManagerSheet(
                    containerName = containerName,
                    initialPath = containerManagerInitialPath,
                    onDismiss = {
                        showContainerFileManager = false
                        onDismiss()
                    },
                    onOpenInDrawer = { fileObj ->
                        lifecycleScope.launch {
                            viewModel.addFileTreeTab(fileObj, save = true)
                            onDismiss()
                        }
                    },
                )
            }

            val is11Plus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
            val isManager = is11Plus && Environment.isExternalStorageManager()
            val storage = Environment.getExternalStorageDirectory()

            if (isManager) {
                val storageManager = context.getSystemService(StorageManager::class.java)
                val volumes = storageManager.storageVolumes

                volumes.forEach { volume ->
                    val root = volume.directory ?: return@forEach
                    if (root == storage) return@forEach
                    if (!root.canRead() || !root.canWrite() || root.listFiles() == null) return@forEach

                    val name = volume.getDescription(context)
                    val removable = volume.isRemovable
                    val description = if (removable) strings.open_removable_storage else strings.open_internal_storage

                    AddDialogItem(
                        icon = Icon.ResourceIcon(drawables.sd_card),
                        title = name,
                        description = stringResource(description),
                    ) {
                        internalFileManagerInitialPath = root.absolutePath
                        showInternalFileManager = true
                    }
                }
            }

            storageOptions.forEach { option ->
                AddDialogItem(
                    icon = option.icon,
                    title = option.title,
                    description = option.description,
                    onClick = { option.onClick(onDismiss) },
                )
            }

            val createOptions = remember {
                AddProjectRegistry.options.filter { it.category == AddProjectCategory.CREATE }
            }
            val hasTemplates = remember { ProjectTemplateRegistry.categories.value.any { it.templates.isNotEmpty() } }

            if (hasTemplates || createOptions.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                SectionHeader(stringResource(strings.create))

                if (hasTemplates) {
                    AddDialogItem(
                        icon = Icon.ResourceIcon(drawables.add),
                        title = stringResource(strings.new_project),
                        description = stringResource(strings.new_project_desc),
                        onClick = {
                            context.startActivity(Intent(context, ProjectCreatorActivity::class.java))
                            onDismiss()
                        },
                    )
                }

                createOptions.forEach { option ->
                    AddDialogItem(
                        icon = option.icon,
                        title = option.title,
                        description = option.description,
                        onClick = { option.onClick(onDismiss) },
                    )
                }
            }

            val otherOptions = remember {
                AddProjectRegistry.options.filter { it.category == AddProjectCategory.OTHER }
            }
            val isDebugMode = FeatureRegistry.isEnabled("debug_mode")

            if (isDebugMode || otherOptions.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                SectionHeader(stringResource(strings.other))

                if (isDebugMode) {
                    AddDialogItem(
                        icon = Icon.ResourceIcon(drawables.build),
                        title = stringResource(strings.private_files),
                        description = stringResource(strings.private_files_desc),
                        onClick = {
                            if (!Settings.has_shown_private_data_dir_warning) {
                                showPrivateFileWarning {
                                    Settings.has_shown_private_data_dir_warning = true
                                    lifecycleScope.launch { onAddProject(FileWrapper(activity.filesDir.parentFile!!)) }
                                }
                            } else {
                                lifecycleScope.launch { onAddProject(FileWrapper(activity.filesDir.parentFile!!)) }
                            }
                            onDismiss()
                        },
                    )
                }

                otherOptions.forEach { option ->
                    AddDialogItem(
                        icon = option.icon,
                        title = option.title,
                        description = option.description,
                        onClick = { option.onClick(onDismiss) },
                    )
                }
            }
        }

    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.semantics { heading() }.padding(vertical = 8.dp, horizontal = 4.dp),
    )
}
