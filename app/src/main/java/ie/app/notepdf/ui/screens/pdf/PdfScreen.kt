package ie.app.notepdf.ui.screens.pdf

import android.graphics.Bitmap
import android.graphics.RectF
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import ie.app.notepdf.R
import ie.app.notepdf.data.local.entity.InkStroke
import ie.app.notepdf.data.local.entity.InkTypeConverters
import ie.app.notepdf.data.local.entity.NormalizedPoint
import ie.app.notepdf.data.local.entity.NoteBox
import ie.app.notepdf.data.local.entity.NoteText
import ie.app.notepdf.data.local.entity.NoteTextConverters
import ie.app.notepdf.data.local.entity.ToolType
import ie.app.notepdf.data.local.relation.NoteBoxAndNoteText
import ie.app.notepdf.ui.component.ToggleFloatingActionButton
import ie.app.notepdf.ui.component.ToggleFloatingActionButtonDefaults
import ie.app.notepdf.ui.component.ToggleFloatingActionButtonDefaults.animateIcon
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

data class GlobalSelectionState(
    val pageIndex: Int = -1,
    val selection: SelectionState = SelectionState()
)

data class SelectionState(
    val startIndex: Int = -1,
    val endIndex: Int = -1,
    val selectedWords: List<WordInfo> = emptyList()
)

@Composable
fun PdfScreen(
    documentId: Long,
    documentName: String,
    onBack: () -> Unit,
    viewModel: PdfViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboard.current

    val uiState by viewModel.uiState.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val allWords by viewModel.pageWords.collectAsState()
    val currentMatchIndex by viewModel.currentMatchIndex.collectAsState()
    val currentTool by viewModel.currentTool.collectAsState()
    val inkStrokes by viewModel.inkStrokes.collectAsState()
    val notes by viewModel.notes.collectAsState()

    // --- State cho Zoom & Pan ---
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // --- State Selection Toàn Cục ---
    var globalSelection by remember { mutableStateOf(GlobalSelectionState()) }

    // State theo dõi việc kéo Selection
    var isSelectionDragging by remember { mutableStateOf(false) }
    // State theo dõi việc CHẠM vào handle
    var isHandleTouched by remember { mutableStateOf(false) }

    // Vị trí ngón tay trên màn hình khi đang kéo
    var dragScreenPosition by remember { mutableStateOf<Offset?>(null) }

    // Lưu kích thước màn hình
    var layoutSize by remember { mutableStateOf(IntSize.Zero) }

    var boxLayoutCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    // --- States cho Dialog Ghi chú ---
    var showNoteBoxDialog by remember { mutableStateOf(false) }
    var showNoteListSheet by remember { mutableStateOf(false) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var capturedRect by remember { mutableStateOf<RectF?>(null) }

    var showNoteComment by remember { mutableStateOf(false) }
    var noteText by remember { mutableStateOf("") }
    var textRect by remember { mutableStateOf<List<RectF>>(emptyList()) }

    var notePageIndex by remember { mutableIntStateOf(-1) }

    var selectedNoteBox by remember { mutableStateOf<NoteBox?>(null) }
    var selectedNoteText by remember { mutableStateOf<NoteText?>(null) }

    var isActiveOfFab by remember { mutableStateOf(false) }
    var commentMode by remember { mutableStateOf(false) }
    var selectionTextMode by remember { mutableStateOf(false) }

    // State cho LazyColumn
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(documentId) {
        viewModel.loadDocument(documentId)
    }

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            globalSelection = GlobalSelectionState()
            isActiveOfFab = false
        }
    }

    LaunchedEffect(currentMatchIndex) {
        if (currentMatchIndex != -1 && searchResults.isNotEmpty()) {
            val match = searchResults[currentMatchIndex]
            listState.animateScrollToItem(match.pageIndex)
        }
    }

    // --- LOGIC AUTO-SCROLL / AUTO-PAN ---
    LaunchedEffect(isSelectionDragging, dragScreenPosition) {
        if (isSelectionDragging && dragScreenPosition != null) {
            val pos = dragScreenPosition!!
            val edgeZone = 150f
            val scrollSpeed = 15f

            while (isSelectionDragging) {
                var dx = 0f
                var dy = 0f

                if (scale > 1f) {
                    if (pos.x < edgeZone) dx = scrollSpeed
                    else if (pos.x > layoutSize.width - edgeZone) dx = -scrollSpeed
                }

                if (pos.y < edgeZone) dy = scrollSpeed
                else if (pos.y > layoutSize.height - edgeZone) dy = -scrollSpeed

                if (dx != 0f || dy != 0f) {
                    if (scale > 1f) {
                        val scaledWidth = layoutSize.width * scale
                        val scaledHeight = layoutSize.height * scale

                        val minX =
                            if (scaledWidth > layoutSize.width) layoutSize.width - scaledWidth else 0f
                        val maxX = 0f
                        val minY =
                            if (scaledHeight > layoutSize.height) layoutSize.height - scaledHeight else 0f
                        val maxY = 0f

                        val newOffset = offset + Offset(dx, dy)
                        offset = Offset(
                            x = newOffset.x.coerceIn(minX, maxX),
                            y = newOffset.y.coerceIn(minY, maxY)
                        )
                    } else {
                        listState.scrollBy(-dy)
                    }
                }
                delay(16)
            }
        }
    }

    if (showNoteBoxDialog && capturedBitmap != null && capturedRect != null) {
        NoteBoxDialog(
            bitmap = capturedBitmap!!,
            onDismiss = {
                showNoteBoxDialog = false
                capturedBitmap = null // Clear bitmap
            },
            onSave = { text ->
                viewModel.addNoteBox(notePageIndex, capturedRect!!, text)
                showNoteBoxDialog = false
                capturedBitmap = null
                // Chuyển về tool NONE sau khi lưu để người dùng có thể scroll tiếp
                viewModel.setTool(ToolType.NONE)
            }
        )
    }

    if (showNoteListSheet) {
        NoteListBottomSheet(
            notes = notes,
            currentPage = listState.firstVisibleItemIndex,
            onDismiss = { showNoteListSheet = false },
            onNoteBoxSelected = { noteBox ->
                scope.launch {
                    showNoteListSheet = false
                    selectedNoteBox = noteBox
                    listState.animateScrollToItem(noteBox.pageIndex)
                }
            },
            onDeleteNoteBox = { noteBox -> viewModel.deleteNoteBox(noteBox) },
            onUpdateNoteBox = { noteBox ->
                viewModel.updateNoteBox(noteBox)
            },
            onNoteTextSelected = { noteText ->
                scope.launch {
                    showNoteListSheet = false
                    selectedNoteText = noteText
                    listState.animateScrollToItem(noteText.pageIndex)
                }
            },
            onUpdateNoteText = { noteText ->
                viewModel.updateNoteText(noteText)
            },
            onDeleteNoteText = { noteText -> viewModel.deleteNoteText(noteText) },
            loadNoteBitmap = { noteBox ->
                viewModel.getNoteBitmap(context, noteBox)
            }
        )
    }

    Scaffold(
        topBar = {
            PdfTopBar(
                title = documentName,
                commentMode = commentMode,
                onCommentModeChanged = {
                    commentMode = !commentMode
                    if (commentMode) isActiveOfFab = true
                },
                selectionTextMode = selectionTextMode,
                onCopy = {
                    val text = globalSelection.selection.selectedWords.joinToString(" ") { it.text }
                    clipboardManager.nativeClipboard.text = AnnotatedString(text)
                    Toast.makeText(context, "Đã copy", Toast.LENGTH_SHORT).show()
                },
                onSelectAll = {
                    allWords[globalSelection.pageIndex]?.let {
                        if (it.isNotEmpty()) {
                            globalSelection = GlobalSelectionState(
                                globalSelection.pageIndex,
                                SelectionState(
                                    startIndex = 0,
                                    endIndex = it.lastIndex,
                                    selectedWords = it
                                )
                            )
                        }
                    }
                },
                onComment = {
                    val selectedRects =
                        globalSelection.selection.selectedWords.map { it.rect }
                    val mergedSelectionRects = mergeRectangles(selectedRects)
                    textRect = mergedSelectionRects
                    notePageIndex = globalSelection.pageIndex
                    noteText = globalSelection.selection.selectedWords.joinToString(" ") { it.text }
                    isActiveOfFab = false
                    showNoteComment = true
                },
                searchQuery = searchQuery,
                searchResults = searchResults,
                onSearchQueryChange = viewModel::onSearchQueryChange,
                clearSearch = viewModel::clearSearch,
                onBack = onBack,
                nextMatch = viewModel::nextMatch,
                prevMatch = viewModel::prevMatch,
                currentMatchIndex = currentMatchIndex,
                onShowNoteList = { showNoteListSheet = true },
                onToolSelected = { viewModel.setTool(ToolType.NONE) }
            )
        },
        floatingActionButton = {
            PdfFabMenu(
                isActiveOfFab && commentMode && !selectionTextMode,
                currentTool,
                onToolSelected = viewModel::setTool,
                onUndo = viewModel::undo,
                onRedo = viewModel::redo
            )
        }
    ) { innerPadding ->
        uiState.document?.let { document ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(Color.LightGray)
                    .onGloballyPositioned {
                        layoutSize = it.size
                        boxLayoutCoordinates = it
                    }
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { centroid, pan, zoom, _ ->
                                if (isSelectionDragging || isHandleTouched || currentTool != ToolType.NONE) return@detectTransformGestures

                                val oldScale = scale
                                val newScale = (scale * zoom).coerceIn(1f, 5f)

                                val zoomOffset = (centroid - offset) / oldScale
                                val rawNewOffset = centroid - (zoomOffset * newScale) + pan

                                val minX =
                                    if (layoutSize.width * newScale > layoutSize.width) layoutSize.width - layoutSize.width * newScale else 0f
                                val maxX = 0f
                                val minY =
                                    if (layoutSize.height * newScale > layoutSize.height) layoutSize.height - layoutSize.height * newScale else 0f
                                val maxY = 0f

                                val clampedOffset = Offset(
                                    x = rawNewOffset.x.coerceIn(minX, maxX),
                                    y = rawNewOffset.y.coerceIn(minY, maxY)
                                )

                                val overscrollY = rawNewOffset.y - clampedOffset.y

                                if (abs(overscrollY) > 0.5f && newScale > 1f) {
                                    scope.launch {
                                        listState.dispatchRawDelta(-overscrollY / newScale)
                                    }
                                }

                                scale = newScale
                                offset = clampedOffset
                            }
                        }
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                            transformOrigin = TransformOrigin(0f, 0f)
                        },
                    userScrollEnabled = currentTool == ToolType.NONE,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    items(document.pageCount, key = { it }) { index ->
                        val pageSelection = if (globalSelection.pageIndex == index) {
                            globalSelection.selection
                        } else {
                            SelectionState()
                        }
                        val pageMatches = searchResults.filter { it.pageIndex == index }
                        val focusedMatch =
                            if (currentMatchIndex != -1 && searchResults.isNotEmpty()) {
                                val match = searchResults[currentMatchIndex]
                                if (match.pageIndex == index) match else null
                            } else null
                        PdfPageItem(
                            index = index,
                            commentMode = commentMode,
                            viewModel = viewModel,
                            allWords = allWords,
                            currentScale = scale,
                            onSelectionTextModeChanged = { selectionTextMode = it },
                            selectionState = pageSelection,
                            pageMatches = pageMatches,
                            focusedMatch = focusedMatch,
                            currentTool = currentTool,
                            selectedNoteBox = selectedNoteBox,
                            selectedNoteText = selectedNoteText,
                            pageStrokes = inkStrokes[index] ?: emptyList(),
                            pageNotes = notes[index] ?: NoteBoxAndNoteText(),
                            onSelectionChanged = { newSelection ->
                                globalSelection = GlobalSelectionState(index, newSelection)
                            },
                            onSelectionDragStateChange = { isDragging ->
                                isSelectionDragging = isDragging
                                if (!isDragging) dragScreenPosition = null
                            },
                            onDragScreenPosition = { screenPos ->
                                dragScreenPosition = screenPos
                            },
                            onHandleTouch = { isTouched ->
                                isHandleTouched = isTouched
                            },
                            onTap = {
                                isActiveOfFab = if (showNoteComment) false else !isActiveOfFab
                                selectedNoteBox = null
                                selectedNoteText = null
                                selectionTextMode = false
                                showNoteComment = false
                            },
                            onDoubleTap = { windowTapPos ->
                                if (currentTool == ToolType.NONE) {
                                    boxLayoutCoordinates?.let { boxCoords ->
                                        val tapInBox = boxCoords.windowToLocal(windowTapPos)

                                        val targetScale = if (scale >= 5f) 1f else scale + 1f

                                        if (targetScale == 1f) {
                                            scale = 1f
                                            offset = Offset.Zero
                                        } else {
                                            // Tính toán offset mới để giữ điểm chạm (tapInBox) cố định
                                            // P_content = (P_screen - offset) / scale
                                            // newOffset = P_screen - (P_content * newScale)
                                            val contentPoint = (tapInBox - offset) / scale
                                            offset = tapInBox - (contentPoint * targetScale)
                                            scale = targetScale
                                        }
                                    }
                                }
                            },
                            onStrokeAdded = { points, color, width, type ->
                                viewModel.addStroke(index, points, color, width, type)
                            },
                            onStrokeRemoved = { stroke ->
                                viewModel.removeStroke(stroke)
                            },
                            onAreaSelected = { rect ->
                                if (rect.width() > 0 && rect.height() > 0) {
                                    scope.launch {
                                        val bitmap =
                                            viewModel.captureSelectionBitmap(context, index, rect)
                                        if (bitmap != null) {
                                            capturedBitmap = bitmap
                                            capturedRect = rect
                                            notePageIndex = index
                                            showNoteBoxDialog = true
                                        }
                                    }
                                }
                            }
                        )
                    }
                }

                PdfFastScroller(
                    listState = listState,
                    currentTool = currentTool,
                    pageCount = document.pageCount,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )

                if (showNoteComment) {
                    NoteTextComment(
                        onSave = {
                            viewModel.addNoteText(notePageIndex, textRect, noteText, it)
                            showNoteComment = false
                        },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfTopBar(
    title: String,
    commentMode: Boolean,
    onCommentModeChanged: () -> Unit,
    selectionTextMode: Boolean,
    onCopy: () -> Unit = {},
    onSelectAll: () -> Unit = {},
    onComment: () -> Unit = {},
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit = {},
    clearSearch: () -> Unit = {},
    currentMatchIndex: Int = -1,
    nextMatch: () -> Unit = {},
    prevMatch: () -> Unit = {},
    searchResults: List<SearchMatch> = emptyList(),
    onBack: () -> Unit = {},
    onShowNoteList: () -> Unit = {},
    onToolSelected: () -> Unit = {},
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    var isSearchVisible by remember { mutableStateOf(false) }
    val matchCount = searchResults.size

    TopAppBar(
        title = {
            AnimatedContent(isSearchVisible) {
                when (it) {
                    true -> TextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        textStyle = MaterialTheme.typography.bodyLarge,
                        placeholder = { Text("Tìm kiếm...") },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            keyboardController?.hide()
                            nextMatch()
                        })
                    )

                    false -> Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = {
                if (isSearchVisible) {
                    isSearchVisible = false
                    clearSearch()
                } else {
                    onBack()
                }
                onToolSelected()
            }) {
                Icon(
                    painter = painterResource(R.drawable.outline_arrow_back_24),
                    contentDescription = "Back"
                )
            }
        },
        actions = {
            if (isSearchVisible) {
                if (matchCount > 0) {
                    Text(
                        text = "${currentMatchIndex + 1}/$matchCount",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    IconButton(onClick = prevMatch) {
                        Icon(
                            painterResource(R.drawable.outline_keyboard_arrow_left_24),
                            contentDescription = "Prev"
                        )
                    }
                    IconButton(onClick = nextMatch) {
                        Icon(
                            painterResource(R.drawable.baseline_keyboard_arrow_right_24),
                            contentDescription = "Next"
                        )
                    }
                }

                IconButton(onClick = {
                    isSearchVisible = false
                    clearSearch()
                    onToolSelected()
                }) {
                    Icon(
                        painter = painterResource(R.drawable.baseline_clear_24),
                        contentDescription = "Close"
                    )
                }
            } else if (selectionTextMode) {
                IconButton(onClick = onCopy) {
                    Icon(
                        painter = painterResource(R.drawable.baseline_content_copy_24),
                        contentDescription = "Copy"
                    )
                }
                IconButton(onClick = onSelectAll) {
                    Icon(
                        painter = painterResource(R.drawable.baseline_select_all_24),
                        contentDescription = "Select All"
                    )
                }
                IconButton(onClick = onComment) {
                    Icon(
                        painter = painterResource(R.drawable.outline_add_comment_24),
                        contentDescription = "Comment"
                    )
                }
            } else {
                IconButton(onClick = {
                    onCommentModeChanged()
                    onToolSelected()
                }) {
                    Icon(
                        painter = painterResource(if (commentMode) R.drawable.outline_edit_off_24 else R.drawable.baseline_edit_document_24),
                        contentDescription = "Edit"
                    )
                }
                IconButton(onClick = {
                    onShowNoteList()
                    onToolSelected()
                }) {
                    Icon(
                        painter = painterResource(R.drawable.outline_comment_24),
                        contentDescription = "Comment"
                    )
                }
                IconButton(onClick = {
                    isSearchVisible = true
                }) {
                    Icon(
                        painterResource(R.drawable.outline_document_search_24),
                        contentDescription = "Search"
                    )
                }
            }
        }
    )
}

@Composable
fun PdfFabMenu(
    isActive: Boolean,
    currentTool: ToolType,
    onToolSelected: (ToolType) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit
) {
    var isMenuExpand by remember { mutableStateOf(false) }

    val alpha by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "FloatActionButtonAlpha"
    )

    Column(
        modifier = Modifier.alpha(alpha),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.End
    ) {
        FloatingActionButton(
            onClick = {
                onToolSelected(if (currentTool == ToolType.BOX_SELECT) ToolType.NONE else ToolType.BOX_SELECT)
            },
            containerColor = if (currentTool == ToolType.BOX_SELECT) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer
        ) {
            Icon(
                painter = painterResource(if (currentTool == ToolType.BOX_SELECT) R.drawable.outline_remove_selection_24 else R.drawable.outline_add_comment_24),
                contentDescription = "Add Comment"
            )
        }
        ToggleFloatingActionButton(
            checked = isMenuExpand,
            onCheckedChange = { isMenuExpand = !isMenuExpand },
            targetWidth = if (isMenuExpand) 328.dp else 56.dp,
            contentClickable = !isMenuExpand,
            containerColor = ToggleFloatingActionButtonDefaults.containerColor(
                initialColor = MaterialTheme.colorScheme.primaryContainer,
                finalColor = MaterialTheme.colorScheme.primaryContainer
            ),
            contentAlignment = Alignment.CenterEnd
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.padding(end = 4.dp)
            ) {
                AnimatedVisibility(
                    visible = isMenuExpand,
                    enter = fadeIn() + expandHorizontally(),
                    exit = fadeOut() + shrinkHorizontally(),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp)
                    ) {
                        IconButton(
                            onClick = { onToolSelected(ToolType.PEN) },
                            modifier = Modifier
                                .background(
                                    if (currentTool == ToolType.PEN) Color.White else Color.Transparent,
                                    CircleShape
                                )
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.outline_stylus_note_24),
                                contentDescription = "Draw"
                            )
                        }
                        IconButton(
                            onClick = { onToolSelected(ToolType.HIGHLIGHTER) },
                            modifier = Modifier
                                .background(
                                    if (currentTool == ToolType.HIGHLIGHTER) Color.White else Color.Transparent,
                                    CircleShape
                                )
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.outline_brush_24),
                                contentDescription = "Mark"
                            )
                        }
                        IconButton(
                            onClick = { onToolSelected(ToolType.ERASER) },
                            modifier = Modifier
                                .background(
                                    if (currentTool == ToolType.ERASER) Color.White else Color.Transparent,
                                    CircleShape
                                )
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.outline_ink_eraser_24),
                                contentDescription = "Eraser"
                            )
                        }
                        IconButton(onClick = onUndo) {
                            Icon(
                                painter = painterResource(R.drawable.outline_undo_24),
                                contentDescription = "Undo"
                            )
                        }
                        IconButton(onClick = onRedo) {
                            Icon(
                                painter = painterResource(R.drawable.outline_redo_24),
                                contentDescription = "Redo"
                            )
                        }
                    }
                }
                val imageVector by remember(checkedProgress) {
                    derivedStateOf {
                        if (checkedProgress > 0.5f) R.drawable.outline_visibility_24
                        else R.drawable.outline_draw_24
                    }
                }
                IconButton(
                    onClick = {
                        isMenuExpand = !isMenuExpand
                        onToolSelected(ToolType.NONE)
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            if (isMenuExpand) Color.White else Color.Transparent,
                            CircleShape
                        )
                ) {
                    Icon(
                        painter = painterResource(imageVector),
                        contentDescription = "Toggle Menu",
                        modifier = Modifier
                            .animateIcon(
                                { checkedProgress },
                                color = ToggleFloatingActionButtonDefaults.iconColor(
                                    initialColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    finalColor = Color.Black // Màu icon khi nền trắng
                                )
                            )
                    )
                }
            }
        }
    }
}

/**
 * Thanh cuộn nhanh
 */
@Composable
fun PdfFastScroller(
    listState: LazyListState,
    currentTool: ToolType,
    pageCount: Int,
    modifier: Modifier = Modifier
) {
    if (pageCount == 0) return

    val scope = rememberCoroutineScope()
    var isDragging by remember { mutableStateOf(false) }
    var isActive by remember { mutableStateOf(false) }

    // State lưu vị trí kéo hiện tại (để handle dính vào tay)
    var dragOffset by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(listState.isScrollInProgress, isDragging, currentTool) {
        if (listState.isScrollInProgress || isDragging || currentTool != ToolType.NONE) {
            isActive = true
        } else {
            delay(1500)
            isActive = false
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "ScrollbarAlpha"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .width(200.dp) // Vẫn giữ rộng để chứa bong bóng (bubble)
            .alpha(alpha)
    ) {
        val density = LocalDensity.current
        val trackHeight = maxHeight
        val trackHeightPx = with(density) { trackHeight.toPx() }
        val thumbHeightDp = 48.dp
        val thumbHeightPx = with(density) { thumbHeightDp.toPx() }

        // Tính vị trí Thumb
        val thumbOffsetPx by remember(
            trackHeightPx,
            thumbHeightPx,
            isDragging,
            dragOffset,
            listState
        ) {
            derivedStateOf {
                if (isDragging) {
                    val scrollRange = trackHeightPx - thumbHeightPx
                    dragOffset.coerceIn(0f, scrollRange)
                } else {
                    val firstVisibleIndex = listState.firstVisibleItemIndex
                    val thumbProgress =
                        firstVisibleIndex.toFloat() / (pageCount - 1).coerceAtLeast(1)
                    val scrollRange = trackHeightPx - thumbHeightPx
                    (thumbProgress * scrollRange).coerceIn(0f, scrollRange)
                }
            }
        }
        // Tay nắm (Thumb) & Bong bóng số trang - Phần Visual (Hiển thị)
        Box(
            modifier = Modifier
                .offset { IntOffset(0, thumbOffsetPx.roundToInt()) }
                .fillMaxWidth()
        ) {
            // 1. Bong bóng hiển thị số trang
            // Đã xóa điều kiện if (isDragging) để luôn hiển thị khi thanh cuộn active
            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 36.dp)
                    .background(Color(0xFF333333), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = run {
                        val scrollRange = trackHeightPx - thumbHeightPx
                        val progress = (thumbOffsetPx / scrollRange).coerceIn(0f, 1f)
                        val page = (progress * (pageCount - 1)).roundToInt() + 1
                        "$page / $pageCount"
                    },
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium
                )
            }

            // 2. Tay nắm (Thumb) - Gắn sự kiện kéo trực tiếp vào đây
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 8.dp)
                    // Gắn pointerInput vào chính cái Handle để chỉ nhận sự kiện khi chạm vào nó
                    .pointerInput(pageCount, trackHeightPx, thumbHeightPx) {
                        detectVerticalDragGestures(
                            onDragStart = {
                                isDragging = true
                                // Đồng bộ dragOffset với vị trí hiện tại của thumb để tránh bị giật
                                dragOffset = thumbOffsetPx
                            },
                            onDragEnd = { isDragging = false },
                            onDragCancel = { isDragging = false },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                dragOffset += dragAmount
                                val scrollRange = trackHeightPx - thumbHeightPx
                                val currentOffset = dragOffset.coerceIn(0f, scrollRange)
                                val progress = (currentOffset / scrollRange).coerceIn(0f, 1f)
                                val targetIndex = (progress * (pageCount - 1)).roundToInt()
                                scope.launch { listState.scrollToItem(targetIndex) }
                            }
                        )
                    }
            ) {
                Surface(
                    modifier = Modifier
                        .size(width = 24.dp, height = thumbHeightDp)
                        .shadow(elevation = 2.dp, shape = CircleShape),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(id = R.drawable.outline_unfold_more_24),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// ITEM PAGE
// -----------------------------------------------------------------------------

enum class DragHandle { Start, End }

@Composable
fun PdfPageItem(
    index: Int,
    commentMode: Boolean,
    onSelectionTextModeChanged: (Boolean) -> Unit,
    viewModel: PdfViewModel,
    allWords: Map<Int, List<WordInfo>>,
    currentScale: Float,
    selectionState: SelectionState,
    pageMatches: List<SearchMatch>,
    focusedMatch: SearchMatch?,
    currentTool: ToolType,
    selectedNoteBox: NoteBox?,
    selectedNoteText: NoteText?,
    pageStrokes: List<InkStroke>,
    pageNotes: NoteBoxAndNoteText,
    onSelectionChanged: (SelectionState) -> Unit,
    onSelectionDragStateChange: (Boolean) -> Unit,
    onDragScreenPosition: (Offset) -> Unit,
    onHandleTouch: (Boolean) -> Unit,
    onTap: () -> Unit = {},
    onDoubleTap: (Offset) -> Unit,
    onStrokeAdded: (List<NormalizedPoint>, Color, Float, ToolType) -> Unit,
    onStrokeRemoved: (InkStroke) -> Unit,
    onAreaSelected: (RectF) -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val wordsOfThisPage = allWords[index] ?: emptyList()

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var originalImageSize by remember { mutableStateOf<IntSize?>(null) }

    var currentDrawingPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var currentBoxSelection by remember { mutableStateOf<Rect?>(null) }

    // --- TÍNH TOÁN KÍCH THƯỚC CHUẨN KHI SCALE ---
    val baseHandleRadiusDp = 10.dp
    val baseHandleRadiusPx = with(density) { baseHandleRadiusDp.toPx() }
    val effectiveHandleRadiusPx = baseHandleRadiusPx / currentScale

    val baseTouchTargetSize = 48.dp
    val baseTouchTargetSizePx = with(density) { baseTouchTargetSize.toPx() }

    // Biến lưu tọa độ của trang
    var pageLayoutCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    LaunchedEffect(index) {
        if (bitmap == null) {
            bitmap = viewModel.loadPage(context, index)
            bitmap?.let { viewModel.extractTextFromPage(index, it) }
        }
    }

    val loadingModifier = if (originalImageSize == null) {
        Modifier.aspectRatio(1f / 1.414f)
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(loadingModifier)
            .onGloballyPositioned {
                viewSize = it.size
                pageLayoutCoordinates = it
            }
    ) {
        AsyncImage(
            model = bitmap,
            contentDescription = "Page $index",
            contentScale = ContentScale.FillWidth,
            placeholder = ColorPainter(Color.White),
            fallback = ColorPainter(Color.White),
            error = ColorPainter(Color(0xFFFFEBEE)),
            onSuccess = { state ->
                val img = state.result.image
                originalImageSize = IntSize(img.width, img.height)
            },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(currentTool, viewSize) {
                    if (currentTool == ToolType.NONE || viewSize.width == 0) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume() // CHẶN MỌI SỰ KIỆN KHÁC

                        val width = viewSize.width.toFloat()
                        val height = viewSize.height.toFloat()
                        fun Offset.clamp(): Offset {
                            return Offset(
                                x.coerceIn(0f, width),
                                y.coerceIn(0f, height)
                            )
                        }

                        val startPos = down.position.clamp()

                        when (currentTool) {
                            ToolType.BOX_SELECT -> {
                                var currentPoint = startPos
                                currentBoxSelection = Rect(startPos, startPos)

                                var dragging = true
                                while (dragging) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.find { it.id == down.id }
                                    if (change == null || !change.pressed) dragging = false
                                    else {
                                        change.consume()
                                        currentPoint = change.position.clamp()
                                        currentBoxSelection = Rect(
                                            left = minOf(startPos.x, currentPoint.x),
                                            top = minOf(startPos.y, currentPoint.y),
                                            right = maxOf(startPos.x, currentPoint.x),
                                            bottom = maxOf(startPos.y, currentPoint.y)
                                        )
                                    }
                                }
// Kết thúc kéo: Chuẩn hóa Rect và gọi callback lưu Note
                                currentBoxSelection?.let { finalRect ->
                                    if (viewSize.width > 0 && viewSize.height > 0) {
                                        val normalizedRect = RectF(
                                            finalRect.left / viewSize.width,
                                            finalRect.top / viewSize.height,
                                            finalRect.right / viewSize.width,
                                            finalRect.bottom / viewSize.height
                                        )
                                        onAreaSelected(normalizedRect)
                                    }
                                }
                                currentBoxSelection = null
                            }

                            ToolType.ERASER -> {
                                var dragging = true
                                while (dragging) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.find { it.id == down.id }
                                    if (change == null || !change.pressed) dragging = false
                                    else {
                                        change.consume()
                                        val pos = change.position
// Eraser logic
                                        val hitStroke = pageStrokes.find { stroke ->
                                            val points =
                                                InkTypeConverters().toPointsList(stroke.pointsJson)
                                            points.any { p ->
                                                val px = p.x * viewSize.width
                                                val py = p.y * viewSize.height
                                                hypot(px - pos.x, py - pos.y) < 50f // Hit threshold
                                            }
                                        }
                                        if (hitStroke != null) onStrokeRemoved(hitStroke)
                                    }
                                }
                            }

                            else -> {
// Pen/Highlighter Logic
                                currentDrawingPoints = listOf(startPos)
                                var dragging = true
                                while (dragging) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.find { it.id == down.id }
                                    if (change == null || !change.pressed) dragging = false
                                    else {
                                        change.consume()
                                        val newPos = change.position.clamp()
// Tránh thêm điểm trùng lặp nếu người dùng giữ tay ở mép
                                        if (currentDrawingPoints.isEmpty() || newPos != currentDrawingPoints.last()) {
                                            currentDrawingPoints = currentDrawingPoints + newPos
                                        }
                                    }
                                }
                                if (currentDrawingPoints.size > 1) {
                                    val normalizedPoints = currentDrawingPoints.map {
                                        NormalizedPoint(
                                            it.x / viewSize.width.toFloat(),
                                            it.y / viewSize.height.toFloat()
                                        )
                                    }
                                    val color =
                                        if (currentTool == ToolType.HIGHLIGHTER) Color.Yellow else Color.Red
                                    val width =
                                        if (currentTool == ToolType.HIGHLIGHTER) 0.03f else 0.005f
                                    onStrokeAdded(normalizedPoints, color, width, currentTool)
                                }
                                currentDrawingPoints = emptyList()
                            }
                        }
                    }
                }
                .pointerInput(wordsOfThisPage, viewSize, currentTool) {
                    if (viewSize.width == 0 || currentTool != ToolType.NONE) return@pointerInput
                    detectTapGestures(
                        onDoubleTap = { localOffset ->
                            val windowPos = pageLayoutCoordinates?.localToWindow(localOffset)
                            if (windowPos != null) {
                                onDoubleTap(windowPos)
                            }
                        },
                        onTap = {
                            onTap()
                            onSelectionChanged(SelectionState())
                            onSelectionDragStateChange(false)
                        },
                        onLongPress = { localOffset ->
                            val wordIndex = findWordIndexAt(localOffset, wordsOfThisPage, viewSize)
                            if (wordIndex != -1) {
                                onSelectionChanged(
                                    SelectionState(
                                        startIndex = wordIndex,
                                        endIndex = wordIndex,
                                        selectedWords = listOf(wordsOfThisPage[wordIndex])
                                    )
                                )
                                onSelectionTextModeChanged(true)
                            }
                        }
                    )
                }
                .pointerInput(wordsOfThisPage, viewSize, currentTool) {
                    if (viewSize.width == 0 || currentTool != ToolType.NONE) return@pointerInput
                    detectDragGesturesAfterLongPress(
                        onDragStart = { },
                        onDrag = { change, _ ->
                            change.consume()
                            val currentPos = change.position
                            val startIndex = selectionState.startIndex

                            if (startIndex != -1) {
                                val currentIndex =
                                    findWordIndexAt(currentPos, wordsOfThisPage, viewSize)
                                if (currentIndex != -1) {
                                    val start = minOf(startIndex, currentIndex)
                                    val end = maxOf(startIndex, currentIndex)
                                    if (start >= 0 && end < wordsOfThisPage.size) {
                                        onSelectionChanged(
                                            selectionState.copy(
                                                startIndex = start,
                                                endIndex = end,
                                                selectedWords = wordsOfThisPage.subList(
                                                    start,
                                                    end + 1
                                                )
                                            )
                                        )
                                    }
                                }
                            }
                        },
                        onDragEnd = {
                            if (selectionState.selectedWords.isNotEmpty()) {
                                onSelectionDragStateChange(true)
                            }
                        }
                    )
                }
                .drawWithCache {
                    // 1. CACHE STROKE PATHS (Tránh parse JSON liên tục)
                    val strokePaths = pageStrokes.map { stroke ->
                        val path = Path()
                        val points = InkTypeConverters().toPointsList(stroke.pointsJson)
                        if (points.isNotEmpty()) {
                            val start = points.first()
                            path.moveTo(start.x * size.width, start.y * size.height)
                            for (i in 1 until points.size) {
                                val p = points[i]
                                path.lineTo(p.x * size.width, p.y * size.height)
                            }
                        }
                        stroke to path
                    }

                    // 2. CACHE SELECTION RECTS
                    val viewSizeInt = IntSize(size.width.toInt(), size.height.toInt())
                    val selectedRects =
                        selectionState.selectedWords.map { it.rect }
                    val mergedSelectionRects = mergeRectangles(selectedRects)
                        .map { it.toScreenRect(viewSizeInt) }

                    // 3. CACHE SEARCH HIGHLIGHTS
                    val cachedSearchMatches = pageMatches.map { match ->
                        val isFocused = (focusedMatch != null && focusedMatch == match)
                        val color = if (isFocused) Color(0x99FF8800) else Color(0x66FFFF00)
                        val rects =
                            mergeRectangles(match.rects)
                                .map { it.toScreenRect(viewSizeInt) }
                        Triple(rects, color, isFocused)
                    }

                    // 4. PREPARE HANDLE DATA
                    val showHandles = selectionState.selectedWords.isNotEmpty()
                    val handleData = if (showHandles) {
                        val handleColor = Color(0xFF4285F4)
                        val lineStroke = 2.dp.toPx() / currentScale

                        val firstRect =
                            selectionState.selectedWords.first().rect.toScreenRect(viewSizeInt)
                        val lastRect =
                            selectionState.selectedWords.last().rect.toScreenRect(viewSizeInt)

                        Triple(firstRect, lastRect, object {
                            val color = handleColor
                            val stroke = lineStroke
                            val radius = effectiveHandleRadiusPx
                        })
                    } else null

                    onDrawWithContent {
                        drawContent() // Vẽ ảnh nền

                        if (commentMode) {
                            pageNotes.noteBoxs.forEach { noteBox ->
                                val noteRect =
                                    RectF(
                                        noteBox.x,
                                        noteBox.y,
                                        noteBox.x + noteBox.width,
                                        noteBox.y + noteBox.height
                                    )
                                val screenRect = noteRect.toScreenRect(
                                    IntSize(
                                        size.width.toInt(),
                                        size.height.toInt()
                                    )
                                )
                                drawRect(
                                    color = if (noteBox.id == selectedNoteBox?.id) Color.Blue.copy(
                                        alpha = 0.4f
                                    ) else Color.Gray.copy(
                                        alpha = 0.5f
                                    ),
                                    topLeft = screenRect.topLeft,
                                    size = screenRect.size,
                                    style = Stroke(width = 1.dp.toPx() / currentScale)
                                )
                            }
                            pageNotes.noteTexts.forEach { noteText ->
                                val rects = NoteTextConverters().toRectList(noteText.pointsJson)
                                rects.forEach { r ->
                                    val noteRect = RectF(r.left, r.top, r.right, r.bottom)
                                    val screenRect = noteRect.toScreenRect(viewSizeInt)
                                    drawRect(
                                        color = Color.Green.copy(alpha = 0.5f),
                                        topLeft = screenRect.topLeft,
                                        size = screenRect.size,
                                        style = if (noteText.id != selectedNoteText?.id) Fill else Stroke(
                                            width = 1.dp.toPx() / currentScale
                                        )
                                    )
                                }
                            }
                            // Vẽ nét mực cũ (từ cache)
                            strokePaths.forEach { (stroke, path) ->
                                val color = Color(stroke.color).copy(alpha = stroke.alpha)
                                drawPath(
                                    path = path,
                                    color = color,
                                    style = Stroke(
                                        width = stroke.strokeWidth * size.width,
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }

                            // Vẽ nét đang vẽ (không cache)
                            if (currentDrawingPoints.isNotEmpty()) {
                                val path = Path()
                                path.moveTo(
                                    currentDrawingPoints.first().x,
                                    currentDrawingPoints.first().y
                                )
                                for (i in 1 until currentDrawingPoints.size) {
                                    path.lineTo(
                                        currentDrawingPoints[i].x,
                                        currentDrawingPoints[i].y
                                    )
                                }
                                val color =
                                    if (currentTool == ToolType.HIGHLIGHTER) Color.Yellow.copy(alpha = 0.4f) else Color.Red
                                val width =
                                    if (currentTool == ToolType.HIGHLIGHTER) 0.03f * size.width else 0.005f * size.width
                                drawPath(
                                    path = path,
                                    color = color,
                                    style = Stroke(
                                        width = width,
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }

                            if (currentTool == ToolType.BOX_SELECT) {
                                val dimColor = Color.Black.copy(alpha = 0.4f)

                                // 1. Nền tối toàn màn hình
                                val outerPath = Path().apply {
                                    addRect(Rect(0f, 0f, size.width, size.height))
                                }

                                // 2. Các vùng cần làm sáng (Lỗ thủng)
                                val holesPath = Path()

                                // Vùng đang kéo
                                currentBoxSelection?.let { selection ->
                                    holesPath.addRect(selection)
                                }

                                // Các ghi chú đã lưu
                                pageNotes.noteBoxs.forEach { noteBox ->
                                    val noteRect =
                                        RectF(
                                            noteBox.x,
                                            noteBox.y,
                                            noteBox.x + noteBox.width,
                                            noteBox.y + noteBox.height
                                        )
                                    holesPath.addRect(noteRect.toScreenRect(viewSizeInt))
                                }

                                // 3. Trừ vùng lỗ khỏi nền tối (Sử dụng Path Operation Difference thay vì EvenOdd)
                                // Điều này đảm bảo các lỗ hổng chồng nhau vẫn là lỗ hổng (Hole OR Hole = Hole)
                                // thay vì bị lấp đầy lại (Hole XOR Hole = Fill) như EvenOdd.
                                outerPath.op(outerPath, holesPath, PathOperation.Difference)

                                drawPath(path = outerPath, color = dimColor)

                                currentBoxSelection?.let { rect ->
                                    drawRect(
                                        color = Color(0xFF4285F4),
                                        topLeft = rect.topLeft,
                                        size = rect.size,
                                        style = Stroke(
                                            width = 2.dp.toPx() / currentScale,
                                            pathEffect = PathEffect.dashPathEffect(
                                                floatArrayOf(
                                                    20f,
                                                    20f
                                                ), 0f
                                            )
                                        )
                                    )
                                }
                            }
                        }
                        // Vẽ Selection & Search & Handles (chỉ khi không dùng tool vẽ)
                        if (currentTool == ToolType.NONE) {
                            // Highlight
                            mergedSelectionRects.forEach { rect ->
                                drawRect(
                                    color = Color(0x664285F4),
                                    topLeft = rect.topLeft,
                                    size = rect.size
                                )
                            }

                            pageNotes.noteTexts.forEach { note ->
                                val rects = NoteTextConverters().toRectList(note.pointsJson)
                                rects.forEach { r ->
                                    val noteRect = RectF(r.left, r.top, r.right, r.bottom)
                                    val screenRect = noteRect.toScreenRect(viewSizeInt)
                                    drawRect(
                                        color = Color(0x44FFFF00),
                                        topLeft = screenRect.topLeft,
                                        size = screenRect.size
                                    )
                                }
                            }
                            // Handles
                            handleData?.let { (first, last, style) ->
                                val startHandleCenter = first.bottomLeft + Offset(0f, style.radius)
                                drawLine(
                                    color = style.color,
                                    start = first.topLeft,
                                    end = first.bottomLeft,
                                    strokeWidth = style.stroke
                                )
                                drawCircle(
                                    color = style.color,
                                    radius = style.radius,
                                    center = startHandleCenter
                                )
                                val endHandleCenter = last.bottomRight + Offset(0f, style.radius)
                                drawLine(
                                    color = style.color,
                                    start = last.topRight,
                                    end = last.bottomRight,
                                    strokeWidth = style.stroke
                                )
                                drawCircle(
                                    color = style.color,
                                    radius = style.radius,
                                    center = endHandleCenter
                                )
                            }
                        }

                        // Search Highlight
                        cachedSearchMatches.forEach { (rects, color, _) ->
                            rects.forEach { rect ->
                                drawRect(
                                    color = color,
                                    topLeft = rect.topLeft,
                                    size = rect.size
                                )
                            }
                        }
                    }
                }
        )
        // 2. Handle Touch Targets
        if (currentTool == ToolType.NONE && selectionState.selectedWords.isNotEmpty() && pageLayoutCoordinates != null) {
            val firstRect = selectionState.selectedWords.first().rect.toScreenRect(viewSize)
            val lastRect = selectionState.selectedWords.last().rect.toScreenRect(viewSize)

            val startHandlePos = firstRect.bottomLeft + Offset(0f, effectiveHandleRadiusPx)
            HandleTouchTarget(
                centerPos = startHandlePos,
                touchSize = baseTouchTargetSize,
                touchSizePx = baseTouchTargetSizePx,
                handleType = DragHandle.Start,
                words = wordsOfThisPage,
                viewSize = viewSize,
                handleRadiusPx = effectiveHandleRadiusPx,
                currentSelection = selectionState,
                onSelectionUpdate = onSelectionChanged,
                onDragStateChange = onSelectionDragStateChange,
                onDragScreenPosition = onDragScreenPosition,
                onHandleTouch = onHandleTouch,
                pageLayoutCoordinates = pageLayoutCoordinates!!
            )

            val endHandlePos = lastRect.bottomRight + Offset(0f, effectiveHandleRadiusPx)
            HandleTouchTarget(
                centerPos = endHandlePos,
                touchSize = baseTouchTargetSize,
                touchSizePx = baseTouchTargetSizePx,
                handleType = DragHandle.End,
                words = wordsOfThisPage,
                viewSize = viewSize,
                handleRadiusPx = effectiveHandleRadiusPx,
                currentSelection = selectionState,
                onSelectionUpdate = onSelectionChanged,
                onDragStateChange = onSelectionDragStateChange,
                onDragScreenPosition = onDragScreenPosition,
                onHandleTouch = onHandleTouch,
                pageLayoutCoordinates = pageLayoutCoordinates!!
            )
        }
    }
}

@Composable
fun NoteBoxDialog(
    bitmap: Bitmap,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Thêm ghi chú") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AsyncImage(
                    model = bitmap,
                    contentDescription = "Selected Area",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp) // Giới hạn chiều cao
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.LightGray),
                    contentScale = ContentScale.Fit
                )

                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Nội dung ghi chú") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(text) },
                enabled = text.isNotBlank()
            ) {
                Text("Lưu")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy")
            }
        }
    )
}

@Composable
fun NoteTextComment(
    onSave: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var commentText by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(100)
        focusRequester.requestFocus()
    }
    OutlinedTextField(
        value = commentText,
        onValueChange = { commentText = it },
        label = { Text("Ghi chú") },
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 16.dp)
            .focusRequester(focusRequester),
        singleLine = false,
        maxLines = 5,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(
            onDone = {
                if (commentText.isNotBlank()) {
                    onSave(commentText)
                }
            }
        )
    )

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteListBottomSheet(
    notes: Map<Int, NoteBoxAndNoteText>,
    currentPage: Int,
    loadNoteBitmap: suspend (NoteBox) -> Bitmap?,
    onNoteBoxSelected: (NoteBox) -> Unit,
    onDeleteNoteBox: (NoteBox) -> Unit,
    onUpdateNoteBox: (NoteBox) -> Unit,
    onNoteTextSelected: (NoteText) -> Unit,
    onDeleteNoteText: (NoteText) -> Unit,
    onUpdateNoteText: (NoteText) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val noteListState = rememberLazyListState()

    // Tự động cuộn đến vị trí gần trang hiện tại nhất
    LaunchedEffect(Unit) {
        if (notes.isNotEmpty()) {
            var scrollIndex = 0
            var found = false
            // Duyệt qua danh sách ĐÃ SẮP XẾP
            for ((pageIndex, notesOfPage) in notes) {
                if (pageIndex >= currentPage) {
                    found = true
                    break
                }
                // Header (1 item) + số lượng note trong trang
                scrollIndex += 1 + notesOfPage.noteTexts.size + notesOfPage.noteBoxs.size
            }
            // Nếu tìm thấy, cuộn đến scrollIndex (Header của trang đó).
            // Nếu không (found=false), scrollIndex sẽ nằm ở cuối list -> cuộn xuống cuối (item cuối cùng).
            if (scrollIndex >= 0) {
                val target = if (found) scrollIndex else (scrollIndex - 1).coerceAtLeast(0)
                noteListState.animateScrollToItem(target)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        if (notes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Chưa có ghi chú nào")
            }
        } else {
            LazyColumn(
                state = noteListState,
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                notes.toSortedMap().forEach { (pageIndex, notesOfPage) ->
                    stickyHeader(
                        key = pageIndex,
                        contentType = { "page_${pageIndex}" }
                    ) {
                        Text(
                            text = "Trang ${pageIndex + 1}",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(4.dp)
                                .animateItem()
                        )
                    }
                    items(notesOfPage.noteBoxs, key = { "box_${it.id}" }) { note ->
                        NoteBoxItem(
                            sheetState = sheetState,
                            noteBox = note,
                            loadBitmap = { loadNoteBitmap(note) },
                            onClick = { onNoteBoxSelected(note) },
                            onDelete = { onDeleteNoteBox(note) },
                            onEdit = { onUpdateNoteBox(it) },
                            modifier = Modifier.animateItem()
                        )
                    }
                    items(notesOfPage.noteTexts, key = { "text_${it.id}" }) { note ->
                        NoteTextItem(
                            sheetState = sheetState,
                            noteText = note,
                            onClick = { onNoteTextSelected(note) },
                            onDelete = { onDeleteNoteText(note) },
                            onEdit = { onUpdateNoteText(it) },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }
    }
}

@OptIn(FlowPreview::class, ExperimentalMaterial3Api::class)
@Composable
fun NoteBoxItem(
    sheetState: SheetState,
    noteBox: NoteBox,
    loadBitmap: suspend () -> Bitmap?,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onEdit: (NoteBox) -> Unit,
    modifier: Modifier = Modifier
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var text by remember { mutableStateOf(noteBox.text) }
    var isFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    val alpha by animateFloatAsState(
        targetValue = if (isFocused) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "SaveAlpha"
    )

    LaunchedEffect(isFocused) {
        if (isFocused) sheetState.expand()
    }

    LaunchedEffect(sheetState.currentValue) {
        if (sheetState.currentValue == SheetValue.PartiallyExpanded ||
            sheetState.currentValue == SheetValue.Hidden) {
            focusManager.clearFocus()
        }
    }

    LaunchedEffect(noteBox.id) {
        if (bitmap == null) bitmap = loadBitmap()
    }

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.baseline_sticky_note_2_24),
                    contentDescription = "Note",
                )
                Text(
                    text = noteBox.createdAt,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = {
                        onEdit(noteBox.copy(text = text))
                        focusManager.clearFocus()
                    },
                    enabled = text.isNotBlank(),
                    modifier = Modifier.alpha(alpha)
                ) {
                    Text("Lưu")
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        painter = painterResource(R.drawable.outline_delete_24),
                        contentDescription = "Delete",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            AsyncImage(
                model = bitmap,
                placeholder = painterResource(R.drawable.baseline_image_24),
                contentDescription = "Note Image",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.LightGray)
            )

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Nội dung ghi chú") },
                minLines = 2,
                maxLines = 5,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { isFocused = it.isFocused },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteTextItem(
    sheetState: SheetState,
    noteText: NoteText,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onEdit: (NoteText) -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf(noteText.comment) }
    var isFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    val alpha by animateFloatAsState(
        targetValue = if (isFocused) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "SaveAlpha"
    )

    LaunchedEffect(isFocused) {
        if (isFocused) sheetState.expand()
    }

    LaunchedEffect(sheetState.currentValue) {
        if (sheetState.currentValue == SheetValue.PartiallyExpanded ||
            sheetState.currentValue == SheetValue.Hidden) {
            focusManager.clearFocus()
        }
    }

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.baseline_sticky_note_2_24),
                    contentDescription = "Note",
                )
                Text(
                    text = noteText.createdAt,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = {
                        onEdit(noteText.copy(comment = text))
                        focusManager.clearFocus()
                    },
                    enabled = text.isNotBlank(),
                    modifier = Modifier.alpha(alpha)
                ) {
                    Text("Lưu")
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        painter = painterResource(R.drawable.outline_delete_24),
                        contentDescription = "Delete",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Text(
                text = noteText.text,
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                minLines = 2,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.LightGray)
                    .padding(8.dp)
            )

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Nội dung ghi chú") },
                minLines = 2,
                maxLines = 5,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { isFocused = it.isFocused },
            )
        }
    }
}

@Composable
fun HandleTouchTarget(
    centerPos: Offset,
    touchSize: androidx.compose.ui.unit.Dp,
    touchSizePx: Float,
    handleType: DragHandle,
    words: List<WordInfo>,
    viewSize: IntSize,
    handleRadiusPx: Float,
    currentSelection: SelectionState,
    onSelectionUpdate: (SelectionState) -> Unit,
    onDragStateChange: (Boolean) -> Unit,
    onDragScreenPosition: (Offset) -> Unit,
    onHandleTouch: (Boolean) -> Unit,
    pageLayoutCoordinates: LayoutCoordinates
) {
    val density = LocalDensity.current
    val offsetX = with(density) { centerPos.x.toDp() - (touchSize / 2) }
    val offsetY = with(density) { centerPos.y.toDp() - (touchSize / 2) }

    val currentSelectionState by rememberUpdatedState(currentSelection)

    // Lưu tọa độ layout của Handle để dùng cho việc map tọa độ
    var handleLayoutCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    Box(
        modifier = Modifier
            .offset(x = offsetX, y = offsetY)
            .size(touchSize)
            .onGloballyPositioned { coordinates ->
                handleLayoutCoordinates = coordinates
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onHandleTouch(true)
                        tryAwaitRelease()
                        onHandleTouch(false)
                    }
                )
            }
            .pointerInput(handleType) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    onHandleTouch(true)
                    onDragStateChange(true)

                    val centerOfBox = Offset(touchSizePx / 2, touchSizePx / 2)
                    // Offset giữa ngón tay và tâm handle (trong hệ tọa độ handle)
                    // Vì Handle và Page chỉ lệch nhau bởi phép tịnh tiến, vector này có thể dùng chung
                    val touchOffset = down.position - centerOfBox

                    var dragging = true
                    while (dragging) {
                        val event = awaitPointerEvent()
                        val change = event.changes.find { it.id == down.id }

                        if (change == null || !change.pressed) {
                            dragging = false
                        } else {
                            if (change.positionChange() != Offset.Zero) {
                                change.consume()

                                val handleCoords = handleLayoutCoordinates
                                if (handleCoords != null && pageLayoutCoordinates.isAttached && handleCoords.isAttached) {
                                    // 1. Tính tọa độ màn hình cho Auto-Scroll
                                    val screenTouchPos = handleCoords.localToWindow(change.position)
                                    onDragScreenPosition(screenTouchPos)

                                    // 2. Chuyển tọa độ chạm từ Handle Space -> Page Space
                                    // Hàm localPositionOf sẽ tự động tính toán phép biến đổi (kể cả scale nếu có)
                                    val pageTouchPos = pageLayoutCoordinates.localPositionOf(
                                        handleCoords,
                                        change.position
                                    )

                                    // 3. Tính vị trí handle mong muốn
                                    val targetHandleCenter = pageTouchPos - touchOffset
                                    val searchPos = targetHandleCenter - Offset(0f, handleRadiusPx)

                                    val newWordIndex =
                                        findClosestWordIndex(searchPos, words, viewSize)

                                    if (newWordIndex != -1) {
                                        var newStart = currentSelectionState.startIndex
                                        var newEnd = currentSelectionState.endIndex

                                        if (handleType == DragHandle.Start) {
                                            newStart = newWordIndex
                                        } else {
                                            newEnd = newWordIndex
                                        }

                                        val finalStart = minOf(newStart, newEnd)
                                        val finalEnd = maxOf(newStart, newEnd)

                                        if (finalStart != currentSelectionState.startIndex || finalEnd != currentSelectionState.endIndex) {
                                            onSelectionUpdate(
                                                currentSelectionState.copy(
                                                    startIndex = finalStart,
                                                    endIndex = finalEnd,
                                                    selectedWords = words.subList(
                                                        finalStart,
                                                        finalEnd + 1
                                                    )
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    onHandleTouch(false)
                    onDragStateChange(false)
                }
            }
    )
}

fun RectF.toScreenRect(viewSize: IntSize): Rect {
    return Rect(
        left = this.left * viewSize.width,
        top = this.top * viewSize.height,
        right = this.right * viewSize.width,
        bottom = this.bottom * viewSize.height
    )
}

fun mergeRectangles(rects: List<RectF>): List<RectF> {
    if (rects.isEmpty()) return emptyList()

    val sorted = rects.sortedWith(compareBy({ it.top }, { it.left }))
    val merged = mutableListOf<RectF>()
    var current = sorted[0]

    for (i in 1 until sorted.size) {
        val next = sorted[i]

        // Kiểm tra xem có cùng dòng không dựa trên độ lệch tâm Y
        val centerYDiff = abs(current.centerY() - next.centerY())
        val avgHeight = (current.height() + next.height()) / 2

        if (centerYDiff < avgHeight * 0.5f) {
            // Cùng dòng -> Gộp lại (bao gồm cả khoảng trắng ở giữa)
            current = RectF(
                minOf(current.left, next.left),
                minOf(current.top, next.top),
                maxOf(current.right, next.right),
                maxOf(current.bottom, next.bottom)
            )
        } else {
            // Khác dòng -> Lưu dòng cũ, bắt đầu dòng mới
            merged.add(current)
            current = next
        }
    }
    merged.add(current)
    return merged
}

fun findWordIndexAt(offset: Offset, words: List<WordInfo>, viewSize: IntSize): Int {
    return words.indexOfFirst { word ->
        val rect = word.rect.toScreenRect(viewSize)
        rect.inflate(10f).contains(offset)
    }
}

fun findClosestWordIndex(offset: Offset, words: List<WordInfo>, viewSize: IntSize): Int {
    if (words.isEmpty()) return -1
    return words.indices.minByOrNull { index ->
        val rect = words[index].rect.toScreenRect(viewSize)
        val center = rect.center
        hypot(offset.x - center.x, offset.y - center.y)
    } ?: -1
}
