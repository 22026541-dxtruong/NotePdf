package ie.app.notepdf.data.local.repository

import ie.app.notepdf.data.local.dao.InkStrokeDao
import ie.app.notepdf.data.local.dao.NoteDao
import ie.app.notepdf.data.local.entity.InkStroke
import ie.app.notepdf.data.local.entity.Note

class NoteRepository(
    private val inkStrokeDao: InkStrokeDao,
    private val noteDao: NoteDao
) {
    fun getAllStrokesForDocument(docId: String) = inkStrokeDao.getAllStrokesForDocument(docId)

    fun getNotesForDocument(documentId: Long) = noteDao.getNotesForDocument(documentId)

    suspend fun insertStroke(stroke: InkStroke) = inkStrokeDao.insertStroke(stroke)

    suspend fun deleteStroke(strokeId: Long) = inkStrokeDao.deleteStroke(strokeId)

    suspend fun insertNote(note: Note) = noteDao.insertNote(note)

    suspend fun updateNote(note: Note) = noteDao.updateNote(note)

    suspend fun deleteNote(noteId: Long) = noteDao.deleteNote(noteId)

}