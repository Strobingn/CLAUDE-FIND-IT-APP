package com.example.ai

import android.content.Context

enum class TerrainAiProvider(val label: String) {
    OPENAI("OpenAI"),
    GEMINI("Gemini"),
}

data class TerrainAiAnswer(
    val text: String,
    val provider: TerrainAiProvider,
    val fallbackReason: String? = null,
)

/**
 * Provider order is intentional: OpenAI first, Gemini second.
 * Local terrain intelligence is separate and never depends on either cloud provider.
 */
internal class TerrainAiGateway(context: Context) {
    private val appContext = context.applicationContext
    private val openAi = OpenAiApiClient(appContext)
    private val gemini = GeminiApiClient(appContext)

    suspend fun generate(
        conversation: List<GeminiConversationTurn>,
        systemContext: String,
        image: GeminiImageInput? = null,
    ): TerrainAiAnswer {
        if (OpenAiApiClient.isConfigured(appContext)) {
            try {
                return TerrainAiAnswer(
                    text = openAi.generate(conversation, systemContext, image),
                    provider = TerrainAiProvider.OPENAI,
                )
            } catch (openAiError: Throwable) {
                if (!GeminiApiClient.isConfigured(appContext)) throw openAiError
                return TerrainAiAnswer(
                    text = gemini.generate(conversation, systemContext, image),
                    provider = TerrainAiProvider.GEMINI,
                    fallbackReason = openAiError.localizedMessage ?: "OpenAI request failed",
                )
            }
        }
        if (GeminiApiClient.isConfigured(appContext)) {
            return TerrainAiAnswer(
                text = gemini.generate(conversation, systemContext, image),
                provider = TerrainAiProvider.GEMINI,
            )
        }
        error("No cloud AI provider is configured. Add an OpenAI key first or a Gemini fallback key.")
    }

    companion object {
        fun preferredProvider(context: Context): TerrainAiProvider? = when {
            OpenAiApiClient.isConfigured(context) -> TerrainAiProvider.OPENAI
            GeminiApiClient.isConfigured(context) -> TerrainAiProvider.GEMINI
            else -> null
        }
    }
}
