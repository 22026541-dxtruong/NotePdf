package ie.app.notepdf.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import ie.app.notepdf.data.local.entity.InkStroke
import kotlinx.coroutines.flow.Flow

@Dao
interface InkStrokeDao {

    @Query("SELECT * FROM ink_strokes WHERE document_id = :docId AND page_index = :pageIdx")
    fun getStrokesForPage(docId: String, pageIdx: Int): Flow<List<InkStroke>>

    @Insert
    suspend fun insertStroke(stroke: InkStroke)

    @Query("DELETE FROM ink_strokes WHERE id = :strokeId")
    suspend fun deleteStroke(strokeId: Long)
    
    // Xóa hết nét vẽ của trang (Clear Page)
    @Query("DELETE FROM ink_strokes WHERE document_id = :docId AND page_index = :pageIdx")
    suspend fun clearPage(docId: String, pageIdx: Int)
}