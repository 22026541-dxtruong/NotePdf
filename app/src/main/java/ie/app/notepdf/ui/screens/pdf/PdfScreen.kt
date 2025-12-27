package ie.app.notepdf.ui.screens.pdf

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage

@Composable
fun PdfScreen(
    uri: Uri?,
    modifier: Modifier = Modifier,
    viewModel: PdfViewModel = hiltViewModel()
) {
    val context = LocalContext.current
//    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uri) {
        uri?.let { viewModel.open(context, uri) }
    }

    LazyColumn {
        items(viewModel.pageCount) { index ->
            var bitmap by remember { mutableStateOf<Bitmap?>(null) }

            LaunchedEffect(index) {
                bitmap = viewModel.loadPage(index)
            }

            AsyncImage(
                model = bitmap,
                contentDescription = "Page $index",
                modifier = modifier
            )
        }
    }

}