package ie.app.notepdf.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import ie.app.notepdf.data.local.entity.Document
import ie.app.notepdf.data.local.entity.Folder
import ie.app.notepdf.data.local.entity.InkStroke
import ie.app.notepdf.data.local.relation.FolderWithSub
import kotlinx.coroutines.flow.Flow

@Dao
interface FileSystemDao {

    @Query("SELECT * FROM folders WHERE name LIKE '%' || :query || '%'")
    suspend fun searchFolders(query: String): List<Folder>

    @Query("SELECT * FROM documents WHERE name LIKE '%' || :query || '%'")
    suspend fun searchDocuments(query: String): List<Document>
    @Transaction
    @Query("SELECT * FROM folders WHERE id = :currentFolderId")
    fun getSubFoldersAndDocumentsInFolder(currentFolderId: Long): Flow<FolderWithSub?>

    @Query("SELECT * FROM documents WHERE id = :documentId")
    suspend fun getDocumentById(documentId: Long): Document?

    @Query("SELECT * FROM ink_strokes WHERE document_id = :documentId")
    fun getInkStrokesByDocumentId(documentId: Long): Flow<List<InkStroke>>

    @Transaction
    @Query("""
        WITH RECURSIVE folder_path AS (
            -- Bắt đầu từ folder hiện tại, đặt level lớn nhất
            SELECT *, 1000 AS level 
            FROM folders 
            WHERE id = :currentFolderId
            
            UNION ALL
            
            -- Tìm ngược lên cha, giảm level xuống
            SELECT f.*, fp.level - 1
            FROM folders f
            INNER JOIN folder_path fp ON f.id = fp.parent_id
        )
        SELECT id, name, parent_id, created_at 
        FROM folder_path 
        ORDER BY level ASC
    """)
    suspend fun getFolderStack(currentFolderId: Long): List<Folder>

    @Query("UPDATE folders SET parent_id = :newParentId WHERE id = :folderId")
    suspend fun moveFolder(folderId: Long, newParentId: Long?)

    @Query("UPDATE documents SET parent_id = :newParentId WHERE id = :documentId")
    suspend fun moveDocument(documentId: Long, newParentId: Long?)

    @Insert
    suspend fun insertFolder(folder: Folder): Long

    @Insert
    suspend fun insertDocument(document: Document): Long

    @Update
    suspend fun updateFolder(folder: Folder)

    @Update
    suspend fun updateDocument(document: Document)

    @Delete
    suspend fun deleteFolder(folder: Folder)

    @Delete
    suspend fun deleteDocument(document: Document)

}