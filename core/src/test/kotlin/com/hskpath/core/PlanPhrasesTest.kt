package com.hskpath.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PlanPhrasesTest {
    private val content = TestData.content

    @Test fun plansCoverEveryLessonAndFormulaInOrder() {
        for (level in content.levels) {
            val days = TrainingPlan.days(level, content)
            assertEquals(TrainingPlan.lengths.getValue(level), days.size)
            assertEquals((1..days.size).toList(), days.map { it.number })
            val lessons = Curriculum.units(level, content.wordsOf(level)).flatMap { it.lessons }
            assertEquals(lessons, days.flatMap { d -> d.tasks.filterIsInstance<PlanTask.Learn>().map { it.lesson } })
            assertEquals(content.grammarOf(level), days.flatMap { d -> d.tasks.filterIsInstance<PlanTask.Grammar>().map { it.point } })
            assertEquals(content.wordsOf(level).size, days.sumOf { it.newWords })
            // Every study day has something to learn and sentence practice; the last day is the test.
            for (d in days.dropLast(1)) {
                assertTrue("HSK $level day ${d.number}", d.tasks.any { it is PlanTask.Learn })
                assertEquals(1, d.tasks.count { it is PlanTask.Sentences })
                assertTrue(d.tasks.count { it is PlanTask.Grammar } <= 1)
            }
            assertEquals(listOf(PlanTask.Test(level)), days.last().tasks)
            // The load is even: no day has more than twice the lightest day's lessons, plus one.
            val perDay = days.dropLast(1).map { d -> d.tasks.count { it is PlanTask.Learn } }
            assertTrue(perDay.max() <= perDay.min() + 1)
            val keys = days.flatMap { d -> d.tasks.mapNotNull { it.key } }
            assertEquals(keys.size, keys.distinct().size)
        }
        assertEquals(1, TrainingPlan.days(1, content).first().tasks.count { it is PlanTask.Grammar })
    }

    @Test fun planDaysCompleteAsTasksAreDone() {
        val days = TrainingPlan.days(1, content)
        var p = Progress()
        assertEquals(1, TrainingPlan.today(days, p)!!.number)
        val first = days.first()
        for (task in first.tasks) {
            assertEquals(task is PlanTask.Write, TrainingPlan.isDone(task, p))
        }
        val result = SessionResult(1, 1, emptyMap(), 10)
        for (task in first.tasks.filterIsInstance<PlanTask.Learn>()) p = p.afterSession(result, 5, task.lesson.id)
        assertFalse(TrainingPlan.isDone(first, p))
        for (task in first.tasks) task.key?.let { p = p.withPlanTask(it) }
        assertTrue(TrainingPlan.isDone(first, p))
        assertEquals(2, TrainingPlan.today(days, p)!!.number)
        assertEquals(p, Progress.fromJson(p.toJson()))

        // Finishing everything, including the test, ends the plan.
        for (d in days) for (task in d.tasks) {
            if (task is PlanTask.Learn) p = p.afterSession(result, 5, task.lesson.id)
            task.key?.let { p = p.withPlanTask(it) }
        }
        assertEquals(days.size, TrainingPlan.today(days, p)!!.number)
        p = p.withTestScore(1, 40)
        assertNull(TrainingPlan.today(days, p))
    }

    @Test fun phraseBooksHaveTheirFullContents() {
        assertEquals(listOf("everyday", "travel", "business"), content.phraseBooks.map { it.id })
        assertEquals(listOf(500, 100, 300), content.phraseBooks.map { it.size })
        assertEquals(listOf(20, 10, 12), content.phraseBooks.map { it.topics.size })
        val all = content.phraseBooks.flatMap { b -> b.topics.flatMap { it.phrases } }
        for (s in all) {
            assertTrue(s.text, Sentence.isPunctuation(s.tokens.last()) && s.words.isNotEmpty())
            assertTrue(s.text, s.pinyin.isNotBlank() && s.english.isNotBlank())
            assertTrue(s.pinyin, s.pinyin.none { it.code > 0x2E80 })
        }
        for (b in content.phraseBooks) {
            assertEquals(b.topics.size, b.topics.map { it.title }.distinct().size)
            val texts = b.topics.flatMap { t -> t.phrases.map { it.text } }
            assertEquals("duplicates in ${b.id}", texts.size, texts.distinct().size)
        }
        assertEquals("travel", content.phraseBook("travel")!!.id)
        assertNull(content.phraseBook("nope"))
    }

    @Test fun phraseDrillUsesOnlyTheTopicsPhrases() {
        for (book in content.phraseBooks) for (topic in book.topics) {
            val drill = ExerciseBuilder(content, Random(3)).phraseDrill(topic)
            assertEquals(minOf(ExerciseBuilder.DRILL_SIZE, topic.phrases.size), drill.size)
            for (e in drill) {
                val sentence = when (e) {
                    is Arrange -> e.sentence
                    is SentenceChoice -> {
                        assertEquals(ExerciseBuilder.SENTENCE_OPTIONS, e.options.distinct().size)
                        e.sentence
                    }
                    else -> error("unexpected exercise $e")
                }
                assertTrue(sentence in topic.phrases)
            }
            assertTrue(drill.any { it is SentenceChoice && it.kind == SentenceKind.LISTEN })
        }
    }

    @Test fun everyLevelHasDialogues() {
        assertEquals(17, content.dialogues.size)
        for (level in content.levels) assertTrue("HSK $level", content.dialoguesOf(level).isNotEmpty())
    }
}
