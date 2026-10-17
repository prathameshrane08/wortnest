package com.prathameshrane.wortnest

import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A saved word's review state. New and forgotten cards go through short steps (1 min, 10 min);
 * after that each card has its own interval in days that grows by its ease factor every time you remember it.
 * The same rules live in index.html (web) and Srs.swift (iOS).
 */
data class Card(
    val due: Long = 0,
    val ivl: Int = 0,          // days
    val ease: Double = 2.5,
    val step: Int = 0,         // learning step, -1 once the card is in review
    val reps: Int = 0,
    val lapses: Int = 0,
    val added: Long = System.currentTimeMillis(),
) {
    val isNew get() = reps == 0
    val isLearning get() = !isNew && step >= 0

    fun toJson(): JSONObject = JSONObject().put("due", due).put("ivl", ivl).put("ease", ease).put("step", step)
        .put("reps", reps).put("lapses", lapses).put("added", added)

    companion object {
        /** Reads a saved card. Cards from the old five-box version keep their progress. */
        fun fromJson(o: JSONObject): Card {
            if (o.has("ease")) return Card(
                due = o.getLong("due"), ivl = o.getInt("ivl"), ease = o.getDouble("ease"), step = o.getInt("step"),
                reps = o.getInt("reps"), lapses = o.getInt("lapses"), added = o.optLong("added"),
            )
            val box = o.optInt("box", 1)
            return if (box <= 1) Card(due = o.optLong("due"), added = 0)
            else Card(due = o.optLong("due"), ivl = intArrayOf(0, 0, 1, 3, 7, 21)[min(box, 5)], step = -1, reps = box - 1, added = 0)
        }
    }
}

object Srs {
    private val STEPS = doubleArrayOf(1.0, 10.0)   // minutes
    const val NEW_PER_DAY = 20
    const val LEARN_AHEAD = 20 * 60_000L
    val GRADE_NAMES = listOf("Again", "Hard", "Good", "Easy")

    /** grade: 0 again, 1 hard, 2 good, 3 easy */
    fun next(c: Card, grade: Int, now: Long = System.currentTimeMillis()): Card {
        val minute = 60_000L
        val day = 86_400_000L
        if (c.step >= 0) {
            return when {
                grade == 0 -> c.copy(reps = c.reps + 1, step = 0, due = now + (STEPS[0] * minute).toLong())
                grade == 1 -> c.copy(reps = c.reps + 1, due = now + ((if (c.step == 0) (STEPS[0] + STEPS[1]) / 2 else STEPS[c.step]) * minute).toLong())
                grade == 2 && c.step + 1 < STEPS.size -> c.copy(reps = c.reps + 1, step = c.step + 1, due = now + (STEPS[c.step + 1] * minute).toLong())
                else -> {
                    val ivl = if (grade == 3) max(4, c.ivl + 1) else max(1, c.ivl)
                    c.copy(reps = c.reps + 1, step = -1, ivl = ivl, due = now + ivl * day)
                }
            }
        }
        if (grade == 0) {
            val step = STEPS.size - 1
            return c.copy(
                reps = c.reps + 1, lapses = c.lapses + 1, ease = max(1.3, c.ease - 0.2),
                ivl = max(1, (c.ivl * 0.5).roundToInt()), step = step, due = now + (STEPS[step] * minute).toLong(),
            )
        }
        // Each button always gives a longer interval than the one to its left.
        val hard = max(c.ivl + 1, (c.ivl * 1.2).roundToInt())
        val good = max(hard + 1, (c.ivl * c.ease).roundToInt())
        val easy = max(good + 1, (c.ivl * c.ease * 1.3).roundToInt())
        val ivl = min(intArrayOf(0, hard, good, easy)[grade], 3650)
        val ease = when (grade) { 1 -> max(1.3, c.ease - 0.15); 3 -> c.ease + 0.15; else -> c.ease }
        return c.copy(reps = c.reps + 1, ivl = ivl, ease = ease, due = now + ivl * day)
    }

    fun label(c: Card, grade: Int, now: Long = System.currentTimeMillis()): String {
        val m = (next(c, grade, now).due - now) / 60_000.0
        if (m < 60) return "${max(1, m.roundToInt())} min"
        if (m < 1440) return "${(m / 60).roundToInt()} h"
        val d = (m / 1440).roundToInt()
        if (d < 31) return if (d == 1) "1 day" else "$d days"
        if (d < 365) return "${(d / 30.0).roundToInt()} mo"
        val y = (d / 365.0 * 10).roundToInt() / 10.0
        return if (y == Math.floor(y)) "${y.toInt()} yr" else "$y yr"
    }
}
