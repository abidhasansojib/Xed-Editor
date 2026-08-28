package com.rk.file

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Environment
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
import com.rk.droidspaces.DroidspacesConstants
import com.rk.droidspaces.FileSortMode
import com.rk.filetree.FileIcon
import com.rk.resources.drawables
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.utils.formatFileSize
import com.rk.utils.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class InternalClipboard(
    val file: File,
    val isCut: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InternalFileManagerSheet(
    initialPath: String? = null,
    onDismiss: () -> Unit,
    onOpenInDrawer: (FileObject) -> Unit,
) {
    val defaultRoot = remember { Environment.getExternalStorageDirectory().absolutePath }
    var currentPath by remember { mutableStateOf(initialPath?.ifBlank { defaultRoot } ?: defaultRoot) }
    var fileItems by remember { mutableStateOf<List<File>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var sortMode by remember { mutableStateOf(FileSortMode.NAME_ASC) }
    var showHiddenFiles by remember { mutableStateOf(false) }
    var isSelectMode by remember { mutableStateOf(false) }
    var selectedItems by remember { mutableStateOf<Set<File>>(emptySet()) }
    var clipboard by remember { mutableStateOf<InternalClipboard?>(null) }

    var showNewFileDialog by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showPropertiesDialog by remember { mutableStateOf(false) }
    var selectedFileForAction by remember { mutableStateOf<File?>(null) }

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
            val rel = clean
                .removePrefix(Environment.getExternalStorageDirectory().absolutePath)
                .removePrefix("/storage/emulated/0")
                .removePrefix("/sdcard")
                .removePrefix("/")
            val cdCmd = if (rel.isEmpty()) {
                "cd '$clean' 2>/dev/null || cd '/sdcard' 2>/dev/null || cd '/storage/emulated/0' 2>/dev/null || cd '$clean'"
            } else {
                "cd '$clean' 2>/dev/null || cd '/sdcard/$rel' 2>/dev/null || cd '/storage/emulated/0/$rel' 2>/dev/null || cd '$clean'"
            }
            val containerName = Settings.droidspaces_container_name.ifBlank { DroidspacesConstants.DEFAULT_CONTAINER_NAME }
            val intent = Intent().setClassName(context.packageName, "com.rk.activities.terminal.Terminal").apply {
                putExtra("initial_command", "$cdCmd && clear")
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
            val dir = File(path)
            val items = withContext(Dispatchers.IO) {
                if (dir.exists() && dir.isDirectory) {
                    dir.listFiles()?.toList() ?: emptyList()
                } else {
                    emptyList()
                }
            }
            fileItems = items
            selectedItems = emptySet()
        } catch (e: Exception) {
            errorMessage = e.localizedMessage ?: "Failed to list directory"
            fileItems = emptyList()
        } finally {
            isLoading = false
        }
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
        var newName by remember { mutableStateOf("NewFile") }
        SingleInputDialog(
            title = stringResource(strings.new_file),
            inputLabel = stringResource(strings.file_name),
            inputValue = newName,
            onInputValueChange = { newName = it },
            onConfirm = {
                val clean = newName.trim()
                if (clean.isNotBlank()) {
                    scope.launch {
                        val newFile = File(currentPath, clean)
                        val success = withContext(Dispatchers.IO) {
                            try {
                                if (newFile.exists()) false else newFile.createNewFile()
                            } catch (_: Exception) {
                                false
                            }
                        }
                        if (success) {
                            loadDirectory(currentPath)
                            toast("File created")
                        } else {
                            toast("Failed to create file")
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
                        val newFolder = File(currentPath, clean)
                        val success = withContext(Dispatchers.IO) {
                            try {
                                newFolder.mkdirs()
                            } catch (_: Exception) {
                                false
                            }
                        }
                        if (success) {
                            loadDirectory(currentPath)
                            toast("Folder created")
                        } else {
                            toast("Failed to create folder")
                        }
                    }
                }
            },
            onFinish = { showNewFolderDialog = false },
        )
    }

    // Rename Dialog
    if (showRenameDialog && selectedFileForAction != null) {
        val target = selectedFileForAction!!
        var renameName by remember { mutableStateOf(target.name) }
        SingleInputDialog(
            title = stringResource(strings.rename),
            inputLabel = stringResource(strings.name),
            inputValue = renameName,
            onInputValueChange = { renameName = it },
            onConfirm = {
                val clean = renameName.trim()
                if (clean.isNotBlank() && clean != target.name) {
                    scope.launch {
                        val parent = target.parentFile ?: File(currentPath)
                        val dstFile = File(parent, clean)
                        val success = withContext(Dispatchers.IO) {
                            try {
                                target.renameTo(dstFile)
                            } catch (_: Exception) {
                                false
                            }
                        }
                        if (success) {
                            loadDirectory(currentPath)
                            toast("Renamed")
                        } else {
                            toast("Failed to rename")
                        }
                    }
                }
            },
            onFinish = {
                showRenameDialog = false
                selectedFileForAction = null
            },
        )
    }

    // Single / Batch Delete Confirmation Dialog
    if (showDeleteConfirmDialog) {
        val count = if (isSelectMode && selectedItems.isNotEmpty()) selectedItems.size else 1
        val itemNameToDelete = selectedFileForAction?.name ?: "$count items"

        AlertDialog(
            onDismissRequest = {
                showDeleteConfirmDialog = false
                selectedFileForAction = null
            },
            title = { Text(stringResource(strings.delete)) },
            text = { Text("Are you sure you want to permanently delete \"$itemNameToDelete\"?") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        scope.launch {
                            val targets = if (isSelectMode && selectedItems.isNotEmpty()) {
                                selectedItems.toList()
                            } else {
                                listOfNotNull(selectedFileForAction)
                            }
                            withContext(Dispatchers.IO) {
                                targets.forEach { file ->
                                    try {
                                        if (file.isDirectory) file.deleteRecursively() else file.delete()
                                    } catch (_: Exception) {}
                                }
                            }
                            selectedItems = emptySet()
                            selectedFileForAction = null
                            loadDirectory(currentPath)
                            toast("Deleted")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(strings.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteConfirmDialog = false
                    selectedFileForAction = null
                }) {
                    Text(stringResource(strings.cancel))
                }
            },
        )
    }

    // File Properties Dialog
    if (showPropertiesDialog && selectedFileForAction != null) {
        val target = selectedFileForAction!!
        AlertDialog(
            onDismissRequest = {
                showPropertiesDialog = false
                selectedFileForAction = null
            },
            title = { Text(stringResource(strings.properties)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(text = "Name: ${target.name}", fontWeight = FontWeight.Bold)
                    Text(text = "Path: ${target.absolutePath}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    Text(text = "Type: ${if (target.isDirectory) "Directory" else "File"}", style = MaterialTheme.typography.bodySmall)
                    if (target.isFile) {
                        Text(text = "Size: ${formatFileSize(target.length())} (${target.length()} bytes)", style = MaterialTheme.typography.bodySmall)
                    }
                    val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(target.lastModified()))
                    Text(text = "Last Modified: $dateStr", style = MaterialTheme.typography.bodySmall)
                    Text(text = "Readable: ${target.canRead()}, Writable: ${target.canWrite()}, Executable: ${target.canExecute()}", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showPropertiesDialog = false
                    selectedFileForAction = null
                }) {
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
                                    text = stringResource(strings.internal_storage),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                )
                                Surface(
                                    shape = MaterialTheme.shapes.extraSmall,
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                ) {
                                    Text(
                                        text = "Device",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
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
                        IconButton(
                            onClick = {
                                isSelectMode = !isSelectMode
                                if (!isSelectMode) selectedItems = emptySet()
                            },
                        ) {
                            Icon(
                                imageVector = if (isSelectMode) Icons.Default.Close else Icons.Default.Check,
                                contentDescription = "Select Mode",
                                tint = if (isSelectMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                }

            Spacer(modifier = Modifier.height(6.dp))

            // Quick Jump Chips
            val extStorage = Environment.getExternalStorageDirectory().absolutePath
            val quickPaths = remember(extStorage) {
                listOf(
                    extStorage to "Internal (~)",
                    "$extStorage/Download" to "Download",
                    "$extStorage/Documents" to "Documents",
                    "$extStorage/DCIM" to "DCIM",
                    "$extStorage/Pictures" to "Pictures",
                    "$extStorage/Music" to "Music",
                    "$extStorage/Movies" to "Movies",
                    "$extStorage/Android" to "Android",
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                quickPaths.forEach { (dir, label) ->
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
                        onClick = { currentPath = defaultRoot },
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

                    // Breadcrumb path segments
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

            // Action Toolbar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Primary: Open in Workspace
                Button(
                    onClick = {
                        val dirObj = FileWrapper(File(currentPath))
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
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // + File Button
                FilledTonalButton(
                    onClick = { showNewFileDialog = true },
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("File", style = MaterialTheme.typography.labelMedium)
                }

                // + Folder Button
                FilledTonalButton(
                    onClick = { showNewFolderDialog = true },
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Folder", style = MaterialTheme.typography.labelMedium)
                }

                // Inline Paste Button (if clipboard active)
                clipboard?.let { clip ->
                    FilledTonalButton(
                        onClick = {
                            scope.launch {
                                val src = clip.file
                                val dst = File(currentPath, src.name)
                                val success = withContext(Dispatchers.IO) {
                                    try {
                                        if (clip.isCut) {
                                            src.renameTo(dst)
                                        } else {
                                            if (src.isDirectory) src.copyRecursively(dst, overwrite = true) else src.copyTo(dst, overwrite = true)
                                            true
                                        }
                                    } catch (_: Exception) {
                                        false
                                    }
                                }
                                if (success) {
                                    if (clip.isCut) clipboard = null
                                    loadDirectory(currentPath)
                                    toast("Pasted")
                                } else {
                                    toast("Failed to paste")
                                }
                            }
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    ) {
                        Icon(painter = painterResource(drawables.paste), contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(strings.paste), style = MaterialTheme.typography.labelMedium)
                    }
                }

                // Sort & Filter Dropdown Menu
                var sortMenuExpanded by remember { mutableStateOf(false) }
                Box {
                    IconButton(
                        onClick = { sortMenuExpanded = true },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(painter = painterResource(drawables.filter), contentDescription = "Sort", modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(
                        expanded = sortMenuExpanded,
                        onDismissRequest = { sortMenuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Name (A to Z)") },
                            trailingIcon = { if (sortMode == FileSortMode.NAME_ASC) Icon(Icons.Default.Check, null) },
                            onClick = { sortMode = FileSortMode.NAME_ASC; sortMenuExpanded = false },
                        )
                        DropdownMenuItem(
                            text = { Text("Name (Z to A)") },
                            trailingIcon = { if (sortMode == FileSortMode.NAME_DESC) Icon(Icons.Default.Check, null) },
                            onClick = { sortMode = FileSortMode.NAME_DESC; sortMenuExpanded = false },
                        )
                        DropdownMenuItem(
                            text = { Text("Size (Large to Small)") },
                            trailingIcon = { if (sortMode == FileSortMode.SIZE_DESC) Icon(Icons.Default.Check, null) },
                            onClick = { sortMode = FileSortMode.SIZE_DESC; sortMenuExpanded = false },
                        )
                        DropdownMenuItem(
                            text = { Text("Size (Small to Large)") },
                            trailingIcon = { if (sortMode == FileSortMode.SIZE_ASC) Icon(Icons.Default.Check, null) },
                            onClick = { sortMode = FileSortMode.SIZE_ASC; sortMenuExpanded = false },
                        )
                        DropdownMenuItem(
                            text = { Text("Date Modified (Newest)") },
                            trailingIcon = { if (sortMode == FileSortMode.DATE_DESC) Icon(Icons.Default.Check, null) },
                            onClick = { sortMode = FileSortMode.DATE_DESC; sortMenuExpanded = false },
                        )
                        DropdownMenuItem(
                            text = { Text("Date Modified (Oldest)") },
                            trailingIcon = { if (sortMode == FileSortMode.DATE_ASC) Icon(Icons.Default.Check, null) },
                            onClick = { sortMode = FileSortMode.DATE_ASC; sortMenuExpanded = false },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Show Hidden Files") },
                            trailingIcon = { if (showHiddenFiles) Icon(Icons.Default.Check, null) },
                            onClick = { showHiddenFiles = !showHiddenFiles; sortMenuExpanded = false },
                        )
                    }
                }

                // Multi-select toggle
                IconButton(
                    onClick = {
                        isSelectMode = !isSelectMode
                        if (!isSelectMode) selectedItems = emptySet()
                    },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = if (isSelectMode) Icons.Default.Check else Icons.Default.Edit,
                        contentDescription = "Select Mode",
                        tint = if (isSelectMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            // Multi-Select Batch Actions Bar
            AnimatedVisibility(visible = isSelectMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${selectedItems.size} selected",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = {
                                selectedItems = if (selectedItems.size == fileItems.size) emptySet() else fileItems.toSet()
                            },
                        ) {
                            Text(if (selectedItems.size == fileItems.size) "Deselect All" else "Select All")
                        }
                        if (selectedItems.isNotEmpty()) {
                            Button(
                                onClick = { showDeleteConfirmDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            ) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Delete (${selectedItems.size})")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search files & folders…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // File Listing
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
                    Text(text = errorMessage!!, color = MaterialTheme.colorScheme.error)
                }
            } else {
                val filteredFiles = remember(fileItems, searchQuery, showHiddenFiles, sortMode) {
                    var list = fileItems
                    if (!showHiddenFiles) {
                        list = list.filter { !it.name.startsWith(".") }
                    }
                    if (searchQuery.isNotBlank()) {
                        list = list.filter { it.name.contains(searchQuery, ignoreCase = true) }
                    }
                    val dirs = list.filter { it.isDirectory }
                    val files = list.filter { it.isFile }

                    val sortedDirs = when (sortMode) {
                        FileSortMode.NAME_ASC -> dirs.sortedBy { it.name.lowercase() }
                        FileSortMode.NAME_DESC -> dirs.sortedByDescending { it.name.lowercase() }
                        FileSortMode.SIZE_DESC -> dirs.sortedByDescending { it.length() }
                        FileSortMode.SIZE_ASC -> dirs.sortedBy { it.length() }
                        FileSortMode.DATE_DESC -> dirs.sortedByDescending { it.lastModified() }
                        FileSortMode.DATE_ASC -> dirs.sortedBy { it.lastModified() }
                    }
                    val sortedFiles = when (sortMode) {
                        FileSortMode.NAME_ASC -> files.sortedBy { it.name.lowercase() }
                        FileSortMode.NAME_DESC -> files.sortedByDescending { it.name.lowercase() }
                        FileSortMode.SIZE_DESC -> files.sortedByDescending { it.length() }
                        FileSortMode.SIZE_ASC -> files.sortedBy { it.length() }
                        FileSortMode.DATE_DESC -> files.sortedByDescending { it.lastModified() }
                        FileSortMode.DATE_ASC -> files.sortedBy { it.lastModified() }
                    }
                    sortedDirs + sortedFiles
                }

                if (filteredFiles.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank()) "No files match \"$searchQuery\"" else "Folder is empty",
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
                        items(filteredFiles, key = { it.absolutePath }) { item ->
                            val isSelected = selectedItems.contains(item)
                            val wrapper = remember(item) { FileWrapper(item) }

                            InternalFileRow(
                                file = item,
                                fileWrapper = wrapper,
                                isSelectMode = isSelectMode,
                                isSelected = isSelected,
                                onToggleSelect = {
                                    selectedItems = if (isSelected) selectedItems - item else selectedItems + item
                                },
                                onClick = {
                                    if (isSelectMode) {
                                        selectedItems = if (isSelected) selectedItems - item else selectedItems + item
                                    } else if (item.isDirectory) {
                                        currentPath = item.absolutePath
                                    } else {
                                        scope.launch {
                                            mainActivity?.viewModel?.editorManager?.openFile(
                                                fileObject = wrapper,
                                                switchToTab = true,
                                            )
                                            onDismiss()
                                        }
                                    }
                                },
                                onOpenAsDrawerTab = {
                                    onOpenInDrawer(wrapper)
                                    onDismiss()
                                },
                                onOpenInTerminal = {
                                    val targetDir = if (item.isDirectory) item.absolutePath else item.parentFile?.absolutePath ?: "/"
                                    openTerminalAtPath(targetDir)
                                },
                                onCopyPath = {
                                    copyToClipboard(item.absolutePath)
                                },
                                onCopy = {
                                    clipboard = InternalClipboard(file = item, isCut = false)
                                    toast("Copied to clipboard")
                                },
                                onCut = {
                                    clipboard = InternalClipboard(file = item, isCut = true)
                                    toast("Cut to clipboard")
                                },
                                onDuplicate = {
                                    scope.launch {
                                        val parent = item.parentFile ?: File(currentPath)
                                        val name = item.name
                                        val newName = if (name.contains('.')) {
                                            val base = name.substringBeforeLast('.')
                                            val ext = name.substringAfterLast('.')
                                            "${base}_copy.$ext"
                                        } else {
                                            "${name}_copy"
                                        }
                                        val dst = File(parent, newName)
                                        withContext(Dispatchers.IO) {
                                            if (item.isDirectory) item.copyRecursively(dst, overwrite = true) else item.copyTo(dst, overwrite = true)
                                        }
                                        loadDirectory(currentPath)
                                    }
                                },
                                onRename = {
                                    selectedFileForAction = item
                                    showRenameDialog = true
                                },
                                onProperties = {
                                    selectedFileForAction = item
                                    showPropertiesDialog = true
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
}

@Composable
fun InternalFileRow(
    file: File,
    fileWrapper: FileWrapper,
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
    onRename: () -> Unit,
    onProperties: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val isDir = file.isDirectory

    // Pre-compute subtitle text once per item to avoid repeated SimpleDateFormat allocations
    // and layout-shifting recompositions that cause scroll jitter
    val subText = remember(file.absolutePath, file.lastModified(), file.length()) {
        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(file.lastModified()))
        if (isDir) dateStr else "${formatFileSize(file.length())} • $dateStr"
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

            FileIcon(file = fileWrapper)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isDir) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                // Always render subtitle to keep row height stable and prevent scroll jitter
                Text(
                    text = subText.ifEmpty { "\u00A0" },
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
                        contentDescription = "More Options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        text = { Text("Duplicate") },
                        leadingIcon = { Icon(painter = painterResource(drawables.copy), null) },
                        onClick = { menuExpanded = false; onDuplicate() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(strings.rename)) },
                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                        onClick = { menuExpanded = false; onRename() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(strings.properties)) },
                        leadingIcon = { Icon(Icons.Default.Info, null) },
                        onClick = { menuExpanded = false; onProperties() },
                    )
                    HorizontalDivider()
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
