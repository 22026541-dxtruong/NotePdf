package ie.app.notepdf.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import ie.app.notepdf.data.local.entity.NoteBox
import ie.app.notepdf.data.local.entity.NoteText
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Query("SELECT * FROM note_boxs WHERE document_id = :documentId")
    fun getNoteBoxsForDocument(documentId: Long): Flow<List<NoteBox>>

    @Query("SELECT * FROM note_texts WHERE document_id = :documentId")
    fun getNoteTextsForDocument(documentId: Long): Flow<List<NoteText>>

    @Query("DELETE FROM note_texts WHERE id = :noteTextId")
    suspend fun deleteNoteText(noteTextId: Long)

    @Query("DELETE FROM note_boxs WHERE id = :noteBoxId")
    suspend fun deleteNoteBox(noteBoxId: Long)

    @Insert
    suspend fun insertNoteBox(noteBox: NoteBox): Long

    @Update
    suspend fun updateNoteBox(noteBox: NoteBox)

    @Insert
    suspend fun insertNoteText(noteText: NoteText): Long

    @Update
    suspend fun updateNoteText(noteText: NoteText)

}