package ie.app.notepdf.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import ie.app.notepdf.data.local.entity.Document
import ie.app.notepdf.data.local.entity.Folder
import ie.app.notepdf.data.local.relation.FolderWithSub
import kotlinx.coroutines.flow.Flow

@Dao
interface FileSystemDao {

    @Transaction
    @Query("SELECT * FROM folders WHERE id = :currentFolderId")
    fun getSubFoldersAndDocumentsInFolder(currentFolderId: Long): Flow<FolderWithSub?>

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