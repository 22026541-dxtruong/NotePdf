package ie.app.notepdf.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import ie.app.notepdf.ui.screens.home.HomeScreen
import ie.app.notepdf.ui.screens.pdf.PdfScreen
import kotlinx.serialization.Serializable

@Serializable
data object Home : NavKey

@Serializable
data class Pdf(val documentId: Long, val name: String) : NavKey

@Composable
fun NotePdfNav(
    modifier: Modifier = Modifier
) {
    val backStack = rememberNavBackStack(Home)

    NavDisplay(
        modifier = modifier,
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        entryProvider = entryProvider {
            entry<Home> {
                HomeScreen(
                    onPdfClick = { backStack.add(Pdf(it.id, name = it.name)) }
                )
            }
            entry<Pdf> { key ->
                PdfScreen(
                    documentId = key.documentId,
                    documentName = key.name,
                    onBack = { backStack.removeLastOrNull() }
                )
            }
        }
    )
}
