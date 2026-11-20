package com.freepdf.manager.ui.files

import androidx.lifecycle.ViewModel
import com.freepdf.manager.db.FileEntity
import com.freepdf.manager.db.FileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Files tab: Recent / Favorites / Recycle Bin + search.
 */
@HiltViewModel
class FilesViewModel @Inject constructor(
    private val repository: FileRepository
) : ViewModel() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        const val TAB_RECENT = 0
        const val TAB_FAVORITES = 1
        const val TAB_BIN = 2
    }

    private val _tab = MutableStateFlow(TAB_RECENT)
    val tab: StateFlow<Int> = _tab

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val recentFlow = repository.observeRecent(50)
    private val favoritesFlow = repository.observeFavorites()
    private val binFlow = repository.observeDeleted()

    private val combined = kotlinx.coroutines.flow.combine(
        recentFlow, favoritesFlow, binFlow
    ) { r, f, b -> Triple(r, f, b) }

    /**
     * Active list, switching with the selected tab and updating live as
     * the underlying Room flows change.
     */
    val files: StateFlow<List<FileEntity>> =
        kotlinx.coroutines.flow.combine(_tab, combined) { tab, t ->
            when (tab) {
                TAB_FAVORITES -> t.second
                TAB_BIN -> t.third
                else -> t.first
            }
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun setTab(tab: Int) {
        scope.launch { _tab.value = tab }
    }

    fun setQuery(q: String) {
        scope.launch { _query.value = q }
    }

    suspend fun searchResults(q: String): List<FileEntity> {
        if (q.isBlank()) return emptyList()
        return repository.search(q)
    }

    fun softDelete(entity: FileEntity) {
        scope.launch { repository.softDelete(entity) }
    }

    fun restore(entity: FileEntity) {
        scope.launch { repository.restore(entity) }
    }

    fun hardDelete(entity: FileEntity) {
        scope.launch { repository.hardDelete(entity) }
    }

    fun emptyBin() {
        scope.launch {
            val entities = binFlow.value
            repository.emptyRecycleBin(entities)
        }
    }

    fun setFavorite(entity: FileEntity, favorite: Boolean) {
        scope.launch { repository.setFavorite(entity, favorite) }
    }
}
