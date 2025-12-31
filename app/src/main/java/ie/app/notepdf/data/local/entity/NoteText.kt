package ie.app.notepdf.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Entity(
    tableName = "note_texts",
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
data class NoteText(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "document_id", index = true)
    val documentId: Long,
    @ColumnInfo(name = "page_index")
    val pageIndex: Int,
    @ColumnInfo(name = "points_json")
    val pointsJson: String,
    val text: String,
    val comment: String,
    @ColumnInfo(name = "created_at", defaultValue = "CURRENT_TIMESTAMP")
    val createdAt: String = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
)

@Serializable
data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

class NoteTextConverters {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @TypeConverter
    fun fromRectList(rects: List<NormalizedRect>): String {
        return json.encodeToString(rects)
    }

    @TypeConverter
    fun toRectList(data: String): List<NormalizedRect> {
        if (data.isEmpty()) return emptyList()
        return try {
            json.decodeFromString(data)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
