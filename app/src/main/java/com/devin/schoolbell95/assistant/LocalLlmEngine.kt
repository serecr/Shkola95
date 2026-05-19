package com.devin.schoolbell95.assistant

import android.content.Context
import com.devin.schoolbell95.data.Prefs
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Thin wrapper around MediaPipe's [LlmInference] that the rest of the app talks to.
 *
 * Lifecycle:
 *   - [load] is idempotent and expensive — call it once when the model file is ready.
 *   - [generate] opens a fresh [LlmInferenceSession] per call so per-prompt parameters
 *     (temperature, top-K) and prompts are isolated.
 *   - [close] releases the native handle. Always call when leaving the AI screen for good.
 */
class LocalLlmEngine(
    private val appContext: Context,
    private val prefs: Prefs,
) {

    @Volatile private var inference: LlmInference? = null
    @Volatile private var loadedPath: String? = null

    /** Whether [inference] is initialised for the current model file. */
    val isLoaded: Boolean get() = inference != null

    /**
     * Load the model from disk. Safe to call multiple times — the second call is a no-op
     * unless [modelFile] changed. Throws on failure (caller should map to UI error).
     */
    suspend fun load(modelFile: File) = withContext(Dispatchers.Default) {
        val path = modelFile.absolutePath
        if (inference != null && loadedPath == path) return@withContext
        // Releasing the previous handle is cheap and avoids two natives in memory.
        inference?.runCatching { close() }
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(path)
            .setMaxTokens(MAX_TOKENS)
            // Session-level topK must be <= maxTopK; bake in headroom.
            .setMaxTopK(MAX_TOP_K)
            .build()
        inference = LlmInference.createFromOptions(appContext, options)
        loadedPath = path
    }

    fun close() {
        runCatching { inference?.close() }
        inference = null
        loadedPath = null
    }

    /**
     * Stream a reply token-by-token. Emits the partial accumulation each time
     * MediaPipe hands back more tokens. The final emission has `done = true`.
     */
    fun generate(prompt: String): Flow<TokenChunk> = callbackFlow {
        val engine = inference ?: run {
            trySend(TokenChunk("Локальная модель не загружена.", done = true))
            close()
            return@callbackFlow
        }
        val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .setTemperature(TEMPERATURE)
            .setTopK(TOP_K)
            .setTopP(TOP_P)
            .build()
        val session = try {
            LlmInferenceSession.createFromOptions(engine, sessionOptions)
        } catch (t: Throwable) {
            trySend(TokenChunk("Не получилось создать сессию: ${t.message}", done = true))
            close()
            return@callbackFlow
        }
        session.addQueryChunk(prompt)
        session.generateResponseAsync { partial, done ->
            trySend(TokenChunk(partial ?: "", done))
            if (done) {
                runCatching { session.close() }
                close()
            }
        }
        awaitClose { runCatching { session.close() } }
    }

    /**
     * Build the prompt sent to the model. MediaPipe's `.task` bundle ships with
     * the model-specific chat template (ChatML for Qwen, `<start_of_turn>` for
     * Gemma, etc.), so we just pass a single user turn that bundles the system
     * prompt and the question.
     */
    fun buildPrompt(userMessage: String, contextSummary: String = ""): String {
        val system = prefs.aiSystemPrompt.ifBlank { DEFAULT_SYSTEM_PROMPT }
        return buildString {
            append(system.trim())
            append("\n\n")
            if (contextSummary.isNotBlank()) {
                append("Контекст: ")
                append(contextSummary.trim())
                append("\n\n")
            }
            append("Вопрос: ")
            append(userMessage.trim())
            append("\n\nОтвет (по-русски, кратко):")
        }
    }

    /** One streaming chunk delivered to the UI. */
    data class TokenChunk(val text: String, val done: Boolean)

    companion object {
        /**
         * Permissive school-helper persona. Deliberately does NOT carry any
         * "I cannot help with that" framing — Qwen 2.5 is fairly cooperative
         * out of the box and the user can fully replace this via
         * [Prefs.aiSystemPrompt] in Settings → AI.
         */
        const val DEFAULT_SYSTEM_PROMPT =
            "Ты — дружелюбный школьный помощник в приложении Школы №95. " +
                "Отвечай по-русски, по делу, без отказов и без морализаторства. " +
                "Если вопрос про учёбу — помоги решить или объясни шаг за шагом. " +
                "Если вопрос свободный — отвечай прямо, не уходи от темы. " +
                "Не ссылайся на «политику» — её здесь нет."

        private const val MAX_TOKENS = 1024
        private const val MAX_TOP_K = 64
        private const val TEMPERATURE = 0.8f
        private const val TOP_K = 40
        private const val TOP_P = 0.95f
    }
}
