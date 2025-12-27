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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class HomeUiState(
    val folderStack: List<Pair<Long, String>> = emptyList(),
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

    fun deleteFile(document: Document) {
        viewModelScope.launch { fileSystemRepository.deleteDocument(document) }
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

    fun uploadFile(context: Context, uri: Uri, fileName: String) {
        viewModelScope.launch {

            val (bitmap, pageCount) = getPdfMetadata(context, uri)

            val thumbPath = bitmap?.let { saveThumbnailToDisk(context, it) }

            val currentFolderId = _folderStack.value.last().first

            val document = Document(
                name = if (fileName.endsWith(".pdf")) fileName else "$fileName.pdf",
                uri = uri.toString(),
                thumbnailPath = thumbPath,
                pageCount = pageCount,
                parentId = currentFolderId
            )

            fileSystemRepository.insertDocument(document)
            bitmap?.recycle()
        }
    }

}