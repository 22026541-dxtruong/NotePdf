package ie.app.notepdf.data.local.repository

import ie.app.notepdf.data.local.dao.FileSystemDao
import ie.app.notepdf.data.local.entity.Document
import ie.app.notepdf.data.local.entity.Folder
import ie.app.notepdf.data.local.relation.FoldersAndDocuments
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class FileSystemRepository(
    private val fileSystemDao: FileSystemDao
) {
    suspend fun searchFoldersAndDocuments(query: String) =
        coroutineScope {
            val foldersDeferred = async {
                fileSystemDao.searchFolders(query)
            }
            val documentsDeferred = async {
                fileSystemDao.searchDocuments(query)
            }

            FoldersAndDocuments(
                folders = foldersDeferred.await(),
                documents = documentsDeferred.await()
            )
        }

    suspend fun insertFolder(folder: Folder) = fileSystemDao.insertFolder(folder)

    suspend fun insertDocument(document: Document) = fileSystemDao.insertDocument(document)

    fun getSubFoldersAndDocumentsInFolder(currentFolderId: Long) = fileSystemDao.getSubFoldersAndDocumentsInFolder(currentFolderId)

    suspend fun getFolderStack(currentFolderId: Long) = fileSystemDao.getFolderStack(currentFolderId)

    suspend fun getDocumentById(documentId: Long) = fileSystemDao.getDocumentById(documentId)

    fun getInkStrokesByDocumentId(documentId: Long) = fileSystemDao.getInkStrokesByDocumentId(documentId)

    suspend fun moveFolder(folderId: Long, newParentId: Long?) = fileSystemDao.moveFolder(folderId, newParentId)

    suspend fun moveDocument(documentId: Long, newParentId: Long?) = fileSystemDao.moveDocument(documentId, newParentId)

    suspend fun deleteFolder(folder: Folder) = fileSystemDao.deleteFolder(folder)

    suspend fun deleteDocument(document: Document) = fileSystemDao.deleteDocument(document)

    suspend fun updateFolder(folder: Folder) = fileSystemDao.updateFolder(folder)

    suspend fun updateDocument(document: Document) = fileSystemDao.updateDocument(document)

}