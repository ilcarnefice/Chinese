package com.hskpath.core

import kotlin.random.Random

sealed interface Exercise {
    /** Words practised by this exercise; used to update spaced repetition. */
    val wordIds: List<String>
}

/** Introduces a new word before it is practised. Not graded. */
data class Teach(val word: Word, val examples: List<Sentence>, val breakdown: List<Word>) : Exercise {
    override val wordIds get() = emptyList<String>()
}

enum class ChoiceKind { MEANING, HANZI, PINYIN, LISTEN }

/** Multiple choice with one right answer. */
data class Choice(
    val kind: ChoiceKind,
    val instruction: String,
    /** Text shown large; empty for listening questions. */
    val prompt: String,
    /** Chinese text to read aloud, if any. */
    val speak: String?,
    val options: List<String>,
    val correctIndex: Int,
    val word: Word,
) : Exercise {
    override val wordIds get() = listOf(word.id)
    val answer: String get() = options[correctIndex]
}

/** Match each word with its meaning. */
data class Match(val words: List<Word>, val meaningOrder: List<Int>) : Exercise {
    override val wordIds get() = words.map { it.id }
}

/** Build the Chinese sentence from a word bank. */
data class Arrange(val sentence: Sentence, val bank: List<String>, val focus: List<Word>) : Exercise {
    override val wordIds get() = focus.map { it.id }
    fun isCorrect(chosen: List<String>) = chosen.joinToString("") == sentence.words.joinToString("")
}

/** Builds the exercise lists for lessons, practice sessions and level tests. */
class ExerciseBuilder(private val content: Content, private val random: Random = Random.Default) {

    /** New words are taught one by one, then mixed practice follows. */
    fun lesson(lesson: Lesson): List<Exercise> {
        if (lesson.isReview) return practice(lesson.words.shuffled(random).take(REVIEW_SIZE))
        val out = mutableListOf<Exercise>()
        for (w in lesson.words) {
            out += Teach(w, content.examplesFor(w), content.characterBreakdown(w))
            out += choice(ChoiceKind.MEANING, w)
        }
        val mixed = mutableListOf<Exercise>()
        for (w in lesson.words) {
            mixed += choice(ChoiceKind.HANZI, w)
            mixed += choice(if (random.nextBoolean()) ChoiceKind.LISTEN else ChoiceKind.PINYIN, w)
        }
        mixed.shuffle(random)
        if (lesson.words.size >= 3) mixed.add(mixed.size / 2, match(lesson.words))
        out += mixed
        out += arrangeFor(lesson.words, 2)
        return out
    }

    /** Mixed practice of words that were already introduced. */
    fun practice(words: List<Word>): List<Exercise> {
        val out = mutableListOf<Exercise>()
        val kinds = ChoiceKind.entries
        words.forEachIndexed { i, w -> out += choice(kinds[(i + random.nextInt(kinds.size)) % kinds.size], w) }
        out.shuffle(random)
        words.chunked(5).filter { it.size >= 3 }.take(2).forEach { out.add(random.nextInt(out.size + 1), match(it)) }
        out += arrangeFor(words, 2)
        return out
    }

    /** A graded test over a whole level: listening, reading and sentence building. */
    fun levelTest(level: Int, size: Int = TEST_SIZE): List<Exercise> {
        val words = content.wordsOf(level).shuffled(random).take(size)
        val kinds = listOf(ChoiceKind.LISTEN, ChoiceKind.MEANING, ChoiceKind.HANZI, ChoiceKind.PINYIN)
        val out = words.mapIndexed<Word, Exercise> { i, w -> choice(kinds[i * kinds.size / words.size.coerceAtLeast(1)], w) }
            .toMutableList()
        val sentences = content.sentences.filter { it.level == level && it.words.size in 3..9 }
            .shuffled(random).take(size / 5)
        out += sentences.map { arrange(it, emptyList()) }
        return out
    }

    /** Sentence-building practice for one grammar structure. */
    fun grammarPractice(point: GrammarPoint): List<Exercise> =
        point.examples.filter { it.words.size >= 2 }.shuffled(random).map { arrange(it, emptyList()) }

    fun choice(kind: ChoiceKind, word: Word): Choice {
        val (prompt, speak, correct, wrong) = when (kind) {
            ChoiceKind.MEANING -> Quad(word.hanzi, word.hanzi, word.gloss, distractors(word) { it.gloss })
            ChoiceKind.HANZI -> Quad(word.gloss, null, word.hanzi, distractors(word) { it.hanzi })
            ChoiceKind.LISTEN -> Quad("", word.hanzi, word.hanzi, distractors(word) { it.hanzi })
            ChoiceKind.PINYIN -> Quad(word.hanzi, null, word.pinyin, pinyinDistractors(word))
        }
        val options = (wrong + correct).shuffled(random)
        val instruction = when (kind) {
            ChoiceKind.MEANING -> "What does this mean?"
            ChoiceKind.HANZI -> "Which one means this?"
            ChoiceKind.LISTEN -> "What do you hear?"
            ChoiceKind.PINYIN -> "How is this pronounced?"
        }
        return Choice(kind, instruction, prompt, speak, options, options.indexOf(correct), word)
    }

    fun match(words: List<Word>): Match {
        val chosen = words.distinctBy { it.gloss }.distinctBy { it.hanzi }.shuffled(random).take(5)
        return Match(chosen, chosen.indices.shuffled(random))
    }

    fun arrange(sentence: Sentence, focus: List<Word>): Arrange {
        val words = sentence.words
        val extra = content.wordsOf(sentence.level.coerceIn(1, Content.LEVEL_COUNT))
            .map { it.hanzi }.filter { it !in words }.shuffled(random).take(EXTRA_TILES)
        return Arrange(sentence, (words + extra).shuffled(random), focus)
    }

    private fun arrangeFor(words: List<Word>, max: Int): List<Arrange> {
        val hanzi = words.associateBy { it.hanzi }
        val level = words.maxOfOrNull { it.level } ?: return emptyList()
        return content.sentences.asSequence()
            .filter { it.level <= level && it.words.size in 3..9 && it.words.any { t -> t in hanzi } }
            .toList().shuffled(random).take(max)
            .map { s -> arrange(s, s.words.mapNotNull { hanzi[it] }.distinct()) }
    }

    private fun distractors(word: Word, text: (Word) -> String): List<String> {
        val own = text(word)
        val pool = content.wordsOf(word.level).filter { it.hanzi != word.hanzi && it.gloss != word.gloss }
        // Prefer words of the same length so the answer does not stand out.
        val (same, other) = pool.shuffled(random).partition { it.hanzi.length == word.hanzi.length }
        return (same + other).map(text).filter { it != own }.distinct().take(OPTIONS - 1)
    }

    private fun pinyinDistractors(word: Word): List<String> {
        val tones = Pinyin.toneVariants(word.pinyinNumbered, 2, random)
        val others = distractors(word) { it.pinyin }.filter { it !in tones }
        return (tones + others).take(OPTIONS - 1)
    }

    private data class Quad(val prompt: String, val speak: String?, val correct: String, val wrong: List<String>)

    companion object {
        const val OPTIONS = 4
        const val EXTRA_TILES = 3
        const val REVIEW_SIZE = 12
        const val TEST_SIZE = 20
    }
}
