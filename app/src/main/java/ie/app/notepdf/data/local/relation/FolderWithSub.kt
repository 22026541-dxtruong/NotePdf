package ie.app.notepdf.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import ie.app.notepdf.data.local.entity.Document
import ie.app.notepdf.data.local.entity.Folder

data class FolderWithSub(
    @Embedded
    val folder: Folder,
    @Relation(
        entity = Folder::class,
        parentColumn = "id",
        entityColumn = "parent_id"
    )
    val subFolders: List<Folder> = emptyList(),
    @Relation(
        entity = Document::class,
        parentColumn = "id",
        entityColumn = "parent_id"
    )
    val subDocuments: List<Document> = emptyList()
)