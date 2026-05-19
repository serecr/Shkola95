package com.devin.schoolbell95.assistant

import com.devin.schoolbell95.BellLogic
import com.devin.schoolbell95.BellState
import com.devin.schoolbell95.data.Bell
import com.devin.schoolbell95.data.DayKind
import com.devin.schoolbell95.data.LearnedQA
import com.devin.schoolbell95.data.Subject
import java.util.Calendar
import java.util.TimeZone

/**
 * High-level classification of a reply so the UI / view-model can repeat the same
 * kind of action when the user says «ещё раз».
 */
enum class ReplyKind {
    JOKE, MOTIVATION, FACT, SCHEDULE, MATH, MEMORY, OTHER,

    /**
     * The rule-based engine has nothing to say. Callers (e.g. the ViewModel) may
     * route the original prompt to the on-device LLM instead of showing the
     * canned «Не понял» hint.
     */
    UNKNOWN,
}

/**
 * Marker for the engine result so the view-model can know whether it needs to persist
 * a newly learned QA pair, save a memory fact, etc.
 */
sealed class AssistantAction {
    data class Reply(val text: String, val kind: ReplyKind = ReplyKind.OTHER) : AssistantAction()
    data class Learn(val question: String, val answer: String, val reply: String) : AssistantAction()
    data class Forget(val question: String, val reply: String) : AssistantAction()
    data object ClearLearned : AssistantAction()
    data class RememberFact(val key: String, val value: String, val reply: String) : AssistantAction()
    data class ForgetFact(val key: String, val reply: String) : AssistantAction()
    data object ClearMemory : AssistantAction()
    /** Repeat last action of the given kind. ViewModel turns this into a new request. */
    data class RepeatLast(val lastKind: ReplyKind) : AssistantAction()
}

/**
 * Offline rule-based assistant. Recognises Russian questions about the school schedule
 * and answers from the local bell data. No network, no LLM.
 *
 * `bellsByDayKind[DayKind.MONDAY]` = Monday bells, etc.
 */
object AssistantEngine {

    private val ufa: TimeZone = TimeZone.getTimeZone("Asia/Yekaterinburg")

    private val DAY_NAMES = listOf(
        Calendar.SUNDAY to "воскресенье",
        Calendar.MONDAY to "понедельник",
        Calendar.TUESDAY to "вторник",
        Calendar.WEDNESDAY to "среду",
        Calendar.THURSDAY to "четверг",
        Calendar.FRIDAY to "пятницу",
        Calendar.SATURDAY to "субботу",
    )

    private val DAY_NAMES_NOM = mapOf(
        Calendar.SUNDAY to "воскресенье",
        Calendar.MONDAY to "понедельник",
        Calendar.TUESDAY to "вторник",
        Calendar.WEDNESDAY to "среда",
        Calendar.THURSDAY to "четверг",
        Calendar.FRIDAY to "пятница",
        Calendar.SATURDAY to "суббота",
    )

    private val MONTHS_GEN = listOf(
        "января", "февраля", "марта", "апреля", "мая", "июня",
        "июля", "августа", "сентября", "октября", "ноября", "декабря",
    )

    fun suggestions(): List<String> = listOf(
        "Что сейчас?",
        "Сколько до звонка?",
        "Расписание на сегодня",
        "Расписание на завтра",
        "Какой следующий урок?",
        "Сколько до конца дня?",
        "Сколько уроков сегодня?",
        "Какой урок третий?",
        "Когда математика?",
        "Какой сегодня день?",
        "Сколько до выходных?",
        "Пошути",
        "Что ты умеешь?",
    )

    /** Total number of "tuned parameters" baked into the offline brain (KB size). */
    fun parameterCount(): Int = KnowledgeBase.size()

    fun answer(
        rawInput: String,
        bellsByDayKind: Map<Int, List<Bell>>,
        subjectsByDow: Map<Int, List<Subject>> = emptyMap(),
        learnedQA: List<LearnedQA> = emptyList(),
        memoryFacts: Map<String, String> = emptyMap(),
        lastKind: ReplyKind = ReplyKind.OTHER,
    ): AssistantAction {
        val raw = rawInput.trim()

        // --- self-learning commands (parse on raw input to preserve case in stored Q/A) ---
        parseTeachCommand(raw)?.let { (qq, aa) ->
            return AssistantAction.Learn(qq, aa, "Запомнил: «$qq» → «$aa». Спрашивай — отвечу.")
        }
        parseForgetCommand(raw)?.let { qq ->
            return AssistantAction.Forget(qq, "Забыл «$qq» (если такое было).")
        }
        if (isClearLearnedCommand(raw)) return AssistantAction.ClearLearned
        if (isShowLearnedCommand(raw)) return AssistantAction.Reply(renderLearnedList(learnedQA))
        if (isParametersQuery(raw)) return AssistantAction.Reply(
            "Во мне зашито ${KnowledgeBase.size()} готовых ответов в коде " +
                "плюс ${learnedQA.size} выученных позже. Всё офлайн.",
        )

        // --- repeat last action ---
        if (isRepeatRequest(raw)) {
            return AssistantAction.RepeatLast(lastKind)
        }

        // --- long-term memory commands ---
        parseRememberFact(raw)?.let { (key, value) ->
            return AssistantAction.RememberFact(
                key, value,
                "Запомнил: ${prettyMemoryAck(key, value)}",
            )
        }
        if (isForgetMeCommand(raw)) return AssistantAction.ClearMemory
        parseForgetFactCommand(raw)?.let { key ->
            return AssistantAction.ForgetFact(key, "Забыл это про тебя.")
        }
        recallFact(raw, memoryFacts)?.let { return AssistantAction.Reply(it, ReplyKind.MEMORY) }
        if (isWhatYouKnowAboutMe(raw)) return AssistantAction.Reply(
            renderMemory(memoryFacts), ReplyKind.MEMORY,
        )

        return answerCore(raw, bellsByDayKind, subjectsByDow, learnedQA, memoryFacts)
    }

    private fun answerCore(
        rawInput: String,
        bellsByDayKind: Map<Int, List<Bell>>,
        subjectsByDow: Map<Int, List<Subject>>,
        learnedQA: List<LearnedQA>,
        memoryFacts: Map<String, String>,
    ): AssistantAction {
        val text = answerText(rawInput, bellsByDayKind, subjectsByDow, learnedQA, memoryFacts)
        return AssistantAction.Reply(text.first, text.second)
    }

    /** Returns answer text and reply kind. */
    private fun answerText(
        rawInput: String,
        bellsByDayKind: Map<Int, List<Bell>>,
        subjectsByDow: Map<Int, List<Subject>>,
        learnedQA: List<LearnedQA>,
        memoryFacts: Map<String, String>,
    ): Pair<String, ReplyKind> {
        val q = normalize(rawInput)
        if (q.isEmpty()) return "Спроси что-нибудь про звонки или расписание." to ReplyKind.OTHER

        // --- personal feelings / identity statements: friendlier than "не понял" ---
        personalStatementReply(q, memoryFacts)?.let { return it to ReplyKind.OTHER }

        // --- smalltalk & meta ---
        if (isGreeting(q)) {
            val name = memoryFacts["name"]
            return (if (name != null) "Привет, $name! Чем помочь?"
            else pick(
                "Привет! Спроси «что сейчас», «расписание на завтра» или «сколько до звонка».",
                "Здравствуй! Чем помочь?",
                "Привет-привет :) Я готов отвечать про звонки и уроки.",
            )) to ReplyKind.OTHER
        }
        if (isHowAreYou(q)) return pick(
            "Я бот — у меня всё стабильно :)",
            "Хорошо, жду твоих вопросов.",
            "Нормально. А ты как? Готов к уроку?",
        ) to ReplyKind.OTHER
        if (isBye(q)) return pick("Пока!", "Удачи на уроках!", "До связи :)") to ReplyKind.OTHER
        if (isThanks(q)) return pick("Не за что :)", "Всегда пожалуйста!", "Обращайся.") to ReplyKind.OTHER
        if (isInsult(q)) return pick(
            "Ну прости, я ещё учусь :)",
            "Понял-понял, исправлюсь.",
            "Не обижай бота, у меня и так не самая лёгкая работа.",
        ) to ReplyKind.OTHER
        if (isWhoAreYou(q)) {
            return ("Я встроенный офлайн-помощник Школы №95. " +
                "Отвечаю по твоему расписанию звонков и предметов — без интернета.") to ReplyKind.OTHER
        }
        if (isWhatCanYouDo(q)) return helpText() to ReplyKind.OTHER
        if (isJokeRequest(q)) return pick(*JOKES) to ReplyKind.JOKE
        if (isComplimentRequest(q)) return pick(
            "Ты сегодня супер!",
            "У тебя всё получится — даже алгебра.",
            "Уверен, на следующем уроке ты будешь лучшим.",
        ) to ReplyKind.MOTIVATION

        // --- math: "2+2", "5 * 3" и т.п. ---
        evalSimpleMath(q)?.let { return it to ReplyKind.MATH }

        // --- time/date queries ---
        val cal = Calendar.getInstance(ufa)
        if (asksCurrentTime(q)) return "Сейчас ${fmt(cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE))} (Уфа)." to ReplyKind.FACT
        if (asksTodayDay(q)) return answerToday(cal) to ReplyKind.FACT
        if (asksTomorrowDay(q)) return answerTomorrowDay(cal) to ReplyKind.FACT
        if (asksUntilWeekend(q)) return answerUntilWeekend(cal) to ReplyKind.FACT

        val dow = cal.get(Calendar.DAY_OF_WEEK)
        val tomorrow = tomorrowDow(dow)
        val todayBells = bellsByDayKind[DayKind.fromCalendarDow(dow)].orEmpty()
        val tomorrowBells = bellsByDayKind[DayKind.fromCalendarDow(tomorrow)].orEmpty()
        val todaySubjects = subjectsByDow[dow].orEmpty()
        val tomorrowSubjects = subjectsByDow[tomorrow].orEmpty()
        val nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val nowSec = cal.get(Calendar.SECOND)
        val isSunday = dow == Calendar.SUNDAY
        val state = BellLogic.computeState(todayBells, nowMin, isSunday)

        // "какой N-й урок?" / "что во 2 уроке?"
        answerLessonByNumber(q, todayBells, todaySubjects, tomorrowBells, tomorrowSubjects)?.let { return it to ReplyKind.SCHEDULE }

        // "когда N урок?" / "во сколько 3 урок?"
        answerLessonTimeByNumber(q, todayBells, tomorrowBells, dow)?.let { return it to ReplyKind.SCHEDULE }

        // "что после математики" / "что перед русским"
        answerAroundSubject(q, todaySubjects, tomorrowSubjects)?.let { return it to ReplyKind.SCHEDULE }

        // "Когда <предмет>?" — общий поиск по предмету
        val subjectMatch = findSubjectQuery(q, subjectsByDow)
        if (subjectMatch != null) return subjectMatch to ReplyKind.SCHEDULE

        val scheduleAnswer = when {
            asksAboutTomorrowSchedule(q) -> renderScheduleWithSubjects("Завтра", tomorrowBells, tomorrowSubjects, tomorrow)
            asksAboutTodaySchedule(q) -> renderScheduleWithSubjects("Сегодня", todayBells, todaySubjects, dow)
            asksHowManyBreaks(q) -> answerHowManyBreaks(todayBells, tomorrowBells, q)
            asksHowManyLessons(q) -> answerHowMany(todayBells, tomorrowBells, q, dow)
            asksLessonLength(q) -> answerLessonLength(todayBells)
            asksLongestBreak(q) -> answerLongestBreak(todayBells)
            asksEndOfDay(q) -> answerEndOfDay(todayBells, dow, nowMin, nowSec)
            asksNextLesson(q) -> answerNextLesson(state, todayBells, todaySubjects)
            asksLastLesson(q) -> answerLastLesson(todayBells, todaySubjects, tomorrowBells, tomorrowSubjects, q)
            asksCurrentLesson(q) -> answerCurrentLesson(state, todaySubjects)
            asksTimeUntilBell(q) -> answerUntilBell(state, nowMin, nowSec, todayBells)
            asksTimeUntilEndOfLesson(q) -> answerUntilEndOfLesson(state, nowMin, nowSec, todaySubjects)
            asksTimeUntilBreak(q) -> answerUntilBreak(state, nowMin, nowSec)
            asksFirstLesson(q) -> answerFirstLesson(todayBells, todaySubjects, dow, tomorrowBells, tomorrowSubjects, q)
            else -> null
        }
        if (scheduleAnswer != null) return scheduleAnswer to ReplyKind.SCHEDULE

        // --- learned (user-taught) QA ---
        lookupLearned(q, learnedQA)?.let { return it to ReplyKind.FACT }

        // --- built-in knowledge base (~10k baked-in answers) ---
        KnowledgeBase.lookup(q)?.let { return it to ReplyKind.FACT }

        return fallback(q) to ReplyKind.UNKNOWN
    }

    // ---------- normalization ----------

    private fun normalize(s: String): String {
        val lower = s.lowercase().replace('ё', 'е').trim()
        return lower.replace(Regex("[!?.,;:\"'«»()\\[\\]{}]+"), " ").replace(Regex("\\s+"), " ").trim()
    }

    // ---------- smalltalk detectors ----------

    private fun isGreeting(q: String) =
        listOf("привет", "здравств", "ку", "хай", "hi", "hello", "доброе утро", "добрый день", "добрый вечер")
            .any { q == it || q.startsWith("$it ") || q.endsWith(" $it") }

    private fun isHowAreYou(q: String) =
        "как дела" in q || "как ты" in q || "как жизнь" in q || "как настроение" in q

    private fun isBye(q: String) =
        q == "пока" || q.startsWith("пока ") || "до свидан" in q || "до встреч" in q || q == "бб"

    private fun isThanks(q: String) = "спасиб" in q || "спс" in q || "благодар" in q || q == "спасибо"

    private fun isInsult(q: String) =
        listOf("тупой", "глупый", "дурак", "плохо отвеч", "тупиш", "тупишь", "ничего не знаешь", "не умеешь", "дурацк")
            .any { it in q }

    private fun isWhoAreYou(q: String) =
        ("кто ты" in q) || ("что ты" in q) || ("ты бот" in q) || ("ты чат" in q) ||
            ("кто это" in q) || ("как тебя зов" in q) || ("твое имя" in q) || ("твоё имя" in q)

    private fun isWhatCanYouDo(q: String) =
        "что ты умеешь" in q || "что умеешь" in q || "помощь" in q || q == "help" ||
            q == "?" || q == "помоги" || "что можешь" in q

    private fun isJokeRequest(q: String) =
        "пошути" in q || "шутк" in q || "анекдот" in q || "расскажи смешн" in q

    private fun isComplimentRequest(q: String) =
        "поддерж" in q || "похвал" in q || "комплимент" in q || "приободри" in q

    private val JOKES = arrayOf(
        "Учитель: «Где Гималаи?» Вова: «На последней странице атласа!»",
        "— Почему программисты путают Хэллоуин и Рождество? — Потому что Oct 31 == Dec 25.",
        "Сын приходит из школы. Папа: «Ну как, что нового узнал?» — «Что в дневник лучше не приносить плохие оценки!»",
        "— Сколько до звонка? — Один спрашивает, второй уже у двери.",
        "У меня хорошие новости: ты дочитал шутку. Плохие — она была не очень.",
        "Звонок звонит для учителя. Хорошо, что в этой школе он звонит ещё и для тебя.",
        "Если математика — это магия, то контрольная — это её экзамен.",
        "Самый честный предмет — физкультура. Там сразу видно, кто бегал, а кто нет.",
        "— Что между уроком и переменой? — Звонок и надежда.",
    )

    private fun helpText(): String = """
Я понимаю много вопросов. Работаю офлайн. Попробуй:

Расписание:
• «что сейчас?», «какой следующий урок?», «сколько до звонка?»
• «расписание на сегодня / на завтра»
• «какой урок третий?», «что во 2 уроке?», «когда 4 урок?»
• «когда математика?», «когда физкультура на неделе?»
• «что после математики?», «что перед русским?»
• «сколько уроков сегодня?», «сколько перемен?»
• «самая длинная перемена», «сколько идёт урок»
• «когда конец уроков?», «когда домой?»

Общие знания (≈10 000 готовых ответов в коде):
• столицы стран («столица Франции?»)
• таблица Менделеева («символ кислорода», «элемент 6»)
• математика: квадраты, кубы, корни, таблица умножения
• физика, биология, география, история, литература
• «который час?», «какой сегодня день?», «сколько до выходных?»
• «привет», «как дела», «пошути», «спасибо»

Самообучение (запоминается в памяти телефона):
• «научи: вопрос => ответ» — запомнить ответ
• «забудь: вопрос» — удалить из памяти
• «что ты выучил» — показать выученное
• «очисти память» — стереть всё выученное
• «сколько параметров» — статистика по базе
    """.trimIndent()

    // ---------- math eval ----------

    private val mathRegex = Regex("^\\s*(-?\\d+(?:[.,]\\d+)?)\\s*([+\\-*x×÷/])\\s*(-?\\d+(?:[.,]\\d+)?)\\s*\$")

    private fun evalSimpleMath(q: String): String? {
        val m = mathRegex.matchEntire(q.replace(" ", "")) ?: mathRegex.matchEntire(q) ?: return null
        val a = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val op = m.groupValues[2]
        val b = m.groupValues[3].replace(',', '.').toDoubleOrNull() ?: return null
        val result = when (op) {
            "+" -> a + b
            "-" -> a - b
            "*", "x", "×" -> a * b
            "/", "÷" -> if (b == 0.0) return "На ноль делить нельзя :)" else a / b
            else -> return null
        }
        val pretty = if (result == result.toLong().toDouble()) result.toLong().toString()
        else "%.4f".format(result).trimEnd('0').trimEnd('.')
        return "$pretty"
    }

    // ---------- date / time intents ----------

    private fun asksCurrentTime(q: String) =
        "который час" in q || "сколько время" in q || "сколько времени" in q ||
            q == "время" || q == "сколько щас времени" || "точное время" in q

    private fun asksTodayDay(q: String) =
        "какой сегодня день" in q || "какое сегодня число" in q || "какой день недели" in q ||
            q == "сегодня?" || q == "сегодня"

    private fun asksTomorrowDay(q: String) =
        "какой завтра день" in q || "какое завтра число" in q || q == "завтра" || q == "завтра?"

    private fun asksUntilWeekend(q: String) =
        ("выходн" in q) && ("до" in q || "сколько" in q || "когда" in q)

    private fun answerToday(cal: Calendar): String {
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val month = MONTHS_GEN[cal.get(Calendar.MONTH)]
        val name = DAY_NAMES_NOM[dow] ?: "?"
        return "Сегодня $name, $day $month."
    }

    private fun answerTomorrowDay(cal: Calendar): String {
        val c2 = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) }
        val name = DAY_NAMES_NOM[c2.get(Calendar.DAY_OF_WEEK)] ?: "?"
        val day = c2.get(Calendar.DAY_OF_MONTH)
        val month = MONTHS_GEN[c2.get(Calendar.MONTH)]
        return "Завтра $name, $day $month."
    }

    private fun answerUntilWeekend(cal: Calendar): String {
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        return when (dow) {
            Calendar.SUNDAY -> "Сегодня воскресенье — уже выходной :)"
            Calendar.SATURDAY -> "Сегодня суббота — уже выходной."
            else -> {
                val daysLeft = Calendar.SATURDAY - dow
                val word = pluralDays(daysLeft)
                "До выходных — $daysLeft $word."
            }
        }
    }

    // ---------- intent detectors (schedule) ----------

    private fun asksAboutTomorrowSchedule(q: String) =
        ("завтра" in q) && ("расписан" in q || "звонк" in q || "урок" in q || "что " in q)

    private fun asksAboutTodaySchedule(q: String) =
        ("сегодня" in q) && ("расписан" in q || "звонк" in q || "урок" in q || "что " in q)

    private fun asksHowManyLessons(q: String) =
        ("сколько" in q) && ("урок" in q)

    private fun asksHowManyBreaks(q: String) =
        ("сколько" in q) && ("перемен" in q)

    private fun asksLessonLength(q: String) =
        ("длина урока" in q || "сколько идет урок" in q || "сколько длится урок" in q ||
            "сколько минут урок" in q || ("урок" in q && "длится" in q))

    private fun asksLongestBreak(q: String) =
        ("перемен" in q || "обед" in q) && ("длинн" in q || "больш" in q || "самая" in q)

    private fun asksEndOfDay(q: String) =
        (("конец" in q || "закончат" in q || "закончит" in q || "до конца" in q) &&
            ("урок" in q || "школ" in q || "учеб" in q || "занят" in q || "дня" in q))
            || ("когда домой" in q) || ("во сколько домой" in q) || ("когда уйд" in q)

    private fun asksNextLesson(q: String) =
        ("следующ" in q || "след " in q || "потом" in q) && ("урок" in q)

    private fun asksLastLesson(q: String) =
        ("последн" in q && "урок" in q)

    private fun asksCurrentLesson(q: String) =
        (("сейчас" in q || "идет" in q) && ("урок" in q)) ||
            q == "что сейчас" || q == "сейчас" || q == "что щас" || q == "что идет"

    private fun asksTimeUntilBell(q: String) =
        "звонк" in q && ("сколько" in q || "когда" in q || "до" in q)

    private fun asksTimeUntilEndOfLesson(q: String) =
        ("до конца урока" in q) || ("конец урока" in q && "когда" in q) || ("когда закончит" in q && "урок" in q)

    private fun asksTimeUntilBreak(q: String) =
        "перемен" in q && ("когда" in q || "до" in q || "сколько" in q)

    private fun asksFirstLesson(q: String) =
        "перв" in q && "урок" in q

    // ---------- answers (schedule) ----------

    private fun answerCurrentLesson(state: BellState, subjects: List<Subject>): String = when (state) {
        is BellState.InLesson -> {
            val name = subjectName(subjects, state.bell.lessonNumber)
            val pretty = if (name != null) "Сейчас ${state.bell.lessonNumber}-й урок — $name" else "Сейчас ${state.bell.lessonNumber}-й урок"
            "$pretty, до ${fmt(state.bell.endMin)}."
        }
        is BellState.InBreak -> {
            val nextName = subjectName(subjects, state.nextBell.lessonNumber)
            val nextPart = if (nextName != null) "${state.nextBell.lessonNumber}-й урок — $nextName" else "${state.nextBell.lessonNumber}-й урок"
            "Сейчас перемена. Следующий — $nextPart в ${fmt(state.nextBell.startMin)}."
        }
        BellState.BeforeFirst -> "Уроки ещё не начались."
        BellState.AfterLast -> "Уроки на сегодня закончились."
        BellState.Weekend -> "Сегодня воскресенье — уроков нет."
    }

    private fun answerNextLesson(state: BellState, bells: List<Bell>, subjects: List<Subject>): String = when (state) {
        is BellState.InLesson -> {
            val next = state.nextBell
            if (next != null) {
                val nextName = subjectName(subjects, next.lessonNumber)
                val pretty = if (nextName != null) "${next.lessonNumber}-й урок — $nextName" else "${next.lessonNumber}-й урок"
                "Следующий — $pretty в ${fmt(next.startMin)} (до ${fmt(next.endMin)})."
            } else "${state.bell.lessonNumber}-й урок — последний на сегодня."
        }
        is BellState.InBreak -> {
            val nextName = subjectName(subjects, state.nextBell.lessonNumber)
            val pretty = if (nextName != null) "${state.nextBell.lessonNumber}-й урок — $nextName" else "${state.nextBell.lessonNumber}-й урок"
            "Следующий — $pretty в ${fmt(state.nextBell.startMin)}."
        }
        BellState.BeforeFirst -> bells.firstOrNull()?.let {
            val name = subjectName(subjects, it.lessonNumber)
            val pretty = if (name != null) "${it.lessonNumber}-й урок — $name" else "Первый урок"
            "$pretty в ${fmt(it.startMin)} (до ${fmt(it.endMin)})."
        } ?: "Сегодня уроков нет."
        BellState.AfterLast -> "Уроки на сегодня закончились."
        BellState.Weekend -> "Сегодня воскресенье — уроков нет."
    }

    private fun answerUntilBell(state: BellState, nowMin: Int, nowSec: Int, bells: List<Bell>): String {
        val secs = BellLogic.secondsUntilNextEvent(state, nowMin, nowSec)
        if (secs == null) {
            val first = bells.firstOrNull()
            return when {
                state == BellState.BeforeFirst && first != null ->
                    "До первого звонка — ${humanDuration((first.startMin - nowMin) * 60 - nowSec)}."
                state == BellState.AfterLast -> "Уроки уже закончились."
                else -> "Сегодня звонков нет."
            }
        }
        val label = when (state) {
            is BellState.InLesson -> "до конца урока"
            is BellState.InBreak -> "до начала ${state.nextBell.lessonNumber}-го урока"
            else -> "до звонка"
        }
        return "${humanDuration(secs)} $label."
    }

    private fun answerUntilEndOfLesson(state: BellState, nowMin: Int, nowSec: Int, subjects: List<Subject>): String =
        when (state) {
            is BellState.InLesson -> {
                val secs = (state.bell.endMin - nowMin) * 60 - nowSec
                val name = subjectName(subjects, state.bell.lessonNumber)
                val pretty = if (name != null) "${state.bell.lessonNumber}-го урока ($name)" else "${state.bell.lessonNumber}-го урока"
                "До конца $pretty — ${humanDuration(secs)}."
            }
            is BellState.InBreak -> "Сейчас перемена, а не урок."
            else -> "Сейчас урок не идёт."
        }

    private fun answerUntilBreak(state: BellState, nowMin: Int, nowSec: Int): String = when (state) {
        is BellState.InLesson -> {
            val secs = (state.bell.endMin - nowMin) * 60 - nowSec
            "До перемены — ${humanDuration(secs)}."
        }
        is BellState.InBreak -> "Сейчас и есть перемена. До конца — ${humanDuration((state.nextBell.startMin - nowMin) * 60 - nowSec)}."
        else -> "Сейчас урок не идёт."
    }

    private fun answerHowMany(today: List<Bell>, tomorrow: List<Bell>, q: String, dow: Int): String {
        return if ("завтра" in q) {
            val name = DAY_NAMES.first { it.first == tomorrowDow(dow) }.second
            "Завтра ($name) — ${tomorrow.size} ${pluralLessons(tomorrow.size)}."
        } else {
            "Сегодня — ${today.size} ${pluralLessons(today.size)}."
        }
    }

    private fun answerHowManyBreaks(today: List<Bell>, tomorrow: List<Bell>, q: String): String {
        val bells = if ("завтра" in q) tomorrow else today
        val count = (bells.size - 1).coerceAtLeast(0)
        val word = pluralBreaks(count)
        val prefix = if ("завтра" in q) "Завтра" else "Сегодня"
        return "$prefix — $count $word."
    }

    private fun answerLessonLength(bells: List<Bell>): String {
        if (bells.isEmpty()) return "Сегодня уроков нет."
        val lengths = bells.map { it.endMin - it.startMin }.distinct().sorted()
        return if (lengths.size == 1) "Урок длится ${lengths[0]} мин."
        else "Уроки идут по ${lengths.joinToString(" / ")} мин (зависит от номера урока)."
    }

    private fun answerLongestBreak(bells: List<Bell>): String {
        if (bells.size < 2) return "Перемен сегодня нет."
        var bestLen = 0
        var bestAfter = 0
        for (i in 0 until bells.size - 1) {
            val len = bells[i + 1].startMin - bells[i].endMin
            if (len > bestLen) {
                bestLen = len
                bestAfter = bells[i].lessonNumber
            }
        }
        return "Самая длинная перемена — $bestLen минут после $bestAfter-го урока."
    }

    private fun answerEndOfDay(bells: List<Bell>, dow: Int, nowMin: Int, nowSec: Int): String {
        if (dow == Calendar.SUNDAY) return "Сегодня воскресенье — уроков нет."
        val last = bells.maxByOrNull { it.endMin } ?: return "Сегодня уроков нет."
        val left = (last.endMin - nowMin) * 60 - nowSec
        val tail = if (left > 0) " До конца дня — ${humanDuration(left)}." else " Уроки уже закончились."
        return "Уроки сегодня заканчиваются в ${fmt(last.endMin)} (после ${last.lessonNumber}-го).$tail"
    }

    private fun answerFirstLesson(
        bells: List<Bell>,
        subjects: List<Subject>,
        dow: Int,
        tomorrowBells: List<Bell>,
        tomorrowSubjects: List<Subject>,
        q: String,
    ): String {
        if ("завтра" in q) {
            val first = tomorrowBells.minByOrNull { it.startMin } ?: return "Завтра уроков нет."
            val name = subjectName(tomorrowSubjects, first.lessonNumber)
            val suffix = if (name != null) " — $name" else ""
            return "Завтра 1-й урок$suffix — ${fmt(first.startMin)} – ${fmt(first.endMin)}."
        }
        if (dow == Calendar.SUNDAY) return "Сегодня воскресенье."
        val first = bells.minByOrNull { it.startMin } ?: return "Сегодня уроков нет."
        val name = subjectName(subjects, first.lessonNumber)
        val suffix = if (name != null) " — $name" else ""
        return "1-й урок$suffix — ${fmt(first.startMin)} – ${fmt(first.endMin)}."
    }

    private fun answerLastLesson(
        today: List<Bell>,
        todaySubjects: List<Subject>,
        tomorrow: List<Bell>,
        tomorrowSubjects: List<Subject>,
        q: String,
    ): String {
        val (bells, subjects, prefix) = if ("завтра" in q) Triple(tomorrow, tomorrowSubjects, "Завтра")
        else Triple(today, todaySubjects, "Сегодня")
        val last = bells.maxByOrNull { it.endMin } ?: return "$prefix уроков нет."
        val name = subjectName(subjects, last.lessonNumber)
        val suffix = if (name != null) " — $name" else ""
        return "$prefix последний — ${last.lessonNumber}-й урок$suffix (${fmt(last.startMin)} – ${fmt(last.endMin)})."
    }

    // ---------- new: lesson-by-number ----------

    private val ORDINAL_WORDS = mapOf(
        "первый" to 1, "первого" to 1, "первом" to 1,
        "второй" to 2, "второго" to 2, "втором" to 2,
        "третий" to 3, "третьего" to 3, "третьем" to 3,
        "четвертый" to 4, "четвертого" to 4, "четвертом" to 4,
        "пятый" to 5, "пятого" to 5, "пятом" to 5,
        "шестой" to 6, "шестого" to 6, "шестом" to 6,
        "седьмой" to 7, "седьмого" to 7, "седьмом" to 7,
        "восьмой" to 8, "восьмого" to 8, "восьмом" to 8,
    )

    private fun extractLessonNumber(q: String): Int? {
        // "3 урок", "3-й урок", "3-го урока", "урок 3"
        val rx = Regex("(?:^|\\D)(\\d{1,2})(?:-?(?:й|го|м|му))?(?=\\D|\$)")
        val m = rx.find(q)
        if (m != null) {
            val n = m.groupValues[1].toIntOrNull()
            if (n != null && n in 1..12) return n
        }
        for ((word, n) in ORDINAL_WORDS) if (word in q) return n
        return null
    }

    private fun answerLessonByNumber(
        q: String,
        todayBells: List<Bell>,
        todaySubjects: List<Subject>,
        tomorrowBells: List<Bell>,
        tomorrowSubjects: List<Subject>,
    ): String? {
        // need: "что", "какой", "урок" + number
        if ("урок" !in q) return null
        if (!(("какой" in q) || ("что " in q) || ("во " in q) || ("в " in q) || q.startsWith("что"))) return null
        // pure "когда N урок" handled separately
        if ("когда" in q || "во сколько" in q) return null
        val n = extractLessonNumber(q) ?: return null
        val (bells, subjects, prefix) = if ("завтра" in q)
            Triple(tomorrowBells, tomorrowSubjects, "Завтра")
        else Triple(todayBells, todaySubjects, "Сегодня")
        if (bells.isEmpty()) return "$prefix уроков нет."
        val bell = bells.firstOrNull { it.lessonNumber == n }
            ?: return "$prefix $n-го урока не нашёл (всего ${bells.size} ${pluralLessons(bells.size)})."
        val name = subjectName(subjects, n)
        val suffix = if (name != null) " — $name" else ""
        return "$prefix $n-й урок$suffix (${fmt(bell.startMin)} – ${fmt(bell.endMin)})."
    }

    private fun answerLessonTimeByNumber(
        q: String,
        todayBells: List<Bell>,
        tomorrowBells: List<Bell>,
        dow: Int,
    ): String? {
        if ("урок" !in q) return null
        if (!("когда" in q || "во сколько" in q || "в котор" in q)) return null
        val n = extractLessonNumber(q) ?: return null
        val (bells, prefix) = if ("завтра" in q) Pair(tomorrowBells, "Завтра") else Pair(todayBells, "Сегодня")
        if (bells.isEmpty()) return "$prefix уроков нет."
        val bell = bells.firstOrNull { it.lessonNumber == n }
            ?: return "$prefix $n-го урока нет (всего ${bells.size})."
        return "$prefix $n-й урок — с ${fmt(bell.startMin)} до ${fmt(bell.endMin)}."
    }

    // ---------- new: around-subject ("что после математики") ----------

    private fun answerAroundSubject(
        q: String,
        todaySubjects: List<Subject>,
        tomorrowSubjects: List<Subject>,
    ): String? {
        val isAfter = "после" in q
        val isBefore = "перед" in q || "до " in q
        if (!isAfter && !isBefore) return null
        val needle = matchSubject(q) ?: return null
        val subjects = if ("завтра" in q) tomorrowSubjects else todaySubjects
        val sorted = subjects.sortedBy { it.lessonNumber }
        val targetIdx = sorted.indexOfFirst { it.name.lowercase().contains(needle.lowercase()) }
        if (targetIdx < 0) return null
        val neighbour = (if (isAfter) sorted.getOrNull(targetIdx + 1) else sorted.getOrNull(targetIdx - 1))
            ?: return if (isAfter) "После $needle уроков нет." else "Перед $needle уроков нет."
        val prefix = if (isAfter) "После $needle —" else "Перед $needle —"
        return "$prefix ${neighbour.lessonNumber}-й урок: ${neighbour.name}."
    }

    // ---------- subject query (existing, slightly tweaked) ----------

    private fun renderScheduleWithSubjects(
        prefix: String,
        bells: List<Bell>,
        subjects: List<Subject>,
        dow: Int,
    ): String {
        if (dow == Calendar.SUNDAY) return "$prefix воскресенье — уроков нет."
        if (bells.isEmpty()) return "$prefix уроков нет."
        val sorted = bells.sortedBy { it.startMin }
        val list = sorted.joinToString("\n") {
            val name = subjectName(subjects, it.lessonNumber)
            val suffix = if (name != null) " — $name" else ""
            "${it.lessonNumber}) ${fmt(it.startMin)} – ${fmt(it.endMin)}$suffix"
        }
        return "$prefix:\n$list"
    }

    private fun subjectName(subjects: List<Subject>, lessonNumber: Int): String? =
        subjects.firstOrNull { it.lessonNumber == lessonNumber }?.name

    /** Detects queries like "когда математика" / "будет ли физкультура завтра". */
    private fun findSubjectQuery(q: String, subjectsByDow: Map<Int, List<Subject>>): String? {
        if (subjectsByDow.isEmpty()) return null
        val needle = matchSubject(q) ?: return null
        val isTomorrow = "завтра" in q
        val isWeek = "недел" in q
        val cal = Calendar.getInstance(ufa)
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        return when {
            isWeek -> renderWeekOccurrences(needle, subjectsByDow)
            isTomorrow -> renderDayOccurrence("завтра", needle, subjectsByDow[tomorrowDow(dow)].orEmpty())
            else -> {
                val today = subjectsByDow[dow].orEmpty().filter { it.name.lowercase().contains(needle.lowercase()) }
                if (today.isNotEmpty()) {
                    val list = today.joinToString(", ") { "${it.lessonNumber}-й урок" }
                    "Сегодня $needle: $list."
                } else {
                    findNextOccurrence(needle, subjectsByDow, dow)
                        ?: "Не нашёл $needle в расписании."
                }
            }
        }
    }

    private fun matchSubject(q: String): String? {
        val candidates = listOf(
            "математик" to "математика",
            "алгебр" to "алгебра",
            "геометр" to "геометрия",
            "русск" to "русский язык",
            "литератур" to "литература",
            "истор" to "история",
            "общество" to "обществознание",
            "физк" to "физкультура",
            "физра" to "физкультура",
            "физик" to "физика",
            "хими" to "химия",
            "родной" to "родной язык",
            "родная литератур" to "родная литература",
            "биолог" to "биология",
            "географ" to "география",
            "изо" to "ИЗО",
            "англ" to "иностранный язык",
            "иностран" to "иностранный язык",
            "башкир" to "башкирский",
            "труд" to "труд",
            "технолог" to "труд",
            "разговор" to "разговоры о важном",
            "важном" to "разговоры о важном",
            "информатик" to "информатика",
            "обж" to "ОБЖ",
            "музык" to "музыка",
        )
        return candidates.firstOrNull { it.first in q }?.second
    }

    private fun renderDayOccurrence(prefix: String, needle: String, daySubjects: List<Subject>): String {
        val hits = daySubjects.filter { it.name.lowercase().contains(needle.lowercase()) }
        return if (hits.isNotEmpty()) {
            val list = hits.joinToString(", ") { "${it.lessonNumber}-й урок" }
            "$prefix $needle: $list."
        } else "$prefix $needle нет."
    }

    private fun renderWeekOccurrences(needle: String, subjectsByDow: Map<Int, List<Subject>>): String {
        val days = listOf(
            Calendar.MONDAY to "пн",
            Calendar.TUESDAY to "вт",
            Calendar.WEDNESDAY to "ср",
            Calendar.THURSDAY to "чт",
            Calendar.FRIDAY to "пт",
            Calendar.SATURDAY to "сб",
        )
        val lines = days.mapNotNull { (d, lbl) ->
            val hits = subjectsByDow[d].orEmpty().filter { it.name.lowercase().contains(needle.lowercase()) }
            if (hits.isEmpty()) null else "$lbl: " + hits.joinToString(", ") { "${it.lessonNumber}-й" }
        }
        return if (lines.isEmpty()) "На этой неделе $needle не нашёл." else "$needle:\n" + lines.joinToString("\n")
    }

    private fun findNextOccurrence(needle: String, subjectsByDow: Map<Int, List<Subject>>, todayDow: Int): String? {
        val order = listOf(
            Calendar.MONDAY,
            Calendar.TUESDAY,
            Calendar.WEDNESDAY,
            Calendar.THURSDAY,
            Calendar.FRIDAY,
            Calendar.SATURDAY,
        )
        val startIdx = order.indexOf(todayDow).let { if (it < 0) 0 else (it + 1) % order.size }
        for (i in 0 until order.size) {
            val d = order[(startIdx + i) % order.size]
            val hits = subjectsByDow[d].orEmpty().filter { it.name.lowercase().contains(needle.lowercase()) }
            if (hits.isNotEmpty()) {
                val label = DAY_NAMES.first { it.first == d }.second
                val list = hits.joinToString(", ") { "${it.lessonNumber}-й урок" }
                return "Ближайший $needle — в $label: $list."
            }
        }
        return null
    }

    private fun tomorrowDow(dow: Int): Int = if (dow == Calendar.SATURDAY) Calendar.MONDAY else (dow % 7) + 1

    // ---------- repeat last ----------

    private fun isRepeatRequest(raw: String): Boolean {
        val n = normalize(raw)
        return n == "еще раз" || n == "еще" || n == "давай еще" || n == "давай еще раз" ||
            n == "повтори" || n == "повтори еще раз" || n == "продолжай" || n == "дальше" ||
            n == "next" || n == "more" || n == "снова"
    }

    // ---------- personal statements ----------

    /** Returns a friendly reply when the user makes a personal statement, else null. */
    private fun personalStatementReply(q: String, mem: Map<String, String>): String? {
        // Identity / orientation — calm acceptance, no judgement.
        if (matchesAny(q, "я гей", "я лгбт", "я бисексуал", "я лесбиян", "я лесбиянка", "я квир", "я транс")) {
            return pick(
                "Окей, принято. Я бот и тут чтобы помогать с уроками, а не оценивать.",
                "Понял. Это твоё дело — а я по расписанию и заданиям.",
                "Хорошо. Главное — будь собой и не забывай про учёбу.",
            )
        }
        if (matchesAny(q, "я натурал", "я гетеро", "я нормальный")) {
            return "Окей, принял. На вопросы по школе всё равно отвечу любому."
        }
        if (matchesAny(q, "ты гей", "ты лгбт", "ты пидор")) {
            return "Я бот — у меня нет ориентации, есть только база знаний :)"
        }

        // Mood support
        if (matchesAny(q, "мне грустно", "мне плохо", "я грущу", "мне печально", "мне тоскливо", "у меня депрессия")) {
            return pick(
                "Сочувствую. Иногда полезно подышать минут 5 и попить воды. А ещё — поделиться с близкими.",
                "Слышу. Если совсем тяжело — позвони на 8-800-2000-122 (телефон доверия для подростков, бесплатно).",
                "Бывает. Дай себе передышку: 10 минут тишины и любимая песня — реально помогает.",
            )
        }
        if (matchesAny(q, "я устал", "я устала", "я заебался", "я задолбался", "сил нет", "выгорел")) {
            return pick(
                "Понимаю. После уроков сделай паузу 20 минут без телефона — мозг скажет спасибо.",
                "Усталость — это нормально. Стакан воды + 5 минут отдыха = ты заново.",
                "Без отдыха не выехать. Сначала восстановись, потом домашка.",
            )
        }
        if (matchesAny(q, "я тупой", "я тупая", "я дурак", "я идиот", "я бездарь", "у меня ничего не получается", "я ничего не могу")) {
            return pick(
                "Ты не тупой. Просто эта тема ещё не уложилась — это вопрос времени и повторений.",
                "Каждый отличник когда-то не понимал тему. Разница только в том, что он попробовал ещё раз.",
                "Ошибиться — нормально. Главное — не сдаться.",
            )
        }
        if (matchesAny(q, "не хочу в школу", "не хочу учиться", "школа бесит", "ненавижу школу", "ненавижу учиться")) {
            return pick(
                "Понимаю. Иногда школа реально достаёт. Постарайся найти в дне хотя бы один любимый урок — это спасает.",
                "Бывает. Прикинь, сколько до конца дня — иногда осталось меньше, чем кажется.",
                "Школа не вечная. Сейчас тяжело, но это окупится.",
            )
        }
        if (matchesAny(q, "мне страшно", "я боюсь", "мне тревожно", "я волнуюсь", "я переживаю")) {
            return "Это нормально — волноваться. Сделай 4 глубоких вдоха-выдоха и сосредоточься на том, что в твоих силах прямо сейчас."
        }
        if (matchesAny(q, "мне скучно", "скучно", "нечего делать")) {
            return pick(
                "Скажи «пошути» — у меня 9 школьных анекдотов.",
                "Можно повторить то, что плохо понял на уроке — потом будет легче.",
                "Попробуй спросить меня про любую страну: «столица Канады» — и так далее.",
            )
        }
        if (matchesAny(q, "я голоден", "я голодный", "хочу есть", "я хочу есть")) {
            return "Перемена близко (или уже идёт). Проверь «когда перемена» и зацени, что в столовой."
        }
        if (matchesAny(q, "хочу спать", "я хочу спать", "сонный", "сонная")) {
            return "После 23:00 учёба не лезет. Постарайся ложиться раньше — а сейчас поплескай в лицо холодной воды."
        }
        if (matchesAny(q, "я люблю тебя", "ты лучший", "ты крутой", "ты молодец", "люблю тебя")) {
            return pick(
                "Спасибо :) Я тоже стараюсь.",
                "Приятно, но я просто код, который пытается быть полезным.",
                "Хорошего тебе дня!",
            )
        }
        if (matchesAny(q, "ты тупой", "ты глупый", "ты бесполезный", "ты лох", "ты тупица")) {
            return pick(
                "Окей, я ещё учусь. Скажи «научи: вопрос => ответ» — и я стану умнее.",
                "Возможно. Но я хотя бы под рукой :)",
            )
        }
        if (matchesAny(q, "ты живой", "ты человек", "ты настоящий")) {
            return "Нет, я программа. Работаю офлайн прямо на твоём телефоне."
        }
        // Falls through
        return null
    }

    private fun matchesAny(q: String, vararg patterns: String): Boolean {
        for (p in patterns) {
            if (q == p || q.startsWith("$p ") || q.endsWith(" $p") || " $p " in " $q ") return true
        }
        return false
    }

    // ---------- long-term memory ----------

    /** Parse statements like "меня зовут Радмир", "я в 7в", "мне 14 лет". */
    private fun parseRememberFact(raw: String): Pair<String, String>? {
        val n = normalize(raw)

        Regex("^меня зовут\\s+(.+)$").find(n)?.let {
            val name = it.groupValues[1].trim().takeIf { s -> s.isNotEmpty() && s.length < 40 }
            if (name != null) return "name" to capitalize(name)
        }
        Regex("^мое имя\\s+(.+)$").find(n)?.let {
            val name = it.groupValues[1].trim().takeIf { s -> s.isNotEmpty() && s.length < 40 }
            if (name != null) return "name" to capitalize(name)
        }
        Regex("^я\\s+([а-яa-z]{2,30})$").find(n)?.let {
            // "я радмир" — accept only if it looks like a single-word name (not "я гей" / "я устал").
            val candidate = it.groupValues[1]
            if (candidate !in PERSONAL_STATEMENT_WORDS) return "name" to capitalize(candidate)
        }
        Regex("^мне\\s+(\\d{1,2})\\s*(?:лет|года?)?$").find(n)?.let {
            return "age" to it.groupValues[1]
        }
        Regex("^я в\\s+(\\d{1,2}\\s*[а-я])\\s*классе?$").find(n)?.let {
            return "class" to it.groupValues[1].replace(" ", "").uppercase()
        }
        Regex("^я\\s+(?:учусь\\s+)?в\\s+(\\d{1,2}\\s*[а-я])\\s*классе?$").find(n)?.let {
            return "class" to it.groupValues[1].replace(" ", "").uppercase()
        }
        Regex("^я в\\s+(\\d{1,2})\\s*классе?$").find(n)?.let {
            return "class" to it.groupValues[1]
        }
        Regex("^(?:мой\\s+)?любимый предмет\\s*[-—:]?\\s*(.+)$").find(n)?.let {
            return "favorite_subject" to capitalize(it.groupValues[1].trim())
        }
        Regex("^я люблю\\s+(.+)$").find(n)?.let {
            val v = it.groupValues[1].trim()
            if (v.length in 2..30) return "like:$v" to "да"
        }
        Regex("^мне нравится\\s+(.+)$").find(n)?.let {
            val v = it.groupValues[1].trim()
            if (v.length in 2..30) return "like:$v" to "да"
        }
        return null
    }

    private val PERSONAL_STATEMENT_WORDS = setOf(
        "гей", "лгбт", "бисексуал", "бисексуалка", "лесбиян", "лесбиянка", "квир", "транс",
        "устал", "устала", "голоден", "голодный", "сонный", "сонная", "тупой", "тупая",
        "дурак", "идиот", "бездарь", "натурал", "гетеро", "нормальный",
    )

    private fun prettyMemoryAck(key: String, value: String): String = when (key) {
        "name" -> "тебя зовут $value."
        "age" -> "тебе $value ${pluralAge(value.toIntOrNull() ?: 0)}."
        "class" -> "ты в $value классе."
        "favorite_subject" -> "твой любимый предмет — $value."
        else -> if (key.startsWith("like:")) "тебе нравится ${key.removePrefix("like:")}." else "$key = $value."
    }

    private fun pluralAge(n: Int): String = when {
        n % 10 == 1 && n % 100 != 11 -> "год"
        n % 10 in 2..4 && (n % 100 !in 12..14) -> "года"
        else -> "лет"
    }

    private fun capitalize(s: String): String =
        if (s.isEmpty()) s else s.first().uppercaseChar() + s.drop(1)

    private fun isForgetMeCommand(raw: String): Boolean {
        val n = normalize(raw)
        return n == "забудь обо мне" || n == "забудь меня" || n == "забудь все обо мне" ||
            n == "очисти память обо мне" || n == "сотри память обо мне"
    }

    private fun parseForgetFactCommand(raw: String): String? {
        val n = normalize(raw)
        // "забудь как меня зовут", "забудь мое имя"
        if (n == "забудь как меня зовут" || n == "забудь мое имя") return "name"
        if (n == "забудь мой возраст" || n == "забудь сколько мне лет") return "age"
        if (n == "забудь мой класс") return "class"
        if (n == "забудь мой любимый предмет") return "favorite_subject"
        return null
    }

    /** Try to answer a recall question like «как меня зовут» from memory. */
    private fun recallFact(raw: String, mem: Map<String, String>): String? {
        val n = normalize(raw)
        // Name
        if (n in NAME_QUESTIONS) {
            return mem["name"]?.let { "Тебя зовут $it." }
                ?: "Я ещё не знаю, как тебя зовут. Скажи: «меня зовут …»."
        }
        // Age
        if (n in AGE_QUESTIONS) {
            return mem["age"]?.let { "Тебе $it ${pluralAge(it.toIntOrNull() ?: 0)}." }
                ?: "Я не знаю, сколько тебе лет. Скажи: «мне … лет»."
        }
        // Class
        if (n in CLASS_QUESTIONS) {
            return mem["class"]?.let { "Ты в $it классе." }
                ?: "Я не знаю, в каком ты классе. Скажи: «я в 7в»."
        }
        // Favorite subject
        if (n in FAVOURITE_QUESTIONS) {
            return mem["favorite_subject"]?.let { "Твой любимый предмет — $it." }
                ?: "Я не знаю твой любимый предмет. Скажи: «мой любимый предмет — …»."
        }
        // "люблю ли я X"
        Regex("^я люблю\\s+(.+)\\??$").find(n)?.let {
            val k = "like:${it.groupValues[1].trim()}"
            if (mem[k] != null) return "Да, ты говорил, что любишь ${it.groupValues[1].trim()}."
        }
        return null
    }

    private val NAME_QUESTIONS = setOf(
        "как меня зовут", "ты помнишь как меня зовут", "помнишь как меня зовут",
        "кто я", "помнишь мое имя", "знаешь как меня зовут", "какое мое имя",
    )
    private val AGE_QUESTIONS = setOf(
        "сколько мне лет", "ты помнишь сколько мне лет", "помнишь сколько мне лет",
        "мой возраст", "какой у меня возраст",
    )
    private val CLASS_QUESTIONS = setOf(
        "в каком я классе", "помнишь в каком я классе", "мой класс", "какой у меня класс",
    )
    private val FAVOURITE_QUESTIONS = setOf(
        "какой мой любимый предмет", "помнишь мой любимый предмет",
        "что я больше всего люблю", "мой любимый предмет",
    )

    private fun isWhatYouKnowAboutMe(raw: String): Boolean {
        val n = normalize(raw)
        return n == "что ты обо мне знаешь" || n == "что ты знаешь обо мне" ||
            n == "что ты помнишь обо мне" || n == "расскажи обо мне" || n == "память"
    }

    private fun renderMemory(mem: Map<String, String>): String {
        if (mem.isEmpty()) return "Я пока ничего о тебе не знаю. Скажи «меня зовут …», «мне … лет», «я в 7в»."
        val parts = mutableListOf<String>()
        mem["name"]?.let { parts += "имя: $it" }
        mem["age"]?.let { parts += "возраст: $it ${pluralAge(it.toIntOrNull() ?: 0)}" }
        mem["class"]?.let { parts += "класс: $it" }
        mem["favorite_subject"]?.let { parts += "любимый предмет: $it" }
        val likes = mem.keys.filter { it.startsWith("like:") }.map { it.removePrefix("like:") }
        if (likes.isNotEmpty()) parts += "любит: ${likes.joinToString(", ")}"
        return "Я о тебе помню:\n• ${parts.joinToString("\n• ")}"
    }

    // ---------- self-learning ----------

    private val teachRegex = Regex(
        "^\\s*(?:научи|запомни|выучи)\\s*[:\\-]?\\s*(.+?)\\s*(?:=>|->|→|=)\\s*(.+?)\\s*$",
        RegexOption.IGNORE_CASE,
    )

    private fun parseTeachCommand(raw: String): Pair<String, String>? {
        val m = teachRegex.matchEntire(raw) ?: return null
        val q = m.groupValues[1].trim().trim('"', '«', '»')
        val a = m.groupValues[2].trim().trim('"', '«', '»')
        if (q.isEmpty() || a.isEmpty()) return null
        return q to a
    }

    // Require an explicit separator (":" "=" "—" "-") so neutral phrases like
    // "забудь про математику" don't accidentally trigger forget.
    private val forgetRegex = Regex(
        "^\\s*(?:забудь|удали)\\s*[:=\\-—]\\s*(.+?)\\s*$",
        RegexOption.IGNORE_CASE,
    )

    private fun parseForgetCommand(raw: String): String? {
        val m = forgetRegex.matchEntire(raw) ?: return null
        val candidate = m.groupValues[1].trim().trim('"', '«', '»')
        if (candidate.isEmpty()) return null
        return candidate
    }

    private fun isClearLearnedCommand(raw: String): Boolean {
        val n = normalize(raw)
        return n == "очисти память" || n == "забудь все" || n == "забудь всё" ||
            n == "очистить память" || n == "стереть память"
    }

    private fun isShowLearnedCommand(raw: String): Boolean {
        val n = normalize(raw)
        return n == "что ты выучил" || n == "что я тебе выучил" || n == "покажи память" ||
            n == "выученное" || n == "что запомнил"
    }

    private fun isParametersQuery(raw: String): Boolean {
        val n = normalize(raw)
        return "параметр" in n && ("сколько" in n || "количеств" in n) ||
            n == "сколько у тебя параметров" || n == "сколько параметров"
    }

    private fun renderLearnedList(learned: List<LearnedQA>): String {
        if (learned.isEmpty()) return "Я пока ничего не выучил. Скажи «научи: вопрос => ответ», и я запомню."
        val head = "Я выучил ${learned.size} ${pluralPairs(learned.size)}:"
        val list = learned.take(20).joinToString("\n") { "• ${it.question} → ${it.answer}" }
        val tail = if (learned.size > 20) "\n… и ещё ${learned.size - 20}." else ""
        return "$head\n$list$tail"
    }

    private fun pluralPairs(n: Int): String = when {
        n % 10 == 1 && n % 100 != 11 -> "пару"
        n % 10 in 2..4 && (n % 100 !in 12..14) -> "пары"
        else -> "пар"
    }

    /** Find a learned QA pair whose question best matches the normalized query. */
    private fun lookupLearned(q: String, learned: List<LearnedQA>): String? {
        if (learned.isEmpty()) return null
        val qWords = q.split(" ").filter { it.length >= 2 }
        if (qWords.isEmpty()) return null
        var bestScore = 0
        var best: LearnedQA? = null
        for (item in learned) {
            val nq = normalize(item.question)
            if (nq == q) return item.answer
            val keyWords = nq.split(" ").filter { it.isNotEmpty() }
            if (keyWords.isEmpty()) continue
            var score = 0
            for (w in qWords) {
                if (keyWords.any { it == w || it.startsWith(w) || w.startsWith(it) }) score++
            }
            // require that almost all query words match
            val needed = (qWords.size * 0.7).toInt().coerceAtLeast(1)
            if (score >= needed && score > bestScore) {
                bestScore = score
                best = item
            }
        }
        return best?.answer
    }

    // ---------- fallback ----------

    private fun fallback(q: String): String {
        // soft hint: попробуем угадать, что человек хотел
        val hints = mutableListOf<String>()
        if ("урок" in q) hints += "«какой следующий урок?», «расписание на сегодня», «какой урок 3?»"
        if ("звонок" in q || "звонк" in q) hints += "«сколько до звонка?», «когда звонок?»"
        if ("перемен" in q) hints += "«самая длинная перемена», «сколько перемен сегодня»"
        if (hints.isEmpty()) hints += "«что сейчас», «расписание на завтра», «когда математика», «пошути»"
        return "Не понял. Попробуй: " + hints.joinToString("; ") + "."
    }

    // ---------- utilities ----------

    private fun pick(vararg s: String): String = s.random()

    private fun pluralLessons(n: Int): String = when {
        n % 10 == 1 && n % 100 != 11 -> "урок"
        n % 10 in 2..4 && (n % 100 !in 12..14) -> "урока"
        else -> "уроков"
    }

    private fun pluralBreaks(n: Int): String = when {
        n % 10 == 1 && n % 100 != 11 -> "перемена"
        n % 10 in 2..4 && (n % 100 !in 12..14) -> "перемены"
        else -> "перемен"
    }

    private fun pluralDays(n: Int): String = when {
        n % 10 == 1 && n % 100 != 11 -> "день"
        n % 10 in 2..4 && (n % 100 !in 12..14) -> "дня"
        else -> "дней"
    }

    private fun humanDuration(totalSeconds: Int): String {
        val s = totalSeconds.coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return when {
            h > 0 -> "%d ч %02d мин".format(h, m)
            m > 0 -> "%d мин %02d сек".format(m, sec)
            else -> "%d сек".format(sec)
        }
    }

    private fun fmt(min: Int): String {
        val h = (min / 60) % 24
        val m = min % 60
        return "%02d:%02d".format(h, m)
    }
}
