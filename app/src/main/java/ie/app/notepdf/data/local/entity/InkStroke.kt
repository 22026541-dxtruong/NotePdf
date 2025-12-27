package ie.app.notepdf.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Entity(
    tableName = "ink_strokes",
    foreignKeys = [
        androidx.room.ForeignKey(
            entity = Document::class,
            parentColumns = ["id"],
            childColumns = ["document_id"],
            onDelete = androidx.room.ForeignKey.CASCADE
        )
    ]
)
data class InkStroke(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "document_id", index = true)
    val documentId: String,
    @ColumnInfo(name = "page_index")
    val pageIndex: Int,
    val color: Int, 
    @ColumnInfo(name = "stroke_width")
    val strokeWidth: Float,
    val alpha: Float = 1.0f,
    @ColumnInfo(name = "tool_type")
    val toolType: Int = 0,
    @ColumnInfo(name = "points_json")
    val pointsJson: String 
)

@Serializable
data class NormalizedPoint(
    val x: Float,
    val y: Float
)

class InkTypeConverters {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @TypeConverter
    fun fromPointsList(points: List<NormalizedPoint>): String {
        return json.encodeToString(points)
    }

    @TypeConverter
    fun toPointsList(data: String): List<NormalizedPoint> {
        if (data.isEmpty()) return emptyList()
        return json.decodeFromString(data)
    }
}