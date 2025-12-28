package ie.app.notepdf.ui.screens.pdf

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect as ComposeRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import ie.app.notepdf.R
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
    val uiState by viewModel.uiState.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val currentMatchIndex by viewModel.currentMatchIndex.collectAsState()

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

    // State cho LazyColumn
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(documentId) {
        viewModel.loadDocument(documentId)
    }

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            globalSelection = GlobalSelectionState()
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

    Scaffold(
        topBar = {
            PdfTopBar(
                title = documentName,
                searchQuery = searchQuery,
                searchResults = searchResults,
                onSearchQueryChange = viewModel::onSearchQueryChange,
                clearSearch = viewModel::clearSearch,
                onBack = onBack,
                nextMatch = viewModel::nextMatch,
                prevMatch = viewModel::prevMatch,
                currentMatchIndex = currentMatchIndex,
            )
        },
        modifier = Modifier
            .onGloballyPositioned { layoutSize = it.size }
    ) { innerPadding ->
        uiState.document?.let { document ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(Color.LightGray)
            ) {
                // 1. Lớp Nội dung (PDF + Zoom/Pan)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { centroid, pan, zoom, _ ->
                                if (isSelectionDragging || isHandleTouched) return@detectTransformGestures

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
                        .pointerInput(Unit) {
                            detectTapGestures(onDoubleTap = {
                                scale = 1f
                                offset = Offset.Zero
                            })
                        }
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offset.x
                                translationY = offset.y
                                transformOrigin = TransformOrigin(0f, 0f)
                            },
                        userScrollEnabled = scale == 1f && !isSelectionDragging && !isHandleTouched,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        items(document.pageCount) { index ->
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
                                viewModel = viewModel,
                                currentScale = scale,
                                selectionState = pageSelection,
                                pageMatches = pageMatches,
                                focusedMatch = focusedMatch,
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
                                }
                            )
                        }
                    }
                }

                PdfFastScroller(
                    listState = listState,
                    pageCount = document.pageCount,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfTopBar(
    title: String,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit = {},
    clearSearch: () -> Unit = {},
    currentMatchIndex: Int = -1,
    nextMatch: () -> Unit = {},
    prevMatch: () -> Unit = {},
    searchResults: List<SearchMatch> = emptyList(),
    onBack: () -> Unit = {}
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
                }) {
                    Icon(
                        painter = painterResource(R.drawable.baseline_clear_24),
                        contentDescription = "Close"
                    )
                }
            } else {
                IconButton(onClick = {

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

/**
 * Thanh cuộn nhanh
 */
@Composable
fun PdfFastScroller(
    listState: LazyListState,
    pageCount: Int,
    modifier: Modifier = Modifier
) {
    if (pageCount == 0) return

    val scope = rememberCoroutineScope()
    var isDragging by remember { mutableStateOf(false) }
    var isActive by remember { mutableStateOf(false) }

    // State lưu vị trí kéo hiện tại (để handle dính vào tay)
    var dragOffset by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(listState.isScrollInProgress, isDragging) {
        if (listState.isScrollInProgress || isDragging) {
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
            listState.firstVisibleItemIndex,
            isDragging,
            dragOffset
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
    viewModel: PdfViewModel,
    currentScale: Float,
    selectionState: SelectionState,
    pageMatches: List<SearchMatch>,
    focusedMatch: SearchMatch?,
    onSelectionChanged: (SelectionState) -> Unit,
    onSelectionDragStateChange: (Boolean) -> Unit,
    onDragScreenPosition: (Offset) -> Unit,
    onHandleTouch: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val allWords by viewModel.pageWords.collectAsState()
    val wordsOfThisPage = allWords[index] ?: emptyList()

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var originalImageSize by remember { mutableStateOf<IntSize?>(null) }

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
        // 1. Ảnh nền
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
                .pointerInput(wordsOfThisPage, viewSize) {
                    if (viewSize.width == 0) return@pointerInput
                    detectTapGestures(
                        onTap = { onSelectionChanged(SelectionState()) },
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
                            }
                        }
                    )
                }
                .pointerInput(wordsOfThisPage, viewSize) {
                    if (viewSize.width == 0) return@pointerInput
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
                        }
                    )
                }
                .drawWithContent {
                    drawContent()

                    val selectedRects = selectionState.selectedWords.map { it.rect.toScreenRect(viewSize) }
                    val mergedSelectionRects = mergeRectangles(selectedRects)

                    mergedSelectionRects.forEach { rect ->
                        drawRect(
                            color = Color(0x664285F4),
                            topLeft = rect.topLeft,
                            size = rect.size
                        )
                    }

                    // --- VẼ SEARCH HIGHLIGHT (ĐÃ MERGE) ---
                    val searchHighlightColor = Color(0x66FFFF00)
                    val focusedHighlightColor = Color(0x99FF8800)

                    pageMatches.forEach { match ->
                        val isFocused = (focusedMatch != null && focusedMatch == match)

                        val matchScreenRects = match.rects.map { it.toScreenRect(viewSize) }
                        val mergedMatchRects = mergeRectangles(matchScreenRects)

                        mergedMatchRects.forEach { rect ->
                            drawRect(
                                color = if (isFocused) focusedHighlightColor else searchHighlightColor,
                                topLeft = rect.topLeft,
                                size = rect.size
                            )
                        }
                    }

                    if (selectionState.selectedWords.isNotEmpty()) {
                        val handleColor = Color(0xFF4285F4)
                        val lineStroke = 2.dp.toPx() / currentScale

                        val firstRect =
                            selectionState.selectedWords.first().rect.toScreenRect(viewSize)
                        val startLineTop = firstRect.topLeft
                        val startLineBottom = firstRect.bottomLeft
                        val startHandleCenter =
                            startLineBottom + Offset(0f, effectiveHandleRadiusPx)

                        drawLine(
                            color = handleColor,
                            start = startLineTop,
                            end = startLineBottom,
                            strokeWidth = lineStroke
                        )
                        drawCircle(
                            color = handleColor,
                            radius = effectiveHandleRadiusPx,
                            center = startHandleCenter
                        )

                        val lastRect =
                            selectionState.selectedWords.last().rect.toScreenRect(viewSize)
                        val endLineTop = lastRect.topRight
                        val endLineBottom = lastRect.bottomRight
                        val endHandleCenter = endLineBottom + Offset(0f, effectiveHandleRadiusPx)

                        drawLine(
                            color = handleColor,
                            start = endLineTop,
                            end = endLineBottom,
                            strokeWidth = lineStroke
                        )
                        drawCircle(
                            color = handleColor,
                            radius = effectiveHandleRadiusPx,
                            center = endHandleCenter
                        )
                    }
                }
        )

        // 2. Handle Touch Targets
        if (selectionState.selectedWords.isNotEmpty() && pageLayoutCoordinates != null) {
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

fun RectF.toScreenRect(viewSize: IntSize): ComposeRect {
    return ComposeRect(
        left = this.left * viewSize.width,
        top = this.top * viewSize.height,
        right = this.right * viewSize.width,
        bottom = this.bottom * viewSize.height
    )
}

fun mergeRectangles(rects: List<ComposeRect>): List<ComposeRect> {
    if (rects.isEmpty()) return emptyList()

    val sorted = rects.sortedWith(compareBy({ it.top }, { it.left }))
    val merged = mutableListOf<ComposeRect>()
    var current = sorted[0]

    for (i in 1 until sorted.size) {
        val next = sorted[i]

        // Kiểm tra xem có cùng dòng không dựa trên độ lệch tâm Y
        val centerYDiff = abs(current.center.y - next.center.y)
        val avgHeight = (current.height + next.height) / 2

        if (centerYDiff < avgHeight * 0.5f) {
            // Cùng dòng -> Gộp lại (bao gồm cả khoảng trắng ở giữa)
            current = ComposeRect(
                left = minOf(current.left, next.left),
                top = minOf(current.top, next.top),
                right = maxOf(current.right, next.right),
                bottom = maxOf(current.bottom, next.bottom)
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
