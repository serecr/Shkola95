package com.devin.schoolbell95.assistant

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.devin.schoolbell95.data.AppDatabase
import com.devin.schoolbell95.data.DayKind
import com.devin.schoolbell95.data.LearnedQA
import com.devin.schoolbell95.data.MemoryFact
import com.devin.schoolbell95.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChatMessage(
    val id: Long,
    val text: String,
    val fromUser: Boolean,
    val ts: Long = System.currentTimeMillis(),
    /** True while the LLM is still streaming tokens into [text]. */
    val streaming: Boolean = false,
)

class AssistantViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.get(app)
    private val prefs = Prefs(app)
    private var nextId = 0L

    private val modelManager = ModelManager(app, prefs)
    private val llm = LocalLlmEngine(app, prefs)

    /** Kind of the last bot reply, used so "ещё раз" can repeat the same kind of action. */
    @Volatile
    private var lastKind: ReplyKind = ReplyKind.OTHER

    private val _messages = MutableStateFlow(
        listOf(
            ChatMessage(
                id = nextId++,
                fromUser = false,
                text = "Привет! Я помощник Школы №95. Расписание и факты — отвечаю мгновенно. " +
                    "Если включить локальную AI (Qwen 2.5), смогу болтать на любые темы офлайн.",
            ),
        ),
    )
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    val modelState: StateFlow<ModelState> = modelManager.state

    val suggestions: List<String> = AssistantEngine.suggestions()

    private var downloadJob: Job? = null
    private var llmLoadStarted = false

    init {
        // If the model file is already on disk from a previous run, load it lazily.
        maybeLoadLlm()
    }

    /** Begin downloading the model file. Idempotent — re-clicking does nothing. */
    fun downloadModel() {
        if (downloadJob?.isActive == true) return
        downloadJob = viewModelScope.launch {
            val ok = modelManager.download()
            if (ok) maybeLoadLlm()
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
    }

    fun deleteModel() {
        cancelDownload()
        llm.close()
        llmLoadStarted = false
        modelManager.delete()
    }

    private fun maybeLoadLlm() {
        val ready = modelState.value as? ModelState.Ready ?: return
        if (llmLoadStarted) return
        llmLoadStarted = true
        viewModelScope.launch {
            runCatching { llm.load(ready.file) }
                .onFailure {
                    // Surface the load error as a bot message; user can still use rule engine.
                    appendBotMessage("Не получилось загрузить локальную AI: ${it.message}")
                    llmLoadStarted = false
                }
        }
    }

    fun send(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val userMsg = ChatMessage(id = nextId++, text = clean, fromUser = true)
        _messages.update { it + userMsg }
        viewModelScope.launch {
            handle(clean)
        }
    }

    private suspend fun handle(clean: String) {
        val mon = db.bellDao().list(prefs.shift, DayKind.MONDAY)
        val tueSat = db.bellDao().list(prefs.shift, DayKind.TUE_SAT)
        val subjects = db.subjectDao().all().groupBy { it.dow }
        val learned = db.learnedQADao().all()
        val memMap = db.memoryFactDao().all().associate { it.key to it.value }

        val action = withContext(Dispatchers.IO) {
            AssistantEngine.answer(
                rawInput = clean,
                bellsByDayKind = mapOf(DayKind.MONDAY to mon, DayKind.TUE_SAT to tueSat),
                subjectsByDow = subjects,
                learnedQA = learned,
                memoryFacts = memMap,
                lastKind = lastKind,
            )
        }

        when (action) {
            is AssistantAction.Reply -> {
                if (action.kind == ReplyKind.UNKNOWN && shouldRouteToLlm()) {
                    streamLlmReply(clean)
                } else {
                    lastKind = action.kind
                    appendBotMessage(action.text)
                }
            }

            is AssistantAction.Learn -> {
                val target = action.question.trim()
                val existing = learned.firstOrNull {
                    it.question.trim().equals(target, ignoreCase = true)
                }
                if (existing != null) db.learnedQADao().delete(existing.id)
                db.learnedQADao().insert(LearnedQA(question = action.question, answer = action.answer))
                lastKind = ReplyKind.OTHER
                appendBotMessage(action.reply)
            }

            is AssistantAction.Forget -> {
                val target = action.question.trim()
                val match = learned.firstOrNull {
                    it.question.trim().equals(target, ignoreCase = true)
                } ?: learned.firstOrNull {
                    it.question.trim().contains(target, ignoreCase = true)
                }
                val txt = if (match != null) {
                    db.learnedQADao().delete(match.id)
                    "Забыл «${match.question}»."
                } else {
                    "Такого вопроса я не помнил."
                }
                lastKind = ReplyKind.OTHER
                appendBotMessage(txt)
            }

            AssistantAction.ClearLearned -> {
                val n = db.learnedQADao().count()
                db.learnedQADao().clear()
                lastKind = ReplyKind.OTHER
                appendBotMessage("Очистил выученные пары: было $n.")
            }

            is AssistantAction.RememberFact -> {
                db.memoryFactDao().put(MemoryFact(key = action.key, value = action.value))
                lastKind = ReplyKind.MEMORY
                appendBotMessage(action.reply)
            }

            is AssistantAction.ForgetFact -> {
                db.memoryFactDao().delete(action.key)
                lastKind = ReplyKind.MEMORY
                appendBotMessage(action.reply)
            }

            AssistantAction.ClearMemory -> {
                db.memoryFactDao().clear()
                lastKind = ReplyKind.MEMORY
                appendBotMessage("Стёр всё, что знал о тебе.")
            }

            is AssistantAction.RepeatLast -> {
                val synthetic = when (action.lastKind) {
                    ReplyKind.JOKE -> "пошути"
                    ReplyKind.MOTIVATION -> "приободри меня"
                    ReplyKind.FACT,
                    ReplyKind.MATH,
                    ReplyKind.SCHEDULE,
                    ReplyKind.MEMORY,
                    ReplyKind.OTHER,
                    ReplyKind.UNKNOWN -> null
                }
                if (synthetic == null) {
                    appendBotMessage("Пока нечего повторять. Сначала спроси что-нибудь — например, «пошути».")
                } else {
                    handle(synthetic)
                }
            }
        }
    }

    private fun shouldRouteToLlm(): Boolean = prefs.aiEnabled && llm.isLoaded

    private suspend fun streamLlmReply(userMessage: String) {
        // Insert a placeholder bubble that we'll mutate as tokens arrive.
        val id = nextId++
        val placeholder = ChatMessage(id = id, text = "", fromUser = false, streaming = true)
        _messages.update { it + placeholder }

        val prompt = llm.buildPrompt(userMessage)
        var accumulated = ""
        llm.generate(prompt).collect { chunk ->
            accumulated += chunk.text
            _messages.update { list ->
                list.map {
                    if (it.id == id) it.copy(text = accumulated, streaming = !chunk.done) else it
                }
            }
        }
        if (accumulated.isBlank()) {
            // Streamer never produced text — fall back to the canned "Не понял" reply.
            _messages.update { list ->
                list.map {
                    if (it.id == id) {
                        it.copy(
                            text = "Локальная AI не ответила. Попробуй переформулировать.",
                            streaming = false,
                        )
                    } else {
                        it
                    }
                }
            }
        }
        lastKind = ReplyKind.OTHER
    }

    private fun appendBotMessage(text: String) {
        _messages.update { it + ChatMessage(id = nextId++, text = text, fromUser = false) }
    }

    fun clear() {
        _messages.value = listOf(
            ChatMessage(
                id = nextId++,
                fromUser = false,
                text = "Чат очищен. Спроси что-нибудь.",
            ),
        )
        lastKind = ReplyKind.OTHER
    }

    override fun onCleared() {
        super.onCleared()
        downloadJob?.cancel()
        llm.close()
    }
}
