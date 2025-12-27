package ie.app.notepdf.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Entity(
    tableName = "documents",
    foreignKeys = [
        // Khoá ngoại: Nếu xoá Folder cha, các File con bên trong cũng tự mất theo (CASCADE)
        androidx.room.ForeignKey(
            entity = Folder::class,
            parentColumns = ["id"],
            childColumns = ["parent_id"],
            onDelete = androidx.room.ForeignKey.CASCADE
        )
    ]
)
data class Document(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val uri: String,
    @ColumnInfo(name = "page_count")
    val pageCount: Int,
    @ColumnInfo(name = "thumbnail_path")
    val thumbnailPath: String?,
    @ColumnInfo(name = "parent_id", index = true)
    val parentId: Long? = null,
    @ColumnInfo(name = "last_modified", defaultValue = "CURRENT_TIMESTAMP")
    val lastModified: String = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date()),
    @ColumnInfo(name = "created_at", defaultValue = "CURRENT_TIMESTAMP")
    val createdAt: String = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
)