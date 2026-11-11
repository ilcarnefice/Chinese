package com.hskpath.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CurriculumTest {
    private val content = TestData.content
    private val units = Curriculum.units(1, content.wordsOf(1))

    @Test fun everyWordIsTaughtExactlyOnce() {
        for (level in content.levels) {
            val words = content.wordsOf(level)
            val taught = Curriculum.units(level, words).flatMap { u -> u.lessons.filter { !it.isReview } }.flatMap { it.words }
            assertEquals(words, taught)
        }
    }

    @Test fun unitsEndWithAReview() {
        assertEquals(8, units.size)
        for (u in units) {
            assertTrue(u.lessons.last().isReview)
            assertEquals(u.words, u.lessons.last().words)
            assertTrue(u.lessons.dropLast(1).none { it.isReview })
            assertTrue(u.lessons.dropLast(1).all { it.words.size in 1..Curriculum.WORDS_PER_LESSON })
        }
        assertEquals(units.flatMap { it.lessons }.size, units.flatMap { it.lessons }.map { it.id }.distinct().size)
    }

    @Test fun lessonTeachesEachWordThenPractisesIt() {
        val lesson = units[0].lessons[0]
        val exercises = ExerciseBuilder(content, Random(7)).lesson(lesson)
        assertEquals(lesson.words, exercises.filterIsInstance<Teach>().map { it.word })
        // Each word is introduced before any question about it.
        for (w in lesson.words) {
            val taughtAt = exercises.indexOfFirst { it is Teach && it.word == w }
            val askedAt = exercises.indexOfFirst { it !is Teach && w.id in it.wordIds }
            assertTrue(taughtAt in 0 until askedAt)
        }
        for (w in lesson.words) assertTrue(exercises.count { it is Choice && it.word == w } >= 3)
        assertEquals(1, exercises.count { it is Match })
        assertTrue(exercises.any { it is Arrange })
    }

    @Test fun choicesHaveOneCorrectAnswerAmongDistinctOptions() {
        val builder = ExerciseBuilder(content, Random(3))
        for (level in content.levels) for (w in content.wordsOf(level).take(60)) for (kind in ChoiceKind.entries) {
            val c = builder.choice(kind, w)
            assertEquals("${w.hanzi} $kind ${c.options}", ExerciseBuilder.OPTIONS, c.options.size)
            assertEquals(c.options.size, c.options.distinct().size)
            val expected = when (kind) {
                ChoiceKind.MEANING -> w.gloss
                ChoiceKind.PINYIN -> w.pinyin
                else -> w.hanzi
            }
            assertEquals(expected, c.answer)
            assertEquals(kind == ChoiceKind.LISTEN, c.prompt.isEmpty())
            if (kind == ChoiceKind.LISTEN || kind == ChoiceKind.MEANING) assertEquals(w.hanzi, c.speak)
        }
    }

    @Test fun matchUsesDistinctPairs() {
        val m = ExerciseBuilder(content, Random(5)).match(content.wordsOf(1).take(5))
        assertEquals(5, m.words.size)
        assertEquals(listOf(0, 1, 2, 3, 4), m.meaningOrder.sorted())
    }

    @Test fun arrangeAcceptsOnlyTheRightOrder() {
        val sentence = content.sentences.first { it.text == "我是学生。" }
        val a = ExerciseBuilder(content, Random(2)).arrange(sentence, emptyList())
        assertTrue(a.bank.containsAll(listOf("我", "是", "学生")))
        assertEquals(3 + ExerciseBuilder.EXTRA_TILES, a.bank.size)
        assertTrue(a.isCorrect(listOf("我", "是", "学生")))
        assertFalse(a.isCorrect(listOf("是", "我", "学生")))
        assertFalse(a.isCorrect(listOf("我", "是")))
    }

    @Test fun levelTestCoversEveryLevel() {
        for (level in content.levels) {
            val test = ExerciseBuilder(content, Random(level)).levelTest(level)
            assertTrue(test.none { it is Teach })
            assertEquals(ExerciseBuilder.TEST_SIZE, test.count { it is Choice })
            assertEquals(ChoiceKind.entries.toSet(), test.filterIsInstance<Choice>().map { it.kind }.toSet())
            assertTrue("HSK $level has sentence questions", test.any { it is Arrange })
            assertEquals(30, test.size)
        }
    }

    @Test fun grammarPracticeBuildsItsExampleSentences() {
        val point = content.grammarOf(1).first()
        val exercises = ExerciseBuilder(content, Random(1)).grammarPractice(point)
        assertEquals(ExerciseBuilder.GRAMMAR_DRILL_SIZE, exercises.size)
        val sentences = exercises.map { (it as Arrange).sentence }
        assertTrue(sentences.containsAll(point.examples))
        assertTrue(sentences.all { it in content.grammarExamples(point, 30) })
    }

    @Test fun clozeBlanksTheWordInARealSentence() {
        val builder = ExerciseBuilder(content, Random(6))
        var made = 0
        for (w in content.wordsOf(1) + content.wordsOf(3).take(100)) {
            val c = builder.cloze(w) ?: continue
            made++
            assertEquals(w.hanzi, c.sentence.tokens[c.blank])
            assertEquals(w.hanzi, c.options[c.correctIndex])
            assertEquals(ExerciseBuilder.OPTIONS, c.options.distinct().size)
            // No other option also appears in the sentence, so only one answer fits by sight.
            assertTrue(c.options.filter { it != w.hanzi }.none { it in c.sentence.words })
            assertTrue(c.sentence.level <= w.level)
        }
        assertTrue("only $made cloze questions", made >= 200)
    }

    @Test fun sentenceDrillMixesTranslatingListeningAndBuilding() {
        for (level in content.levels) {
            val drill = ExerciseBuilder(content, Random(level)).sentenceDrill(level)
            assertEquals(ExerciseBuilder.DRILL_SIZE, drill.size)
            assertEquals(4, drill.count { it is Arrange })
            val choices = drill.filterIsInstance<SentenceChoice>()
            assertEquals(setOf(SentenceKind.TRANSLATE, SentenceKind.LISTEN), choices.map { it.kind }.toSet())
            for (c in choices) {
                assertEquals(ExerciseBuilder.SENTENCE_OPTIONS, c.options.distinct().size)
                val expected = if (c.kind == SentenceKind.TRANSLATE) c.sentence.english else c.sentence.text
                assertEquals(expected, c.options[c.correctIndex])
                assertEquals(level, c.sentence.level)
            }
        }
    }

    @Test fun practiceAsksAboutEveryWord() {
        val words = content.wordsOf(2).take(8)
        val exercises = ExerciseBuilder(content, Random(9)).practice(words)
        assertTrue(words.all { w -> exercises.any { w.id in it.wordIds } })
        assertTrue(exercises.none { it is Teach })
    }
}
