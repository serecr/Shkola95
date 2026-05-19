package com.devin.schoolbell95

import com.devin.schoolbell95.data.Bell

/**
 * What is happening "right now" relative to a sorted list of bells.
 */
sealed class BellState {
    data object BeforeFirst : BellState()
    data object AfterLast : BellState()
    data object Weekend : BellState()
    data class InLesson(val bell: Bell, val nextBell: Bell?) : BellState()
    data class InBreak(val prevBell: Bell, val nextBell: Bell) : BellState()
}

object BellLogic {

    /** Compute the current state given local time-of-day in minutes plus seconds. */
    fun computeState(
        bells: List<Bell>,
        nowMin: Int,
        isSunday: Boolean,
    ): BellState {
        if (bells.isEmpty()) return BellState.BeforeFirst
        if (isSunday) return BellState.Weekend
        val sorted = bells.sortedBy { it.startMin }

        if (nowMin < sorted.first().startMin) return BellState.BeforeFirst

        for ((i, b) in sorted.withIndex()) {
            if (nowMin >= b.startMin && nowMin < b.endMin) {
                val next = sorted.getOrNull(i + 1)
                return BellState.InLesson(b, next)
            }
        }

        for (i in 0 until sorted.size - 1) {
            val cur = sorted[i]
            val nxt = sorted[i + 1]
            if (nowMin >= cur.endMin && nowMin < nxt.startMin) {
                return BellState.InBreak(cur, nxt)
            }
        }
        return BellState.AfterLast
    }

    /** Seconds until the next event (start or end of a lesson). */
    fun secondsUntilNextEvent(state: BellState, nowMin: Int, nowSec: Int): Int? {
        val target = when (state) {
            is BellState.InLesson -> state.bell.endMin
            is BellState.InBreak -> state.nextBell.startMin
            else -> return null
        }
        val total = (target - nowMin) * 60 - nowSec
        return total.coerceAtLeast(0)
    }
}
