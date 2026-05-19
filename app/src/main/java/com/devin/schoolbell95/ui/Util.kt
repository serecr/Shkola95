package com.devin.schoolbell95.ui

fun fmtTime(min: Int): String {
    val h = (min / 60) % 24
    val m = min % 60
    return "%02d:%02d".format(h, m)
}

fun fmtCountdown(totalSeconds: Int): String {
    val s = totalSeconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec)
    else "%02d:%02d".format(m, sec)
}

fun dayOfWeekName(dow: Int): String = when (dow) {
    1 -> "Понедельник"
    2 -> "Вторник"
    3 -> "Среда"
    4 -> "Четверг"
    5 -> "Пятница"
    6 -> "Суббота"
    7 -> "Воскресенье"
    else -> "?"
}
