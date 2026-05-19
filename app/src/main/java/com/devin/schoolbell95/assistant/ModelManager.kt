package com.devin.schoolbell95.assistant

import android.content.Context
import com.devin.schoolbell95.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/**
 * State of the on-device LLM model file.
 *
 * Intentionally a sealed class so the UI can render distinct states (banner with
 * progress bar, "ready" pill, "error" with retry button, etc).
 */
sealed class ModelState {
    /** No file on disk yet. */
    data object Missing : ModelState()

    /** Download in progress. [received] / [total] are in bytes; total may be -1 if unknown. */
    data class Downloading(val received: Long, val total: Long) : ModelState() {
        val fraction: Float
            get() = if (total > 0) (received.toFloat() / total).coerceIn(0f, 1f) else 0f

        val percent: Int get() = (fraction * 100).toInt()
    }

    /** Model file is on disk and ready for inference. */
    data class Ready(val file: File, val sizeBytes: Long) : ModelState()

    /** Last attempt failed; [message] is a human-readable hint. */
    data class Error(val message: String) : ModelState()
}

/**
 * Downloads, stores and exposes the on-device Gemma model file.
 *
 * The Hugging Face repo is gated by Google's Gemma license, so users must:
 *   1. Open https://huggingface.co/litert-community/Gemma3-1B-IT in a browser,
 *      sign in and accept the license.
 *   2. Create a read token at https://huggingface.co/settings/tokens.
 *   3. Paste the token into the app's settings.
 *
 * The token is stored in [Prefs.hfToken] and sent as `Authorization: Bearer`.
 * A custom URL can be set in [Prefs.modelUrl] to point at any other .task file
 * (e.g. a self-hosted mirror) — in that case the token is not required.
 */
class ModelManager(
    private val appContext: Context,
    private val prefs: Prefs,
) {

    private val modelDir: File = File(appContext.filesDir, "models").apply { mkdirs() }
    val modelFile: File = File(modelDir, MODEL_FILENAME)
    private val partFile: File = File(modelDir, "$MODEL_FILENAME.part")

    private val _state = MutableStateFlow<ModelState>(initialState())
    val state: StateFlow<ModelState> = _state.asStateFlow()

    /** True when the model file exists and is non-empty. */
    val isReady: Boolean get() = _state.value is ModelState.Ready

    private fun initialState(): ModelState =
        if (modelFile.exists() && modelFile.length() > 0L) {
            ModelState.Ready(modelFile, modelFile.length())
        } else {
            ModelState.Missing
        }

    /** Drop any cached file and revert to [ModelState.Missing]. */
    fun delete() {
        modelFile.delete()
        partFile.delete()
        _state.value = ModelState.Missing
    }

    /**
     * Downloads the model file (with resume support).
     *
     * Returns true on success. The caller is responsible for cancelling the
     * coroutine if the user navigates away.
     */
    suspend fun download(): Boolean = withContext(Dispatchers.IO) {
        if (modelFile.exists() && modelFile.length() > 0L) {
            _state.value = ModelState.Ready(modelFile, modelFile.length())
            return@withContext true
        }
        val url = effectiveUrl()
        val token = prefs.hfToken.takeIf { it.isNotBlank() }
        if (url.contains("huggingface.co") && token.isNullOrBlank()) {
            _state.value = ModelState.Error(
                "Нужен Hugging Face токен. Зайди на huggingface.co, прими лицензию Gemma " +
                    "и вставь read-токен в Настройки → AI.",
            )
            return@withContext false
        }
        _state.value = ModelState.Downloading(0L, -1L)
        val resumeFrom = if (partFile.exists()) partFile.length() else 0L
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                requestMethod = "GET"
                if (token != null) setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("User-Agent", "Shkola95/1.1 (Android)")
                if (resumeFrom > 0) setRequestProperty("Range", "bytes=$resumeFrom-")
            }
            connection.connect()
            val code = connection.responseCode
            if (code !in 200..299) {
                _state.value = ModelState.Error(
                    when (code) {
                        401, 403 -> "Hugging Face отклонил токен (HTTP $code). " +
                            "Проверь, что лицензия Gemma принята и токен read."
                        404 -> "Файл модели не найден (HTTP 404). Проверь URL в Настройках."
                        else -> "Ошибка сети: HTTP $code"
                    },
                )
                return@withContext false
            }

            val contentLength = connection.contentLengthLong
            val total = if (code == 206 && resumeFrom > 0) resumeFrom + contentLength else contentLength
            val append = (code == 206 && resumeFrom > 0)
            // Open in append mode for HTTP 206 resumes so we keep the bytes we
            // already have. Otherwise truncate — the server is sending the
            // whole file from byte 0 (HTTP 200) and any leftover `.part` is
            // stale.
            val out = FileOutputStream(partFile, append).buffered()
            var received = if (append) resumeFrom else 0L
            try {
                connection.inputStream.use { input ->
                    val buf = ByteArray(64 * 1024)
                    var lastEmit = 0L
                    while (coroutineContext.isActive) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        received += n
                        val now = System.currentTimeMillis()
                        if (now - lastEmit > 250) {
                            _state.value = ModelState.Downloading(received, total)
                            lastEmit = now
                        }
                    }
                }
            } finally {
                out.flush()
                out.close()
            }
            if (!coroutineContext.isActive) {
                _state.value = ModelState.Missing
                return@withContext false
            }
            if (!partFile.renameTo(modelFile)) {
                _state.value = ModelState.Error("Не удалось сохранить файл модели.")
                return@withContext false
            }
            _state.value = ModelState.Ready(modelFile, modelFile.length())
            return@withContext true
        } catch (io: IOException) {
            _state.value = ModelState.Error("Сеть: ${io.message ?: "соединение прервано"}")
            return@withContext false
        } finally {
            connection?.disconnect()
        }
    }

    private fun effectiveUrl(): String =
        prefs.modelUrl.takeIf { it.isNotBlank() } ?: DEFAULT_MODEL_URL

    companion object {
        /**
         * Gemma 3 1B Instruct, INT4 quantised, ~530 MB. The file lives in the
         * `litert-community/Gemma3-1B-IT` HF repo and is gated by Google's
         * Gemma license — users need a HF token (see [Prefs.hfToken]).
         */
        const val DEFAULT_MODEL_URL =
            "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/" +
                "Gemma3-1B-IT_multi-prefill-seq_q4_ekv2048.task"

        const val MODEL_FILENAME = "gemma3-1b-it-int4.task"
    }
}
