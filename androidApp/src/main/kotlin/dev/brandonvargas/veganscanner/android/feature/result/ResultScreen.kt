package dev.brandonvargas.veganscanner.android.feature.result

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.brandonvargas.veganscanner.R
import dev.brandonvargas.veganscanner.android.ui.components.labelRes
import dev.brandonvargas.veganscanner.android.ui.components.messageRes
import dev.brandonvargas.veganscanner.android.ui.components.style
import dev.brandonvargas.veganscanner.core.common.AppError
import dev.brandonvargas.veganscanner.core.model.FlaggedIngredient
import dev.brandonvargas.veganscanner.core.model.IngredientVeganStatus
import dev.brandonvargas.veganscanner.core.model.IngredientsSource
import dev.brandonvargas.veganscanner.core.model.Product
import dev.brandonvargas.veganscanner.core.model.VeganStatus
import dev.brandonvargas.veganscanner.core.model.VeganVerdict
import dev.brandonvargas.veganscanner.feature.scanner.domain.research.ResearchIssue
import dev.brandonvargas.veganscanner.feature.scanner.presentation.result.ProductResultAction
import dev.brandonvargas.veganscanner.feature.scanner.presentation.result.ProductResultUiState
import dev.brandonvargas.veganscanner.feature.scanner.presentation.result.ProductResultViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ResultScreen(
    barcode: String,
    onBack: () -> Unit,
    onScanLabel: () -> Unit,
    viewModel: ProductResultViewModel = koinViewModel { parametersOf(barcode) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ResultContent(state = state, onBack = onBack, onScanLabel = onScanLabel, onAction = viewModel::onAction)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultContent(
    state: ProductResultUiState,
    onBack: () -> Unit,
    onScanLabel: () -> Unit,
    onAction: (ProductResultAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.result_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                ProductResultUiState.Loading -> {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }

                is ProductResultUiState.Found -> {
                    FoundContent(
                        state = state,
                        onScanLabel = onScanLabel,
                        onReport = { onAction(ProductResultAction.ReportResearched(it)) },
                    )
                }

                is ProductResultUiState.NotFound -> {
                    NotFoundContent(state.barcode, onScanAnother = onBack, onScanLabel = onScanLabel)
                }

                is ProductResultUiState.Error -> {
                    ErrorContent(
                        state.error,
                        onRetry = { onAction(ProductResultAction.Retry) },
                        onScanLabel = onScanLabel,
                    )
                }
            }
        }
    }
}

@Composable
private fun FoundContent(
    state: ProductResultUiState.Found,
    onScanLabel: () -> Unit,
    onReport: (String) -> Unit,
) {
    val product = state.product
    val verdict = state.verdict
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        VerdictBanner(verdict)
        if (state.isResearching) ResearchingIndicator()
        state.researchIssue?.let { if (state.unresearchedCount > 0) ResearchIssueNote(it, state.unresearchedCount) }
        ProductHeader(product)
        if (verdict.flaggedIngredients.isNotEmpty()) {
            FlaggedIngredients(verdict, reportedKeys = state.reportedKeys, onReport = onReport)
        }
        if (verdict.researched.isNotEmpty()) {
            ResearchedIngredients(verdict.researched, reportedKeys = state.reportedKeys, onReport = onReport)
        }
        if (!verdict.isConclusive && !state.isResearching) {
            InconclusiveNotice(
                fromLabel = product.ingredientsSource == IngredientsSource.LABEL_SCAN,
                onScanLabel = onScanLabel,
            )
        }
        product.ingredientsText?.let { IngredientsText(it) }
        HorizontalDivider()
        Text(
            text = stringResource(R.string.result_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun VerdictBanner(verdict: VeganVerdict) {
    val style = verdict.status.style()
    Card(colors = CardDefaults.cardColors(containerColor = style.container, contentColor = style.content)) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(style.icon, contentDescription = null, modifier = Modifier.size(48.dp))
            Column(Modifier.padding(start = 16.dp)) {
                Text(
                    text = stringResource(style.label),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() },
                )
                Text(stringResource(verdict.source.labelRes()), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ProductHeader(product: Product) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (product.imageUrl != null) {
            AsyncImage(
                model = product.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)),
            )
        }
        Column(Modifier.padding(start = if (product.imageUrl != null) 16.dp else 0.dp)) {
            Text(
                text = product.name ?: stringResource(R.string.product_unnamed),
                style = MaterialTheme.typography.titleLarge,
            )
            product.brands?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Text(
                text = product.barcode.value,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FlaggedIngredients(
    verdict: VeganVerdict,
    reportedKeys: Set<String>,
    onReport: (String) -> Unit,
) {
    val title =
        when (verdict.status) {
            VeganStatus.NON_VEGAN -> R.string.flagged_non_vegan
            VeganStatus.LIKELY_VEGAN -> R.string.flagged_unrecognized
            else -> R.string.flagged_check
        }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleMedium,
            modifier =
                Modifier.semantics {
                    heading()
                },
        )
        verdict.flaggedIngredients.forEach { flagged ->
            val reason =
                when (flagged.status) {
                    IngredientVeganStatus.NO -> R.string.ingredient_status_no
                    IngredientVeganStatus.MAYBE -> R.string.ingredient_status_maybe
                    IngredientVeganStatus.UNKNOWN, IngredientVeganStatus.YES -> R.string.ingredient_status_unknown
                }
            Text("• ${flagged.name} — ${stringResource(reason)}", style = MaterialTheme.typography.bodyLarge)
            if (flagged.note == null && flagged.researchKey == null) return@forEach
            Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                flagged.note?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (flagged.researchKey != null) {
                    // An AI explanation of a dictionary finding: the status above didn't come from the AI.
                    Text(
                        stringResource(R.string.research_explanation_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ResearchLinks(flagged, reportedKeys, onReport)
                }
            }
        }
    }
}

/** Research didn't happen for some ingredients: say so (and what the user can do) instead of failing silently. */
@Composable
private fun ResearchIssueNote(issue: ResearchIssue, count: Int) {
    val (icon, message) =
        when (issue) {
            ResearchIssue.OFFLINE -> Icons.Rounded.CloudOff to R.plurals.research_issue_offline
            ResearchIssue.BUSY -> Icons.Rounded.Schedule to R.plurals.research_issue_busy
            ResearchIssue.UNAVAILABLE -> Icons.Rounded.ErrorOutline to R.plurals.research_issue_unavailable
        }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(
            pluralStringResource(message, count, count),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
private fun ResearchingIndicator() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        Text(
            stringResource(R.string.research_in_progress),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

/** Ingredients resolved by AI web research: always shown with a warning, reasons, sources and a report option. */
@Composable
private fun ResearchedIngredients(
    researched: List<FlaggedIngredient>,
    reportedKeys: Set<String>,
    onReport: (String) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                Text(
                    stringResource(R.string.research_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp).semantics { heading() },
                )
            }
            Text(stringResource(R.string.research_warning), style = MaterialTheme.typography.bodySmall)
            researched.forEach { ingredient ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "${ingredient.name} — ${stringResource(ingredient.status.researchLabel())}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    ingredient.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    ResearchLinks(ingredient, reportedKeys, onReport)
                }
            }
        }
    }
}

/** Sources and the report option of a web-researched ingredient. */
@Composable
private fun ResearchLinks(
    ingredient: FlaggedIngredient,
    reportedKeys: Set<String>,
    onReport: (String) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    ingredient.sources.take(3).forEach { source ->
        Text(
            source.title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier.clickable { uriHandler.openUri(source.url) },
        )
    }
    ingredient.researchKey?.let { key ->
        if (key in reportedKeys) {
            Text(stringResource(R.string.research_reported), style = MaterialTheme.typography.labelMedium)
        } else {
            TextButton(onClick = { onReport(key) }) { Text(stringResource(R.string.research_report)) }
        }
    }
}

private fun IngredientVeganStatus.researchLabel(): Int =
    when (this) {
        IngredientVeganStatus.YES -> R.string.research_status_vegan
        IngredientVeganStatus.NO -> R.string.ingredient_status_no
        IngredientVeganStatus.MAYBE -> R.string.ingredient_status_maybe
        IngredientVeganStatus.UNKNOWN -> R.string.ingredient_status_unknown
    }

@Composable
private fun InconclusiveNotice(fromLabel: Boolean, onScanLabel: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row {
                Icon(Icons.Rounded.Info, contentDescription = null)
                Text(
                    text =
                        stringResource(
                            if (fromLabel) R.string.inconclusive_notice_label else R.string.inconclusive_notice,
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            Button(onClick = onScanLabel, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.DocumentScanner, contentDescription = null)
                Text(
                    stringResource(if (fromLabel) R.string.action_rescan_label else R.string.action_scan_label),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun IngredientsText(text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(R.string.ingredients_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun NotFoundContent(barcode: String, onScanAnother: () -> Unit, onScanLabel: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    CenteredMessage(
        icon = { Icon(Icons.Rounded.SearchOff, contentDescription = null, modifier = Modifier.size(56.dp)) },
        title = stringResource(R.string.not_found_title),
        body = stringResource(R.string.not_found_body, barcode),
    ) {
        Button(onClick = onScanLabel) { Text(stringResource(R.string.action_scan_label)) }
        OutlinedButton(onClick = onScanAnother) { Text(stringResource(R.string.action_scan_another)) }
        TextButton(onClick = { uriHandler.openUri("https://world.openfoodfacts.org/product/$barcode") }) {
            Text(stringResource(R.string.action_add_to_off))
        }
    }
}

@Composable
private fun ErrorContent(error: AppError, onRetry: () -> Unit, onScanLabel: () -> Unit) {
    CenteredMessage(
        icon = { Icon(Icons.Rounded.CloudOff, contentDescription = null, modifier = Modifier.size(56.dp)) },
        title = stringResource(R.string.error_title),
        body = stringResource(error.messageRes()),
    ) {
        Button(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
        OutlinedButton(onClick = onScanLabel) { Text(stringResource(R.string.action_scan_label_offline)) }
    }
}

@Composable
private fun CenteredMessage(
    icon: @Composable () -> Unit,
    title: String,
    body: String,
    actions: @Composable () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        icon()
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        actions()
    }
}
