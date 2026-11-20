package com.freepdf.manager.ui.home

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
 * View model for the Home screen: recent files + navigation intents.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: FileRepository
) : ViewModel() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val recent: StateFlow<List<FileEntity>> = repository.observeRecent(10)
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val _pendingOpen = MutableStateFlow<FileEntity?>(null)
    val pendingOpen: StateFlow<FileEntity?> = _pendingOpen

    fun requestOpen(entity: FileEntity) {
        scope.launch { _pendingOpen.value = entity }
    }

    fun clearPending() {
        scope.launch { _pendingOpen.value = null }
    }

    /** Toggles the favorite flag on a file. */
    fun toggleFavorite(entity: FileEntity) {
        scope.launch {
            repository.setFavorite(entity, !entity.is_favorite)
        }
    }
}
