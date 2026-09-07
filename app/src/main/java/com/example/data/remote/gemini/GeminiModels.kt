package com.example.data.remote.gemini

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GeminiGenerationConfig(
    @Json(name = "temperature") val temperature: Float? = null,
    @Json(name = "topP") val topP: Float? = null,
    @Json(name = "maxOutputTokens") val maxOutputTokens: Int? = null,
    @Json(name = "responseMimeType") val responseMimeType: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiGenerateContentRequest(
    @Json(name = "contents") val contents: List<GeminiContent>,
    @Json(name = "systemInstruction") val systemInstruction: GeminiContent? = null,
    @Json(name = "generationConfig") val generationConfig: GeminiGenerationConfig? = null
) {
    companion object {
        fun forText(prompt: String): GeminiGenerateContentRequest {
            val cleanPrompt = prompt.trim()
            require(cleanPrompt.isNotEmpty()) { "Prompt text cannot be empty" }
            return GeminiGenerateContentRequest(
                contents = listOf(
                    GeminiContent(
                        parts = listOf(GeminiPart(text = cleanPrompt))
                    )
                )
            )
        }

        fun forVision(
            prompt: String,
            base64Data: String,
            mimeType: String = "image/jpeg"
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
            partsList.add(GeminiPart(text = userPrompt.trim()))

            return GeminiGenerateContentRequest(
                contents = listOf(
                    GeminiContent(
                        parts = partsList
                    )
                ),
                systemInstruction = GeminiContent(
                    parts = listOf(GeminiPart(text = systemPrompt.trim()))
                ),
                generationConfig = GeminiGenerationConfig(
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
                systemInstruction = GeminiContent(
                    parts = listOf(GeminiPart(text = systemPrompt.trim()))
                ),
                generationConfig = GeminiGenerationConfig(
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

