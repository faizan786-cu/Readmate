package com.example.data.remote.gemini

import com.example.data.model.GeminiModelRegistry
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GeminiThinkingConfig(
    @Json(name = "thinkingBudget") val thinkingBudget: Int? = null,
    @Json(name = "thinkingLevel") val thinkingLevel: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiGenerationConfig(
    @Json(name = "temperature") val temperature: Float? = null,
    @Json(name = "topP") val topP: Float? = null,
    @Json(name = "maxOutputTokens") val maxOutputTokens: Int? = null,
    @Json(name = "responseMimeType") val responseMimeType: String? = null,
    @Json(name = "thinkingConfig") val thinkingConfig: GeminiThinkingConfig? = null
) {
    companion object {
        /**
         * Heavy Analysis Tier (Flash Pipeline) configuration with low thinking level
         * to prioritize instant streaming responsiveness.
         */
        fun forFlash(
            maxOutputTokens: Int = 2500,
            temperature: Float? = 0.35f,
            responseMimeType: String? = null
        ): GeminiGenerationConfig = GeminiGenerationConfig(
            temperature = temperature,
            maxOutputTokens = maxOutputTokens,
            responseMimeType = responseMimeType,
            thinkingConfig = GeminiThinkingConfig(thinkingLevel = "LOW", thinkingBudget = null)
        )

        /**
         * Lightweight Utility Tier (Flash-Lite Pipeline) configuration.
         * Flash-Lite models throw HTTP 400 when sent an explicit thinkingConfig or thinkingBudget parameter.
         * thinkingConfig is strictly null.
         */
        fun forFlashLite(
            maxOutputTokens: Int = 2048,
            temperature: Float? = 0.2f,
            responseMimeType: String? = null
        ): GeminiGenerationConfig = GeminiGenerationConfig(
            temperature = temperature,
            maxOutputTokens = maxOutputTokens,
            responseMimeType = responseMimeType,
            thinkingConfig = null
        )

        fun lowLatency(
            maxOutputTokens: Int = 2048,
            temperature: Float? = 0.3f,
            responseMimeType: String? = null
        ): GeminiGenerationConfig = forFlashLite(
            maxOutputTokens = maxOutputTokens,
            temperature = temperature,
            responseMimeType = responseMimeType
        )
    }
}

@JsonClass(generateAdapter = true)
data class GeminiGenerateContentRequest(
    @Json(name = "contents") val contents: List<GeminiContent>,
    @Json(name = "systemInstruction") val systemInstruction: GeminiContent? = null,
    @Json(name = "generationConfig") val generationConfig: GeminiGenerationConfig? = null
) {
    /**
     * Sanitizes request payload according to the target Gemini model:
     * - Strips thinkingConfig completely for all Flash-Lite models (HTTP 400 prevention).
     * - Configures thinkingLevel for Gemini 3 Flash models ("LOW", thinkingBudget = null).
     * - Configures thinkingBudget for Gemini 2.5 Flash emergency fallback (thinkingBudget = 0, thinkingLevel = null).
     * - Safeguards systemInstruction: omitted entirely if blank/empty instead of empty object; always preserved during fallback.
     * - Guarantees non-blank text and valid base64 image data parts.
     * - If stripOptionalConfigs is true (HTTP 400 recovery), strips responseMimeType and extra parameters.
     */
    fun sanitizedForModel(modelId: String, stripOptionalConfigs: Boolean = false): GeminiGenerateContentRequest {
        val isLite = GeminiModelRegistry.isFlashLite(modelId)

        val cleanContents = contents.mapNotNull { content ->
            val validParts = content.parts.mapNotNull { part ->
                when {
                    part.inlineData != null -> {
                        val cleanBase64 = part.inlineData.data.trim()
                            .substringAfter("base64,")
                            .replace("\n", "")
                            .replace("\r", "")
                            .replace(" ", "")
                        if (cleanBase64.isNotEmpty()) {
                            GeminiPart(
                                inlineData = GeminiInlineData(
                                    mimeType = part.inlineData.mimeType.trim().ifEmpty { "image/jpeg" },
                                    data = cleanBase64
                                )
                            )
                        } else null
                    }
                    !part.text.isNullOrBlank() -> GeminiPart(text = part.text.trim())
                    else -> null
                }
            }
            if (validParts.isNotEmpty()) {
                GeminiContent(parts = validParts, role = content.role?.takeIf { it.isNotBlank() })
            } else null
        }.ifEmpty {
            listOf(GeminiContent(parts = listOf(GeminiPart(text = "Hello"))))
        }

        // Always preserve systemInstruction (e.g. ReadMate teaching prompt), ensuring non-empty parts
        val cleanSystemInstruction = systemInstruction?.let { si ->
            val validParts = si.parts.mapNotNull { part ->
                when {
                    part.inlineData != null -> part
                    !part.text.isNullOrBlank() -> GeminiPart(text = part.text.trim())
                    else -> null
                }
            }
            if (validParts.isNotEmpty()) {
                GeminiContent(parts = validParts, role = si.role?.takeIf { it.isNotBlank() })
            } else null
        }

        val cleanGenerationConfig = if (stripOptionalConfigs) {
            generationConfig?.let { cfg ->
                GeminiGenerationConfig(
                    temperature = cfg.temperature,
                    maxOutputTokens = cfg.maxOutputTokens,
                    responseMimeType = null,
                    thinkingConfig = null
                )
            }
        } else {
            generationConfig?.let { cfg ->
                when {
                    // Flash-Lite utility models: strictly omit thinkingConfig completely
                    isLite -> cfg.copy(thinkingConfig = null)

                    // Gemini 3 Flash models (gemini-3.8-flash, 3.6, 3.5, 3-preview):
                    // Use thinkingLevel = "LOW", thinkingBudget = null
                    GeminiModelRegistry.isGemini3(modelId) -> cfg.copy(
                        thinkingConfig = GeminiThinkingConfig(
                            thinkingLevel = "LOW",
                            thinkingBudget = null
                        )
                    )

                    // Gemini 2.5 Flash emergency fallback:
                    // Use thinkingBudget = 0, thinkingLevel = null
                    GeminiModelRegistry.isGemini25(modelId) -> cfg.copy(
                        thinkingConfig = GeminiThinkingConfig(
                            thinkingBudget = 0,
                            thinkingLevel = null
                        )
                    )

                    else -> cfg.copy(thinkingConfig = null)
                }
            }
        }

        return GeminiGenerateContentRequest(
            contents = cleanContents,
            systemInstruction = cleanSystemInstruction,
            generationConfig = cleanGenerationConfig
        )
    }

    companion object {
        fun forText(
            prompt: String,
            systemInstruction: String? = null,
            generationConfig: GeminiGenerationConfig? = GeminiGenerationConfig.forFlash()
        ): GeminiGenerateContentRequest {
            val cleanPrompt = prompt.trim()
            require(cleanPrompt.isNotEmpty()) { "Prompt text cannot be empty" }
            return GeminiGenerateContentRequest(
                contents = listOf(
                    GeminiContent(
                        parts = listOf(GeminiPart(text = cleanPrompt))
                    )
                ),
                systemInstruction = systemInstruction?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    GeminiContent(parts = listOf(GeminiPart(text = it)))
                },
                generationConfig = generationConfig
            )
        }

        fun forVision(
            prompt: String,
            base64Data: String,
            mimeType: String = "image/jpeg",
            systemInstruction: String? = null
        ): GeminiGenerateContentRequest {
            val cleanPrompt = prompt.trim()
            val cleanBase64 = base64Data.trim()
                .substringAfter("base64,")
                .replace("\n", "")
                .replace("\r", "")
                .replace(" ", "")
                .trim()

            require(cleanBase64.isNotEmpty()) { "Base64 image data cannot be empty" }

            val partsList = mutableListOf<GeminiPart>()
            if (cleanPrompt.isNotEmpty()) {
                partsList.add(GeminiPart(text = cleanPrompt))
            }
            partsList.add(
                GeminiPart(
                    inlineData = GeminiInlineData(
                        mimeType = mimeType.trim().ifEmpty { "image/jpeg" },
                        data = cleanBase64
                    )
                )
            )

            return GeminiGenerateContentRequest(
                contents = listOf(
                    GeminiContent(
                        parts = partsList
                    )
                ),
                systemInstruction = systemInstruction?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    GeminiContent(parts = listOf(GeminiPart(text = it)))
                },
                generationConfig = GeminiGenerationConfig.forFlashLite(
                    maxOutputTokens = 2048,
                    temperature = 0.1f
                )
            )
        }

        fun forPdfStructure(
            systemPrompt: String,
            userPrompt: String,
            pdfBase64: String
        ): GeminiGenerateContentRequest {
            val cleanPdf = pdfBase64.trim()
                .substringAfter("base64,")
                .replace("\n", "")
                .replace("\r", "")
                .replace(" ", "")
                .trim()

            val partsList = mutableListOf<GeminiPart>()
            if (cleanPdf.isNotEmpty()) {
                partsList.add(
                    GeminiPart(
                        inlineData = GeminiInlineData(
                            mimeType = "application/pdf",
                            data = cleanPdf
                        )
                    )
                )
            }
            if (userPrompt.isNotBlank()) {
                partsList.add(GeminiPart(text = userPrompt.trim()))
            }

            return GeminiGenerateContentRequest(
                contents = listOf(
                    GeminiContent(
                        parts = partsList
                    )
                ),
                systemInstruction = systemPrompt.trim().takeIf { it.isNotEmpty() }?.let {
                    GeminiContent(parts = listOf(GeminiPart(text = it)))
                },
                generationConfig = GeminiGenerationConfig.forFlashLite(
                    maxOutputTokens = 2500,
                    temperature = 0.1f,
                    responseMimeType = "application/json"
                )
            )
        }

        fun forPdfTextStructure(
            systemPrompt: String,
            userPrompt: String
        ): GeminiGenerateContentRequest {
            return GeminiGenerateContentRequest(
                contents = listOf(
                    GeminiContent(
                        parts = listOf(GeminiPart(text = userPrompt.trim()))
                    )
                ),
                systemInstruction = systemPrompt.trim().takeIf { it.isNotEmpty() }?.let {
                    GeminiContent(parts = listOf(GeminiPart(text = it)))
                },
                generationConfig = GeminiGenerationConfig.forFlashLite(
                    maxOutputTokens = 2500,
                    temperature = 0.1f,
                    responseMimeType = "application/json"
                )
            )
        }
    }
}

@JsonClass(generateAdapter = true)
data class GeminiContent(
    @Json(name = "parts") val parts: List<GeminiPart>,
    @Json(name = "role") val role: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiPart(
    @Json(name = "text") val text: String? = null,
    @Json(name = "inlineData") val inlineData: GeminiInlineData? = null
)

@JsonClass(generateAdapter = true)
data class GeminiInlineData(
    @Json(name = "mimeType") val mimeType: String,
    @Json(name = "data") val data: String
)

@JsonClass(generateAdapter = true)
data class GeminiGenerateContentResponse(
    @Json(name = "candidates") val candidates: List<GeminiCandidate>? = null,
    @Json(name = "promptFeedback") val promptFeedback: GeminiPromptFeedback? = null
)

@JsonClass(generateAdapter = true)
data class GeminiCandidate(
    @Json(name = "content") val content: GeminiContent? = null,
    @Json(name = "finishReason") val finishReason: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiPromptFeedback(
    @Json(name = "blockReason") val blockReason: String? = null
)
