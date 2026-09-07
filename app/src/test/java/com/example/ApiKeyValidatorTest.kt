package com.example

import com.example.data.manager.ApiKeyValidationResult
import com.example.data.manager.ApiKeyValidator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiKeyValidatorTest {

    @Test
    fun validateKey_emptyKey_returnsError() = runBlocking {
        val validator = ApiKeyValidator()
        val result = validator.validateKey("   ")
        assertTrue(result is ApiKeyValidationResult.Error)
        assertEquals("API key cannot be empty.", (result as ApiKeyValidationResult.Error).message)
    }

    @Test
    fun validateKey_shortInvalidKey_returnsFormatError() = runBlocking {
        val validator = ApiKeyValidator()
        val result = validator.validateKey("short_invalid_key")
        assertTrue(result is ApiKeyValidationResult.Error)
        assertTrue((result as ApiKeyValidationResult.Error).message.contains("Invalid key format"))
    }
}
