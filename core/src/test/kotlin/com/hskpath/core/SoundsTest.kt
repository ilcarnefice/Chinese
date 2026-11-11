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
        for (s in Sounds.all) {
            assertTrue(s.letters, s.how.length > 30 && s.like.isNotBlank() && s.example.length == 1)
            assertTrue(s.letters, s.steps.isNotEmpty() && s.steps.all { it in Sounds.shapes })
            assertNotNull("example ${s.example} is a known character", content.lookup(s.example) ?: s.example.takeIf { it in "啊哦" })
        }
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
