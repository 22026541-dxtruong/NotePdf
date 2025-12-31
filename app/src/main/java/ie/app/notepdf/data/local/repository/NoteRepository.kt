package ie.app.notepdf.data.local.repository

import ie.app.notepdf.data.local.dao.InkStrokeDao
import ie.app.notepdf.data.local.dao.NoteDao
import ie.app.notepdf.data.local.entity.InkStroke
import ie.app.notepdf.data.local.entity.NoteBox
import ie.app.notepdf.data.local.entity.NoteText
import ie.app.notepdf.data.local.relation.NoteBoxAndNoteText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn

class NoteRepository(
    private val inkStrokeDao: InkStrokeDao,
    private val noteDao: NoteDao
) {
    fun getAllStrokesForDocument(docId: String) = inkStrokeDao.getAllStrokesForDocument(docId)

    fun getNotesForDocument(documentId: Long): Flow<Map<Int, NoteBoxAndNoteText>> {
        return noteDao.getNoteBoxsForDocument(documentId)
            .combine(noteDao.getNoteTextsForDocument(documentId)) { boxs, texts ->
                val allPageIds = (boxs.map { it.pageIndex } + texts.map { it.pageIndex }).distinct()
                val boxsGrouped = boxs.groupBy { it.pageIndex }
                val textsGrouped = texts.groupBy { it.pageIndex }
                allPageIds.associateWith { pageId ->
                    NoteBoxAndNoteText(
                        noteBoxs = boxsGrouped[pageId] ?: emptyList(),
                        noteTexts = textsGrouped[pageId] ?: emptyList()
                    )
                }
            }.flowOn(Dispatchers.Default)
            .distinctUntilChanged()
    }

    suspend fun insertStroke(stroke: InkStroke) = inkStrokeDao.insertStroke(stroke)

    suspend fun deleteStroke(strokeId: Long) = inkStrokeDao.deleteStroke(strokeId)

    suspend fun insertNoteBox(noteBox: NoteBox) = noteDao.insertNoteBox(noteBox)

    suspend fun updateNoteBox(noteBox: NoteBox) = noteDao.updateNoteBox(noteBox)

    suspend fun deleteNoteBox(noteBoxId: Long) = noteDao.deleteNoteBox(noteBoxId)

    suspend fun insertNoteText(noteText: NoteText) = noteDao.insertNoteText(noteText)

    suspend fun updateNoteText(noteText: NoteText) = noteDao.updateNoteText(noteText)

    suspend fun deleteNoteText(noteTextId: Long) = noteDao.deleteNoteText(noteTextId)

}