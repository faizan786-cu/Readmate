package com.example.data.model

sealed interface GeminiConnectionState {
    data object NotConnected : GeminiConnectionState
    data object Testing : GeminiConnectionState
    data class Connected(
        val maskedKey: String,
        val totalKeyCount: Int = 1,
        val activeKeyCount: Int = 1,
        val cooldownKeyCount: Int = 0,
        val errorKeyCount: Int = 0,
        val keys: List<GeminiApiKeyItem> = emptyList()
    ) : GeminiConnectionState
    data class TestFailed(val errorMessage: String) : GeminiConnectionState
    data class Error(val message: String) : GeminiConnectionState
}

sealed interface TestConnectionResult {
    data object Success : TestConnectionResult
    data class Failure(val message: String, val statusCode: Int? = null) : TestConnectionResult
}

