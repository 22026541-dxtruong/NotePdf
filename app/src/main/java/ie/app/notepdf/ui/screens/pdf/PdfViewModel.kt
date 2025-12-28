package ie.app.notepdf.ui.screens.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.collection.LruCache
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import androidx.core.graphics.createBitmap
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import ie.app.notepdf.data.local.entity.Document
import ie.app.notepdf.data.local.repository.FileSystemRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield
import java.io.File
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

@HiltViewModel
class PdfViewModel @Inject constructor(
    private val fileSystemRepository: FileSystemRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PdfUiState())
    val uiState = _uiState.asStateFlow()


    private val _pageWords = MutableStateFlow<Map<Int, List<WordInfo>>>(emptyMap())
    val pageWords = _pageWords.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private var fullScanJob: Job? = null

    // Sử dụng combine để tự động tính toán searchResults mỗi khi query hoặc pageWords thay đổi
    val searchResults = combine(_searchQuery, _pageWords) { query, currentWords ->
        if (query.isBlank()) {
            emptyList()
        } else {
            val matches = mutableListOf<SearchMatch>()
            val sortedPages = currentWords.keys.sorted()

            for (pageIndex in sortedPages) {
                val words = currentWords[pageIndex] ?: continue

                // 1. Tái tạo văn bản đầy đủ của trang để tìm kiếm cụm từ
                // Lưu mapping giữa index ký tự trong fullText và index của từ trong list words
                val fullTextBuilder = StringBuilder()
                val charToWordMap = mutableListOf<Pair<Int, WordInfo>>()

                var currentCharIndex = 0
                words.forEach { word ->
                    // Lưu vị trí bắt đầu của từ này trong chuỗi tổng
                    charToWordMap.add(currentCharIndex to word)
                    fullTextBuilder.append(word.text).append(" ") // Thêm dấu cách ảo
                    currentCharIndex += word.text.length + 1
                }

                val fullText = fullTextBuilder.toString()

                // 2. Tìm kiếm tất cả vị trí xuất hiện của query
                var searchIndex = 0
                while (true) {
                    val foundStartIndex = fullText.indexOf(query, searchIndex, ignoreCase = true)
                    if (foundStartIndex == -1) break

                    val foundEndIndex = foundStartIndex + query.length

                    // 3. Ánh xạ ngược từ vị trí ký tự về các Rect
                    val matchRects = mutableListOf<RectF>()

                    // Tìm các từ bị ảnh hưởng bởi kết quả tìm kiếm này
                    // Duyệt qua tất cả các từ để xem từ nào nằm trong khoảng [foundStartIndex, foundEndIndex]
                    for ((wordStartIndex, word) in charToWordMap) {
                        val wordEndIndex = wordStartIndex + word.text.length

                        // Kiểm tra giao nhau giữa từ hiện tại và vùng tìm thấy
                        val intersectStart = maxOf(foundStartIndex, wordStartIndex)
                        val intersectEnd = minOf(foundEndIndex, wordEndIndex)

                        if (intersectStart < intersectEnd) {
                            // Từ này chứa một phần hoặc toàn bộ query

                            // Tính toán vị trí tương đối trong từ (0..length)
                            val localStart = intersectStart - wordStartIndex
                            val localEnd = intersectEnd - wordStartIndex

                            // Nội suy Rect (Interpolation)
                            // Giả sử các ký tự có độ rộng bằng nhau (xấp xỉ)
                            val charWidth = word.rect.width() / word.text.length

                            val highlightLeft = word.rect.left + (localStart * charWidth)
                            val highlightRight = word.rect.left + (localEnd * charWidth)

                            val highlightRect = RectF(
                                highlightLeft,
                                word.rect.top,
                                highlightRight,
                                word.rect.bottom
                            )
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
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _currentMatchIndex = MutableStateFlow(-1)
    val currentMatchIndex = _currentMatchIndex.asStateFlow()

    private var pdfRenderer: PdfRenderer? = null
    private var fileDescriptor: ParcelFileDescriptor? = null

    private val pdfMutex = Mutex()
    private val bitmapCache = LruCache<Int, Bitmap>(10)

    private val extractionQueue = ArrayDeque<Pair<Int, Bitmap>>()
    private val queueMutex = Mutex() // Lock riêng cho queue để không ảnh hưởng PDF Renderer
    // Channel dùng làm tín hiệu đánh thức (Wake-up signal) cho worker
    private val processingTrigger = Channel<Unit>(Channel.CONFLATED)

    init {
        // Khởi chạy Consumer Loop để xử lý các yêu cầu từ Queue
        processTextExtractionQueue()

        viewModelScope.launch {
            _searchQuery.collect {
                _currentMatchIndex.value = -1
            }
        }

        // Tự động chọn kết quả đầu tiên (index 0) nếu có kết quả tìm thấy và chưa chọn gì
        viewModelScope.launch {
            searchResults.collect { matches ->
                if (matches.isNotEmpty() && _currentMatchIndex.value == -1) {
                    _currentMatchIndex.value = 0
                } else if (matches.isEmpty()) {
                    _currentMatchIndex.value = -1
                }
            }
        }
    }

    private fun processTextExtractionQueue() {
        viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                // 1. Lấy task ưu tiên nhất (LIFO - Last In First Out)
                // Trang nào mới được thêm vào cuối cùng sẽ được lấy ra xử lý trước
                val nextTask = queueMutex.withLock {
                    extractionQueue.removeLastOrNull()
                }

                // 2. Nếu có task thì xử lý
                if (nextTask != null) {
                    val (index, bitmap) = nextTask
                    // Kiểm tra lại lần nữa xem đã có kết quả chưa (tránh làm thừa)
                    if (!_pageWords.value.containsKey(index)) {
                        performTextRecognition(index, bitmap)
                    }
                } else {
                    // 3. Queue rỗng, tạm dừng coroutine chờ tín hiệu có task mới
                    processingTrigger.receive()
                }
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query

        // Hủy job quét cũ nếu có
        fullScanJob?.cancel()

        if (query.isNotBlank()) {
            // Bắt đầu quét toàn bộ các trang chưa load
            fullScanJob = viewModelScope.launch(Dispatchers.Default) {
                scanEntireDocument()
            }
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

    // --- FULL DOCUMENT SCANNING ---
    private suspend fun scanEntireDocument() {
        val renderer = pdfRenderer ?: return
        val pageCount = renderer.pageCount

        // Duyệt qua tất cả các trang
        for (i in 0 until pageCount) {
            // Kiểm tra xem job còn hoạt động không (để dừng khi người dùng xóa search)
            if (!currentCoroutineContext().isActive) break

            // Nếu trang này chưa được quét, thì tiến hành quét
            if (!_pageWords.value.containsKey(i)) {
                try {
                    // 1. Render Bitmap tạm thời (Low/Medium quality cho nhanh)
                    // Sử dụng pdfMutex để tránh conflict với luồng hiển thị UI
                    val bitmap = pdfMutex.withLock {
                        if (pdfRenderer == null) return@withLock null

                        try {
                            pdfRenderer?.openPage(i)?.use { page ->
                                // Scale 2.0 đủ để OCR tốt mà không tốn quá nhiều RAM
                                val scale = 2.0f
                                val width = (page.width * scale).toInt()
                                val height = (page.height * scale).toInt()

                                val bm = createBitmap(width, height)
                                val canvas = Canvas(bm)
                                canvas.drawColor(Color.WHITE)
                                page.render(bm, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                bm
                            }
                        } catch (e: Exception) {
                            null
                        }
                    }

                    // 2. Chạy ML Kit OCR và cập nhật _pageWords
                    if (bitmap != null) {
                        performTextRecognition(i, bitmap)
                        // Quan trọng: Giải phóng Bitmap ngay lập tức để tránh OOM
                        bitmap.recycle()
                    }

                    // Nhường CPU một chút để không block UI quá lâu
                    yield()

                } catch (e: Exception) {
                    e.printStackTrace()
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

    /**
     * Render một trang PDF thành Bitmap.
     * Hàm này chạy trên IO thread và cache kết quả.
     */
    suspend fun loadPage(context: Context, index: Int): Bitmap? {
        // 1. Kiểm tra Cache trước
        bitmapCache[index]?.let { return it }

        return withContext(Dispatchers.IO) {
            val renderer = pdfRenderer ?: return@withContext null
            // Kiểm tra index hợp lệ
            if (index < 0 || index >= renderer.pageCount) return@withContext null

            try {
                pdfMutex.withLock {
                    renderer.openPage(index).use { page ->
                        // Tính toán độ phân giải dựa trên mật độ điểm ảnh màn hình
                        val density = context.resources.displayMetrics.density
                        // Hệ số nhân 2.0f giúp ảnh nét hơn khi zoom, nhưng tốn bộ nhớ hơn
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
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    fun extractTextFromPage(index: Int, bitmap: Bitmap) {
        if (_pageWords.value.containsKey(index)) return

        viewModelScope.launch {
            queueMutex.withLock {
                val iterator = extractionQueue.iterator()
                while (iterator.hasNext()) {
                    if (iterator.next().first == index) {
                        iterator.remove()
                        break
                    }
                }
                extractionQueue.addLast(index to bitmap)
            }
            processingTrigger.trySend(Unit)
        }
    }

    /**
     * Logic thực sự của ML Kit, được gọi tuần tự bên trong Consumer Loop
     */
    private suspend fun performTextRecognition(index: Int, bitmap: Bitmap) = suspendCancellableCoroutine { continuation ->
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val wordsList = mutableListOf<WordInfo>()
                    val bitmapWidth = bitmap.width.toFloat()
                    val bitmapHeight = bitmap.height.toFloat()
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
                .addOnFailureListener { e ->
                    e.printStackTrace()
                    continuation.resume(Unit) // Vẫn resume để không treo coroutine
                }
        } catch (e: Exception) {
            e.printStackTrace()
            continuation.resume(Unit)
        }
    }

    private fun openPdfFromPath(path: String) {
        try {
            closeResources()
            val file = File(path)
            if (!file.exists()) throw Exception("File không tồn tại tại đường dẫn: $path")

            fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            fileDescriptor?.let {
                pdfRenderer = PdfRenderer(it)
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(errorMessage = "Không thể mở file PDF: ${e.message}") }
        }
    }

    private fun closeResources() {
        try {
            pdfRenderer?.close()
            fileDescriptor?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
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