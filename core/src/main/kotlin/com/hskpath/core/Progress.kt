package com.hskpath.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.max
import kotlin.math.roundToInt

/** Spaced-repetition state of one word. Days are counted since 1970-01-01. */
@Serializable
data class CardState(
    val reps: Int = 0,
    val intervalDays: Int = 0,
    val ease: Double = 2.5,
    val due: Long = 0,
)

object Srs {
    /** Next state after practising a word: right answers space it out, a miss brings it back tomorrow. */
    fun review(state: CardState?, correct: Boolean, today: Long): CardState {
        val s = state ?: CardState()
        if (!correct) return s.copy(reps = 0, intervalDays = 1, ease = max(1.3, s.ease - 0.2), due = today + 1)
        val interval = when (s.reps) {
            0 -> 1
            1 -> 3
            else -> max(s.intervalDays + 1, (s.intervalDays * s.ease).roundToInt())
        }
        return s.copy(reps = s.reps + 1, intervalDays = interval, ease = s.ease + 0.05, due = today + interval)
    }
}

/** Everything the app remembers about the learner. Immutable; updates return a copy. */
@Serializable
data class Progress(
    val cards: Map<String, CardState> = emptyMap(),
    /** Lesson id -> number of times completed. */
    val completed: Map<String, Int> = emptyMap(),
    val xp: Int = 0,
    val dailyXp: Map<Long, Int> = emptyMap(),
    val streak: Int = 0,
    val lastStudyDay: Long = -1,
    /** Level -> best level-test score in percent. */
    val testBest: Map<Int, Int> = emptyMap(),
) {
    fun isCompleted(lesson: Lesson) = (completed[lesson.id] ?: 0) > 0

    fun passedTest(level: Int) = (testBest[level] ?: 0) >= TEST_PASS_PERCENT

    /** A lesson opens once the one before it is done, or the level test was passed. */
    fun isUnlocked(lesson: Lesson, units: List<CourseUnit>): Boolean {
        if (passedTest(lesson.level)) return true
        val all = units.flatMap { it.lessons }
        val i = all.indexOfFirst { it.id == lesson.id }
        return i <= 0 || isCompleted(all[i - 1])
    }

    fun learnedCount(level: Int): Int = cards.keys.count { it.startsWith("$level:") }

    fun xpToday(today: Long): Int = dailyXp[today] ?: 0

    /** The streak as it stands today: it lapses after a full day without study. */
    fun currentStreak(today: Long): Int = if (lastStudyDay >= today - 1) streak else 0

    /** Ids of learned words that are due for practice, most overdue first. */
    fun dueWordIds(today: Long): List<String> =
        cards.filter { it.value.due <= today }.entries.sortedBy { it.value.due }.map { it.key }

    /** Applies a finished session: schedules its words, adds XP and extends the streak. */
    fun afterSession(result: SessionResult, today: Long, lessonId: String? = null): Progress {
        val newCards = cards.toMutableMap()
        for ((id, correct) in result.wordResults) newCards[id] = Srs.review(cards[id], correct, today)
        val newStreak = when (lastStudyDay) {
            today -> max(streak, 1)
            today - 1 -> streak + 1
            else -> 1
        }
        return copy(
            cards = newCards,
            completed = if (lessonId == null) completed else completed + (lessonId to (completed[lessonId] ?: 0) + 1),
            xp = xp + result.xp,
            dailyXp = dailyXp.filterKeys { it > today - 30 } + (today to xpToday(today) + result.xp),
            streak = newStreak,
            lastStudyDay = today,
        )
    }

    /** Records a level-test score; XP and streak come from [afterSession]. */
    fun withTestScore(level: Int, percent: Int): Progress =
        copy(testBest = testBest + (level to max(percent, testBest[level] ?: 0)))

    fun toJson(): String = json.encodeToString(this)

    companion object {
        const val TEST_PASS_PERCENT = 80
        const val DAILY_GOAL_XP = 30
        private val json = Json { ignoreUnknownKeys = true }

        /** Reads saved progress; damaged or missing data starts a fresh profile. */
        fun fromJson(text: String?): Progress =
            if (text.isNullOrBlank()) Progress()
            else runCatching { json.decodeFromString<Progress>(text) }.getOrDefault(Progress())
    }
}
