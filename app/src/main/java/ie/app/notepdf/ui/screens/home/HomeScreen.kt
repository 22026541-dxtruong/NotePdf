package ie.app.notepdf.ui.screens.home

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import ie.app.notepdf.R
import ie.app.notepdf.data.local.entity.Document
import ie.app.notepdf.data.local.entity.Folder
import kotlinx.coroutines.launch

enum class View {
    LIST, GRID
}

@Composable
fun HomeScreen(
    onSettingClick: () -> Unit = {},
    onPdfClick: (Document) -> Unit = {},
    @SuppressLint("ModifierParameter")
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    val uiState by viewModel.uiState.collectAsState()

    var view by remember { mutableStateOf(View.LIST) }
    var selectedFolder by remember { mutableStateOf<Folder?>(null) }
    var selectedDocument by remember { mutableStateOf<Document?>(null) }

    var openUploadFileDialog by remember { mutableStateOf(false) }
    var openRenameDialog by remember { mutableStateOf(false) }
    var openCreateFolderDialog by remember { mutableStateOf(false) }
    var showFolderSheet by remember { mutableStateOf(false) }

    var selectedPdfUri by remember { mutableStateOf<Uri?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            selectedPdfUri = uri
            openUploadFileDialog = true
        }
    }

    BackHandler {
        viewModel.goBackFolder()
    }

    if (openUploadFileDialog) {
        UploadFileDialog(
            currentFolder = uiState.folderStack.last().second,
            fileName = viewModel.getFileName(context, selectedPdfUri!!) ?: "Unnamed.pdf",
            thumbnail = viewModel.getPdfMetadata(context, selectedPdfUri!!).first,
            onDismissRequest = {
                openUploadFileDialog = false
                selectedPdfUri = null
            },
            onShowFolderSheet = {
                showFolderSheet = true
            },
            onConfirmation = {
                viewModel.uploadFile(context, selectedPdfUri!!, it)
            }
        )
    }

    if (openRenameDialog) {
        RenameDialog(
            initialName = selectedFolder?.name ?: selectedDocument?.name ?: "",
            onDismissRequest = {
                openRenameDialog = false
                selectedFolder = null
                selectedDocument = null
            },
            onConfirmation = { name ->
                when {
                    selectedFolder != null -> viewModel.editFolderName(selectedFolder!!.copy(name = name))
                    selectedDocument != null -> viewModel.editFileName(selectedDocument!!.copy(name = name))
                }
            }
        )
    }

    if (showFolderSheet) {
        FolderBottomSheet(
            folderStack = uiState.folderStack,
            isMove = selectedFolder != null || selectedDocument != null,
            view = view,
            onViewChange = { view = it },
            movingFolderId = selectedFolder?.id,
            folders = uiState.folderWithSub?.subFolders ?: emptyList(),
            jumpToFolder = viewModel::jumpToFolder,
            onDismiss = { showFolderSheet = false },
            onCancel = {
                showFolderSheet = false
                openUploadFileDialog = false
                selectedPdfUri = null
            },
            onOpenCreateFolderDialog = { openCreateFolderDialog = true },
            enterFolder = viewModel::enterFolder,
            onConfirmSelection = {
                when {
                    selectedFolder != null -> viewModel.moveFolder(selectedFolder!!.id, it)
                    selectedDocument != null -> viewModel.moveFile(selectedDocument!!.id, it)
                }
                selectedFolder = null
                selectedDocument = null
            }
        )
    }

    if (openCreateFolderDialog) {
        CreateFolderDialog(
            onDismissRequest = { openCreateFolderDialog = false },
            onConfirmation = { viewModel.createFolder(it) }
        )
    }

    Scaffold(
        topBar = {
            HomeTopBar(onSettingClick = onSettingClick)
        },
        floatingActionButton = {
            FabMenu(
                onUploadFile = { launcher.launch(arrayOf("application/pdf")) },
                onCreateFolder = { openCreateFolderDialog = true }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .padding(innerPadding)
                .padding(horizontal = 8.dp)
        ) {
            Breadcrumb(
                folderStack = uiState.folderStack,
                jumpToFolder = viewModel::jumpToFolder
            )
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = {  }
                ) {
                    Text("Tên")
                }
                Spacer(Modifier.weight(1f))
                SingleChoiceSegmentedButtonRow {
                    View.entries.forEachIndexed { index, otherView ->
                        SegmentedButton(
                            selected = view == otherView,
                            onClick = { view = otherView },
                            shape = SegmentedButtonDefaults.itemShape(index, View.entries.size)
                        ) {
                            Icon(
                                painter = painterResource(
                                    when (otherView) {
                                        View.GRID -> R.drawable.outline_grid_view_24
                                        View.LIST -> R.drawable.outline_list_24
                                    }
                                ),
                                contentDescription = "$otherView"
                            )
                        }
                    }
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                thickness = 1.dp,
            )
            AnimatedContent(
                targetState = view == View.GRID
            ) {
                when(it) {
                    true -> HomeGridLayout(
                        folderWithSub = uiState.folderWithSub,
                        enterFolder = viewModel::enterFolder,
                        deleteFolder = viewModel::deleteFolder,
                        editFolderName = {
                            selectedFolder = it
                            openRenameDialog = true
                        },
                        moveFolder = {
                            selectedFolder = it
                            showFolderSheet = true
                        },
                        deleteFile = viewModel::deleteFile,
                        editFileName = {
                            selectedDocument = it
                            openRenameDialog = true
                        },
                        openFile = onPdfClick,
                        moveFile = {
                            selectedDocument = it
                            showFolderSheet = true
                        }
                    )
                    false -> HomeLinearLayout(
                            folderWithSub = uiState.folderWithSub,
                            enterFolder = viewModel::enterFolder,
                            deleteFolder = viewModel::deleteFolder,
                            editFolderName = {
                                selectedFolder = it
                                openRenameDialog = true
                            },
                            moveFolder = {
                                selectedFolder = it
                                showFolderSheet = true
                            },
                            deleteFile = viewModel::deleteFile,
                            editFileName = {
                                selectedDocument = it
                                openRenameDialog = true
                            },
                            openFile = onPdfClick,
                            moveFile = {
                                selectedDocument = it
                                showFolderSheet = true
                            }
                        )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTopBar(
    modifier: Modifier = Modifier,
    onSettingClick: () -> Unit = {}
) {
    val searchBarState = rememberSearchBarState()
    val textFieldState = rememberTextFieldState()
    val coroutineScope = rememberCoroutineScope()

    val inputField = @Composable {
        SearchBarDefaults.InputField(
            textFieldState = textFieldState,
            searchBarState = searchBarState,
            textStyle = MaterialTheme.typography.bodyLarge,
            onSearch = { coroutineScope.launch { searchBarState.animateToCollapsed() } },
            placeholder = { Text("Tìm kiếm PDFs") },
            leadingIcon = {
                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            if (searchBarState.currentValue == SearchBarValue.Expanded)
                                searchBarState.animateToCollapsed()
                        }
                    }
                ) {
                    val iconPainter = if (searchBarState.currentValue == SearchBarValue.Expanded)
                        painterResource(R.drawable.outline_arrow_back_24)
                    else
                        painterResource(R.drawable.outline_document_search_24)

                    Icon(
                        painter = iconPainter,
                        contentDescription = if (searchBarState.currentValue == SearchBarValue.Expanded) "Back" else "Search"
                    )
                }
            },
            trailingIcon = {
                if (searchBarState.currentValue == SearchBarValue.Expanded) {
                    IconButton(
                        onClick = {
                            textFieldState.edit {
                                replace(0, length, "")
                            }
                        }
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.baseline_clear_24),
                            contentDescription = "Clear"
                        )
                    }
                }
            }
        )
    }

    CenterAlignedTopAppBar(
        title = {
            SearchBar(searchBarState, inputField)
        },
        actions = {
            IconButton(
                onClick = onSettingClick
            ) {
                Icon(
                    painter = painterResource(R.drawable.outline_settings_24),
                    contentDescription = "Setting"
                )
            }
        },
        modifier = modifier
    )
    ExpandedFullScreenSearchBar(
        state = searchBarState,
        inputField = inputField
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Green)
        )
    }
}

@Composable
fun RenameDialog(
    initialName: String = "",
    isFolder: Boolean = true,
    onDismissRequest: () -> Unit,
    onConfirmation: (String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = CardDefaults.shape,
            color = CardDefaults.cardColors().containerColor,
            contentColor = CardDefaults.cardColors().contentColor,
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val typeText = if (isFolder) "Thư mục" else "Tài liệu"
                Text(
                    text = "Đổi tên $typeText",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = MaterialTheme.typography.titleMedium.fontWeight,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                    },
                    label = { Text(if (isFolder) "Tên thư mục" else "Tên tài liệu") }
                )
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    TextButton(
                        onClick = { onDismissRequest() }
                    ) { Text(text = "Hủy") }
                    Button(
                        onClick = {
                            onConfirmation(name)
                            onDismissRequest()
                        },
                        enabled = name.isNotBlank()
                    ) { Text(text = "Lưu") }
                }
            }
        }
    }
}

@Composable
fun CreateFolderDialog(
    onDismissRequest: () -> Unit,
    onConfirmation: (String) -> Unit
) {
    var folderName by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = CardDefaults.shape,
            color = CardDefaults.cardColors().containerColor,
            contentColor = CardDefaults.cardColors().contentColor,
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Thư mục mới",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = MaterialTheme.typography.titleMedium.fontWeight,
                )
                OutlinedTextField(
                    value = folderName,
                    onValueChange = {
                        folderName = it
                    },
                    label = { Text("Tên thư mục") }
                )
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    TextButton(
                        onClick = { onDismissRequest() }
                    ) { Text(text = "Hủy") }
                    Button(
                        onClick = {
                            onConfirmation(folderName)
                            onDismissRequest()
                        },
                        enabled = folderName.isNotBlank()
                    ) { Text(text = "Tạo") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadFileDialog(
    currentFolder: String,
    fileName: String,
    thumbnail: Bitmap?,
    onShowFolderSheet: () -> Unit = {},
    onDismissRequest: () -> Unit,
    onConfirmation: (String) -> Unit
) {
    var fileName by remember { mutableStateOf(fileName) }
    var thumbnail by remember { mutableStateOf(thumbnail) }

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = CardDefaults.shape,
            color = CardDefaults.cardColors().containerColor,
            contentColor = CardDefaults.cardColors().contentColor,
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Tải lên NotePdf",
                    style = MaterialTheme.typography.titleMedium,
                )
                AsyncImage(
                    model = thumbnail,
                    contentDescription = "PDF Preview",
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Gray.copy(alpha = 0.1f)),
                    contentScale = ContentScale.Crop,
                    placeholder = painterResource(R.drawable.baseline_picture_as_pdf_24),
                    error = painterResource(R.drawable.baseline_picture_as_pdf_24)
                )
                OutlinedTextField(
                    value = fileName,
                    onValueChange = {
                        fileName = it
                    },
                    label = { Text("Tên tệp") },
                    singleLine = true,
                    leadingIcon = {
                        Image(
                            painter = painterResource(R.drawable.baseline_picture_as_pdf_24),
                            contentDescription = "PDF"
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = {
                            fileName = ".pdf"
                        }) {
                            Icon(
                                painter = painterResource(R.drawable.baseline_clear_24),
                                contentDescription = "Clear"
                            )
                        }
                    }
                )
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        readOnly = true,
                        enabled = false,
                        value = currentFolder,
                        onValueChange = { },
                        singleLine = true,
                        label = { Text("Vị trí") },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.outline_folder_24),
                                contentDescription = "Folder"
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth(),
                    )
                    Surface(
                        onClick = { onShowFolderSheet() },
                        modifier = Modifier
                            .matchParentSize()
                            .padding(top = 8.dp)
                            .clip(OutlinedTextFieldDefaults.shape),
                        color = Color.Transparent
                    ) {}
                }
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    TextButton(
                        onClick = { onDismissRequest() }
                    ) { Text(text = "Hủy") }
                    Button(
                        onClick = {
                            onConfirmation(fileName)
                            onDismissRequest()
                        },
                        enabled = fileName.isNotBlank() && fileName != ".pdf"
                    ) { Text(text = "Tải lên") }
                }
            }
        }
    }
}

@Composable
fun FabMenu(
    onUploadFile: () -> Unit = {},
    onCreateFolder: () -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(end = 16.dp, bottom = 16.dp)
    ) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ExtendedFloatingActionButton(
                    onClick = {
                        onUploadFile()
                        expanded = false
                    },
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp,
                        focusedElevation = 0.dp,
                        hoveredElevation = 0.dp
                    )
                ) {
                    Icon(
                        painter = painterResource(R.drawable.outline_upload_file_24),
                        contentDescription = "Upload File"
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(text = "Tải lên")
                }
                ExtendedFloatingActionButton(
                    onClick = {
                        onCreateFolder()
                        expanded = false
                    },
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp,
                        focusedElevation = 0.dp,
                        hoveredElevation = 0.dp
                    )
                ) {
                    Icon(
                        painter = painterResource(R.drawable.outline_create_new_folder_24),
                        contentDescription = "Create Folder"
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(text = "Tạo thư mục")
                }
            }
        }

        FloatingActionButton(onClick = { expanded = !expanded }) {
            Icon(
                painter = painterResource(if (expanded) R.drawable.baseline_clear_24 else R.drawable.outline_add_24),
                contentDescription = "Menu"
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderBottomSheet(
    folderStack: List<Pair<Long, String>>,
    folders: List<Folder>,
    isMove: Boolean = false,
    view: View = View.LIST,
    onViewChange: (View) -> Unit = {},
    movingFolderId: Long? = null,
    onDismiss: () -> Unit,
    onCancel: () -> Unit,
    jumpToFolder: (Long) -> Unit = {},
    onOpenCreateFolderDialog: () -> Unit = {},
    enterFolder: (Long, String) -> Unit,
    onConfirmSelection: (Long) -> Unit = { }
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )
    val coroutineScope = rememberCoroutineScope()

    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = Modifier.fillMaxSize()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Breadcrumb(
                folderStack,
                jumpToFolder,
                maxVisibleItems = 3,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onOpenCreateFolderDialog) {
                Icon(
                    painter = painterResource(R.drawable.outline_create_new_folder_24),
                    contentDescription = "Create Folder"
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(modifier = Modifier.weight(1f))
            SingleChoiceSegmentedButtonRow {
                View.entries.forEachIndexed { index, otherView ->
                    SegmentedButton(
                        selected = view == otherView,
                        onClick = { onViewChange(otherView) },
                        shape = SegmentedButtonDefaults.itemShape(index, View.entries.size)
                    ) {
                        Icon(
                            painter = painterResource(
                                when (otherView) {
                                    View.GRID -> R.drawable.outline_grid_view_24
                                    View.LIST -> R.drawable.outline_list_24
                                }
                            ),
                            contentDescription = "$otherView"
                        )
                    }
                }
            }
        }

        AnimatedContent(
            targetState = view == View.GRID,
            modifier = Modifier.weight(1f)
        ) {
            when(it) {
                false -> FolderListLinear(
                    folders = folders,
                    movingFolderId = movingFolderId,
                    enterFolder = enterFolder,
                )
                true -> FolderListGrid(
                    folders = folders,
                    movingFolderId = movingFolderId,
                    enterFolder = enterFolder
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.End,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            TextButton(
                onClick = {
                    coroutineScope.launch {
                        sheetState.hide()
                    }.invokeOnCompletion {
                        onCancel()
                    }
                }
            ) { Text(text = "Hủy") }
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Button(
                onClick = {
                    if (isMove) {
                        val currentFolder = folderStack.last()
                        onConfirmSelection(currentFolder.first)
                    }
                    coroutineScope.launch {
                        sheetState.hide()
                    }.invokeOnCompletion {
                        onDismiss()
                    }
                },
                enabled = movingFolderId != folderStack.last().first
            ) { Text(text = if (isMove) "Di chuyển" else "Chọn") }
        }
    }
}

@Composable
fun Breadcrumb(
    folderStack: List<Pair<Long, String>>,
    jumpToFolder: (Long) -> Unit = {},
    maxVisibleItems: Int = 4,
    maxWidthNameLength: Int = 80,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        val size = folderStack.size
        val displayStack: List<Pair<Long, String>>
        val hiddenFolderBeforeEllipsis: Pair<Long, String>?

        if (size <= maxVisibleItems) {
            displayStack = folderStack
            hiddenFolderBeforeEllipsis = null
        } else {
            val first = folderStack.first() // Home
            val lastItems = folderStack.takeLast(maxVisibleItems - 1)
            hiddenFolderBeforeEllipsis = folderStack[size - maxVisibleItems]
            displayStack =
                listOf(first.first to first.second) + listOf(hiddenFolderBeforeEllipsis.first to "...") + lastItems.drop(
                    1
                )
        }

        displayStack.forEachIndexed { index, (id, name) ->
            TextButton(
                onClick = {
                    if (name == "...") {
                        hiddenFolderBeforeEllipsis?.let { jumpToFolder(it.first) }
                    } else {
                        jumpToFolder(id)
                    }
                }
            ) {
                Text(
                    name,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    modifier = Modifier.widthIn(max = maxWidthNameLength.dp)
                )
            }
            if (index != displayStack.lastIndex) {
                Icon(
                    painter = painterResource(R.drawable.baseline_keyboard_arrow_right_24),
                    contentDescription = "Next",
                    tint = Color.Gray
                )
            }
        }
    }
}

