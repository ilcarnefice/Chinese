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

/** Multiple choice about one word. */
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

/** A sentence with one word missing; pick the word that fits. */
data class Cloze(
    val sentence: Sentence,
    /** Index of the missing word in [Sentence.tokens]. */
    val blank: Int,
    val options: List<String>,
    val correctIndex: Int,
    val word: Word,
) : Exercise {
    override val wordIds get() = listOf(word.id)
}

enum class SentenceKind { TRANSLATE, LISTEN }

/** Read or hear a whole sentence and pick its meaning (or what was said). */
data class SentenceChoice(
    val kind: SentenceKind,
    val sentence: Sentence,
    val options: List<String>,
    val correctIndex: Int,
) : Exercise {
    override val wordIds get() = emptyList<String>()
}

/** Builds the exercise lists for lessons, practice sessions, drills and level tests. */
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
        mixed += clozeFor(lesson.words, 2)
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
        out += clozeFor(words, 3)
        out.shuffle(random)
        words.chunked(5).filter { it.size >= 3 }.take(2).forEach { out.add(random.nextInt(out.size + 1), match(it)) }
        out += arrangeFor(words, 2)
        return out
    }

    /** Whole-sentence practice for a level: translate, listen and build. */
    fun sentenceDrill(level: Int, size: Int = DRILL_SIZE): List<Exercise> {
        val pool = content.sentencesOf(level).filter { it.words.size in 3..10 }
        return pool.shuffled(random).take(size).mapIndexed { i, s ->
            when (i % 3) {
                0 -> sentenceChoice(SentenceKind.TRANSLATE, s, pool)
                1 -> arrange(s, emptyList())
                else -> sentenceChoice(SentenceKind.LISTEN, s, pool)
            }
        }
    }

    /** Practice for one phrase-book topic: understand, hear and rebuild its phrases. */
    fun phraseDrill(topic: PhraseTopic, size: Int = DRILL_SIZE): List<Exercise> =
        topic.phrases.shuffled(random).take(size).mapIndexed { i, s ->
            when {
                i % 3 == 1 && s.words.size in 2..9 -> arrange(s, emptyList())
                i % 3 == 2 -> sentenceChoice(SentenceKind.LISTEN, s, topic.phrases)
                else -> sentenceChoice(SentenceKind.TRANSLATE, s, topic.phrases)
            }
        }

    /** A graded test over a whole level: listening, reading and sentences. */
    fun levelTest(level: Int, size: Int = TEST_SIZE): List<Exercise> {
        val words = content.wordsOf(level).shuffled(random).take(size)
        val kinds = listOf(ChoiceKind.LISTEN, ChoiceKind.MEANING, ChoiceKind.HANZI, ChoiceKind.PINYIN)
        val out = words.mapIndexed<Word, Exercise> { i, w -> choice(kinds[i * kinds.size / words.size.coerceAtLeast(1)], w) }
            .toMutableList()
        out += sentenceDrill(level, size / 2)
        return out
    }

    /** Sentence practice for one grammar structure, using its examples from the corpus too. */
    fun grammarPractice(point: GrammarPoint): List<Exercise> {
        val examples = content.grammarExamples(point, 30).filter { it.words.size in 2..12 }
        val own = examples.filter { it.curated }
        val more = examples.filter { !it.curated }.shuffled(random).take(GRAMMAR_DRILL_SIZE - own.size)
        return (own + more).shuffled(random).map { arrange(it, emptyList()) }
    }

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

    /** Blanks out [word] in one of its example sentences, or returns null if it has none. */
    fun cloze(word: Word): Cloze? {
        val sentence = content.sentencesWith(word.hanzi)
            .filter { it.level <= word.level && it.words.size in 3..10 }
            .let { list -> list.filter { it.curated }.ifEmpty { list.take(40) } }
            .randomOrNull(random) ?: return null
        val wrong = content.wordsOf(word.level).asSequence()
            .filter { it.hanzi != word.hanzi && it.hanzi !in sentence.words }
            .toList().shuffled(random)
            .sortedBy { if (it.hanzi.length == word.hanzi.length) 0 else 1 }
            .map { it.hanzi }.distinct().take(OPTIONS - 1)
        val options = (wrong + word.hanzi).shuffled(random)
        return Cloze(sentence, sentence.tokens.indexOf(word.hanzi), options, options.indexOf(word.hanzi), word)
    }

    fun sentenceChoice(kind: SentenceKind, sentence: Sentence, pool: List<Sentence>): SentenceChoice {
        val text: (Sentence) -> String = if (kind == SentenceKind.TRANSLATE) ({ it.english }) else ({ it.text })
        val wrong = LinkedHashSet<String>()
        var attempts = 0
        while (wrong.size < SENTENCE_OPTIONS - 1 && attempts++ < 200 && pool.isNotEmpty()) {
            val other = text(pool[random.nextInt(pool.size)])
            if (other != text(sentence)) wrong += other
        }
        val options = (wrong + text(sentence)).shuffled(random)
        return SentenceChoice(kind, sentence, options, options.indexOf(text(sentence)))
    }

    private fun clozeFor(words: List<Word>, max: Int): List<Cloze> =
        words.shuffled(random).asSequence().mapNotNull { cloze(it) }.take(max).toList()

    private fun arrangeFor(words: List<Word>, max: Int): List<Arrange> {
        val hanzi = words.associateBy { it.hanzi }
        val level = words.maxOfOrNull { it.level } ?: return emptyList()
        val candidates = words.flatMap { w ->
            content.sentencesWith(w.hanzi).filter { it.level <= level && it.words.size in 3..9 }
                .let { list -> list.filter { it.curated } + list.filter { !it.curated }.take(15) }
        }.distinct()
        return candidates.shuffled(random).take(max)
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
        const val SENTENCE_OPTIONS = 3
        const val EXTRA_TILES = 3
        const val REVIEW_SIZE = 12
        const val TEST_SIZE = 20
        const val DRILL_SIZE = 12
        const val GRAMMAR_DRILL_SIZE = 6
    }
}
