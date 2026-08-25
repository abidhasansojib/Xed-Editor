package com.rk.droidspaces

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rk.activities.main.MainActivity
import com.rk.components.SingleInputDialog
import com.rk.file.FileObject
import com.rk.resources.drawables
import com.rk.resources.strings
import com.rk.utils.formatFileSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContainerFileManagerSheet(
    containerName: String,
    initialPath: String = "/root",
    onDismiss: () -> Unit,
    onOpenInDrawer: (FileObject) -> Unit,
) {
    var currentPath by remember { mutableStateOf(initialPath.ifBlank { "/root" }) }
    var fileItems by remember { mutableStateOf<List<FileObject>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var users by remember { mutableStateOf<List<ContainerUser>>(emptyList()) }

    var showNewFileDialog by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var selectedFileForAction by remember { mutableStateOf<FileObject?>(null) }

    val scope = rememberCoroutineScope()
    val mainActivity = MainActivity.instance

    suspend fun loadDirectory(path: String) {
        isLoading = true
        errorMessage = null
        try {
            val items = DroidspacesShell.listFiles(containerName, path)
            fileItems = items
        } catch (e: Exception) {
            errorMessage = e.localizedMessage ?: "Failed to list directory"
            fileItems = emptyList()
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(containerName) {
        users = DroidspacesManager.getContainerUsers(containerName, useCache = true)
    }

    LaunchedEffect(currentPath) {
        loadDirectory(currentPath)
    }

    if (showNewFileDialog) {
        var newName by remember { mutableStateOf("newfile.txt") }
        SingleInputDialog(
            title = stringResource(strings.new_file),
            inputLabel = stringResource(strings.file_name),
            inputValue = newName,
            onInputValueChange = { newName = it },
            onConfirm = {
                val clean = newName.trim()
                if (clean.isNotBlank()) {
                    scope.launch {
                        val fullPath = if (currentPath == "/") "/$clean" else "$currentPath/$clean"
                        val success = DroidspacesShell.touch(containerName, fullPath)
                        if (success) {
                            loadDirectory(currentPath)
                            val createdObj = DroidspacesFileObject(containerName, fullPath, isFileFlag = true)
                            mainActivity?.viewModel?.editorManager?.openFile(createdObj, switchToTab = true)
                            onDismiss()
                        }
                    }
                }
            },
            onFinish = { showNewFileDialog = false },
        )
    }

    if (showNewFolderDialog) {
        var newFolderName by remember { mutableStateOf("NewFolder") }
        SingleInputDialog(
            title = stringResource(strings.new_folder),
            inputLabel = stringResource(strings.folder_name),
            inputValue = newFolderName,
            onInputValueChange = { newFolderName = it },
            onConfirm = {
                val clean = newFolderName.trim()
                if (clean.isNotBlank()) {
                    scope.launch {
                        val fullPath = if (currentPath == "/") "/$clean" else "$currentPath/$clean"
                        DroidspacesShell.mkdirs(containerName, fullPath)
                        loadDirectory(currentPath)
                    }
                }
            },
            onFinish = { showNewFolderDialog = false },
        )
    }

    if (showRenameDialog && selectedFileForAction != null) {
        val target = selectedFileForAction!!
        var renameName by remember { mutableStateOf(target.getName()) }
        SingleInputDialog(
            title = stringResource(strings.rename),
            inputLabel = stringResource(strings.name),
            inputValue = renameName,
            onInputValueChange = { renameName = it },
            onConfirm = {
                val clean = renameName.trim()
                if (clean.isNotBlank() && clean != target.getName()) {
                    scope.launch {
                        target.renameTo(clean)
                        loadDirectory(currentPath)
                    }
                }
            },
            onFinish = {
                showRenameDialog = false
                selectedFileForAction = null
            },
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(horizontal = 16.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(strings.container_file_manager),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "$containerName : $currentPath",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Quick Jump Chips (Users & Rootfs)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                users.forEach { user ->
                    FilterChip(
                        selected = currentPath == user.homeDir,
                        onClick = { currentPath = user.homeDir },
                        label = { Text(if (user.isRoot) "root (/root)" else "${user.username} (${user.homeDir})") },
                    )
                }
                FilterChip(
                    selected = currentPath == "/",
                    onClick = { currentPath = "/" },
                    label = { Text("/ (rootfs)") },
                )
                FilterChip(
                    selected = currentPath == "/etc",
                    onClick = { currentPath = "/etc" },
                    label = { Text("/etc") },
                )
                FilterChip(
                    selected = currentPath == "/var",
                    onClick = { currentPath = "/var" },
                    label = { Text("/var") },
                )
                FilterChip(
                    selected = currentPath == "/opt",
                    onClick = { currentPath = "/opt" },
                    label = { Text("/opt") },
                )
                FilterChip(
                    selected = currentPath == "/tmp",
                    onClick = { currentPath = "/tmp" },
                    label = { Text("/tmp") },
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Breadcrumbs Navigation Bar
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { currentPath = initialPath.ifBlank { "/root" } },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = "Home",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    IconButton(
                        enabled = currentPath != "/" && currentPath.isNotEmpty(),
                        onClick = {
                            val parent = currentPath.trimEnd('/').substringBeforeLast('/', "").ifEmpty { "/" }
                            currentPath = parent
                        },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Up",
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    // Breadcrumb path parts
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val segments = currentPath.split("/").filter { it.isNotEmpty() }
                        Text(
                            text = "/",
                            modifier = Modifier
                                .clickable { currentPath = "/" }
                                .padding(horizontal = 4.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (segments.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                        var accumulated = ""
                        segments.forEachIndexed { index, seg ->
                            accumulated += "/$seg"
                            val targetPath = accumulated
                            val isLast = index == segments.lastIndex
                            Text(
                                text = "$seg /",
                                modifier = Modifier
                                    .clickable { currentPath = targetPath }
                                    .padding(horizontal = 2.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isLast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }

                    IconButton(
                        onClick = { scope.launch { loadDirectory(currentPath) } },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action Toolbar (Open in Drawer, New File, New Folder)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = {
                        val dirObj = DroidspacesFileObject(containerName, currentPath, isDirectoryFlag = true)
                        onOpenInDrawer(dirObj)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        painter = painterResource(drawables.folder),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(strings.open_in_workspace),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                    )
                }

                OutlinedButton(
                    onClick = { showNewFileDialog = true },
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(strings.new_file))
                }

                OutlinedButton(
                    onClick = { showNewFolderDialog = true },
                ) {
                    Icon(
                        painter = painterResource(drawables.folder),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(strings.new_folder))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Search filter
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(strings.search)) },
                leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()

            // File items list
            val filteredItems = remember(fileItems, searchQuery) {
                if (searchQuery.isBlank()) fileItems
                else fileItems.filter { it.getName().contains(searchQuery, ignoreCase = true) }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else if (errorMessage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = errorMessage ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = { scope.launch { loadDirectory(currentPath) } }) {
                            Text(stringResource(strings.refresh))
                        }
                    }
                }
            } else if (filteredItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(strings.container_empty_dir),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    items(
                        items = filteredItems,
                        key = { it.getAbsolutePath() },
                    ) { item ->
                        ContainerFileRow(
                            fileObject = item,
                            onClick = {
                                if (item.isDirectory()) {
                                    currentPath = item.getAbsolutePath().substringAfter("://").substringAfter("/")
                                    if (!currentPath.startsWith("/")) {
                                        currentPath = "/$currentPath"
                                    }
                                } else {
                                    scope.launch {
                                        mainActivity?.viewModel?.editorManager?.openFile(
                                            fileObject = item,
                                            projectRoot = null,
                                            checkDuplicate = true,
                                            switchToTab = true,
                                        )
                                        onDismiss()
                                    }
                                }
                            },
                            onOpenAsDrawerTab = {
                                onOpenInDrawer(item)
                                onDismiss()
                            },
                            onRename = {
                                selectedFileForAction = item
                                showRenameDialog = true
                            },
                            onDelete = {
                                scope.launch {
                                    item.delete()
                                    loadDirectory(currentPath)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContainerFileRow(
    fileObject: FileObject,
    onClick: () -> Unit,
    onOpenAsDrawerTab: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val isDir = fileObject.isDirectory()
    val cachedLen = if (fileObject is DroidspacesFileObject) fileObject.cachedLength else 0L
    var sizeText by remember(fileObject) {
        mutableStateOf(if (cachedLen > 0L) formatFileSize(cachedLen) else "")
    }

    if (!isDir && sizeText.isEmpty()) {
        LaunchedEffect(fileObject) {
            val len = withContext(Dispatchers.IO) { fileObject.length() }
            if (len > 0L) {
                sizeText = formatFileSize(len)
            }
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painter = painterResource(if (isDir) drawables.folder else drawables.file),
                contentDescription = null,
                tint = if (isDir) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fileObject.getName(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!isDir && sizeText.isNotEmpty()) {
                    Text(
                        text = sizeText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        modifier = Modifier.size(18.dp),
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    if (isDir) {
                        DropdownMenuItem(
                            text = { Text(stringResource(strings.add_to_workspace_drawer)) },
                            onClick = {
                                menuExpanded = false
                                onOpenAsDrawerTab()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(strings.rename)) },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(strings.delete)) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}
