package co.edu.unab.unabgo.cliente.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import co.edu.unab.unabgo.cliente.data.api.ApiError
import co.edu.unab.unabgo.cliente.ui.common.FormState
import co.edu.unab.unabgo.cliente.ui.common.PagedState
import co.edu.unab.unabgo.cliente.ui.common.UiState
import co.edu.unab.unabgo.cliente.ui.theme.LocalRolePalette
import co.edu.unab.unabgo.cliente.ui.theme.Orange
import co.edu.unab.unabgo.cliente.i18n.Text

// ------------------------------------------------------------------------------------------------ barra y andamio

/** Barra superior con el degradado del rol (antes de iniciar sesión, morado institucional y la franja de cuatro colores). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    val p = LocalRolePalette.current
    Column {
        Box(Modifier.fillMaxWidth().background(Brush.horizontalGradient(p.gradient))) {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold, maxLines = 1) },
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent,
                    titleContentColor = p.onGradient, navigationIconContentColor = p.onGradient, actionIconContentColor = p.onGradient
                )
            )
        }
        if (p.neutral) BrandStrip()
    }
}

@Composable
fun AppScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        topBar = { AppTopBar(title, onBack, actions) },
        floatingActionButton = floatingActionButton,
        snackbarHost = snackbarHost,
        containerColor = MaterialTheme.colorScheme.background,
        content = content
    )
}

// ------------------------------------------------------------------------------------------------ tarjetas y botones

/** Tarjeta blanca con borde suave. Entra de forma escalonada; [accent] dibuja una barra de color a la izquierda. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    index: Int? = null,
    accent: Color? = null,
    container: Color = MaterialTheme.colorScheme.surface,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val counter = LocalEntranceCounter.current
    val order = remember { index ?: counter?.let { it.next++ } }
    val base = modifier.fillMaxWidth().let { if (order != null) it.entrance(order) else it }
    val shape = MaterialTheme.shapes.large
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    val inner: @Composable () -> Unit = {
        Row(Modifier.height(IntrinsicSize.Min)) {
            if (accent != null) Box(Modifier.width(6.dp).fillMaxHeight().background(accent))
            Column(Modifier.weight(1f).padding(contentPadding), content = content)
        }
    }
    if (onClick != null) Surface(onClick = onClick, modifier = base, shape = shape, color = container, border = border, content = inner)
    else Surface(modifier = base, shape = shape, color = container, border = border, content = inner)
}

@Composable
fun AppButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    Button(onClick = onClick, modifier = modifier.heightIn(min = 48.dp), enabled = enabled, shape = MaterialTheme.shapes.medium, content = content)
}

@Composable
fun AppOutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    val accent = LocalRolePalette.current.accentText
    OutlinedButton(
        onClick = onClick, modifier = modifier.heightIn(min = 48.dp), enabled = enabled, shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
        border = BorderStroke(1.5.dp, accent.copy(alpha = if (enabled) 0.7f else 0.25f)), content = content
    )
}

@Composable
fun AppTextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    TextButton(
        onClick = onClick, modifier = modifier, enabled = enabled, shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.textButtonColors(contentColor = LocalRolePalette.current.accentText), content = content
    )
}

/** Etiqueta pequeña con el color del rol (o el dado). */
@Composable
fun AppChip(text: String, modifier: Modifier = Modifier, container: Color = LocalRolePalette.current.container, content: Color = LocalRolePalette.current.onContainer) {
    Box(modifier.clip(CircleShape).background(container).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(text, color = content, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Row(modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.dp).height(18.dp).clip(CircleShape).background(LocalRolePalette.current.primary))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { AppTextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { AppTextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

// ------------------------------------------------------------------------------------------------ estados

@Composable
fun SkeletonBlock(modifier: Modifier = Modifier, height: Dp = 14.dp, fraction: Float = 1f) {
    Box(modifier.fillMaxWidth(fraction).height(height).clip(MaterialTheme.shapes.small).shimmer())
}

@Composable
fun SkeletonCard(modifier: Modifier = Modifier) {
    AppCard(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SkeletonBlock(height = 18.dp, fraction = 0.6f)
            SkeletonBlock(fraction = 0.9f)
            SkeletonBlock(fraction = 0.4f)
        }
    }
}

/** Esqueletos con brillo mientras carga, en lugar de un indicador giratorio. */
@Composable
fun SkeletonList(modifier: Modifier = Modifier, count: Int = 4) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) { repeat(count) { SkeletonCard() } }
}

@Composable
fun LoadingView(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(16.dp)) { SkeletonList() }
}

@Composable
private fun StateBadge(icon: ImageVector, tint: Color, container: Color) {
    Box(Modifier.size(64.dp).clip(CircleShape).background(container), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(30.dp))
    }
}

@Composable
fun EmptyView(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val p = LocalRolePalette.current
        StateBadge(Icons.Default.Info, p.accentText, p.container)
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ErrorView(error: ApiError, onRetry: (() -> Unit)?, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StateBadge(Icons.Default.Warning, MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.errorContainer)
        Text(error.userMessage, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        if (onRetry != null) AppOutlinedButton(onClick = onRetry) { Text("Reintentar") }
    }
}

@Composable
fun ErrorBanner(text: String) {
    Text(text, color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
}

@Composable
fun SuccessText(text: String, modifier: Modifier = Modifier) {
    Text(text, color = LocalRolePalette.current.success, fontWeight = FontWeight.SemiBold, modifier = modifier)
}

private enum class Kind { Loading, Failed, Ready }

/** Pantalla de un recurso: esqueletos al cargar, error con reintento, o el contenido con "tirar para refrescar". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> StateContent(
    state: UiState<T>,
    onRefresh: () -> Unit,
    padding: PaddingValues,
    content: @Composable (T) -> Unit
) {
    val kind = when (state) {
        UiState.Loading -> Kind.Loading
        is UiState.Failed -> Kind.Failed
        is UiState.Ready -> Kind.Ready
    }
    Box(Modifier.padding(padding).fillMaxSize()) {
        AnimatedContent(kind, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "state") { k ->
            when (k) {
                Kind.Loading -> LoadingView()
                Kind.Failed -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    (state as? UiState.Failed)?.let { ErrorView(it.error, onRefresh) }
                }
                Kind.Ready -> (state as? UiState.Ready)?.let { ready ->
                    PullToRefreshBox(isRefreshing = ready.refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
                        val counter = remember { EntranceCounter() }
                        CompositionLocalProvider(LocalEntranceCounter provides counter) {
                            Column(
                                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                ready.error?.let { ErrorBanner(it.userMessage) }
                                content(ready.data)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Lista paginada con esqueletos, vacío, error, refrescar y "cargar más" al llegar al final. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> PagedContent(
    state: PagedState<T>,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    padding: PaddingValues,
    emptyText: String,
    header: (LazyListScope.() -> Unit)? = null,
    row: @Composable (T) -> Unit
) {
    Box(Modifier.padding(padding).fillMaxSize()) {
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                header?.invoke(this)
                when {
                    state.loading -> item { SkeletonList() }
                    state.items.isEmpty() && state.error != null -> item { ErrorView(state.error, onRefresh) }
                    state.items.isEmpty() -> item { EmptyView(emptyText) }
                    else -> {
                        state.error?.let { e -> item { ErrorBanner(e.userMessage) } }
                        items(state.items.size) { i -> Box(Modifier.entrance(i)) { row(state.items[i]) } }
                        if (state.loadingMore) item { SkeletonCard() }
                        if (state.canLoadMore) item {
                            LaunchedEffect(state.page) { onLoadMore() }
                            AppOutlinedButton(onClick = onLoadMore, modifier = Modifier.fillMaxWidth()) { Text("Cargar más") }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------ formularios

@Composable
fun appFieldColors(): TextFieldColors {
    val accent = LocalRolePalette.current.accentText
    return OutlinedTextFieldDefaults.colors(
        focusedBorderColor = accent, focusedLabelColor = accent, cursorColor = accent,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        focusedContainerColor = MaterialTheme.colorScheme.surface, unfocusedContainerColor = MaterialTheme.colorScheme.surface
    )
}

/** Campo de búsqueda con lupa. */
@Composable
fun SearchField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true, shape = MaterialTheme.shapes.medium,
        colors = appFieldColors(), modifier = modifier.fillMaxWidth()
    )
}

/** Campo de texto con el mensaje del servidor debajo (campos del backend en snake_case). */
@Composable
fun FormField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    form: FormState,
    field: String,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    number: Boolean = false,
    singleLine: Boolean = true,
    enabled: Boolean = !form.submitting
) {
    val error = form.fields[field]
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = singleLine,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        colors = appFieldColors(),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else if (number) KeyboardType.Decimal else KeyboardType.Text),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
fun SubmitButton(text: String, form: FormState, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        form.generalError?.let { ErrorBanner(it) }
        AppButton(onClick = onClick, enabled = enabled && !form.submitting, modifier = Modifier.fillMaxWidth()) {
            if (form.submitting) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(8.dp))
                Text("Enviando")
            } else Text(text)
        }
    }
}

/** Parsea "#RRGGBB" del backend; si es inválido usa el naranja institucional. */
fun parseColor(hex: String): Color = runCatching {
    Color(android.graphics.Color.parseColor(hex))
}.getOrDefault(Orange)
