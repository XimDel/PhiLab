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

/**
 * ViewModel de la pantalla de historial de experimentos.
 *
 * Expone la lista de sesiones guardadas como un [StateFlow] reactivo respaldado
 * por Room, y ofrece operaciones de eliminación y renombrado que se ejecutan en
 * [viewModelScope].
 *
 * @param repository Repositorio de sesiones que abstrae el acceso a la base de datos.
 */
class HistoryViewModel(
    private val repository: SessionRepository
) : ViewModel() {

    /**
     * Lista reactiva de todas las sesiones guardadas, ordenada por el repositorio.
     *
     * Se mantiene activa mientras haya suscriptores y se detiene tras 5 segundos
     * sin ninguno ([SharingStarted.WhileSubscribed] con timeout de 5 000 ms).
     * El valor inicial es una lista vacía hasta que Room emite el primer resultado.
     */
    val sessions: StateFlow<List<SessionEntity>> = repository
        .getAllSessions()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /**
     * Elimina de forma permanente la sesión con el identificador indicado.
     *
     * La operación se lanza en [viewModelScope] y no puede deshacerse.
     *
     * @param id Identificador único de la sesión a eliminar.
     */
    fun delete(id: Long) {
        viewModelScope.launch { repository.deleteSession(id) }
    }

    /**
     * Cambia el nombre del experimento asociado a la sesión indicada.
     *
     * @param id   Identificador único de la sesión a renombrar.
     * @param name Nuevo nombre; se asume que llega ya validado y sin espacios
     *             sobrantes desde la UI.
     */
    fun rename(id: Long, name: String) {
        viewModelScope.launch { repository.renameSession(id, name) }
    }
}

/**
 * Fábrica para instanciar [HistoryViewModel] con su dependencia [SessionRepository].
 *
 * Necesaria porque [HistoryViewModel] no tiene un constructor sin parámetros y
 * Compose no puede crearlo directamente con `viewModel()`.
 *
 * @param repository Repositorio de sesiones inyectado en el ViewModel creado.
 */
class HistoryViewModelFactory(
    private val repository: SessionRepository
) : ViewModelProvider.Factory {
    /**
     * Crea una instancia de [HistoryViewModel].
     *
     * @param modelClass Clase del ViewModel solicitado.
     * @return Instancia de [HistoryViewModel] casteada al tipo esperado.
     * @throws IllegalArgumentException si [modelClass] no es [HistoryViewModel].
     */
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return HistoryViewModel(repository) as T
    }
}