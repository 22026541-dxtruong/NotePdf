package ie.app.notepdf.di

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IntentManager @Inject constructor() {
    private val _incomingUri = MutableStateFlow<Uri?>(null)
    val incomingUri = _incomingUri.asStateFlow()

    fun setUri(uri: Uri?) { _incomingUri.value = uri }
    fun clear() { _incomingUri.value = null }
}