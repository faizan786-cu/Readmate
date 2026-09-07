package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class UserPreferencesRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _responseFontSizePercent = MutableStateFlow(
        prefs.getInt(KEY_RESPONSE_FONT_SIZE_PERCENT, DEFAULT_FONT_SIZE_PERCENT)
            .coerceIn(MIN_FONT_SIZE_PERCENT, MAX_FONT_SIZE_PERCENT)
    )
    val responseFontSizePercent: StateFlow<Int> = _responseFontSizePercent.asStateFlow()

    private val _isApiKeyConfigured = MutableStateFlow(
        prefs.getBoolean(KEY_IS_API_KEY_CONFIGURED, false)
    )
    val isApiKeyConfigured: StateFlow<Boolean> = _isApiKeyConfigured.asStateFlow()

    fun setApiKeyConfigured(configured: Boolean) {
        _isApiKeyConfigured.value = configured
        prefs.edit().putBoolean(KEY_IS_API_KEY_CONFIGURED, configured).apply()
    }

    fun isApiKeyConfiguredDirect(): Boolean {
        return prefs.getBoolean(KEY_IS_API_KEY_CONFIGURED, false)
    }

    fun isFirstLaunchCompleted(): Boolean {
        return prefs.getBoolean(KEY_FIRST_LAUNCH_COMPLETED, false)
    }

    fun setFirstLaunchCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_FIRST_LAUNCH_COMPLETED, completed).apply()
    }

    fun setResponseFontSizePercent(percent: Int) {
        val clamped = percent.coerceIn(MIN_FONT_SIZE_PERCENT, MAX_FONT_SIZE_PERCENT)
        _responseFontSizePercent.value = clamped
        prefs.edit().putInt(KEY_RESPONSE_FONT_SIZE_PERCENT, clamped).apply()
    }

    fun increaseFontSize(step: Int = STEP_FONT_SIZE_PERCENT) {
        setResponseFontSizePercent(_responseFontSizePercent.value + step)
    }

    fun decreaseFontSize(step: Int = STEP_FONT_SIZE_PERCENT) {
        setResponseFontSizePercent(_responseFontSizePercent.value - step)
    }

    fun resetFontSize() {
        setResponseFontSizePercent(DEFAULT_FONT_SIZE_PERCENT)
    }

    companion object {
        private const val PREFS_NAME = "readmate_user_preferences"
        private const val KEY_RESPONSE_FONT_SIZE_PERCENT = "pref_response_font_size_percent"
        private const val KEY_IS_API_KEY_CONFIGURED = "pref_is_api_key_configured"
        private const val KEY_FIRST_LAUNCH_COMPLETED = "pref_first_launch_completed"

        const val DEFAULT_FONT_SIZE_PERCENT = 100
        const val MIN_FONT_SIZE_PERCENT = 80
        const val MAX_FONT_SIZE_PERCENT = 150
        const val STEP_FONT_SIZE_PERCENT = 10
    }
}
