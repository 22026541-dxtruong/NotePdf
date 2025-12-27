package ie.app.notepdf.di

import android.app.Application
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ie.app.notepdf.data.local.NotePdfDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(application: Application) = Room
        .databaseBuilder(application, NotePdfDatabase::class.java, "note_pdf_db")
        .fallbackToDestructiveMigration(true)
        .addCallback(object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                db.execSQL("INSERT INTO folders (id, name, parent_id) VALUES (1, 'Home', NULL)")
            }
        })
        .build()

    @Provides
    @Singleton
    fun provideFileSystemDao(database: NotePdfDatabase) = database.fileSystemDao()

    @Provides
    @Singleton
    fun provideInkStrokeDao(database: NotePdfDatabase) = database.inkStrokeDao()

}