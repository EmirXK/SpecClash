package com.example.specclash.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.example.specclash.domain.SpecComparator.ScoringPreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Lightweight SharedPreferences-backed store for the two user-tunable
 * settings that drive the scoring engine:
 *
 *  * [selectedPreset] - which scoring profile is active.
 *  * [excludeLegacyDevices] - whether to devalue pre-2021 phones in
 *    the value-for-money index.
 *
 * The current values are exposed as [StateFlow]s so Compose can collect
 * them with [kotlinx.coroutines.flow.collectAsStateWithLifecycle].
 */
class UserPreferencesRepository private constructor(
    private val prefs: SharedPreferences,
) {
    private val _selectedPreset = MutableStateFlow(
        runCatching { ScoringPreset.valueOf(prefs.getString(KEY_PRESET, null) ?: ScoringPreset.BALANCED.name) }
            .getOrDefault(ScoringPreset.BALANCED)
    )
    val selectedPreset: StateFlow<ScoringPreset> = _selectedPreset.asStateFlow()

    private val _excludeLegacy = MutableStateFlow(prefs.getBoolean(KEY_LEGACY, false))
    val excludeLegacyDevices: StateFlow<Boolean> = _excludeLegacy.asStateFlow()

    fun setSelectedPreset(preset: ScoringPreset) {
        prefs.edit().putString(KEY_PRESET, preset.name).apply()
        _selectedPreset.value = preset
    }

    fun setExcludeLegacyDevices(value: Boolean) {
        prefs.edit().putBoolean(KEY_LEGACY, value).apply()
        _excludeLegacy.value = value
    }

    companion object {
        private const val PREFS_NAME = "specclash_user_prefs"
        private const val KEY_PRESET = "selected_preset"
        private const val KEY_LEGACY = "exclude_legacy_devices"

        @Volatile
        private var instance: UserPreferencesRepository? = null

        fun get(context: Context): UserPreferencesRepository = instance ?: synchronized(this) {
            instance ?: UserPreferencesRepository(
                context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
            ).also { instance = it }
        }
    }
}
