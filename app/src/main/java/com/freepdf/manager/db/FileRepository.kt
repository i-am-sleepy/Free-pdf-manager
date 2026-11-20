package com.freepdf.manager.db

import android.content.Context
import com.freepdf.manager.core.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * Business logic on top of [FileDao]: registration of created files,
 * recycle-bin operations, favorites and storage directories.
 */
@Singleton
class FileRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val dao: FileDao = AppDatabase.get(context).fileDao()

    /** Directory where the app stores its own generated files. */
    val appFilesDir: File
        get() = File(context.filesDir, "pdfs").apply { mkdirs() }

    val daoFlow: Flow<List<FileEntity>> get() = dao.observeRecent(20)

    fun observeByFolder(folderId: Long): Flow<List<FileEntity>> = dao.observeByFolder(folderId)

    fun observeFavorites(): Flow<List<FileEntity>> = dao.observeFavorites()

    fun observeDeleted(): Flow<List<FileEntity>> = dao.observeDeleted()

    fun observeRecent(limit: Int): Flow<List<FileEntity>> = dao.observeRecent(limit)

    fun search(query: String): List<FileEntity> = dao.search(query)

    /**
     * Registers (or re-registers) a file on disk in the database.
     * Returns the entity.
     */
    suspend fun register(
        file: File,
        folderId: Long = 0L,
        pageCount: Int = 0,
        kind: String? = null
    ): FileEntity {
        val existing = dao.byPath(file.absolutePath)
        val now = System.currentTimeMillis()
        val entity = existing?.copy(
            name = file.name,
            size = file.length(),
            page_count = if (pageCount > 0) pageCount else existing.page_count,
            folder_id = folderId,
            updated_at = now
        ) ?: FileEntity(
            name = file.name,
            path = file.absolutePath,
            kind = kind ?: (if (Constants.Files.isPdf(file.name)) Constants.FileKind.PDF
                else Constants.FileKind.IMAGE),
            size = file.length(),
            page_count = pageCount,
            folder_id = folderId,
            created_at = now,
            updated_at = now
        )
        dao.upsert(entity)
        return entity
    }

    suspend fun softDelete(entity: FileEntity) =
        dao.softDelete(entity.id, System.currentTimeMillis())

    suspend fun restore(entity: FileEntity) =
        dao.restore(entity.id, System.currentTimeMillis())

    suspend fun hardDelete(entity: FileEntity) {
        File(entity.path).delete()
        dao.hardDelete(entity.id)
    }

    suspend fun emptyRecycleBin(entities: List<FileEntity>) {
        entities.forEach { File(it.path).delete() }
        dao.emptyRecycleBin()
    }

    suspend fun setFavorite(entity: FileEntity, favorite: Boolean) =
        dao.setFavorite(entity.id, favorite)

    suspend fun rename(entity: FileEntity, newName: String, dir: File): FileEntity? {
        val target = File(dir, newName)
        if (!File(entity.path).renameTo(target)) return null
        val updated = entity.copy(
            name = newName,
            path = target.absolutePath,
            updated_at = System.currentTimeMillis()
        )
        dao.update(updated)
        return updated
    }

    suspend fun byPath(path: String): FileEntity? = dao.byPath(path)
}
