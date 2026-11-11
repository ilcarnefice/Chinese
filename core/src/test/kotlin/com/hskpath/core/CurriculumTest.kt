package com.hskpath.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CurriculumTest {
    private val content = TestData.content
    private val units = Curriculum.units(1, content)

    @Test fun everyWordIsTaughtExactlyOnce() {
        for (level in content.levels) {
            val words = content.wordsOf(level)
            val taught = Curriculum.units(level, content).flatMap { u -> u.lessons.filter { !it.isReview } }.flatMap { it.words }
            assertEquals("HSK $level", words.size, taught.size)
            assertEquals("HSK $level", words.toSet(), taught.toSet())
        }
    }

    @Test fun unitsAreThemedAndEndWithAReview() {
        for (level in content.levels) {
            val all = Curriculum.units(level, content)
            assertEquals((1..all.size).toList(), all.map { it.number })
            for (u in all) {
                assertTrue("HSK $level unit ${u.number} has no title", u.title.isNotBlank())
                assertTrue(u.lessons.last().isReview)
                assertEquals(u.words, u.lessons.last().words)
                val steps = u.lessons.dropLast(1)
                assertTrue(steps.none { it.isReview })
                // A unit opens with vocabulary, never with grammar.
                assertFalse("HSK $level unit ${u.number}", steps.first().isGrammar)
                for (l in steps) {
                    if (l.isGrammar) assertTrue(l.words.isEmpty())
                    else assertTrue("HSK $level ${l.id}: ${l.words.size} words", l.words.size in 1..6)
                }
                assertTrue("HSK $level unit ${u.number}", u.words.size in 3..24)
            }
            val ids = all.flatMap { it.lessons }.map { it.id }
            assertEquals(ids.size, ids.distinct().size)
        }
    }

    @Test fun everyGrammarFormulaIsALessonOnThePath() {
        for (level in content.levels) {
            val all = Curriculum.units(level, content)
            val taught = all.flatMap { it.grammar }
            assertEquals("HSK $level", content.grammarOf(level).size, taught.size)
            assertEquals("HSK $level", content.grammarOf(level).toSet(), taught.toSet())
            assertTrue("HSK $level", all.all { it.grammar.size <= 4 })
        }
        // Beginners meet grammar from the first units on, mixed in with vocabulary.
        assertTrue(units.take(2).any { it.grammar.isNotEmpty() })
        val kinds = units.flatMap { it.lessons }.filter { !it.isReview }.map { it.isGrammar }
        assertTrue("grammar lessons are bunched together", kinds.windowed(4).none { w -> w.all { it } })
    }

    @Test fun grammarLessonShowsTheFormulaThenPractisesIt() {
        for (level in content.levels) {
            val lesson = Curriculum.units(level, content).flatMap { it.lessons }.first { it.isGrammar }
            val point = lesson.grammar!!
            val exercises = ExerciseBuilder(content, Random(level)).lesson(lesson)
            assertEquals(point, (exercises.first() as TeachGrammar).point)
            assertEquals(1, exercises.count { it is TeachGrammar })
            assertTrue((exercises.first() as TeachGrammar).examples.isNotEmpty())
            val practice = exercises.drop(1)
            assertTrue("HSK $level ${point.title}: ${practice.size} exercises", practice.size >= point.examples.size)
            assertTrue(practice.any { it is Arrange })
            val allowed = content.grammarExamples(point, 30)
            for (e in practice) when (e) {
                is Arrange -> assertTrue(e.sentence in allowed)
                is SentenceChoice -> assertTrue(e.sentence in allowed)
                is Cloze -> {
                    assertNull(e.word)
                    assertTrue(e.sentence in allowed)
                    assertEquals(e.sentence.tokens[e.blank], e.answer)
                    assertTrue(point.isKeyword(e.answer))
                    assertEquals(ExerciseBuilder.OPTIONS, e.options.distinct().size)
                    assertTrue(e.options.filter { it != e.answer }.none { point.isKeyword(it) || it in e.sentence.words })
                }
                else -> error("unexpected exercise in a grammar lesson: $e")
            }
            // The formula's own examples are always built at least once.
            for (s in point.examples.filter { it.words.size in 2..12 }) {
                assertTrue(practice.any { it is Arrange && it.sentence == s } || practice.any { it is Cloze && it.sentence == s } ||
                    practice.any { it is SentenceChoice && it.sentence == s })
            }
            // A grammar lesson is finished like any other and teaches no vocabulary.
            val session = Session(exercises)
            while (!session.isFinished) session.submit(true)
            assertEquals(100, session.result().percent)
            assertTrue(session.result().wordResults.isEmpty())
        }
    }

    @Test fun outlinesAreParsed() {
        val units = Content.parseOutline("""
            # a comment
            ## Saying hello
            你 好 我 是 不
            > Saying no: 不
            他 她 我们
            ## Numbers
            一 二 三
        """.trimIndent())
        assertEquals(listOf("Saying hello", "Numbers"), units.map { it.title })
        assertEquals(listOf(OutlineItem.Words(listOf("你", "好", "我", "是", "不")), OutlineItem.Grammar("Saying no: 不"),
            OutlineItem.Words(listOf("他", "她", "我们"))), units[0].items)
        assertEquals(1, units[1].items.size)
    }

    @Test fun lessonTeachesEachWordThenPractisesIt() {
        val lesson = units[0].lessons.first { !it.isGrammar && !it.isReview }
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
