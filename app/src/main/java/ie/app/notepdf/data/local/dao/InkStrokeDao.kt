package ie.app.notepdf.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import ie.app.notepdf.data.local.entity.InkStroke
import kotlinx.coroutines.flow.Flow

@Dao
interface InkStrokeDao {

    @Query("SELECT * FROM ink_strokes WHERE document_id = :docId")
    fun getAllStrokesForDocument(docId: String): Flow<List<InkStroke>>

    @Query("SELECT * FROM ink_strokes WHERE document_id = :docId AND page_index = :pageId")
    fun getStrokesForPage(docId: String, pageId: Int): Flow<List<InkStroke>>

    @Insert
    suspend fun insertStroke(stroke: InkStroke): Long

    @Query("DELETE FROM ink_strokes WHERE id = :strokeId")
    suspend fun deleteStroke(strokeId: Long)

    @Query("DELETE FROM ink_strokes WHERE document_id = :docId AND page_index = :pageIdx")
    suspend fun clearPage(docId: String, pageIdx: Int)
}