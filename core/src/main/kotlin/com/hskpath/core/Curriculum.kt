package com.hskpath.core

/** A short lesson: a handful of new words, or a review of a whole unit. */
data class Lesson(
    val level: Int,
    val unit: Int,
    val index: Int,
    val words: List<Word>,
    val isReview: Boolean,
) {
    val id: String get() = "$level-$unit-$index"
}

/** A unit on the learning path: several lessons that end with a review. */
data class CourseUnit(val level: Int, val number: Int, val lessons: List<Lesson>) {
    val words: List<Word> get() = lessons.filter { !it.isReview }.flatMap { it.words }
}

object Curriculum {
    const val WORDS_PER_LESSON = 5
    const val LESSONS_PER_UNIT = 4

    /** Splits a level's word list (already ordered from common to rare) into units. */
    fun units(level: Int, words: List<Word>): List<CourseUnit> =
        words.chunked(WORDS_PER_LESSON * LESSONS_PER_UNIT).mapIndexed { u, unitWords ->
            val lessons = unitWords.chunked(WORDS_PER_LESSON).mapIndexed { i, chunk ->
                Lesson(level, u + 1, i + 1, chunk, isReview = false)
            }
            val review = Lesson(level, u + 1, lessons.size + 1, unitWords, isReview = true)
            CourseUnit(level, u + 1, lessons + review)
        }
}
