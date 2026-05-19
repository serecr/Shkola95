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
)

class AssistantViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.get(app)
    private val prefs = Prefs(app)
    private var nextId = 0L

    /** Kind of the last bot reply, used so "ещё раз" can repeat the same kind of action. */
    @Volatile
    private var lastKind: ReplyKind = ReplyKind.OTHER

    private val _messages = MutableStateFlow(
        listOf(
            ChatMessage(
                id = nextId++,
                fromUser = false,
                text = "Привет! Я помощник по расписанию Школы №95. Работаю офлайн, " +
                    "помню кое-что о тебе и умею учиться. Скажи «что ты умеешь» — расскажу.",
            ),
        ),
    )
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    val suggestions: List<String> = AssistantEngine.suggestions()

    fun send(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val userMsg = ChatMessage(id = nextId++, text = clean, fromUser = true)
        _messages.update { it + userMsg }
        viewModelScope.launch {
            val (reply, kind) = withContext(Dispatchers.IO) { generateReply(clean) }
            lastKind = kind
            _messages.update { it + ChatMessage(id = nextId++, text = reply, fromUser = false) }
        }
    }

    /** Returns (reply text, kind of reply). Runs on IO. */
    private suspend fun generateReply(clean: String): Pair<String, ReplyKind> {
        val mon = db.bellDao().list(prefs.shift, DayKind.MONDAY)
        val tueSat = db.bellDao().list(prefs.shift, DayKind.TUE_SAT)
        val subjects = db.subjectDao().all().groupBy { it.dow }
        val learned = db.learnedQADao().all()
        val memMap = db.memoryFactDao().all().associate { it.key to it.value }

        val action = AssistantEngine.answer(
            rawInput = clean,
            bellsByDayKind = mapOf(DayKind.MONDAY to mon, DayKind.TUE_SAT to tueSat),
            subjectsByDow = subjects,
            learnedQA = learned,
            memoryFacts = memMap,
            lastKind = lastKind,
        )

        return when (action) {
            is AssistantAction.Reply -> action.text to action.kind

            is AssistantAction.Learn -> {
                val target = action.question.trim()
                val existing = learned.firstOrNull {
                    it.question.trim().equals(target, ignoreCase = true)
                }
                if (existing != null) db.learnedQADao().delete(existing.id)
                db.learnedQADao().insert(LearnedQA(question = action.question, answer = action.answer))
                action.reply to ReplyKind.OTHER
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
                txt to ReplyKind.OTHER
            }

            AssistantAction.ClearLearned -> {
                val n = db.learnedQADao().count()
                db.learnedQADao().clear()
                "Очистил выученные пары: было $n." to ReplyKind.OTHER
            }

            is AssistantAction.RememberFact -> {
                db.memoryFactDao().put(MemoryFact(key = action.key, value = action.value))
                action.reply to ReplyKind.MEMORY
            }

            is AssistantAction.ForgetFact -> {
                db.memoryFactDao().delete(action.key)
                action.reply to ReplyKind.MEMORY
            }

            AssistantAction.ClearMemory -> {
                db.memoryFactDao().clear()
                "Стёр всё, что знал о тебе." to ReplyKind.MEMORY
            }

            is AssistantAction.RepeatLast -> {
                // Translate the requested kind into a fresh prompt and ask the engine again.
                val synthetic = when (action.lastKind) {
                    ReplyKind.JOKE -> "пошути"
                    ReplyKind.MOTIVATION -> "приободри меня"
                    ReplyKind.FACT, ReplyKind.MATH, ReplyKind.SCHEDULE, ReplyKind.MEMORY, ReplyKind.OTHER -> null
                }
                if (synthetic == null) {
                    "Пока нечего повторять. Сначала спроси что-нибудь — например, «пошути»." to ReplyKind.OTHER
                } else {
                    val again = AssistantEngine.answer(
                        rawInput = synthetic,
                        bellsByDayKind = mapOf(DayKind.MONDAY to mon, DayKind.TUE_SAT to tueSat),
                        subjectsByDow = subjects,
                        learnedQA = learned,
                        memoryFacts = memMap,
                        lastKind = lastKind,
                    )
                    if (again is AssistantAction.Reply) again.text to again.kind
                    else "Хм, не получилось повторить." to ReplyKind.OTHER
                }
            }
        }
    }

    fun clear() {
        _messages.value = listOf(
            ChatMessage(
                id = nextId++,
                fromUser = false,
                text = "Чат очищен. Спроси что-нибудь про расписание.",
            ),
        )
        lastKind = ReplyKind.OTHER
    }
}
