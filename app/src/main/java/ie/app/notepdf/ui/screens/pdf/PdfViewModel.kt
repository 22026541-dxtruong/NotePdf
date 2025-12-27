package ie.app.notepdf.ui.screens.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.collection.LruCache
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import javax.inject.Inject
import androidx.core.graphics.createBitmap
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PdfPageState(
    val pageIndex: Int,
    val bitmap: Bitmap? = null,
    val isLoading: Boolean = false
)

@HiltViewModel
class PdfViewModel @Inject constructor(

) : ViewModel() {
//    private val _uiState = MutableStateFlow(PdfUiState())
//    val uiState = _uiState.asStateFlow()

    private lateinit var renderer: PdfRenderer

    private val cache = LruCache<Int, Bitmap>(10)

    fun open(context: Context, uri: Uri) {
        val fd = context.contentResolver.openFileDescriptor(uri, "r")!!
        renderer = PdfRenderer(fd)
    }
    suspend fun loadPage(index: Int): Bitmap {
        cache[index]?.let { return it }

        return withContext(Dispatchers.IO) {
            renderer.openPage(index).use { page ->
                val bitmap = createBitmap(page.width, page.height)
                Canvas(bitmap).drawColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                cache.put(index, bitmap)
                bitmap
            }
        }
    }

    val pageCount get() = renderer.pageCount
}