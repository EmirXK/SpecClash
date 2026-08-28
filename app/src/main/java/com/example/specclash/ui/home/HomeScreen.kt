package com.example.specclash.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.specclash.SpecClashApplication
import com.example.specclash.domain.SpecComparator
import com.example.specclash.ui.home.HomeViewModel.Slot
import com.example.specclash.ui.home.components.CategorySection
import com.example.specclash.ui.home.components.DeviceSlot
import com.example.specclash.ui.home.components.EmptyState
import com.example.specclash.ui.home.components.ErrorState
import com.example.specclash.ui.home.components.HeroHeader
import com.example.specclash.ui.home.components.NavDrawerContent
import com.example.specclash.ui.home.components.PriceOverrideDialog
import com.example.specclash.ui.home.components.SkeletonSpecSection
import com.example.specclash.ui.home.components.SpecCategory
import com.example.specclash.ui.home.components.SpecMatrixBuilder
import com.example.specclash.ui.home.components.SwapButton
import com.example.specclash.ui.home.components.VerdictCard
import com.example.specclash.ui.search.SearchDialog
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as SpecClashApplication
    val viewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.Factory(app.repository, app.preferences),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val results by viewModel.searchResults.collectAsStateWithLifecycle()
    val searchLoading by viewModel.isSearchLoading.collectAsStateWithLifecycle()
    val searchError by viewModel.searchError.collectAsStateWithLifecycle()
    val overridePriceA by viewModel.overridePriceA.collectAsStateWithLifecycle()
    val overridePriceB by viewModel.overridePriceB.collectAsStateWithLifecycle()
    val preset by viewModel.selectedPreset.collectAsStateWithLifecycle()
    val excludeLegacy by viewModel.excludeLegacyDevices.collectAsStateWithLifecycle()

    var pickerSlot by remember { mutableStateOf<Slot?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var editingPriceSlot by remember { mutableStateOf<Slot?>(null) }

    val accentA = MaterialTheme.colorScheme.primary
    val accentB = MaterialTheme.colorScheme.secondary

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val isCurrentPinned by viewModel.isCurrentMatchupPinned.collectAsStateWithLifecycle()

    // Auto-dismiss snackbar on state changes
    androidx.compose.runtime.LaunchedEffect(state.snackbar) {
        val msg = state.snackbar
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeSnackbar()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            NavDrawerContent(
                viewModel = viewModel,
                onSelectMatchup = { slugA, slugB ->
                    viewModel.loadComparison(slugA, slugB)
                },
                onCloseDrawer = {
                    scope.launch { drawerState.close() }
                },
            )
        },
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
            topBar = {
                com.example.specclash.ui.home.components.SpecClashTopAppBar(
                    onNavigationIconClick = { scope.launch { drawerState.open() } },
                    onPinMatchupClick = { viewModel.togglePinCurrentMatchup() },
                    isCurrentMatchupPinned = isCurrentPinned,
                )
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            HomeBody(
                state = state,
                accentA = accentA,
                accentB = accentB,
                overridePriceA = overridePriceA,
                overridePriceB = overridePriceB,
                preset = preset,
                excludeLegacy = excludeLegacy,
                onSlotClicked = { slot ->
                    pickerSlot = slot
                    searchQuery = ""
                },
                onClearSlot = viewModel::clearSlot,
                onSwap = viewModel::swap,
                onDifferencesOnlyChange = viewModel::setDifferencesOnly,
                onRetry = { slot -> viewModel.retry(slot) },
                onEditPriceA = { editingPriceSlot = Slot.A },
                onEditPriceB = { editingPriceSlot = Slot.B },
            )
        }
    }

    if (pickerSlot != null) {
        val slot = pickerSlot!!
        SearchDialog(
            sheetState = rememberModalBottomSheetState(),
            query = searchQuery,
            results = results,
            loading = searchLoading,
            error = searchError,
            onQueryChange = {
                searchQuery = it
                viewModel.onSearchQueryChange(it)
            },
            onPicked = { viewModel.pickDevice(slot, it) },
            onDismiss = { pickerSlot = null },
            onRetry = { viewModel.retrySearch() },
        )
    }

    if (editingPriceSlot != null) {
        val slot = editingPriceSlot!!
        val deviceName = if (slot == Slot.A) state.deviceA?.name else state.deviceB?.name
        val current = if (slot == Slot.A) overridePriceA else overridePriceB
        PriceOverrideDialog(
            deviceName = deviceName,
            currentValue = current,
            onConfirm = { price ->
                if (slot == Slot.A) viewModel.setOverridePriceA(price) else viewModel.setOverridePriceB(price)
                editingPriceSlot = null
            },
            onDismiss = { editingPriceSlot = null },
        )
    }
    } // end ModalNavigationDrawer
}

@Composable
private fun HomeBody(
    state: HomeViewModel.UiState,
    accentA: Color,
    accentB: Color,
    overridePriceA: Double?,
    overridePriceB: Double?,
    preset: SpecComparator.ScoringPreset,
    excludeLegacy: Boolean,
    onSlotClicked: (Slot) -> Unit,
    onClearSlot: (Slot) -> Unit,
    onSwap: () -> Unit,
    onDifferencesOnlyChange: (Boolean) -> Unit,
    onRetry: (Slot) -> Unit,
    onEditPriceA: () -> Unit,
    onEditPriceB: () -> Unit,
) {
    // Build the full weighted verdict once when both specs are loaded, or
    // whenever a manual price override or scoring preset changes.
    val verdict: SpecComparator.FullComparisonVerdict? = remember(
        state.specA?.slug, state.specB?.slug, overridePriceA, overridePriceB, preset, excludeLegacy,
    ) {
        val a = state.specA
        val b = state.specB
        if (a != null && b != null) {
            SpecComparator.buildVerdict(
                a, b,
                preset = preset,
                excludeLegacy = excludeLegacy,
                overridePriceAUsd = overridePriceA,
                overridePriceBUsd = overridePriceB,
            )
        } else null
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DeviceSlot(
                    label = "Device A",
                    device = state.deviceA,
                    loading = state.loadingA,
                    accent = accentA,
                    onClick = { onSlotClicked(Slot.A) },
                    onClear = { onClearSlot(Slot.A) },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                SwapButton(
                    onClick = onSwap,
                    enabled = state.deviceA != null && state.deviceB != null,
                )
                Spacer(Modifier.width(8.dp))
                DeviceSlot(
                    label = "Device B",
                    device = state.deviceB,
                    loading = state.loadingB,
                    accent = accentB,
                    onClick = { onSlotClicked(Slot.B) },
                    onClear = { onClearSlot(Slot.B) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        when {
            state.deviceA == null || state.deviceB == null -> {
                item { EmptyState() }
            }
            state.loadingA || state.loadingB -> {
                item {
                    HeroHeader(
                        specA = state.specA,
                        specB = state.specB,
                        accentA = accentA,
                        accentB = accentB,
                    )
                }
                items(SpecMatrixBuilder.categories) { _ ->
                    SkeletonSpecSection()
                }
            }
            state.errorA != null || state.errorB != null -> {
                item {
                    HeroHeader(
                        specA = state.specA,
                        specB = state.specB,
                        accentA = accentA,
                        accentB = accentB,
                    )
                }
                item {
                    ErrorState(
                        message = state.errorA ?: state.errorB,
                        onRetry = {
                            if (state.errorA != null) onRetry(Slot.A) else onRetry(Slot.B)
                        },
                    )
                }
            }
            else -> ComparisonContent(
                state = state,
                verdict = verdict,
                accentA = accentA,
                accentB = accentB,
                isPriceACustom = overridePriceA != null,
                isPriceBCustom = overridePriceB != null,
                onDifferencesOnlyChange = onDifferencesOnlyChange,
                onEditPriceA = onEditPriceA,
                onEditPriceB = onEditPriceB,
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.ComparisonContent(
    state: HomeViewModel.UiState,
    verdict: SpecComparator.FullComparisonVerdict?,
    accentA: Color,
    accentB: Color,
    isPriceACustom: Boolean,
    isPriceBCustom: Boolean,
    onDifferencesOnlyChange: (Boolean) -> Unit,
    onEditPriceA: () -> Unit,
    onEditPriceB: () -> Unit,
) {
    item {
        HeroHeader(
            specA = state.specA,
            specB = state.specB,
            accentA = accentA,
            accentB = accentB,
            priceA = verdict?.value?.priceA,
            priceB = verdict?.value?.priceB,
            isPriceACustom = isPriceACustom,
            isPriceBCustom = isPriceBCustom,
            onEditPriceA = onEditPriceA,
            onEditPriceB = onEditPriceB,
        )
    }
    item {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(14.dp),
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Show differences only",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = state.showDifferencesOnly,
                onCheckedChange = onDifferencesOnlyChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }
    }
    val specA = state.specA
    val specB = state.specB
    if (specA != null && specB != null && verdict != null) {
        val categoryScoreFor: (SpecCategory) -> SpecComparator.CategoryScore? = { cat ->
            when (cat.sourceKey) {
                "Display" -> verdict.display
                "Main Camera" -> verdict.camera
                "Platform" -> verdict.performance
                "Battery" -> verdict.battery
                "Body" -> verdict.build
                "Misc" -> buildPriceCategoryScore(specA, specB)
                else -> null
            }
        }
        items(SpecMatrixBuilder.categories) { cat ->
            val rows = SpecMatrixBuilder.buildRows(
                specA = specA,
                specB = specB,
                category = cat,
                differencesOnly = state.showDifferencesOnly,
            )
            if (rows.isNotEmpty()) {
                CategorySection(
                    title = cat.title,
                    rows = rows,
                    accentA = accentA,
                    accentB = accentB,
                    categoryScore = categoryScoreFor(cat),
                    initiallyExpanded = cat.defaultOpen,
                )
            }
        }
        item {
            Spacer(Modifier.height(4.dp))
            VerdictCard(
                verdict = verdict,
                accentA = accentA,
                accentB = accentB,
                isPriceACustom = isPriceACustom,
                isPriceBCustom = isPriceBCustom,
                onEditPriceA = onEditPriceA,
                onEditPriceB = onEditPriceB,
            )
        }
    }
}

/**
 * Build a non-hardware "price" CategoryScore for the Misc & Pricing section.
 * Lower USD price is *better*, so we present the score as "affordability"
 * (inverse of price). Both devices without a price get 0.
 */
private fun buildPriceCategoryScore(
    specA: com.example.specclash.domain.PhoneSpec,
    specB: com.example.specclash.domain.PhoneSpec,
): SpecComparator.CategoryScore {
    val priceA = SpecComparator.extractPrice(specA.specs["Misc"]?.get("Price"))
    val priceB = SpecComparator.extractPrice(specB.specs["Misc"]?.get("Price"))
    // Use a soft "score per dollar of affordability". When one side is unpriced,
    // it gets 0; the priced side gets 100. When both are priced, normalise so
    // the cheaper phone wins.
    val maxUsd = listOfNotNull(priceA?.amountUsd, priceB?.amountUsd).maxOrNull() ?: 0.0
    val scoreA = when {
        priceA == null || priceA.amountUsd <= 0.0 -> 0.0
        maxUsd <= 0.0 -> 100.0
        else -> ((maxUsd - priceA.amountUsd) / maxUsd * 100.0 + 1.0)
    }
    val scoreB = when {
        priceB == null || priceB.amountUsd <= 0.0 -> 0.0
        maxUsd <= 0.0 -> 100.0
        else -> ((maxUsd - priceB.amountUsd) / maxUsd * 100.0 + 1.0)
    }
    val winner = when {
        scoreA > scoreB + 0.5 -> SpecComparator.Winner.A
        scoreB > scoreA + 0.5 -> SpecComparator.Winner.B
        else -> SpecComparator.Winner.TIE
    }
    val summaryA = priceA?.formattedDisplay ?: "Unpriced"
    val summaryB = priceB?.formattedDisplay ?: "Unpriced"
    val ratioText = if (priceA != null && priceB != null) {
        val cheaper = if (priceA.amountUsd <= priceB.amountUsd) specA.name else specB.name
        val cheaperUsd = minOf(priceA.amountUsd, priceB.amountUsd)
        val ratio = if (priceA.amountUsd > 0 && priceB.amountUsd > 0) {
            maxOf(priceA.amountUsd, priceB.amountUsd) / cheaperUsd
        } else 1.0
        if (ratio > 1.05) {
            "$cheaper is " + String.format("%.1fx", ratio) + " cheaper"
        } else {
            "Both priced similarly"
        }
    } else {
        "Pricing information incomplete"
    }
    return SpecComparator.CategoryScore(
        category = SpecComparator.Category.PRICE,
        scoreA = scoreA.coerceIn(0.0, 100.0),
        scoreB = scoreB.coerceIn(0.0, 100.0),
        ratioText = ratioText,
        winner = winner,
        summaryA = summaryA,
        summaryB = summaryB,
    )
}
