package co.edu.unab.unabgo.cliente.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.edu.unab.unabgo.cliente.data.api.ApiResult
import co.edu.unab.unabgo.cliente.data.repo.SummaryKind
import co.edu.unab.unabgo.cliente.data.repo.SummaryRepository
import co.edu.unab.unabgo.cliente.data.session.Roles
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Estado de una tarjeta del resumen: cargando, con su total, o oculta porque la llamada falló (por ejemplo, un 403). */
sealed interface StatState {
    data object Loading : StatState
    data class Value(val total: Long) : StatState
    data object Hidden : StatState
}

/** Tarjetas del resumen según el rol, en el orden en que se muestran. */
fun summaryKindsFor(role: String?): List<SummaryKind> = when (role) {
    Roles.ADMIN -> listOf(SummaryKind.USERS, SummaryKind.PROGRAMS, SummaryKind.STUDY_PLANS, SummaryKind.ANNOUNCEMENTS)
    Roles.MANAGER -> listOf(SummaryKind.ANNOUNCEMENTS, SummaryKind.FAQS, SummaryKind.ROUTES, SummaryKind.LIVE_BUSES)
    else -> emptyList()
}

/** Totales del resumen del inicio de gestores y administradores; cada tarjeta carga y falla por separado. */
class HomeSummaryViewModel(private val repo: SummaryRepository, val kinds: List<SummaryKind>) : ViewModel() {
    private val _stats = MutableStateFlow<Map<SummaryKind, StatState>>(kinds.associateWith { StatState.Loading })
    val stats: StateFlow<Map<SummaryKind, StatState>> get() = _stats
    private var job: Job? = null

    /** Vuelve a pedir todos los totales; los que ya se veían se conservan mientras llegan los nuevos. */
    fun refresh() {
        job?.cancel()
        job = viewModelScope.launch {
            coroutineScope {
                kinds.map { kind ->
                    async {
                        val result = repo.total(kind)
                        _stats.value = _stats.value + (kind to when (result) {
                            is ApiResult.Success -> StatState.Value(result.data)
                            is ApiResult.Failure -> _stats.value[kind].takeIf { it is StatState.Value } ?: StatState.Hidden
                        })
                    }
                }.awaitAll()
            }
        }
    }
}
