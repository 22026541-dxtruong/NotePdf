package ie.app.notepdf.di

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// KHAI BÁO NGOÀI CLASS: Để đảm bảo chỉ có 1 thực thể duy nhất quản lý file này
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "layout_settings")

@Singleton
class LayoutSettingsManager @Inject constructor(
    private val context: Application // Sử dụng Application là chính xác cho Singleton
) {
    companion object {
        val IS_GRID_MODE = booleanPreferencesKey("is_grid_mode")
    }

    val isGridMode: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[IS_GRID_MODE] ?: false
    }

    suspend fun setGridMode(isGrid: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_GRID_MODE] = isGrid
        }
    }
}