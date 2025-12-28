package ie.app.notepdf.data.local.relation

import ie.app.notepdf.data.local.entity.Document
import ie.app.notepdf.data.local.entity.Folder

data class FoldersAndDocuments(
    val folders: List<Folder> = emptyList(),
    val documents: List<Document> = emptyList()
)