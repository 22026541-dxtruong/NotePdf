package ie.app.notepdf.data.local.repository

import ie.app.notepdf.data.local.dao.FileSystemDao
import ie.app.notepdf.data.local.entity.Document
import ie.app.notepdf.data.local.entity.Folder

class FileSystemRepository(
    private val fileSystemDao: FileSystemDao
) {

    suspend fun insertFolder(folder: Folder) = fileSystemDao.insertFolder(folder)

    suspend fun insertDocument(document: Document) = fileSystemDao.insertDocument(document)

    fun getSubFoldersAndDocumentsInFolder(currentFolderId: Long) = fileSystemDao.getSubFoldersAndDocumentsInFolder(currentFolderId)

    suspend fun moveFolder(folderId: Long, newParentId: Long?) = fileSystemDao.moveFolder(folderId, newParentId)

    suspend fun moveDocument(documentId: Long, newParentId: Long?) = fileSystemDao.moveDocument(documentId, newParentId)

    suspend fun deleteFolder(folder: Folder) = fileSystemDao.deleteFolder(folder)

    suspend fun deleteDocument(document: Document) = fileSystemDao.deleteDocument(document)

    suspend fun updateFolder(folder: Folder) = fileSystemDao.updateFolder(folder)

    suspend fun updateDocument(document: Document) = fileSystemDao.updateDocument(document)

}