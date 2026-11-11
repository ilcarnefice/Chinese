package com.hskpath.core

/** Outcome of a finished session. */
data class SessionResult(
    val graded: Int,
    val correctFirstTry: Int,
    /** Word id -> whether every exercise on it was answered right the first time. */
    val wordResults: Map<String, Boolean>,
    val xp: Int,
) {
    val percent: Int get() = if (graded == 0) 100 else correctFirstTry * 100 / graded
}

/**
 * Runs through a list of exercises. In lessons a missed exercise comes back at
 * the end until it is answered correctly; in tests every exercise is asked once.
 */
class Session(exercises: List<Exercise>, private val retryMistakes: Boolean = true) {
    private val queue = ArrayDeque(exercises)
    private val retried = HashSet<Exercise>()
    private val wordResults = LinkedHashMap<String, Boolean>()
    private val total = exercises.size
    private var done = 0

    val graded: Int = exercises.count { it !is Teach }
    var correctFirstTry = 0
        private set

    val current: Exercise? get() = queue.firstOrNull()
    val isFinished: Boolean get() = queue.isEmpty()
    val progress: Float get() = if (total == 0) 1f else done.toFloat() / total

    /** Records the answer to the current exercise and moves on. */
    fun submit(correct: Boolean) {
        val exercise = queue.removeFirstOrNull() ?: return
        val firstTry = exercise !in retried
        if (exercise !is Teach && firstTry) {
            if (correct) correctFirstTry++
            for (id in exercise.wordIds) wordResults[id] = (wordResults[id] ?: true) && correct
        }
        if (!correct && retryMistakes && exercise !is Teach) {
            retried += exercise
            queue.addLast(exercise)
        } else {
            done++
        }
    }

    fun result(): SessionResult {
        val perfect = correctFirstTry == graded
        return SessionResult(graded, correctFirstTry, wordResults.toMap(), BASE_XP + if (perfect) PERFECT_BONUS else 0)
    }

    companion object {
        const val BASE_XP = 10
        const val PERFECT_BONUS = 5
    }
}
