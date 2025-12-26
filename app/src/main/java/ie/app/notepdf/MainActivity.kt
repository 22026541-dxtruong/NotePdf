package ie.app.notepdf

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.pdf.PdfDocument
import androidx.pdf.compose.PdfViewer
import androidx.pdf.compose.PdfViewerState
import ie.app.notepdf.ui.theme.NotePdfTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NotePdfTheme {
                val viewerState = remember { PdfViewerState() }
                // 2. Tải PdfDocument (Bạn cần xử lý việc load URI thành PdfDocument)
                // Lưu ý: Thường dùng PdfLoader hoặc DocumentService để lấy đối tượng này
                var pdfDoc by remember { mutableStateOf<PdfDocument?>(null) }
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                    PdfViewer(
                        pdfDocument = pdfDoc,
                        state = viewerState,
                        modifier = Modifier.fillMaxSize(),
                        maxZoom = 5f, // Cho phép phóng to tối đa 5 lần để đọc kỹ
                        onUrlLinkClicked = { uri ->
                            // Xử lý khi người dùng nhấn vào link trong PDF
                            true
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    NotePdfTheme {
        Greeting("Android")
    }
}