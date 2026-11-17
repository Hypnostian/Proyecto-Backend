package co.edu.unab.unabgo.cliente.ui.common

import co.edu.unab.unabgo.cliente.data.api.ApiError
import co.edu.unab.unabgo.cliente.data.api.ApiResult
import co.edu.unab.unabgo.cliente.data.api.PageMeta
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Estado de una pantalla que carga un solo recurso: cargando, listo (con posible error al refrescar) o fallido. */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Ready<T>(val data: T, val refreshing: Boolean = false, val error: ApiError? = null) : UiState<T>
    data class Failed(val error: ApiError) : UiState<Nothing>
}

/** Carga o recarga el recurso: la primera vez muestra Loading; al refrescar conserva los datos anteriores. */
suspend fun <T> MutableStateFlow<UiState<T>>.reload(fetch: suspend () -> ApiResult<T>) {
    val previous = value as? UiState.Ready<T>
    value = previous?.copy(refreshing = true, error = null) ?: UiState.Loading
    value = when (val result = fetch()) {
        is ApiResult.Success -> UiState.Ready(result.data)
        is ApiResult.Failure ->
            if (previous != null) previous.copy(refreshing = false, error = result.error) else UiState.Failed(result.error)
    }
}

/** Estado de un formulario o de una acción puntual: enviando, error del servidor (con campos) o terminado. */
data class FormState(val submitting: Boolean = false, val error: ApiError? = null, val done: Boolean = false) {
    val fields: Map<String, String> get() = error?.fields.orEmpty()
    /** Error general (sin campos) para mostrarlo sobre el botón. */
    val generalError: String? get() = error?.takeIf { it.fields.isEmpty() }?.userMessage
}

/** Ejecuta una acción de formulario: marca "enviando", y deja el resultado en [FormState]. */
suspend fun <T> MutableStateFlow<FormState>.submit(call: suspend () -> ApiResult<T>, onSuccess: suspend (T) -> Unit = {}) {
    if (value.submitting) return
    value = FormState(submitting = true)
    when (val r = call()) {
        is ApiResult.Success -> {
            onSuccess(r.data)
            value = FormState(done = true)
        }
        is ApiResult.Failure -> value = FormState(error = r.error)
    }
}

/** Lista paginada: se refresca desde la primera página y agrega las siguientes con [PagedLoader.loadMore]. */
data class PagedState<T>(
    val items: List<T> = emptyList(),
    val page: Int = 0,
    val lastPage: Int = 1,
    val total: Long = 0,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val error: ApiError? = null
) {
    val canLoadMore: Boolean get() = !loading && !loadingMore && error == null && page in 1 until lastPage
}

class PagedLoader<T>(
    private val scope: CoroutineScope,
    private val fetch: suspend (page: Int) -> ApiResult<List<T>>
) {
    private val _state = MutableStateFlow(PagedState<T>())
    val state: StateFlow<PagedState<T>> get() = _state
    private var job: Job? = null

    /** Vuelve a la primera página; si ya había datos los conserva mientras carga. */
    fun refresh() {
        job?.cancel()
        val hadItems = _state.value.items.isNotEmpty()
        _state.value = _state.value.copy(loading = !hadItems, refreshing = hadItems, loadingMore = false, error = null)
        job = scope.launch {
            when (val r = fetch(1)) {
                is ApiResult.Success -> _state.value = r.toState(r.data, 1)
                is ApiResult.Failure -> _state.value = _state.value.copy(loading = false, refreshing = false, error = r.error)
            }
        }
    }

    fun loadMore() {
        val current = _state.value
        if (!current.canLoadMore) return
        _state.value = current.copy(loadingMore = true)
        job = scope.launch {
            when (val r = fetch(current.page + 1)) {
                is ApiResult.Success -> _state.value = r.toState(current.items + r.data, current.page + 1)
                is ApiResult.Failure -> _state.value = _state.value.copy(loadingMore = false, error = r.error)
            }
        }
    }

    /** Quita un elemento de la lista local (después de borrarlo en el servidor). */
    fun remove(predicate: (T) -> Boolean) {
        _state.value = _state.value.copy(items = _state.value.items.filterNot(predicate), total = (_state.value.total - 1).coerceAtLeast(0))
    }

    private fun ApiResult.Success<List<T>>.toState(items: List<T>, page: Int): PagedState<T> {
        val m: PageMeta? = meta
        return PagedState(items = items, page = page, lastPage = m?.lastPage ?: page, total = m?.total ?: items.size.toLong(), loading = false)
    }
}
