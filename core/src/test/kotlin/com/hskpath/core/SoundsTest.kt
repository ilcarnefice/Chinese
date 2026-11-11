package com.hskpath.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundsTest {
    private val content = TestData.content

    @Test fun everySoundHasAMouthGuide() {
        assertEquals(21, Sounds.initials.size)
        assertEquals(35, Sounds.finals.size)
        assertEquals(Sounds.all.size, Sounds.all.map { it.letters to it.isInitial }.distinct().size)
        assertEquals(25, Sounds.shapes.size)
        for (s in Sounds.all) {
            assertTrue(s.letters, s.how.length > 30 && s.like.isNotBlank() && s.example.length == 1)
            assertTrue(s.letters, s.steps.isNotEmpty() && s.steps.all { it.shape in Sounds.shapes })
            assertTrue(s.letters, s.steps.all { it.caption.isNotBlank() })
            assertNotNull("example ${s.example} is a known character", content.lookup(s.example) ?: s.example.takeIf { it in "啊哦" })
        }
        // Every drawable shape is used by some sound, apart from the neutral start.
        assertEquals(Sounds.shapes - "rest", Sounds.all.flatMap { s -> s.steps.map { it.shape } }.toSet())
    }

    @Test fun exampleCharactersAreSaidWithTheSoundTheyIllustrate() {
        for (s in Sounds.all) {
            val reading = content.lookup(s.example)?.pinyinNumbered ?: continue
            val (initial, final) = Sounds.split(reading.substringBefore(' '))
            if (s.isInitial) assertEquals("${s.example} for ${s.letters}", s.letters, initial)
            else assertEquals("${s.example} for ${s.letters}", s.letters, final)
        }
    }

    @Test fun stepsDescribeTheBreathCorrectly() {
        fun steps(letters: String) = Sounds.find(letters, true)!!.steps
        // Stops start blocked and unvoiced; the aspirated ones add a burst before the vowel.
        for (letters in listOf("b", "d", "g")) {
            assertEquals(listOf(Air.NONE, Air.ORAL), steps(letters).map { it.air })
            assertEquals(listOf(false, true), steps(letters).map { it.voiced })
        }
        for (letters in listOf("p", "t", "k", "q", "ch", "c")) {
            assertEquals(letters, 1, steps(letters).count { it.air == Air.PUFF })
            assertEquals(letters, Air.NONE, steps(letters).first().air)
            assertTrue(letters, steps(letters).first { it.air == Air.PUFF }.caption.contains("puff"))
        }
        for (letters in listOf("b", "d", "g", "j", "zh", "z", "f", "h", "x", "sh", "s", "m", "n", "l", "r")) {
            assertTrue(letters, steps(letters).none { it.air == Air.PUFF })
        }
        // Nasals send the air through the nose; m, n, l and r are voiced from the start.
        assertEquals(Air.NASAL, steps("m").first().air)
        assertEquals(Air.NASAL, steps("n").first().air)
        for (letters in listOf("m", "n", "l", "r")) assertTrue(letters, steps(letters).first().voiced)
        for (letters in listOf("f", "h", "x", "sh", "s")) {
            assertEquals(letters, Air.ORAL, steps(letters).first().air)
            assertTrue(letters, !steps(letters).first().voiced)
        }
        // Every sound ends voiced, and finals are voiced throughout with nasal endings through the nose.
        for (s in Sounds.all) assertTrue(s.letters, s.steps.last().voiced)
        for (s in Sounds.finals) {
            assertTrue(s.letters, s.steps.all { it.voiced })
            for (step in s.steps) {
                assertEquals(s.letters, step.shape in setOf("ridgeNasal", "velarNasal"), step.air == Air.NASAL)
            }
        }
        assertEquals("ridgeNasal", Sounds.find("an", false)!!.steps.last().shape)
        assertEquals("velarNasal", Sounds.find("ang", false)!!.steps.last().shape)
        assertEquals(listOf("i", "a", "u"), Sounds.find("iao", false)!!.steps.map { it.shape })
        assertEquals(listOf("yu"), Sounds.find("ü", false)!!.steps.map { it.shape })
    }

    @Test fun aspiratedPairsMentionThePuffOfAir() {
        for (letters in listOf("p", "t", "k", "q", "ch", "c")) assertTrue(Sounds.find(letters, true)!!.how.contains("flutter"))
        for (letters in listOf("b", "d", "g", "j", "zh", "z")) assertTrue(Sounds.find(letters, true)!!.how.contains("stay still"))
    }

    @Test fun splitsSyllablesIntoInitialAndFinal() {
        assertEquals("zh" to "ong", Sounds.split("zhong1"))
        assertEquals("z" to "i", Sounds.split("zi4"))
        assertEquals("" to "i", Sounds.split("yi1"))
        assertEquals("" to "ü", Sounds.split("yu2"))
        assertEquals("" to "üan", Sounds.split("yuan3"))
        assertEquals("" to "iu", Sounds.split("you3"))
        assertEquals("" to "iao", Sounds.split("yao4"))
        assertEquals("" to "u", Sounds.split("wu3"))
        assertEquals("" to "uo", Sounds.split("wo3"))
        assertEquals("" to "ui", Sounds.split("wei4"))
        assertEquals("q" to "ü", Sounds.split("qu4"))
        assertEquals("x" to "üe", Sounds.split("xue2"))
        assertEquals("n" to "ü", Sounds.split("nv3"))
        assertEquals("l" to "u", Sounds.split("lu4"))
        assertEquals("" to "er", Sounds.split("er4"))
        assertEquals("" to "a", Sounds.split("a5"))
    }

    @Test fun everySyllableInTheWordListsSplitsIntoKnownSounds() {
        val initials = Sounds.initials.map { it.letters }.toSet() + ""
        val finals = Sounds.finals.map { it.letters }.toSet() + setOf("ueng", "r", "io")
        for (level in content.levels) for (w in content.wordsOf(level)) for (syllable in w.pinyinNumbered.split(' ')) {
            val (initial, final) = Sounds.split(syllable)
            assertTrue("$syllable in ${w.hanzi}", initial in initials && final in finals)
        }
    }

    @Test fun everySoundHasExampleWords() {
        for (s in Sounds.all) {
            val examples = Sounds.examples(content, s)
            assertTrue("no example words for ${s.letters}", examples.isNotEmpty())
            for (w in examples) {
                val (initial, final) = Sounds.split(w.pinyinNumbered.substringBefore(' '))
                assertEquals(s.letters, if (s.isInitial) initial else final)
            }
        }
    }

    @Test fun shortGlossDropsBracketedNotes() {
        assertEquals("of", content.lookup("的")!!.shortGloss)
        assertEquals("I", content.lookup("我")!!.shortGloss)
        assertTrue(content.wordsOf(1).all { it.shortGloss.isNotBlank() })
    }
}
