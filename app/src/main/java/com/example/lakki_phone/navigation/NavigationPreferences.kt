package com.example.lakki_phone.navigation

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

object NavigationPreferences {
    private const val PREFS_NAME = "navigation_preferences"
    private const val KEY_NAVIGATION_ENABLED = "navigation_enabled"

    fun isNavigationEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_NAVIGATION_ENABLED, false)
    }

    fun setNavigationEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_NAVIGATION_ENABLED, enabled).apply()
    }

    fun navigationEnabledFlow(context: Context): Flow<Boolean> {
        return callbackFlow {
            val preferences = prefs(context)
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { updatedPrefs, key ->
                if (key == KEY_NAVIGATION_ENABLED) {
                    trySend(updatedPrefs.getBoolean(KEY_NAVIGATION_ENABLED, false))
                }
            }
            trySend(preferences.getBoolean(KEY_NAVIGATION_ENABLED, false))
            preferences.registerOnSharedPreferenceChangeListener(listener)
            awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
        }.distinctUntilChanged()
    }

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}
