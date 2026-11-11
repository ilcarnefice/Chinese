package com.hskpath.core

/** A goal the learner works towards; [current] counts up to [target]. */
data class Achievement(val icon: String, val title: String, val description: String, val current: Int, val target: Int) {
    val done: Boolean get() = current >= target
    val fraction: Float get() = (current.toFloat() / target).coerceIn(0f, 1f)
}

object Achievements {
    const val XP_PER_LEVEL = 100

    /** The learner's rank: one level for every 100 XP. */
    fun xpLevel(xp: Int): Int = xp / XP_PER_LEVEL + 1

    fun xpIntoLevel(xp: Int): Int = xp % XP_PER_LEVEL

    fun all(progress: Progress, today: Long): List<Achievement> {
        val words = progress.cards.size
        val lessons = progress.completed.values.count { it > 0 }
        val streak = progress.currentStreak(today)
        val tests = (1..Content.LEVEL_COUNT).count { progress.passedTest(it) }
        return listOf(
            Achievement("🌱", "First steps", "Finish your first lesson", lessons, 1),
            Achievement("📚", "Bookworm", "Finish 25 lessons", lessons, 25),
            Achievement("🎓", "Scholar", "Finish 100 lessons", lessons, 100),
            Achievement("🧺", "Word collector", "Learn 50 words", words, 50),
            Achievement("🗃", "Word hoarder", "Learn 300 words", words, 300),
            Achievement("🏛", "Walking dictionary", "Learn 1,200 words", words, 1200),
            Achievement("🔥", "Warming up", "Keep a 3-day streak", streak, 3),
            Achievement("☄", "On fire", "Keep a 7-day streak", streak, 7),
            Achievement("🌋", "Unstoppable", "Keep a 30-day streak", streak, 30),
            Achievement("⚡", "Charged", "Earn 500 XP", progress.xp, 500),
            Achievement("🚀", "Lift-off", "Earn 5,000 XP", progress.xp, 5000),
            Achievement("🏅", "Certified", "Pass a level test", tests, 1),
            Achievement("👑", "Grand master", "Pass all six level tests", tests, 6),
        )
    }
}
