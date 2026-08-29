package com.example.specclash.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.specclash.data.SpecClashRepository
import com.example.specclash.data.local.ComparisonHistoryEntity
import com.example.specclash.data.local.SavedMatchupEntity
import com.example.specclash.data.preferences.UserPreferencesRepository
import com.example.specclash.domain.PhoneSpec
import com.example.specclash.domain.SearchDevice
import com.example.specclash.domain.SpecComparator.ScoringPreset
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val repository: SpecClashRepository,
    private val preferences: UserPreferencesRepository,
) : ViewModel() {

    data class UiState(
        val deviceA: SearchDevice? = null,
        val deviceB: SearchDevice? = null,
        val specA: PhoneSpec? = null,
        val specB: PhoneSpec? = null,
        val loadingA: Boolean = false,
        val loadingB: Boolean = false,
        val errorA: String? = null,
        val errorB: String? = null,
        val showDifferencesOnly: Boolean = false,
        val isSwapped: Boolean = false,
        /** Snackbar message - set by the drawer actions. */
        val snackbar: String? = null,
    ) {
        val canCompare: Boolean get() = deviceA != null && deviceB != null
        val bothLoaded: Boolean get() = specA != null && specB != null
        val anyLoading: Boolean get() = loadingA || loadingB
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    // Debounced search
    private val searchQuery = MutableStateFlow("")
    private val _searchResults = MutableStateFlow<List<SearchDevice>>(emptyList())
    val searchResults: StateFlow<List<SearchDevice>> = _searchResults.asStateFlow()

    /**
     * True only while a search request is actually in flight. Unlike
     * inferring "loading" from `query.isNotBlank() && results.isEmpty()`,
     * this correctly settles to `false` when a query genuinely finds zero
     * matches, instead of spinning forever.
     */
    private val _isSearchLoading = MutableStateFlow(false)
    val isSearchLoading: StateFlow<Boolean> = _isSearchLoading.asStateFlow()

    /** Set when the last search attempt failed (network/server error). `null` otherwise. */
    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError.asStateFlow()

    // Drawer / persistence flows
    val recentHistory: StateFlow<List<ComparisonHistoryEntity>> =
        repository.recentHistory(10)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val savedMatchups: StateFlow<List<SavedMatchupEntity>> =
        repository.savedMatchups()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val selectedPreset: StateFlow<ScoringPreset> = preferences.selectedPreset
    val excludeLegacyDevices: StateFlow<Boolean> = preferences.excludeLegacyDevices

    /**
     * Manual street-price overrides entered via the price override dialog.
     * When non-null, these take priority over the upstream-scraped price in
     * the value-for-money calculation (see [com.example.specclash.domain.SpecComparator.buildVerdict]).
     */
    val overridePriceA: MutableStateFlow<Double?> = MutableStateFlow(null)
    val overridePriceB: MutableStateFlow<Double?> = MutableStateFlow(null)

    fun setOverridePriceA(price: Double?) {
        overridePriceA.value = price
    }

    fun setOverridePriceB(price: Double?) {
        overridePriceB.value = price
    }

    fun resetPriceOverrides() {
        overridePriceA.value = null
        overridePriceB.value = null
    }

    /** Current cache size (row count of cached_phones). */
    private val _cachedPhoneCount = MutableStateFlow(0)
    val cachedPhoneCount: StateFlow<Int> = _cachedPhoneCount.asStateFlow()

    init {
        viewModelScope.launch {
            _cachedPhoneCount.value = repository.cachedPhoneCount()
        }
    }

    /** Whether the active (specA, specB) matchup is currently pinned. */
    val isCurrentMatchupPinned: StateFlow<Boolean> = _state
        .flatMapLatest { s ->
            if (s.specA == null || s.specB == null) flowOf(false)
            else repository.isMatchupSaved(s.specA.slug, s.specB.slug)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    @OptIn(FlowPreview::class)
    private val searchJob: Job = viewModelScope.launch {
        searchQuery
            .debounce(300L)
            .distinctUntilChanged()
            .collect { q -> performSearch(q) }
    }

    /**
     * Runs one search attempt for [q] and updates [searchResults],
     * [isSearchLoading], and [searchError]. [isSearchLoading] is always
     * cleared in both the success and failure branches — including when a
     * query legitimately finds zero matches — so the spinner never spins
     * forever. Ranking/deduplication of multi-word queries already happened
     * in [SpecClashRepository.searchPhones]; this just surfaces the result.
     */
    private suspend fun performSearch(q: String) {
        if (q.isBlank()) {
            _searchResults.value = emptyList()
            _isSearchLoading.value = false
            _searchError.value = null
            return
        }
        _isSearchLoading.value = true
        _searchError.value = null
        repository.searchPhones(q)
            .onSuccess { devices ->
                _searchResults.value = devices
                _isSearchLoading.value = false
            }
            .onFailure { e ->
                _searchResults.value = emptyList()
                _isSearchLoading.value = false
                _searchError.value = e.localizedMessage ?: "Couldn't search right now. Check your connection and try again."
            }
    }

    fun onSearchQueryChange(q: String) {
        searchQuery.value = q
    }

    /** Re-runs the search for the current query text, e.g. from an error state's retry action. */
    fun retrySearch() {
        val q = searchQuery.value
        if (q.isBlank()) return
        viewModelScope.launch { performSearch(q) }
    }

    fun clearSearch() {
        searchQuery.value = ""
        _searchResults.value = emptyList()
        _isSearchLoading.value = false
        _searchError.value = null
    }

    fun pickDevice(slot: Slot, device: SearchDevice) {
        if (slot == Slot.A) {
            overridePriceA.value = null
            _state.update { it.copy(deviceA = device, loadingA = true, errorA = null) }
            loadSpec(device.slug, Slot.A)
        } else {
            overridePriceB.value = null
            _state.update { it.copy(deviceB = device, loadingB = true, errorB = null) }
            loadSpec(device.slug, Slot.B)
        }
    }

    fun clearSlot(slot: Slot) {
        if (slot == Slot.A) {
            overridePriceA.value = null
            _state.update { it.copy(deviceA = null, specA = null, errorA = null, loadingA = false) }
        } else {
            overridePriceB.value = null
            _state.update { it.copy(deviceB = null, specB = null, errorB = null, loadingB = false) }
        }
    }

    fun swap() {
        _state.update {
            it.copy(
                deviceA = it.deviceB,
                deviceB = it.deviceA,
                specA = it.specB,
                specB = it.specA,
                loadingA = it.loadingB,
                loadingB = it.loadingA,
                errorA = it.errorB,
                errorB = it.errorA,
                isSwapped = !it.isSwapped,
            )
        }
    }

    fun setDifferencesOnly(value: Boolean) {
        _state.update { it.copy(showDifferencesOnly = value) }
    }

    fun retry(slot: Slot) {
        val device = if (slot == Slot.A) _state.value.deviceA else _state.value.deviceB
        device?.let { loadSpec(it.slug, slot) }
    }

    fun consumeSnackbar() {
        _state.update { it.copy(snackbar = null) }
    }

    /**
     * Load a comparison from two cached slugs. Used by the navigation
     * drawer when the user taps a pinned or recent matchup. We don't
     * hit the network if the spec is already in memory.
     */
    fun loadComparison(slugA: String, slugB: String) {
        // Quick in-memory: if the user already has the same pair loaded, no-op.
        val s = _state.value
        if (s.specA?.slug == slugA && s.specB?.slug == slugB) return
        _state.update { it.copy(snackbar = "Loading $slugA vs $slugB from cache\u2026") }
        viewModelScope.launch {
            val specA = repository.getPhoneSpec(slugA).getOrNull()
            val specB = repository.getPhoneSpec(slugB).getOrNull()
            val deviceA = specA?.let { SearchDevice(name = it.name, slug = it.slug, thumbnail = it.image) }
            val deviceB = specB?.let { SearchDevice(name = it.name, slug = it.slug, thumbnail = it.image) }
            _state.update {
                it.copy(
                    deviceA = deviceA ?: it.deviceA,
                    deviceB = deviceB ?: it.deviceB,
                    specA = specA ?: it.specA,
                    specB = specB ?: it.specB,
                    loadingA = false,
                    loadingB = false,
                    errorA = if (specA == null) "Failed to load $slugA from cache" else null,
                    errorB = if (specB == null) "Failed to load $slugB from cache" else null,
                    snackbar = if (specA != null && specB != null) "Loaded from cache" else it.snackbar,
                )
            }
        }
    }

    fun setPreset(preset: ScoringPreset) {
        preferences.setSelectedPreset(preset)
    }

    fun setExcludeLegacyDevices(value: Boolean) {
        preferences.setExcludeLegacyDevices(value)
    }

    fun clearOfflineCache() {
        viewModelScope.launch {
            repository.clearOfflineCache()
            refreshCacheCount()
            _state.update { it.copy(snackbar = "Offline cache cleared.") }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
            _state.update { it.copy(snackbar = "Recent comparison history cleared.") }
        }
    }

    /**
     * Pin or unpin the currently-loaded matchup. The icon in the top bar
     * reflects the resulting state via [isCurrentMatchupPinned].
     */
    fun togglePinCurrentMatchup() {
        val s = _state.value
        val specA = s.specA
        val specB = s.specB
        if (specA == null || specB == null) {
            _state.update { it.copy(snackbar = "Load two devices first to pin a matchup.") }
            return
        }
        viewModelScope.launch {
            val currentlyPinned = isCurrentMatchupPinned.value
            if (currentlyPinned) {
                repository.deleteMatchup(specA.slug, specB.slug)
                _state.update { it.copy(snackbar = "Removed from pinned matchups.") }
            } else {
                repository.saveMatchup(
                    slugA = specA.slug, nameA = specA.name, imageA = specA.image,
                    slugB = specB.slug, nameB = specB.name, imageB = specB.image,
                )
                _state.update { it.copy(snackbar = "Pinned \u2014 find it in the drawer.") }
            }
        }
    }

    fun deletePinnedMatchup(slugA: String, slugB: String) {
        viewModelScope.launch {
            repository.deleteMatchup(slugA, slugB)
        }
    }

    fun refreshCacheCount() {
        viewModelScope.launch {
            _cachedPhoneCount.value = repository.cachedPhoneCount()
        }
    }

    private fun loadSpec(slug: String, slot: Slot) {
        viewModelScope.launch {
            val result = repository.getPhoneSpec(slug)
            result.onSuccess { spec ->
                _state.update {
                    if (slot == Slot.A) {
                        it.copy(
                            specA = spec, loadingA = false, errorA = null,
                            deviceA = it.deviceA ?: SearchDevice(
                                name = spec.name, slug = spec.slug, thumbnail = spec.image,
                            ),
                        )
                    } else {
                        it.copy(
                            specB = spec, loadingB = false, errorB = null,
                            deviceB = it.deviceB ?: SearchDevice(
                                name = spec.name, slug = spec.slug, thumbnail = spec.image,
                            ),
                        )
                    }
                }
                // Both sides loaded -> record into history.
                val after = _state.value
                if (after.specA != null && after.specB != null) {
                    repository.recordComparison(
                        slugA = after.specA.slug, nameA = after.specA.name, imageA = after.specA.image,
                        slugB = after.specB.slug, nameB = after.specB.name, imageB = after.specB.image,
                    )
                    refreshCacheCount()
                }
            }.onFailure { e ->
                _state.update {
                    if (slot == Slot.A) it.copy(
                        loadingA = false,
                        errorA = e.localizedMessage ?: "Failed to load",
                    )
                    else it.copy(
                        loadingB = false,
                        errorB = e.localizedMessage ?: "Failed to load",
                    )
                }
            }
        }
    }

    enum class Slot { A, B }

    class Factory(
        private val repository: SpecClashRepository,
        private val preferences: UserPreferencesRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(repository, preferences) as T
        }
    }
}
