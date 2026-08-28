package com.rk.droidspaces

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rk.activities.main.MainActivity
import com.rk.components.SingleInputDialog
import com.rk.file.FileObject
import com.rk.filetree.FileIcon
import com.rk.resources.drawables
import com.rk.resources.strings
import com.rk.utils.formatFileSize
import com.rk.utils.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class FileSortMode {
    NAME_ASC,
    NAME_DESC,
    SIZE_DESC,
    SIZE_ASC,
    DATE_DESC,
    DATE_ASC,
}

data class ContainerClipboard(
    val path: String,
    val isCut: Boolean,
)

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

    var sortMode by remember { mutableStateOf(FileSortMode.NAME_ASC) }
    var showHiddenFiles by remember { mutableStateOf(true) }
    var isSelectMode by remember { mutableStateOf(false) }
    var selectedItems by remember { mutableStateOf<Set<FileObject>>(emptySet()) }
    var clipboard by remember { mutableStateOf<ContainerClipboard?>(null) }

    var showNewFileDialog by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showChmodDialog by remember { mutableStateOf(false) }
    var showChownDialog by remember { mutableStateOf(false) }
    var showPropertiesDialog by remember { mutableStateOf(false) }
    var selectedFileForAction by remember { mutableStateOf<FileObject?>(null) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val mainActivity = MainActivity.instance

    val lazyListState = rememberLazyListState()
    val listNestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                // Absorb remaining vertical scroll at boundaries so the parent ModalBottomSheet doesn't shake/bounce
                return Offset(0f, available.y)
            }

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity,
            ): Velocity {
                // Absorb remaining vertical fling velocity so bottom sheet never shakes during fast swipe
                return Velocity(0f, available.y)
            }
        }
    }

    fun copyToClipboard(text: String, label: String = "Path") {
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboardManager?.setPrimaryClip(clip)
        toast(strings.file_copied)
    }

    fun openTerminalAtPath(path: String) {
        try {
            val clean = path.trimEnd('/').ifEmpty { "/" }
            val intent = Intent().setClassName(context.packageName, "com.rk.activities.terminal.Terminal").apply {
                putExtra("initial_command", "cd '$clean' && clear")
                putExtra("container_name", containerName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            toast("Terminal activity not available")
        }
    }

    suspend fun loadDirectory(path: String) {
        isLoading = true
        errorMessage = null
        try {
            val items = DroidspacesShell.listFiles(containerName, path)
            fileItems = items
            selectedItems = emptySet()
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
        if (lazyListState.firstVisibleItemIndex > 0) {
            try {
                lazyListState.scrollToItem(0)
            } catch (_: Exception) {}
        }
    }

    // New File Dialog
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

    // New Folder Dialog
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

    // Rename Dialog
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

    // Delete Confirmation Dialog
    if (showDeleteConfirmDialog) {
        val count = if (isSelectMode && selectedItems.isNotEmpty()) selectedItems.size else 1
        val titleText = if (count == 1) {
            val name = selectedFileForAction?.getName() ?: selectedItems.firstOrNull()?.getName() ?: ""
            "Delete '$name'?"
        } else {
            "Delete $count items?"
        }

        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text(text = titleText) },
            text = { Text(text = "This action cannot be undone. Files will be permanently removed.") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        showDeleteConfirmDialog = false
                        scope.launch {
                            if (isSelectMode && selectedItems.isNotEmpty()) {
                                selectedItems.forEach { it.delete() }
                                selectedItems = emptySet()
                                isSelectMode = false
                            } else {
                                selectedFileForAction?.delete()
                                selectedFileForAction = null
                            }
                            loadDirectory(currentPath)
                        }
                    },
                ) {
                    Text(stringResource(strings.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text(stringResource(strings.cancel))
                }
            },
        )
    }

    // Permissions (chmod) Dialog
    if (showChmodDialog) {
        val targets = if (isSelectMode && selectedItems.isNotEmpty()) selectedItems.toList() else listOfNotNull(selectedFileForAction)
        var octalMode by remember { mutableStateOf("755") }

        AlertDialog(
            onDismissRequest = { showChmodDialog = false },
            title = { Text(stringResource(strings.change_permissions)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (targets.size == 1) "Target: ${targets.first().getName()}" else "Targets: ${targets.size} items",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    OutlinedTextField(
                        value = octalMode,
                        onValueChange = { octalMode = it },
                        label = { Text(stringResource(strings.file_mode)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(text = "Quick Presets:", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf("644" to "644 (rw-r--r--)", "755" to "755 (rwxr-xr-x)", "777" to "777 (rwxrwxrwx)", "600" to "600 (rw-------)").forEach { (mode, label) ->
                            FilterChip(
                                selected = octalMode == mode,
                                onClick = { octalMode = mode },
                                label = { Text(label) },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanMode = octalMode.trim()
                        if (cleanMode.isNotBlank()) {
                            showChmodDialog = false
                            scope.launch {
                                targets.forEach { target ->
                                    val path = if (target is DroidspacesFileObject) target.containerPath else target.getAbsolutePath()
                                    DroidspacesShell.chmod(containerName, path, cleanMode)
                                }
                                loadDirectory(currentPath)
                            }
                        }
                    },
                ) {
                    Text(stringResource(strings.apply))
                }
            },
            dismissButton = {
                TextButton(onClick = { showChmodDialog = false }) {
                    Text(stringResource(strings.cancel))
                }
            },
        )
    }

    // Change Owner (chown) Dialog
    if (showChownDialog && selectedFileForAction != null) {
        val target = selectedFileForAction!!
        var ownerGroup by remember { mutableStateOf("root:root") }

        AlertDialog(
            onDismissRequest = { showChownDialog = false },
            title = { Text(stringResource(strings.change_owner)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Target: ${target.getName()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    OutlinedTextField(
                        value = ownerGroup,
                        onValueChange = { ownerGroup = it },
                        label = { Text(stringResource(strings.file_owner)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(text = "Container Users:", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        users.forEach { user ->
                            val og = "${user.username}:${user.username}"
                            FilterChip(
                                selected = ownerGroup == og,
                                onClick = { ownerGroup = og },
                                label = { Text(user.username) },
                            )
                        }
                        FilterChip(
                            selected = ownerGroup == "root:root",
                            onClick = { ownerGroup = "root:root" },
                            label = { Text("root:root") },
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanOG = ownerGroup.trim()
                        if (cleanOG.isNotBlank()) {
                            showChownDialog = false
                            scope.launch {
                                val path = if (target is DroidspacesFileObject) target.containerPath else target.getAbsolutePath()
                                DroidspacesShell.chown(containerName, path, cleanOG)
                                loadDirectory(currentPath)
                            }
                        }
                    },
                ) {
                    Text(stringResource(strings.apply))
                }
            },
            dismissButton = {
                TextButton(onClick = { showChownDialog = false }) {
                    Text(stringResource(strings.cancel))
                }
            },
        )
    }

    // Properties / Details Dialog
    if (showPropertiesDialog && selectedFileForAction != null) {
        val target = selectedFileForAction!!
        val path = if (target is DroidspacesFileObject) target.containerPath else target.getAbsolutePath()
        var statInfo by remember { mutableStateOf<DroidspacesShell.DetailedStat?>(null) }
        var isFetchingStat by remember { mutableStateOf(true) }

        LaunchedEffect(target) {
            statInfo = DroidspacesShell.getDetailedStat(containerName, path)
            isFetchingStat = false
        }

        AlertDialog(
            onDismissRequest = { showPropertiesDialog = false },
            title = { Text(stringResource(strings.file_properties)) },
            text = {
                if (isFetchingStat) {
                    Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(text = "Name: ${target.getName()}", fontWeight = FontWeight.Bold)
                        Text(text = "Container: $containerName", style = MaterialTheme.typography.bodySmall)
                        Text(text = "Path: $path", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                        statInfo?.let { stat ->
                            Text(text = "Type: ${stat.fileType}", style = MaterialTheme.typography.bodySmall)
                            Text(text = "Size: ${formatFileSize(stat.size)} (${stat.size} bytes)", style = MaterialTheme.typography.bodySmall)
                            Text(text = "Permissions: ${stat.octalMode} (${stat.symbolicMode})", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                            Text(text = "Owner/Group: ${stat.owner}:${stat.group}", style = MaterialTheme.typography.bodySmall)
                            val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(stat.lastModified))
                            Text(text = "Last Modified: $dateStr", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPropertiesDialog = false }) {
                    Text(stringResource(strings.ok))
                }
            },
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                // Top Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = stringResource(strings.container_file_manager),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                )
                                Surface(
                                    shape = MaterialTheme.shapes.extraSmall,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                ) {
                                    Text(
                                        text = containerName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        maxLines = 1,
                                        softWrap = false,
                                    )
                                }
                            }
                            Text(
                                text = currentPath,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { openTerminalAtPath(currentPath) },
                        ) {
                            Icon(
                                painter = painterResource(drawables.terminal),
                                contentDescription = stringResource(strings.open_in_terminal),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }

            Spacer(modifier = Modifier.height(6.dp))

            // Quick Jump Chips (Users & Rootfs Paths)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                users.forEach { user ->
                    FilterChip(
                        selected = currentPath == user.homeDir,
                        onClick = { currentPath = user.homeDir },
                        label = { Text(if (user.isRoot) "root (~)" else "${user.username} (~)") },
                    )
                }
                listOf("/" to "/ (rootfs)", "/etc" to "/etc", "/var" to "/var", "/opt" to "/opt", "/tmp" to "/tmp", "/usr" to "/usr", "/home" to "/home").forEach { (dir, label) ->
                    FilterChip(
                        selected = currentPath == dir,
                        onClick = { currentPath = dir },
                        label = { Text(label) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Breadcrumbs Navigation Bar
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { currentPath = initialPath.ifBlank { "/root" } },
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = "Home",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }

                    IconButton(
                        enabled = currentPath != "/" && currentPath.isNotEmpty(),
                        onClick = {
                            val parent = currentPath.trimEnd('/').substringBeforeLast('/', "").ifEmpty { "/" }
                            currentPath = parent
                        },
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Up",
                            modifier = Modifier.size(18.dp),
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
                            fontWeight = if (segments.isEmpty()) FontWeight.Bold else FontWeight.Normal,
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
                                fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                                color = if (isLast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }

                    IconButton(
                        onClick = { copyToClipboard(currentPath, "Current Path") },
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(
                            painter = painterResource(drawables.copy),
                            contentDescription = stringResource(strings.copy_path),
                            modifier = Modifier.size(18.dp),
                        )
                    }

                    IconButton(
                        onClick = { scope.launch { loadDirectory(currentPath) } },
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action Toolbar (Optimized Layout)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Primary: Open in Workspace
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

                // New File
                FilledTonalButton(
                    onClick = { showNewFileDialog = true },
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(strings.new_file))
                }

                // New Folder
                FilledTonalButton(
                    onClick = { showNewFolderDialog = true },
                ) {
                    Icon(painter = painterResource(drawables.folder), contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(strings.new_folder))
                }

                // Paste button if clipboard is active
                clipboard?.let { clip ->
                    FilledTonalButton(
                        onClick = {
                            scope.launch {
                                val src = clip.path
                                val name = src.trimEnd('/').substringAfterLast('/')
                                val dst = if (currentPath == "/") "/$name" else "$currentPath/$name"
                                if (clip.isCut) {
                                    DroidspacesShell.move(containerName, src, dst)
                                    clipboard = null
                                } else {
                                    DroidspacesShell.copy(containerName, src, dst)
                                }
                                loadDirectory(currentPath)
                            }
                        },
                    ) {
                        Icon(painter = painterResource(drawables.paste), contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(strings.paste_here))
                    }
                }

                // Sort & Filter Dropdown Menu
                var sortMenuExpanded by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { sortMenuExpanded = true }) {
                        Icon(painter = painterResource(drawables.filter), contentDescription = stringResource(strings.sort_by))
                    }

                    DropdownMenu(
                        expanded = sortMenuExpanded,
                        onDismissRequest = { sortMenuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Name (A to Z)") },
                            leadingIcon = { if (sortMode == FileSortMode.NAME_ASC) Icon(Icons.Default.Check, null) },
                            onClick = { sortMode = FileSortMode.NAME_ASC; sortMenuExpanded = false },
                        )
                        DropdownMenuItem(
                            text = { Text("Name (Z to A)") },
                            leadingIcon = { if (sortMode == FileSortMode.NAME_DESC) Icon(Icons.Default.Check, null) },
                            onClick = { sortMode = FileSortMode.NAME_DESC; sortMenuExpanded = false },
                        )
                        DropdownMenuItem(
                            text = { Text("Size (Largest first)") },
                            leadingIcon = { if (sortMode == FileSortMode.SIZE_DESC) Icon(Icons.Default.Check, null) },
                            onClick = { sortMode = FileSortMode.SIZE_DESC; sortMenuExpanded = false },
                        )
                        DropdownMenuItem(
                            text = { Text("Date (Newest first)") },
                            leadingIcon = { if (sortMode == FileSortMode.DATE_DESC) Icon(Icons.Default.Check, null) },
                            onClick = { sortMode = FileSortMode.DATE_DESC; sortMenuExpanded = false },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(if (showHiddenFiles) "Hide hidden files" else "Show hidden files") },
                            onClick = {
                                showHiddenFiles = !showHiddenFiles
                                sortMenuExpanded = false
                            },
                        )
                    }
                }

                // Select Mode Toggle
                IconButton(
                    onClick = {
                        isSelectMode = !isSelectMode
                        if (!isSelectMode) selectedItems = emptySet()
                    },
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Select Mode",
                        tint = if (isSelectMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Multi-Select Batch Action Bar
            AnimatedVisibility(visible = isSelectMode) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Checkbox(
                                checked = selectedItems.size == fileItems.size && fileItems.isNotEmpty(),
                                onCheckedChange = { checked ->
                                    selectedItems = if (checked) fileItems.toSet() else emptySet()
                                },
                            )
                            Text(
                                text = "${selectedItems.size} selected",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (selectedItems.isNotEmpty()) {
                                IconButton(onClick = { showChmodDialog = true }) {
                                    Icon(
                                        painter = painterResource(drawables.lock),
                                        contentDescription = stringResource(strings.change_permissions),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                                IconButton(onClick = { showDeleteConfirmDialog = true }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = stringResource(strings.delete),
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                            IconButton(onClick = { isSelectMode = false; selectedItems = emptySet() }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cancel",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Search filter bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(strings.search)) },
                leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider()

            // Filtered & Sorted File Items
            val processedItems = remember(fileItems, searchQuery, sortMode, showHiddenFiles) {
                var list = fileItems
                if (!showHiddenFiles) {
                    list = list.filter { !it.getName().startsWith(".") }
                }
                if (searchQuery.isNotBlank()) {
                    list = list.filter { it.getName().contains(searchQuery, ignoreCase = true) }
                }

                // Partition: directories always first, then files
                val (dirs, files) = list.partition { it.isDirectory() }
                val sortedDirs = when (sortMode) {
                    FileSortMode.NAME_ASC -> dirs.sortedBy { it.getName().lowercase() }
                    FileSortMode.NAME_DESC -> dirs.sortedByDescending { it.getName().lowercase() }
                    else -> dirs.sortedBy { it.getName().lowercase() }
                }
                val sortedFiles = when (sortMode) {
                    FileSortMode.NAME_ASC -> files.sortedBy { it.getName().lowercase() }
                    FileSortMode.NAME_DESC -> files.sortedByDescending { it.getName().lowercase() }
                    FileSortMode.SIZE_DESC -> files.sortedByDescending { (it as? DroidspacesFileObject)?.cachedLength ?: 0L }
                    FileSortMode.SIZE_ASC -> files.sortedBy { (it as? DroidspacesFileObject)?.cachedLength ?: 0L }
                    FileSortMode.DATE_DESC -> files.sortedByDescending { (it as? DroidspacesFileObject)?.cachedLength ?: 0L }
                    FileSortMode.DATE_ASC -> files.sortedBy { (it as? DroidspacesFileObject)?.cachedLength ?: 0L }
                }
                sortedDirs + sortedFiles
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
            } else if (processedItems.isEmpty()) {
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
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .nestedScroll(listNestedScrollConnection),
                ) {
                    items(
                        items = processedItems,
                        key = { it.getAbsolutePath() },
                    ) { item ->
                        val isSelected = selectedItems.contains(item)
                        ContainerFileRow(
                            fileObject = item,
                            isSelectMode = isSelectMode,
                            isSelected = isSelected,
                            onToggleSelect = {
                                selectedItems = if (isSelected) selectedItems - item else selectedItems + item
                            },
                            onClick = {
                                if (isSelectMode) {
                                    selectedItems = if (isSelected) selectedItems - item else selectedItems + item
                                } else if (item.isDirectory()) {
                                    val targetPath = if (item is DroidspacesFileObject) {
                                        item.containerPath
                                    } else {
                                        val raw = item.getAbsolutePath().substringAfter("://").substringAfter("/")
                                        if (raw.startsWith("/")) raw else "/$raw"
                                    }
                                    currentPath = targetPath
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
                            onOpenInTerminal = {
                                val path = if (item is DroidspacesFileObject) item.containerPath else item.getAbsolutePath().substringAfter("://").substringAfter("/")
                                val targetDir = if (item.isDirectory()) path else path.substringBeforeLast('/', "").ifEmpty { "/" }
                                openTerminalAtPath(targetDir)
                            },
                            onCopyPath = {
                                val path = if (item is DroidspacesFileObject) item.containerPath else item.getAbsolutePath()
                                copyToClipboard(path)
                            },
                            onCopy = {
                                val path = if (item is DroidspacesFileObject) item.containerPath else item.getAbsolutePath()
                                clipboard = ContainerClipboard(path = path, isCut = false)
                                toast("Copied to container clipboard")
                            },
                            onCut = {
                                val path = if (item is DroidspacesFileObject) item.containerPath else item.getAbsolutePath()
                                clipboard = ContainerClipboard(path = path, isCut = true)
                                toast("Cut to container clipboard")
                            },
                            onDuplicate = {
                                scope.launch {
                                    val path = if (item is DroidspacesFileObject) item.containerPath else item.getAbsolutePath()
                                    val clean = path.trimEnd('/')
                                    val parent = clean.substringBeforeLast('/', "").ifEmpty { "/" }
                                    val name = clean.substringAfterLast('/')
                                    val newName = if (name.contains('.')) {
                                        val base = name.substringBeforeLast('.')
                                        val ext = name.substringAfterLast('.')
                                        "${base}_copy.$ext"
                                    } else {
                                        "${name}_copy"
                                    }
                                    val dst = if (parent == "/") "/$newName" else "$parent/$newName"
                                    DroidspacesShell.copy(containerName, path, dst)
                                    loadDirectory(currentPath)
                                }
                            },
                            onChmod = {
                                selectedFileForAction = item
                                showChmodDialog = true
                            },
                            onChown = {
                                selectedFileForAction = item
                                showChownDialog = true
                            },
                            onProperties = {
                                selectedFileForAction = item
                                showPropertiesDialog = true
                            },
                            onRename = {
                                selectedFileForAction = item
                                showRenameDialog = true
                            },
                            onDelete = {
                                selectedFileForAction = item
                                showDeleteConfirmDialog = true
                            },
                        )
                    }
                }
            }
        }
    }
}
}

@Composable
private fun ContainerFileRow(
    fileObject: FileObject,
    isSelectMode: Boolean,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onClick: () -> Unit,
    onOpenAsDrawerTab: () -> Unit,
    onOpenInTerminal: () -> Unit,
    onCopyPath: () -> Unit,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onDuplicate: () -> Unit,
    onChmod: () -> Unit,
    onChown: () -> Unit,
    onProperties: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val isDir = fileObject.isDirectory()
    val cachedLen = if (fileObject is DroidspacesFileObject) fileObject.cachedLength else 0L
    // Use cached length immediately to avoid async height changes that cause scroll jitter
    val sizeText = remember(fileObject) {
        if (cachedLen > 0L) formatFileSize(cachedLen) else ""
    }

    Surface(
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (isSelectMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelect() },
                    modifier = Modifier.size(24.dp),
                )
            }

            FileIcon(file = fileObject)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fileObject.getName(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isDir) FontWeight.Medium else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Always render subtitle to keep row height stable and prevent scroll jitter
                Text(
                    text = if (isDir) "Directory" else sizeText.ifEmpty { "\u00A0" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
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
                            leadingIcon = { Icon(painter = painterResource(drawables.folder), null) },
                            onClick = { menuExpanded = false; onOpenAsDrawerTab() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(strings.open_in_terminal)) },
                            leadingIcon = { Icon(painter = painterResource(drawables.terminal), null) },
                            onClick = { menuExpanded = false; onOpenInTerminal() },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(strings.copy_path)) },
                        leadingIcon = { Icon(painter = painterResource(drawables.copy), null) },
                        onClick = { menuExpanded = false; onCopyPath() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(strings.copy)) },
                        leadingIcon = { Icon(painter = painterResource(drawables.copy), null) },
                        onClick = { menuExpanded = false; onCopy() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(strings.cut)) },
                        leadingIcon = { Icon(painter = painterResource(drawables.cut), null) },
                        onClick = { menuExpanded = false; onCut() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(strings.duplicate)) },
                        leadingIcon = { Icon(painter = painterResource(drawables.copy), null) },
                        onClick = { menuExpanded = false; onDuplicate() },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(strings.change_permissions)) },
                        leadingIcon = { Icon(painter = painterResource(drawables.lock), null) },
                        onClick = { menuExpanded = false; onChmod() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(strings.change_owner)) },
                        leadingIcon = { Icon(painter = painterResource(drawables.android), null) },
                        onClick = { menuExpanded = false; onChown() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(strings.file_properties)) },
                        leadingIcon = { Icon(Icons.Default.Info, null) },
                        onClick = { menuExpanded = false; onProperties() },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(strings.rename)) },
                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                        onClick = { menuExpanded = false; onRename() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(strings.delete), color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { menuExpanded = false; onDelete() },
                    )
                }
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}
