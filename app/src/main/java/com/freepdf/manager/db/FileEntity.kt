package com.freepdf.manager.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A file known to the app (PDF, image or folder).
 *
 * [path] is the absolute path on disk. [deleted] marks soft-deleted files
 * (Recycle Bin) — they are physically removed on "Empty Recycle Bin".
 */
@Entity(
    tableName = "files",
    indices = [
        Index(value = ["updated_at"]),
        Index(value = ["folder_id"]),
        Index(value = ["name"])
    ]
)
data class FileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val path: String,
    val kind: String,          // Constants.FileKind.PDF / IMAGE / FOLDER
    val size: Long = 0L,
    val page_count: Int = 0,
    val folder_id: Long = 0L,  // 0 = root
    val is_favorite: Boolean = false,
    val created_at: Long,
    val updated_at: Long,
    val deleted: Boolean = false
)
