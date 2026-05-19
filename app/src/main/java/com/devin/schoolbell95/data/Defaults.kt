package com.devin.schoolbell95.data

/**
 * Real schedule for МАОУ «Центр образования №95» г. Уфа (per утв. ВрИО директора Р.Т. Вах…).
 *
 * Monday — 1st lesson is 30 min, breaks after lessons 1,2,3 are longer (15/15/10).
 * Tuesday–Friday (and Saturday) — all lessons 40 min, breaks 15/15/10/10/5/...
 *
 * The school rings up to 8 bells; for class 6А we default to the first 6.
 */
object Defaults {

    fun defaultBellsMonShift1(): List<Bell> = listOf(
        Bell(shift = 1, dayKind = DayKind.MONDAY, lessonNumber = 1, startMin = h(8, 30), endMin = h(9, 0)),
        Bell(shift = 1, dayKind = DayKind.MONDAY, lessonNumber = 2, startMin = h(9, 15), endMin = h(9, 55)),
        Bell(shift = 1, dayKind = DayKind.MONDAY, lessonNumber = 3, startMin = h(10, 10), endMin = h(10, 50)),
        Bell(shift = 1, dayKind = DayKind.MONDAY, lessonNumber = 4, startMin = h(11, 0), endMin = h(11, 40)),
        Bell(shift = 1, dayKind = DayKind.MONDAY, lessonNumber = 5, startMin = h(11, 45), endMin = h(12, 25)),
        Bell(shift = 1, dayKind = DayKind.MONDAY, lessonNumber = 6, startMin = h(12, 30), endMin = h(13, 10)),
        Bell(shift = 1, dayKind = DayKind.MONDAY, lessonNumber = 7, startMin = h(13, 15), endMin = h(13, 55)),
    )

    fun defaultBellsTueSatShift1(): List<Bell> = listOf(
        Bell(shift = 1, dayKind = DayKind.TUE_SAT, lessonNumber = 1, startMin = h(8, 30), endMin = h(9, 10)),
        Bell(shift = 1, dayKind = DayKind.TUE_SAT, lessonNumber = 2, startMin = h(9, 25), endMin = h(10, 5)),
        Bell(shift = 1, dayKind = DayKind.TUE_SAT, lessonNumber = 3, startMin = h(10, 20), endMin = h(11, 0)),
        Bell(shift = 1, dayKind = DayKind.TUE_SAT, lessonNumber = 4, startMin = h(11, 10), endMin = h(11, 50)),
        Bell(shift = 1, dayKind = DayKind.TUE_SAT, lessonNumber = 5, startMin = h(12, 0), endMin = h(12, 40)),
        Bell(shift = 1, dayKind = DayKind.TUE_SAT, lessonNumber = 6, startMin = h(12, 45), endMin = h(13, 25)),
        Bell(shift = 1, dayKind = DayKind.TUE_SAT, lessonNumber = 7, startMin = h(13, 30), endMin = h(14, 10)),
    )

    fun defaultBellsMonShift2(): List<Bell> = defaultBellsMonShift1().map {
        it.copy(id = 0, shift = 2, startMin = it.startMin + 5 * 60, endMin = it.endMin + 5 * 60)
    }

    fun defaultBellsTueSatShift2(): List<Bell> = defaultBellsTueSatShift1().map {
        it.copy(id = 0, shift = 2, startMin = it.startMin + 5 * 60, endMin = it.endMin + 5 * 60)
    }

    fun defaultsFor(shift: Int, dayKind: Int): List<Bell> = when {
        shift == 1 && dayKind == DayKind.MONDAY -> defaultBellsMonShift1()
        shift == 1 -> defaultBellsTueSatShift1()
        dayKind == DayKind.MONDAY -> defaultBellsMonShift2()
        else -> defaultBellsTueSatShift2()
    }

    /** Shortened day: 6 lessons of 30 min, 5 min breaks. Used on holidays. */
    fun shortenedFor(shift: Int, dayKind: Int): List<Bell> {
        val base = listOf(
            8 * 60 + 30 to 9 * 60,
            9 * 60 + 5 to 9 * 60 + 35,
            9 * 60 + 40 to 10 * 60 + 10,
            10 * 60 + 15 to 10 * 60 + 45,
            10 * 60 + 50 to 11 * 60 + 20,
            11 * 60 + 25 to 11 * 60 + 55,
        )
        val offset = if (shift == 2) 5 * 60 else 0
        return base.mapIndexed { i, (s, e) ->
            Bell(
                shift = shift,
                dayKind = dayKind,
                lessonNumber = i + 1,
                startMin = s + offset,
                endMin = e + offset,
            )
        }
    }

    fun allDefaults(): List<Bell> =
        defaultBellsMonShift1() + defaultBellsTueSatShift1() +
            defaultBellsMonShift2() + defaultBellsTueSatShift2()

    private fun h(hour: Int, minute: Int) = hour * 60 + minute

    /**
     * Default subjects for class 6А (per school timetable photo).
     * Mon=2, Tue=3, Wed=4, Thu=5, Fri=6 (Calendar constants).
     */
    fun defaultSubjects6A(): List<Subject> {
        val mon = 2
        val tue = 3
        val wed = 4
        val thu = 5
        val fri = 6
        return listOf(
            // Понедельник
            Subject(dow = mon, lessonNumber = 1, name = "Разговоры о важном"),
            Subject(dow = mon, lessonNumber = 2, name = "Литература"),
            Subject(dow = mon, lessonNumber = 3, name = "Математика"),
            Subject(dow = mon, lessonNumber = 4, name = "История"),
            Subject(dow = mon, lessonNumber = 5, name = "Физкультура"),
            Subject(dow = mon, lessonNumber = 6, name = "Родная литература"),
            Subject(dow = mon, lessonNumber = 7, name = "Русский язык"),
            // Вторник
            Subject(dow = tue, lessonNumber = 1, name = "Математика"),
            Subject(dow = tue, lessonNumber = 2, name = "Математика"),
            Subject(dow = tue, lessonNumber = 3, name = "История"),
            Subject(dow = tue, lessonNumber = 4, name = "Русский язык"),
            Subject(dow = tue, lessonNumber = 5, name = "Литература"),
            Subject(dow = tue, lessonNumber = 6, name = "Труд (технология)"),
            // Среда
            Subject(dow = wed, lessonNumber = 1, name = "Математика"),
            Subject(dow = wed, lessonNumber = 2, name = "География"),
            Subject(dow = wed, lessonNumber = 3, name = "Иностранный язык"),
            Subject(dow = wed, lessonNumber = 4, name = "Иностранный язык"),
            Subject(dow = wed, lessonNumber = 5, name = "Русский язык"),
            Subject(dow = wed, lessonNumber = 6, name = "ИЗО"),
            // Четверг
            Subject(dow = thu, lessonNumber = 1, name = "Математика"),
            Subject(dow = thu, lessonNumber = 2, name = "Русский язык"),
            Subject(dow = thu, lessonNumber = 3, name = "Русский язык"),
            Subject(dow = thu, lessonNumber = 4, name = "История"),
            Subject(dow = thu, lessonNumber = 5, name = "Гос. (башкирский) язык РБ"),
            Subject(dow = thu, lessonNumber = 6, name = "Труд (технология)"),
            // Пятница
            Subject(dow = fri, lessonNumber = 1, name = "Физкультура"),
            Subject(dow = fri, lessonNumber = 2, name = "Иностранный язык"),
            Subject(dow = fri, lessonNumber = 3, name = "Литература"),
            Subject(dow = fri, lessonNumber = 4, name = "Биология"),
            Subject(dow = fri, lessonNumber = 5, name = "Родной язык"),
            Subject(dow = fri, lessonNumber = 6, name = "Русский язык"),
        )
    }
}
