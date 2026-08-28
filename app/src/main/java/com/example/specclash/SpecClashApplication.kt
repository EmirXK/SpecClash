package com.example.specclash

import android.app.Application
import com.example.specclash.data.SpecClashRepository
import com.example.specclash.data.preferences.UserPreferencesRepository

class SpecClashApplication : Application() {
    val repository: SpecClashRepository by lazy { SpecClashRepository.get(this) }
    val preferences: UserPreferencesRepository by lazy { UserPreferencesRepository.get(this) }
}
