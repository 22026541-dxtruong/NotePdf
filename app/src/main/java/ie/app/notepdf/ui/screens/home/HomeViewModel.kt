package ie.app.notepdf.ui.screens.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ie.app.notepdf.data.local.relation.FolderWithSub
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import ie.app.notepdf.data.local.entity.Document
import ie.app.notepdf.data.local.entity.Folder
import ie.app.notepdf.data.local.repository.FileSystemRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import androidx.core.graphics.createBitmap
import ie.app.notepdf.data.local.relation.FoldersAndDocuments
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class HomeUiState(
    val folderStack: List<Pair<Long, String>> = listOf(1L to "Home"),
    val folderWithSub: FolderWithSub? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val fileSystemRepository: FileSystemRepository
) : ViewModel() {
    private val _currentFolderId = MutableStateFlow(1L)

    private val _folderStack = MutableStateFlow(listOf(1L to "Home"))

    private val queryFlow = MutableStateFlow("")

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val searchResult = queryFlow
        .filter { it.isNotBlank() }
        .flatMapLatest { query ->
            flow {
                emit(fileSystemRepository.searchFoldersAndDocuments(query))
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            FoldersAndDocuments(emptyList(), emptyList())
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<HomeUiState> = combine(
        _folderStack,
        _currentFolderId.flatMapLatest { id ->
            fileSystemRepository.getSubFoldersAndDocumentsInFolder(id)
        }
    ) { stack, data ->
        HomeUiState(
            folderStack = stack,
            folderWithSub = data,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState(isLoading = true)
    )

    fun enterFolder(folderId: Long, folderName: String) {
        if (_currentFolderId.value == folderId) return

        _folderStack.update { it + (folderId to folderName) }
        _currentFolderId.value = folderId
    }

    fun getFolderStackAndEnterFolder(folderId: Long) {
        viewModelScope.launch {
            val stackFromDb = fileSystemRepository.getFolderStack(folderId)

            val newStack = stackFromDb.map { it.id to it.name }
            _folderStack.value = newStack
            _currentFolderId.value = folderId
        }
    }


    fun goBackFolder(): Boolean {
        val stack = _folderStack.value
        if (stack.size <= 1) return false

        val newStack = stack.dropLast(1)
        _folderStack.value = newStack
        _currentFolderId.value = newStack.last().first
        return true
    }

    fun jumpToFolder(folderId: Long) {
        val stack = _folderStack.value
        val index = stack.indexOfFirst { it.first == folderId }
        if (index == -1 || stack.last().first == folderId) return

        val newStack = stack.subList(0, index + 1)
        _folderStack.value = newStack
        _currentFolderId.value = folderId
    }

    fun onSearchQueryChanged(query: String) {
        queryFlow.value = query
    }

    fun createFolder(folderName: String) {
        viewModelScope.launch {
            fileSystemRepository.insertFolder(
                Folder(name = folderName, parentId = _currentFolderId.value)
            )
        }
    }

    fun deleteFolder(folder: Folder) {
        viewModelScope.launch { fileSystemRepository.deleteFolder(folder) }
    }

    fun editFileName(document: Document) {
        viewModelScope.launch { fileSystemRepository.updateDocument(document) }
    }

    fun editFolderName(folder: Folder) {
        viewModelScope.launch { fileSystemRepository.updateFolder(folder) }
    }

    fun moveFile(documentId: Long, newFolderId: Long) {
        viewModelScope.launch {
            fileSystemRepository.moveDocument(documentId, newFolderId)
        }
    }

    fun moveFolder(folderId: Long, newFolderId: Long) {
        viewModelScope.launch {
            fileSystemRepository.moveFolder(folderId, newFolderId)
        }
    }

    fun getFileName(context: Context, uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) result = it.getString(index)
                }
            }
        }
        return result ?: uri.path?.substringAfterLast('/')
    }

    fun getPdfMetadata(context: Context, uri: Uri): Pair<Bitmap?, Int> {
        return try {
            val fileDescriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return null to 0
            val renderer = PdfRenderer(fileDescriptor)

            val totalPages = renderer.pageCount

            val page = renderer.openPage(0)
            val targetWidth = 300
            val aspectRatio = page.height.toFloat() / page.width.toFloat()
            val targetHeight = (targetWidth * aspectRatio).toInt()
            val bitmap = createBitmap(targetWidth, targetHeight)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

            page.close()
            renderer.close()

            bitmap to totalPages
        } catch (e: Exception) {
            Log.e("PDF", "Error getting metadata", e)
            null to 0
        }
    }

    private suspend fun saveThumbnailToDisk(context: Context, bitmap: Bitmap): String? {
        return withContext(Dispatchers.IO) {
            try {
                val fileName = "thumb_${System.currentTimeMillis()}.png"
                val file = File(context.filesDir, fileName)

                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
                }
                file.absolutePath
            } catch (e: Exception) {
                Log.e("FileStorage", "Error saving thumbnail", e)
                null
            }
        }
    }

    private suspend fun copyPdfToInternalStorage(context: Context, uri: Uri, originalName: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                // Tạo tên file duy nhất để tránh trùng lặp (ví dụ: pdf_1735312345.pdf)
                val uniqueFileName = "pdf_${System.currentTimeMillis()}_${originalName.filter { it.isLetterOrDigit() || it == '.' }}"
                val destinationFile = File(context.filesDir, uniqueFileName)

                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    FileOutputStream(destinationFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                destinationFile.absolutePath // Trả về đường dẫn để lưu vào database
            } catch (e: Exception) {
                Log.e("FileStorage", "Error copying PDF file", e)
                null
            }
        }
    }

    fun uploadFile(context: Context, uri: Uri, fileName: String) {
        viewModelScope.launch {
            // 1. Lấy metadata (Thumbnail và số trang) từ URI gốc
            val (bitmap, pageCount) = getPdfMetadata(context, uri)

            // 2. Lưu thumbnail xuống đĩa
            val thumbPath = bitmap?.let { saveThumbnailToDisk(context, it) }

            // 3. COPY FILE GỐC VÀO BỘ NHỚ TRONG (Giải pháp cho SecurityException)
            val internalPath = copyPdfToInternalStorage(context, uri, fileName)

            if (internalPath != null) {
                val currentFolderId = _folderStack.value.last().first

                val document = Document(
                    name = if (fileName.endsWith(".pdf")) fileName else "$fileName.pdf",
                    // LƯU Ý: Trường 'uri' trong bảng Document bây giờ sẽ chứa đường dẫn file nội bộ
                    uri = internalPath,
                    thumbnailPath = thumbPath,
                    pageCount = pageCount,
                    parentId = currentFolderId
                )

                fileSystemRepository.insertDocument(document)
            } else {
                // Xử lý lỗi nếu không copy được file
                Log.e("Upload", "Failed to copy file to internal storage")
            }

            bitmap?.recycle()
        }
    }

    fun deleteFile(document: Document) {
        viewModelScope.launch {
            // Xóa file PDF gốc trong máy
            document.uri.let { path ->
                val file = File(path)
                if (file.exists()) file.delete()
            }

            // Xóa file Thumbnail
            document.thumbnailPath?.let { path ->
                val file = File(path)
                if (file.exists()) file.delete()
            }

            // Xóa trong Room
            fileSystemRepository.deleteDocument(document)
        }
    }
}