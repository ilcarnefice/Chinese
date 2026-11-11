package com.hskpath.core

/** One entry of a unit in a course outline: a vocabulary lesson or a grammar lesson. */
sealed interface OutlineItem {
    data class Words(val words: List<String>) : OutlineItem
    data class Grammar(val title: String) : OutlineItem
}

/** A themed unit as written in `content/outline_hsk<N>.txt`. */
data class UnitOutline(val title: String, val items: List<OutlineItem>)

/**
 * A step on the learning path: a few new words, one grammar formula, or a
 * review of the whole unit.
 */
data class Lesson(
    val level: Int,
    val unit: Int,
    val index: Int,
    val words: List<Word>,
    val isReview: Boolean,
    /** Set for a grammar lesson, which teaches this formula instead of new words. */
    val grammar: GrammarPoint? = null,
) {
    val id: String get() = "$level-$unit-$index"
    val isGrammar: Boolean get() = grammar != null
}

/** A themed unit on the learning path: vocabulary and grammar lessons, then a review. */
data class CourseUnit(val level: Int, val number: Int, val title: String, val lessons: List<Lesson>) {
    /** The new words this unit teaches. */
    val words: List<Word> get() = lessons.filter { !it.isReview && !it.isGrammar }.flatMap { it.words }
    val grammar: List<GrammarPoint> get() = lessons.mapNotNull { it.grammar }
}

object Curriculum {
    const val WORDS_PER_LESSON = 5
    const val LESSONS_PER_UNIT = 4

    /**
     * The path for a level. Units, their themes and the order of words come from
     * the level's outline; without one, words are taken in list order. Grammar
     * formulas the outline does not place are spread evenly through the level.
     */
    fun units(level: Int, content: Content): List<CourseUnit> {
        val words = content.wordsOf(level)
        val byHanzi = words.associateBy { it.hanzi }
        val grammar = content.grammarOf(level)
        val grammarByTitle = grammar.associateBy { it.title }

        // Each unit as a title and a list of steps: a list of words, or a formula.
        val drafts = mutableListOf<Pair<String, MutableList<Any>>>()
        val taught = LinkedHashSet<String>()
        val placed = LinkedHashSet<GrammarPoint>()
        for (unit in content.outline(level).orEmpty()) {
            val steps = mutableListOf<Any>()
            for (item in unit.items) when (item) {
                is OutlineItem.Words -> {
                    val lesson = item.words.mapNotNull { byHanzi[it] }.filter { taught.add(it.hanzi) }
                    if (lesson.isNotEmpty()) steps.add(lesson)
                }
                is OutlineItem.Grammar -> grammarByTitle[item.title]?.let { if (placed.add(it)) steps.add(it) }
            }
            if (steps.any { it is List<*> }) drafts += unit.title to steps
        }
        // Words the outline does not mention still get taught, in list order.
        val rest = words.filter { it.hanzi !in taught }
        rest.chunked(WORDS_PER_LESSON * LESSONS_PER_UNIT).forEach { chunk ->
            val title = chunk.take(3).joinToString(", ") { it.shortGloss }
            drafts += title to chunk.chunked(WORDS_PER_LESSON).toMutableList<Any>()
        }
        // Formulas without a place go after the second lesson of evenly spaced units.
        val loose = grammar.filter { it !in placed }
        loose.forEachIndexed { i, point ->
            if (drafts.isEmpty()) return@forEachIndexed
            val steps = drafts[(i * drafts.size / loose.size).coerceAtMost(drafts.lastIndex)].second
            val wordLessons = steps.withIndex().filter { it.value is List<*> }.map { it.index }
            val after = wordLessons.getOrElse(1) { wordLessons.last() }
            // Keep earlier formulas of the same unit ahead of later ones.
            var at = after + 1
            while (at < steps.size && steps[at] is GrammarPoint) at++
            steps.add(at, point)
        }

        return drafts.mapIndexed { u, (title, steps) ->
            val lessons = steps.mapIndexed { i, step ->
                @Suppress("UNCHECKED_CAST")
                if (step is GrammarPoint) Lesson(level, u + 1, i + 1, emptyList(), isReview = false, grammar = step)
                else Lesson(level, u + 1, i + 1, step as List<Word>, isReview = false)
            }
            val unitWords = lessons.flatMap { it.words }
            CourseUnit(level, u + 1, title, lessons + Lesson(level, u + 1, lessons.size + 1, unitWords, isReview = true))
        }
    }
}
