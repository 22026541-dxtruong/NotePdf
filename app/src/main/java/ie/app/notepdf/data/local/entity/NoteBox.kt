package ie.app.notepdf.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Entity(
    tableName = "note_boxs",
    foreignKeys = [
        ForeignKey(
            entity = Document::class,
            parentColumns = ["id"],
            childColumns = ["document_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ]
)
data class NoteBox(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "document_id", index = true)
    val documentId: Long,
    @ColumnInfo(name = "page_index")
    val pageIndex: Int,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val text: String,
    @ColumnInfo(name = "created_at", defaultValue = "CURRENT_TIMESTAMP")
    val createdAt: String = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
)