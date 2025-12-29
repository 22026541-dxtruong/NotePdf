package ie.app.notepdf.data.local.repository

import ie.app.notepdf.data.local.dao.InkStrokeDao
import ie.app.notepdf.data.local.entity.InkStroke

class NoteRepository(
    private val inkStrokeDao: InkStrokeDao
) {
    fun getAllStrokesForDocument(docId: String) = inkStrokeDao.getAllStrokesForDocument(docId)

    fun getStrokesForPage(docId: String, pageId: Int) = inkStrokeDao.getStrokesForPage(docId, pageId)

    suspend fun insertStroke(stroke: InkStroke) = inkStrokeDao.insertStroke(stroke)

    suspend fun deleteStroke(strokeId: Long) = inkStrokeDao.deleteStroke(strokeId)

}