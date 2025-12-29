package ie.app.notepdf.ui.screens.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.collection.LruCache
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.createBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import ie.app.notepdf.data.local.entity.Document
import ie.app.notepdf.data.local.entity.InkStroke
import ie.app.notepdf.data.local.entity.InkTypeConverters
import ie.app.notepdf.data.local.entity.NormalizedPoint
import ie.app.notepdf.data.local.entity.ToolType
import ie.app.notepdf.data.local.repository.FileSystemRepository
import ie.app.notepdf.data.local.repository.NoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.File
import java.util.ArrayDeque
import javax.inject.Inject
import kotlin.coroutines.resume

data class PdfUiState(
    val document: Document? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

data class WordInfo(
    val text: String,
    val rect: RectF
)

data class SearchMatch(
    val pageIndex: Int,
    val rects: List<RectF>
)

// Sealed class for Undo/Redo operations
sealed class InkOperation {
    data class Added(val strokeId: Long, val stroke: InkStroke) : InkOperation()
    data class Removed(val stroke: InkStroke) : InkOperation()
}

@HiltViewModel
class PdfViewModel @Inject constructor(
    private val fileSystemRepository: FileSystemRepository,
    private val noteRepository: NoteRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PdfUiState())
    val uiState = _uiState.asStateFlow()

    private var pdfRenderer: PdfRenderer? = null
    private var fileDescriptor: ParcelFileDescriptor? = null
    private val pdfMutex = Mutex()

    private val _pageWords = MutableStateFlow<Map<Int, List<WordInfo>>>(emptyMap())
    val pageWords = _pageWords.asStateFlow()

    private val bitmapCache = LruCache<Int, Bitmap>(10)

    private val extractionQueue = ArrayDeque<Pair<Int, Bitmap>>()
    private val queueMutex = Mutex()
    private val processingTrigger = Channel<Unit>(Channel.CONFLATED)

    // --- SEARCH STATES ---
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private var fullScanJob: Job? = null

    val searchResults = combine(_searchQuery, _pageWords) { query, currentWords ->
        if (query.isBlank()) {
            emptyList()
        } else {
            val matches = mutableListOf<SearchMatch>()
            val sortedPages = currentWords.keys.sorted()

            for (pageIndex in sortedPages) {
                val words = currentWords[pageIndex] ?: continue
                val fullTextBuilder = StringBuilder()
                val charToWordMap = mutableListOf<Pair<Int, WordInfo>>()

                var currentCharIndex = 0
                words.forEach { word ->
                    charToWordMap.add(currentCharIndex to word)
                    fullTextBuilder.append(word.text).append(" ")
                    currentCharIndex += word.text.length + 1
                }

                val fullText = fullTextBuilder.toString()

                var searchIndex = 0
                while (true) {
                    val foundStartIndex = fullText.indexOf(query, searchIndex, ignoreCase = true)
                    if (foundStartIndex == -1) break

                    val foundEndIndex = foundStartIndex + query.length
                    val matchRects = mutableListOf<RectF>()

                    for ((wordStartIndex, word) in charToWordMap) {
                        val wordEndIndex = wordStartIndex + word.text.length
                        val intersectStart = maxOf(foundStartIndex, wordStartIndex)
                        val intersectEnd = minOf(foundEndIndex, wordEndIndex)

                        if (intersectStart < intersectEnd) {
                            val localStart = intersectStart - wordStartIndex
                            val localEnd = intersectEnd - wordStartIndex
                            val charWidth = word.rect.width() / word.text.length
                            val highlightLeft = word.rect.left + (localStart * charWidth)
                            val highlightRight = word.rect.left + (localEnd * charWidth)

                            val highlightRect = RectF(highlightLeft, word.rect.top, highlightRight, word.rect.bottom)
                            matchRects.add(highlightRect)
                        }
                    }

                    if (matchRects.isNotEmpty()) {
                        matches.add(SearchMatch(pageIndex, matchRects))
                    }
                    searchIndex = foundStartIndex + 1
                }
            }
            matches
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = emptyList())

    private val _currentMatchIndex = MutableStateFlow(-1)
    val currentMatchIndex = _currentMatchIndex.asStateFlow()

    // --- DRAWING STATES ---
    private val _currentTool = MutableStateFlow(ToolType.NONE)
    val currentTool = _currentTool.asStateFlow()

    private val _inkStrokesFromDb = MutableStateFlow<List<InkStroke>>(emptyList())

    val inkStrokes = _inkStrokesFromDb.map { list ->
        list.groupBy { it.pageIndex }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val undoStack = ArrayDeque<InkOperation>()
    private val redoStack = ArrayDeque<InkOperation>()

    private val jsonConverter = InkTypeConverters()
    private var loadStrokesJob: Job? = null

    // --- DRAWING LOGIC ---
    fun setTool(tool: ToolType) {
        _currentTool.value = tool
    }

    fun addStroke(pageIndex: Int, points: List<NormalizedPoint>, color: androidx.compose.ui.graphics.Color, width: Float, toolType: ToolType) {
        val currentDocId = _uiState.value.document?.id ?: return

        val stroke = InkStroke(
            documentId = currentDocId,
            pageIndex = pageIndex,
            color = color.toArgb(),
            strokeWidth = width,
            toolType = toolType.value,
            pointsJson = jsonConverter.fromPointsList(points),
            alpha = if (toolType == ToolType.HIGHLIGHTER) 0.4f else 1.0f
        )
        _inkStrokesFromDb.update { it + stroke }

        viewModelScope.launch {
            val newId = noteRepository.insertStroke(stroke)
            val savedStroke = stroke.copy(id = newId)

            undoStack.addLast(InkOperation.Added(newId, savedStroke))
            redoStack.clear()
        }
    }

    fun removeStroke(stroke: InkStroke) {
        viewModelScope.launch {
            noteRepository.deleteStroke(stroke.id)
            undoStack.addLast(InkOperation.Removed(stroke))
            redoStack.clear()
        }
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val operation = undoStack.removeLast()

        viewModelScope.launch {
            when (operation) {
                is InkOperation.Added -> {
                    // Undo Add = Delete
                    noteRepository.deleteStroke(operation.strokeId)
                    redoStack.addLast(operation)
                }
                is InkOperation.Removed -> {
                    // Undo Remove = Insert back
                    val newId = noteRepository.insertStroke(operation.stroke)
                    // Update the operation with the new ID for future redo/undo cycles
                    val updatedStroke = operation.stroke.copy(id = newId)
                    redoStack.addLast(InkOperation.Removed(updatedStroke))
                }
            }
        }
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val operation = redoStack.removeLast()

        viewModelScope.launch {
            when (operation) {
                is InkOperation.Added -> {
                    // Redo Add = Insert again
                    val newId = noteRepository.insertStroke(operation.stroke)
                    val updatedStroke = operation.stroke.copy(id = newId)
                    undoStack.addLast(InkOperation.Added(newId, updatedStroke))
                }
                is InkOperation.Removed -> {
                    // Redo Remove = Delete again
                    noteRepository.deleteStroke(operation.stroke.id)
                    undoStack.addLast(operation)
                }
            }
        }
    }

    // --- INIT & LOAD ---
    init {
        processTextExtractionQueue()
        viewModelScope.launch {
            _searchQuery.collect { _currentMatchIndex.value = -1 }
        }
        viewModelScope.launch {
            searchResults.collect { matches ->
                if (matches.isNotEmpty() && _currentMatchIndex.value == -1) _currentMatchIndex.value = 0
                else if (matches.isEmpty()) _currentMatchIndex.value = -1
            }
        }
    }

    // --- SEARCH HELPERS ---
    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        fullScanJob?.cancel()
        if (query.isNotBlank()) {
            fullScanJob = viewModelScope.launch(Dispatchers.Default) { scanEntireDocument() }
        }
    }

    fun clearSearch() {
        _searchQuery.value = ""
        fullScanJob?.cancel()
    }

    fun nextMatch() {
        val count = searchResults.value.size
        if (count == 0) return
        _currentMatchIndex.update { (it + 1) % count }
    }

    fun prevMatch() {
        val count = searchResults.value.size
        if (count == 0) return
        _currentMatchIndex.update { if (it - 1 < 0) count - 1 else it - 1 }
    }

    // --- DOCUMENT LOADING ---
    private suspend fun scanEntireDocument() {
        val renderer = pdfRenderer ?: return
        val pageCount = renderer.pageCount
        for (i in 0 until pageCount) {
            if (!currentCoroutineContext().isActive) break
            if (!_pageWords.value.containsKey(i)) {
                try {
                    val bitmap = pdfMutex.withLock {
                        if (pdfRenderer == null) return@withLock null
                        try {
                            pdfRenderer?.openPage(i)?.use { page ->
                                val scale = 2.0f
                                val width = (page.width * scale).toInt()
                                val height = (page.height * scale).toInt()
                                val bm = createBitmap(width, height)
                                val canvas = Canvas(bm)
                                canvas.drawColor(Color.WHITE)
                                page.render(bm, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                bm
                            }
                        } catch (e: Exception) { null }
                    }
                    if (bitmap != null) {
                        performTextRecognition(i, bitmap)
                        bitmap.recycle()
                    }
                    yield()
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    private fun processTextExtractionQueue() {
        viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                val nextTask = queueMutex.withLock { extractionQueue.pollLast() }
                if (nextTask != null) {
                    val (index, bitmap) = nextTask
                    if (!_pageWords.value.containsKey(index)) performTextRecognition(index, bitmap)
                } else {
                    processingTrigger.receive()
                }
            }
        }
    }

    fun loadDocument(documentId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val document = fileSystemRepository.getDocumentById(documentId)
                if (document != null) {
                    _uiState.update { it.copy(document = document) }
                    openPdfFromPath(document.uri)

                    // Observe Ink Strokes from DB
                    loadStrokesJob?.cancel()
                    loadStrokesJob = viewModelScope.launch {
                        noteRepository.getAllStrokesForDocument(documentId.toString())
                            .collect { strokes ->
                                _inkStrokesFromDb.value = strokes
                            }
                    }
                } else {
                    _uiState.update { it.copy(errorMessage = "Không tìm thấy tài liệu") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Lỗi khi tải thông tin: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    suspend fun loadPage(context: Context, index: Int): Bitmap? {
        bitmapCache[index]?.let { return it }
        return withContext(Dispatchers.IO) {
            val renderer = pdfRenderer ?: return@withContext null
            if (index < 0 || index >= renderer.pageCount) return@withContext null
            try {
                pdfMutex.withLock {
                    renderer.openPage(index).use { page ->
                        val density = context.resources.displayMetrics.density
                        val scaleFactor = density * 2.0f
                        val targetWidth = (page.width * scaleFactor).toInt()
                        val targetHeight = (page.height * scaleFactor).toInt()
                        val bitmap = createBitmap(targetWidth, targetHeight)
                        val canvas = Canvas(bitmap)
                        canvas.drawColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmapCache.put(index, bitmap)
                        bitmap
                    }
                }
            } catch (e: Exception) { e.printStackTrace(); null }
        }
    }

    fun extractTextFromPage(index: Int, bitmap: Bitmap) {
        if (_pageWords.value.containsKey(index)) return
        viewModelScope.launch {
            queueMutex.withLock {
                val iterator = extractionQueue.iterator()
                while (iterator.hasNext()) {
                    if (iterator.next().first == index) { iterator.remove(); break }
                }
                extractionQueue.addLast(index to bitmap)
            }
            processingTrigger.trySend(Unit)
        }
    }

    private suspend fun performTextRecognition(index: Int, bitmap: Bitmap) = suspendCancellableCoroutine { continuation ->
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val bitmapWidth = bitmap.width.toFloat()
            val bitmapHeight = bitmap.height.toFloat()
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val wordsList = mutableListOf<WordInfo>()
                    for (block in visionText.textBlocks) {
                        for (line in block.lines) {
                            for (element in line.elements) {
                                element.boundingBox?.let { box ->
                                    val normalizedRect = RectF(
                                        box.left / bitmapWidth,
                                        box.top / bitmapHeight,
                                        box.right / bitmapWidth,
                                        box.bottom / bitmapHeight
                                    )
                                    wordsList.add(WordInfo(element.text, normalizedRect))
                                }
                            }
                        }
                    }
                    _pageWords.update { it + (index to wordsList) }
                    continuation.resume(Unit)
                }
                .addOnFailureListener { e -> e.printStackTrace(); continuation.resume(Unit) }
        } catch (e: Exception) { e.printStackTrace(); continuation.resume(Unit) }
    }

    private fun openPdfFromPath(path: String) {
        try {
            closeResources()
            val file = File(path)
            if (!file.exists()) throw Exception("File không tồn tại tại đường dẫn: $path")
            fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            fileDescriptor?.let { pdfRenderer = PdfRenderer(it) }
        } catch (e: Exception) {
            _uiState.update { it.copy(errorMessage = "Không thể mở file PDF: ${e.message}") }
        }
    }

    private fun closeResources() {
        try {
            pdfRenderer?.close()
            fileDescriptor?.close()
        } catch (e: Exception) { e.printStackTrace() }
        pdfRenderer = null
        fileDescriptor = null
        bitmapCache.evictAll()
    }

    override fun onCleared() {
        super.onCleared()
        closeResources()
        processingTrigger.close()
    }
}
