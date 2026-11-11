package com.hskpath.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PinyinTest {
    @Test fun marksTonesOnTheRightVowel() {
        assertEquals("nǐ", Pinyin.markSyllable("ni3"))
        assertEquals("hǎo", Pinyin.markSyllable("hao3"))
        assertEquals("xué", Pinyin.markSyllable("xue2"))
        assertEquals("guì", Pinyin.markSyllable("gui4"))
        assertEquals("liù", Pinyin.markSyllable("liu4"))
        assertEquals("gǒu", Pinyin.markSyllable("gou3"))
        assertEquals("duō", Pinyin.markSyllable("duo1"))
        assertEquals("nǚ", Pinyin.markSyllable("nv3"))
        assertEquals("lǜ", Pinyin.markSyllable("lu:4"))
        assertEquals("ma", Pinyin.markSyllable("ma5"))
    }

    @Test fun joinsSyllablesIntoWords() {
        assertEquals("wǒmen", Pinyin.toMarked("wo3 men5"))
        assertEquals("nǚ'ér", Pinyin.toMarked("nv3 er2"))
        assertEquals("nǎr", Pinyin.toMarked("na3 r5"))
        assertEquals("yīdiǎnr", Pinyin.toMarked("yi1 dian3 r5"))
        assertEquals("kě'ài", Pinyin.toMarked("ke3 ai4"))
        assertEquals("chūzūchē", Pinyin.toMarked("chu1 zu1 che1"))
    }

    @Test fun readsTones() {
        assertEquals(3, Pinyin.tone("hao3"))
        assertEquals(5, Pinyin.tone("ma5"))
        assertEquals(5, Pinyin.tone("ma"))
    }

    @Test fun searchKeyIgnoresTonesAndSpaces() {
        assertEquals("nihao", Pinyin.searchKey("nǐ hǎo"))
        assertEquals("nihao", Pinyin.searchKey("ni3 hao3"))
        assertEquals("nver", Pinyin.searchKey("nǚ'ér"))
        assertEquals("nver", Pinyin.searchKey("nv3 er2"))
    }

    @Test fun toneVariantsDifferFromTheCorrectReading() {
        val variants = Pinyin.toneVariants("xue2 xi2", 3, Random(1))
        assertEquals(3, variants.size)
        assertEquals(3, variants.distinct().size)
        assertFalse("xuéxí" in variants)
        assertTrue(variants.all { Pinyin.searchKey(it) == "xuexi" })
    }
}
