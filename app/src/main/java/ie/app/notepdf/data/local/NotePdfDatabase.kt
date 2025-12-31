package ie.app.notepdf.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import ie.app.notepdf.data.local.dao.FileSystemDao
import ie.app.notepdf.data.local.dao.InkStrokeDao
import ie.app.notepdf.data.local.dao.NoteDao
import ie.app.notepdf.data.local.entity.Document
import ie.app.notepdf.data.local.entity.Folder
import ie.app.notepdf.data.local.entity.InkStroke
import ie.app.notepdf.data.local.entity.InkTypeConverters
import ie.app.notepdf.data.local.entity.NoteBox
import ie.app.notepdf.data.local.entity.NoteText
import ie.app.notepdf.data.local.entity.NoteTextConverters
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Database(
    entities = [Folder::class, Document::class, InkStroke::class, NoteBox::class, NoteText::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(InkTypeConverters::class, DateToStringConverter::class, NoteTextConverters::class)
abstract class NotePdfDatabase : RoomDatabase() {

    abstract fun fileSystemDao(): FileSystemDao
    abstract fun inkStrokeDao(): InkStrokeDao
    abstract fun noteDao(): NoteDao
}

class DateToStringConverter {
    private val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    @TypeConverter
    fun fromTimestamp(value: Long?): String? {
        return value?.let { formatter.format(Date(it)) }
    }

    @TypeConverter
    fun dateToTimestamp(dateString: String?): Long? {
        return try {
            dateString?.let { formatter.parse(it)?.time }
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }
}