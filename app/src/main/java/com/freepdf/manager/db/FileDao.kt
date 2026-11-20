package com.freepdf.manager.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FileDao {

    // ---------- queries ----------

    @Query("SELECT * FROM files WHERE deleted = 0 AND folder_id = :folderId ORDER BY updated_at DESC")
    fun observeByFolder(folderId: Long): Flow<List<FileEntity>>

    @Query("SELECT * FROM files WHERE deleted = 0 AND is_favorite = 1 ORDER BY updated_at DESC")
    fun observeFavorites(): Flow<List<FileEntity>>

    @Query("SELECT * FROM files WHERE deleted = 1 ORDER BY updated_at DESC")
    fun observeDeleted(): Flow<List<FileEntity>>

    @Query("SELECT * FROM files WHERE deleted = 0 AND name LIKE '%' || :query || '%' ORDER BY updated_at DESC")
    fun search(query: String): List<FileEntity>

    @Query("SELECT * FROM files WHERE id = :id")
    suspend fun byId(id: Long): FileEntity?

    @Query("SELECT * FROM files WHERE path = :path LIMIT 1")
    suspend fun byPath(path: String): FileEntity?

    @Query("SELECT * FROM files WHERE deleted = 0 ORDER BY updated_at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<FileEntity>>

    // ---------- mutations ----------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FileEntity): Long

    @Update
    suspend fun update(entity: FileEntity)

    @Query("UPDATE files SET deleted = 1, updated_at = :now WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long)

    @Query("UPDATE files SET deleted = 0, updated_at = :now WHERE id = :id")
    suspend fun restore(id: Long, now: Long)

    @Query("DELETE FROM files WHERE id = :id")
    suspend fun hardDelete(id: Long)

    @Query("DELETE FROM files WHERE deleted = 1")
    suspend fun emptyRecycleBin()

    @Query("UPDATE files SET is_favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)
}
