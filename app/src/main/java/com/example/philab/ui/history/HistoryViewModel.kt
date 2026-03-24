package com.example.philab.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.philab.data.local.entity.SessionEntity
import com.example.philab.data.repository.SessionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(
    private val repository: SessionRepository
) : ViewModel() {

    /** Lista reactiva de sesiones — se actualiza automáticamente con Room Flow. */
    val sessions: StateFlow<List<SessionEntity>> = repository
        .getAllSessions()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    fun delete(id: Long) {
        viewModelScope.launch { repository.deleteSession(id) }
    }

    fun rename(id: Long, name: String) {
        viewModelScope.launch { repository.renameSession(id, name) }
    }
}

class HistoryViewModelFactory(
    private val repository: SessionRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return HistoryViewModel(repository) as T
    }
}