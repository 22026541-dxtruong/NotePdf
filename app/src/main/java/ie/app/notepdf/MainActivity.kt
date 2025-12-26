package ie.app.notepdf

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import ie.app.notepdf.ui.screens.NotePdfNav
import ie.app.notepdf.ui.theme.NotePdfTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NotePdfTheme {
                NotePdfNav(modifier = Modifier.fillMaxSize())
            }
        }
    }
}
