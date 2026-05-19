package com.devin.schoolbell95

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.devin.schoolbell95.data.AppDatabase
import com.devin.schoolbell95.data.Bell
import com.devin.schoolbell95.data.DayKind
import com.devin.schoolbell95.data.Defaults
import com.devin.schoolbell95.data.Prefs
import com.devin.schoolbell95.data.Subject
import com.devin.schoolbell95.notif.BellScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.TimeZone

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.get(app)
    private val prefs = Prefs(app)
    private val ufa = TimeZone.getTimeZone("Asia/Yekaterinburg")

    private val _shift = MutableStateFlow(prefs.shift)
    val shift: StateFlow<Int> = _shift.asStateFlow()

    private val _className = MutableStateFlow(prefs.className)
    val className: StateFlow<String> = _className.asStateFlow()

    private val _darkMode = MutableStateFlow(prefs.darkMode)
    val darkMode: StateFlow<String> = _darkMode.asStateFlow()

    private val _accentColor = MutableStateFlow(prefs.accentColor)
    val accentColor: StateFlow<String> = _accentColor.asStateFlow()

    private val _dynamicColor = MutableStateFlow(prefs.dynamicColor)
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    private val _notifyBell = MutableStateFlow(prefs.notifyOnBell)
    val notifyBell: StateFlow<Boolean> = _notifyBell.asStateFlow()

    private val _notify5 = MutableStateFlow(prefs.notify5Min)
    val notify5: StateFlow<Boolean> = _notify5.asStateFlow()

    /** Which dayKind the user is currently viewing/editing in the Bells screen. */
    private val _selectedDayKind = MutableStateFlow(todayDayKind())
    val selectedDayKind: StateFlow<Int> = _selectedDayKind.asStateFlow()

    /** Bells for the currently-selected schedule (used in Bells screen). */
    val bells: StateFlow<List<Bell>> = combine(_shift, _selectedDayKind, ::Pair)
        .flatMapLatest { (s, kind) -> db.bellDao().observe(s, kind) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Bells for today's schedule (used on Home screen, derived by day-of-week). */
    val todayBells: StateFlow<List<Bell>> = _shift
        .flatMapLatest { db.bellDao().observe(it, todayDayKind()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Which day-of-week the user is currently viewing/editing in the Subjects screen. */
    private val _selectedSubjectsDow = MutableStateFlow(todayDow())
    val selectedSubjectsDow: StateFlow<Int> = _selectedSubjectsDow.asStateFlow()

    /** Subjects for the day currently selected in the Subjects screen. */
    val subjectsForSelectedDay: StateFlow<List<Subject>> = _selectedSubjectsDow
        .flatMapLatest { db.subjectDao().observe(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Subjects for today (used on Home + widgets). */
    val todaySubjects: StateFlow<List<Subject>> = MutableStateFlow(todayDow())
        .flatMapLatest { db.subjectDao().observe(todayDow()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            if (db.bellDao().count() == 0) {
                db.bellDao().insertAll(Defaults.allDefaults())
            }
            if (db.subjectDao().count() == 0) {
                db.subjectDao().insertAll(Defaults.defaultSubjects6A())
            }
            BellScheduler.rescheduleAll(getApplication())
        }
    }

    private fun todayDow(): Int {
        val cal = Calendar.getInstance(ufa)
        return cal.get(Calendar.DAY_OF_WEEK)
    }

    private fun todayDayKind(): Int {
        val cal = Calendar.getInstance(ufa)
        return DayKind.fromCalendarDow(cal.get(Calendar.DAY_OF_WEEK))
    }

    fun setShift(value: Int) {
        prefs.shift = value
        _shift.update { value }
        BellScheduler.rescheduleAll(getApplication())
    }

    fun setClassName(value: String) {
        prefs.className = value
        _className.update { value }
    }

    fun setDarkMode(mode: String) {
        prefs.darkMode = mode
        _darkMode.update { mode }
    }

    fun setAccentColor(key: String) {
        prefs.accentColor = key
        _accentColor.update { key }
    }

    fun setDynamicColor(value: Boolean) {
        prefs.dynamicColor = value
        _dynamicColor.update { value }
    }

    fun setNotifyBell(v: Boolean) {
        prefs.notifyOnBell = v
        _notifyBell.update { v }
        BellScheduler.rescheduleAll(getApplication())
    }

    fun setNotify5(v: Boolean) {
        prefs.notify5Min = v
        _notify5.update { v }
        BellScheduler.rescheduleAll(getApplication())
    }

    fun setSelectedDayKind(kind: Int) {
        _selectedDayKind.update { kind }
    }

    fun upsertBell(bell: Bell) = viewModelScope.launch(Dispatchers.IO) {
        if (bell.id == 0L) db.bellDao().insert(bell) else db.bellDao().update(bell)
        BellScheduler.rescheduleAll(getApplication())
    }

    fun deleteBell(bell: Bell) = viewModelScope.launch(Dispatchers.IO) {
        db.bellDao().delete(bell)
        BellScheduler.rescheduleAll(getApplication())
    }

    /** Reset the currently-selected dayKind/shift to its real МАОУ ЦО №95 schedule. */
    fun resetBellsToDefault() = viewModelScope.launch(Dispatchers.IO) {
        val shiftValue = _shift.value
        val kind = _selectedDayKind.value
        db.bellDao().deleteShiftDayKind(shiftValue, kind)
        db.bellDao().insertAll(Defaults.defaultsFor(shiftValue, kind))
        BellScheduler.rescheduleAll(getApplication())
    }

    /** Apply 30-min shortened schedule to the currently-selected dayKind/shift. */
    fun applyShortenedSchedule() = viewModelScope.launch(Dispatchers.IO) {
        val shiftValue = _shift.value
        val kind = _selectedDayKind.value
        db.bellDao().deleteShiftDayKind(shiftValue, kind)
        db.bellDao().insertAll(Defaults.shortenedFor(shiftValue, kind))
        BellScheduler.rescheduleAll(getApplication())
    }

    suspend fun bellsForShiftDayKind(shift: Int, dayKind: Int): List<Bell> =
        withContext(Dispatchers.IO) { db.bellDao().list(shift, dayKind) }

    suspend fun subjectsFor(dow: Int): List<Subject> =
        withContext(Dispatchers.IO) { db.subjectDao().list(dow) }

    fun setSelectedSubjectsDow(dow: Int) {
        _selectedSubjectsDow.update { dow }
    }

    fun upsertSubject(subject: Subject) = viewModelScope.launch(Dispatchers.IO) {
        if (subject.id == 0L) db.subjectDao().insert(subject) else db.subjectDao().update(subject)
    }

    fun deleteSubject(subject: Subject) = viewModelScope.launch(Dispatchers.IO) {
        db.subjectDao().delete(subject)
    }

    fun resetSubjectsToDefault() = viewModelScope.launch(Dispatchers.IO) {
        for (dow in 1..7) db.subjectDao().deleteDay(dow)
        db.subjectDao().insertAll(Defaults.defaultSubjects6A())
    }
}
