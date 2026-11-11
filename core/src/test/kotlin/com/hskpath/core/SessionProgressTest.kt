package com.hskpath.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SessionProgressTest {
    private val content = TestData.content
    private val units = Curriculum.units(1, content.wordsOf(1))
    private val lesson = units[0].lessons[0]
    private fun exercises() = ExerciseBuilder(content, Random(11)).lesson(lesson)

    @Test fun perfectSessionEarnsTheBonus() {
        val session = Session(exercises())
        assertEquals(0f, session.progress)
        while (!session.isFinished) session.submit(true)
        val result = session.result()
        assertEquals(1f, session.progress)
        assertEquals(100, result.percent)
        assertEquals(Session.BASE_XP + Session.PERFECT_BONUS, result.xp)
        assertEquals(lesson.words.map { it.id }.toSet(), result.wordResults.keys)
        assertTrue(result.wordResults.values.all { it })
        assertNull(session.current)
    }

    @Test fun mistakesComeBackUntilAnsweredCorrectly() {
        val all = exercises()
        val session = Session(all)
        val missed = all.first { it is Choice } as Choice
        var seenAgain = 0
        var steps = 0
        while (!session.isFinished) {
            val current = session.current!!
            if (current === missed) seenAgain++
            // Miss it twice, then get it right.
            session.submit(!(current === missed && seenAgain <= 2))
            steps++
        }
        assertEquals(3, seenAgain)
        assertEquals(all.size + 2, steps)
        val result = session.result()
        assertEquals(result.graded - 1, result.correctFirstTry)
        assertEquals(Session.BASE_XP, result.xp)
        assertFalse(result.wordResults.getValue(missed.word.id))
        assertEquals(1, result.wordResults.values.count { !it })
    }

    @Test fun testsAskEachQuestionOnce() {
        val test = ExerciseBuilder(content, Random(4)).levelTest(1)
        val session = Session(test, retryMistakes = false)
        var steps = 0
        while (!session.isFinished) { session.submit(steps % 2 == 0); steps++ }
        assertEquals(test.size, steps)
        assertEquals(50, session.result().percent)
    }

    @Test fun spacedRepetitionSpreadsCorrectAnswersOut() {
        var card = Srs.review(null, correct = true, today = 100)
        assertEquals(101L, card.due)
        card = Srs.review(card, true, 101)
        assertEquals(104L, card.due)
        card = Srs.review(card, true, 104)
        assertTrue(card.intervalDays >= 7)
        val longInterval = card.intervalDays
        card = Srs.review(card, true, card.due)
        assertTrue(card.intervalDays > longInterval)
        val failed = Srs.review(card, false, 200)
        assertEquals(201L, failed.due)
        assertEquals(0, failed.reps)
        assertTrue(failed.ease < card.ease)
        var worst = failed
        repeat(20) { worst = Srs.review(worst, false, 200) }
        assertTrue(worst.ease >= 1.3)
    }

    private fun perfectResult(): SessionResult {
        val s = Session(exercises())
        while (!s.isFinished) s.submit(true)
        return s.result()
    }

    @Test fun finishingALessonUnlocksTheNextOne() {
        val second = units[0].lessons[1]
        var p = Progress()
        assertTrue(p.isUnlocked(lesson, units))
        assertFalse(p.isUnlocked(second, units))
        p = p.afterSession(perfectResult(), today = 50, lessonId = lesson.id)
        assertTrue(p.isCompleted(lesson))
        assertTrue(p.isUnlocked(second, units))
        assertFalse(p.isUnlocked(units[0].lessons[2], units))
        assertEquals(5, p.learnedCount(1))
        assertEquals(0, p.learnedCount(2))
        assertEquals(15, p.xp)
        assertEquals(15, p.xpToday(50))
        assertEquals(0, p.xpToday(51))
    }

    @Test fun passingTheLevelTestUnlocksTheWholeLevel() {
        val last = units.last().lessons.last()
        assertFalse(Progress().isUnlocked(last, units))
        assertFalse(Progress().withTestScore(1, 70).isUnlocked(last, units))
        val passed = Progress().withTestScore(1, 85).withTestScore(1, 40)
        assertEquals(85, passed.testBest[1])
        assertTrue(passed.isUnlocked(last, units))
        assertFalse(passed.passedTest(2))
    }

    @Test fun streakGrowsDailyAndResetsAfterAGap() {
        val r = perfectResult()
        var p = Progress().afterSession(r, 10)
        assertEquals(1, p.streak)
        p = p.afterSession(r, 10)
        assertEquals(1, p.streak)
        p = p.afterSession(r, 11)
        assertEquals(2, p.streak)
        assertEquals(2, p.currentStreak(12))
        assertEquals(0, p.currentStreak(13))
        p = p.afterSession(r, 14)
        assertEquals(1, p.streak)
    }

    @Test fun learnedWordsBecomeDueForPractice() {
        val p = Progress().afterSession(perfectResult(), today = 10, lessonId = lesson.id)
        assertTrue(p.dueWordIds(10).isEmpty())
        assertEquals(lesson.words.map { it.id }.toSet(), p.dueWordIds(11).toSet())
        assertTrue(p.dueWordIds(11).all { content.word(it) != null })
    }

    @Test fun progressSurvivesSavingAndLoading() {
        val p = Progress().afterSession(perfectResult(), 10, lesson.id).withTestScore(2, 90)
        assertEquals(p, Progress.fromJson(p.toJson()))
        assertEquals(Progress(), Progress.fromJson(null))
        assertEquals(Progress(), Progress.fromJson("not json"))
        assertEquals(7, Progress.fromJson("""{"xp":7,"someFutureField":true}""").xp)
    }
}
