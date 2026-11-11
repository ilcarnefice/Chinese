package com.hskpath.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StrokesAchievementsTest {
    private val book = StrokeBook(
        File(System.getProperty("assetsDir") ?: "../app/src/main/assets", "strokes.tsv").readText())

    @Test fun everyTaughtCharacterHasStrokeOrder() {
        val content = TestData.content
        val characters = content.levels.flatMap { content.wordsOf(it) }.flatMap { w -> w.hanzi.map { it.toString() } }.toSet()
        assertTrue(characters.size > 2500)
        assertTrue(characters.all { book.has(it) })
        assertNull(book.strokes("A"))
    }

    @Test fun strokeCountsAreRight() {
        assertEquals(1, book.strokes("一")!!.size)
        assertEquals(3, book.strokes("大")!!.size)
        assertEquals(4, book.strokes("中")!!.size)
        assertEquals(7, book.strokes("我")!!.size)
        val all = book.strokes("我")!!.flatMap { it.points }
        assertTrue(all.all { it.first in 0f..Stroke.GRID && it.second in -124f..Stroke.GRID })
    }

    @Test fun horizontalStrokeRunsLeftToRight() {
        val yi = book.strokes("一")!!.single()
        assertTrue(yi.start.first < yi.end.first)
        assertTrue(yi.length > 500f)
    }

    @Test fun tracedStrokeMustFollowTheRightDirection() {
        val yi = book.strokes("一")!!.single()
        val traced = listOf(yi.start.first + 40f to yi.start.second - 30f, 500f to yi.start.second, yi.end.first - 30f to yi.end.second + 20f)
        assertTrue(StrokeBook.matches(traced, yi))
        assertFalse("backwards", StrokeBook.matches(traced.reversed(), yi))
        assertFalse("too short", StrokeBook.matches(traced.take(1) + (traced[0].first + 60f to traced[0].second), yi))
        assertFalse("a tap", StrokeBook.matches(traced.take(1), yi))
        val vertical = listOf(500f to 100f, 500f to 800f)
        assertFalse("wrong stroke", StrokeBook.matches(vertical, yi))
    }

    @Test fun achievementsFollowProgress() {
        val fresh = Achievements.all(Progress(), today = 10)
        assertTrue(fresh.none { it.done })
        assertTrue(fresh.all { it.fraction == 0f })
        val p = Progress(
            completed = mapOf("1-1-1" to 2, "1-1-2" to 1),
            cards = (1..60).associate { "1:w$it" to CardState() },
            xp = 520, streak = 3, lastStudyDay = 10, testBest = mapOf(1 to 90, 2 to 50),
        )
        val done = Achievements.all(p, today = 10).filter { it.done }.map { it.title }
        assertEquals(listOf("First steps", "Word collector", "Warming up", "Charged", "Certified"), done)
        assertEquals(0.2f, Achievements.all(p, 10).first { it.title == "Word hoarder" }.fraction)
        // A lapsed streak no longer counts.
        assertFalse(Achievements.all(p, today = 12).first { it.title == "Warming up" }.done)
        assertEquals(1, Achievements.xpLevel(0))
        assertEquals(6, Achievements.xpLevel(520))
        assertEquals(20, Achievements.xpIntoLevel(520))
    }
}
