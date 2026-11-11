package com.hskpath.core

/** One thing to do on a plan day. */
sealed interface PlanTask {
    /** Key under which completion is stored; null when it follows from other progress. */
    val key: String?

    data class Learn(val lesson: Lesson) : PlanTask {
        override val key: String? get() = null
    }

    data class Sentences(val level: Int, val day: Int) : PlanTask {
        override val key get() = "$level:$day:sentences"
    }

    /** Optional handwriting practice; it never blocks a day. */
    data class Write(val characters: List<String>) : PlanTask {
        override val key: String? get() = null
    }

    data class Test(val level: Int) : PlanTask {
        override val key: String? get() = null
    }
}

data class PlanDay(val level: Int, val number: Int, val tasks: List<PlanTask>) {
    val newWords: Int get() = tasks.filterIsInstance<PlanTask.Learn>().filter { !it.lesson.isReview }.sumOf { it.lesson.words.size }
    val grammarLessons: Int get() = tasks.count { it is PlanTask.Learn && it.lesson.isGrammar }
}

/**
 * A day-by-day schedule through one HSK level: every lesson of the path
 * (vocabulary, grammar and reviews), daily sentence practice, and the level test last.
 */
object TrainingPlan {
    /** Days allowed for each level. */
    val lengths = mapOf(1 to 30, 2 to 30, 3 to 30, 4 to 60, 5 to 100, 6 to 150)

    fun days(level: Int, content: Content): List<PlanDay> {
        val total = lengths.getValue(level)
        val studyDays = total - 1
        // Vocabulary, grammar and review lessons of the path, in order.
        val lessons = Curriculum.units(level, content).flatMap { it.lessons }
        val days = (0 until studyDays).map { i ->
            val todays = lessons.subList(i * lessons.size / studyDays, (i + 1) * lessons.size / studyDays)
            val tasks = mutableListOf<PlanTask>()
            todays.forEach { tasks += PlanTask.Learn(it) }
            tasks += PlanTask.Sentences(level, i + 1)
            val characters = todays.filter { !it.isReview }.flatMap { l -> l.words.flatMap { w -> w.hanzi.map { it.toString() } } }
                .distinct().take(WRITE_PER_DAY)
            if (characters.isNotEmpty()) tasks += PlanTask.Write(characters)
            PlanDay(level, i + 1, tasks)
        }
        return days + PlanDay(level, total, listOf(PlanTask.Test(level)))
    }

    fun isDone(task: PlanTask, progress: Progress): Boolean = when (task) {
        is PlanTask.Learn -> progress.isCompleted(task.lesson)
        is PlanTask.Test -> progress.testBest[task.level] != null
        is PlanTask.Write -> true
        else -> task.key in progress.planDone
    }

    fun isDone(day: PlanDay, progress: Progress): Boolean = day.tasks.all { isDone(it, progress) }

    /** The first day with something left to do, or null when the plan is finished. */
    fun today(days: List<PlanDay>, progress: Progress): PlanDay? = days.firstOrNull { !isDone(it, progress) }

    const val WRITE_PER_DAY = 3
}
