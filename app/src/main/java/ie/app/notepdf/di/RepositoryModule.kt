package ie.app.notepdf.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ie.app.notepdf.data.local.dao.FileSystemDao
import ie.app.notepdf.data.local.repository.FileSystemRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideFileSystemRepository(fileSystemDao: FileSystemDao) =
        FileSystemRepository(fileSystemDao)

}