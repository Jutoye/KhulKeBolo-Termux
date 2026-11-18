package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "khul_ke_bolo_prefs"
)

data class LocalOnboardingState(
    val ageConfirmed: Boolean = false,
    val ageRejected: Boolean = false,
    val rulesAcceptedAtMillis: Long = 0L
) {
    val rulesAccepted: Boolean get() = rulesAcceptedAtMillis > 0L
}

class UserPreferencesStore(private val context: Context) {

    private object Keys {
        val AGE_CONFIRMED = booleanPreferencesKey("age_confirmed")
        val AGE_REJECTED = booleanPreferencesKey("age_rejected")
        val RULES_ACCEPTED_AT = longPreferencesKey("rules_accepted_at")
    }

    val onboardingState: Flow<LocalOnboardingState> = context.userPreferencesDataStore.data.map { prefs ->
        LocalOnboardingState(
            ageConfirmed = prefs[Keys.AGE_CONFIRMED] ?: false,
            ageRejected = prefs[Keys.AGE_REJECTED] ?: false,
            rulesAcceptedAtMillis = prefs[Keys.RULES_ACCEPTED_AT] ?: 0L
        )
    }

    suspend fun setAgeConfirmed(confirmed: Boolean) {
        context.userPreferencesDataStore.edit { prefs ->
            prefs[Keys.AGE_CONFIRMED] = confirmed
            prefs[Keys.AGE_REJECTED] = !confirmed
        }
    }

    suspend fun setRulesAccepted(timestampMillis: Long = System.currentTimeMillis()) {
        context.userPreferencesDataStore.edit { prefs ->
            prefs[Keys.RULES_ACCEPTED_AT] = timestampMillis
        }
    }

    suspend fun clearAll() {
        context.userPreferencesDataStore.edit { prefs ->
            prefs.clear()
        }
    }
}
