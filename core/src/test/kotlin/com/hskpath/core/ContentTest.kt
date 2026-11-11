package com.hskpath.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentTest {
    private val content = TestData.content

    @Test fun everyLevelHasItsFullWordList() {
        assertEquals(listOf(150, 151, 300, 600, 1300, 2500), (1..6).map { content.wordsOf(it).size })
    }

    @Test fun wordsAreWellFormed() {
        for (level in content.levels) for (w in content.wordsOf(level)) {
            assertTrue("no meaning: ${w.hanzi}", w.definitions.isNotEmpty())
            val syllables = w.pinyinNumbered.split(' ')
            assertTrue("bad pinyin for ${w.hanzi}: ${w.pinyinNumbered}",
                syllables.all { Regex("[a-z]+[1-5]").matches(it) })
            assertEquals("one syllable per character: ${w.hanzi} ${w.pinyinNumbered}", w.hanzi.length, syllables.size)
            assertEquals(w.hanzi.length, w.traditional.length)
        }
    }

    @Test fun wordsAreUniqueWithinALevel() {
        for (level in content.levels) {
            val words = content.wordsOf(level)
            assertEquals(words.size, words.map { it.hanzi }.distinct().size)
        }
    }

    @Test fun sentencesHavePinyinForEveryWord() {
        assertTrue(content.sentences.count { it.curated } >= 200)
        assertTrue("corpus size ${content.sentences.size}", content.sentences.size >= 20_000)
        for (level in 1..6) assertTrue(content.sentencesOf(level).size >= 2000)
        for (s in content.sentences + content.grammar.flatMap { it.examples } +
            content.dialogues.flatMap { d -> d.lines.map { it.sentence } }) {
            assertTrue(s.text, s.words.isNotEmpty() && s.english.isNotBlank())
            assertTrue("must end with punctuation: ${s.text}", Sentence.isPunctuation(s.tokens.last()))
            assertTrue("pinyin must not contain characters: ${s.pinyin}", s.pinyin.none { it.code > 0x2E80 })
            // Pinyin is written word by word, so it can never have more words than characters.
            val pinyinWords = s.pinyin.split(' ').size
            assertTrue("${s.text} / ${s.pinyin}", pinyinWords <= s.words.sumOf { it.length })
            // ...and each syllable has at least one vowel, so vowel groups bound the character count.
            val hanzi = s.words.sumOf { it.length }
            val vowelGroups = Regex("[aeiouüāáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜ]+").findAll(s.pinyin.lowercase()).count()
            if (s.curated) assertTrue("${s.text} / ${s.pinyin}",
                vowelGroups in (hanzi - s.words.count { it.endsWith("儿") })..hanzi + 2)
        }
    }

    @Test fun beginnerWordsHaveExampleSentences() {
        val hsk1 = content.wordsOf(1)
        val withExample = hsk1.count { content.examplesFor(it).isNotEmpty() }
        assertTrue("only $withExample of ${hsk1.size} HSK 1 words have an example", withExample >= hsk1.size * 9 / 10)
        val hsk2 = content.wordsOf(2)
        assertTrue(hsk2.count { content.examplesFor(it).isNotEmpty() } >= hsk2.size * 8 / 10)
    }

    @Test fun examplesContainTheWord() {
        val word = content.lookup("学生")!!
        val examples = content.examplesFor(word)
        assertTrue(examples.isNotEmpty())
        assertTrue(examples.all { it.text.contains("学生") })
        // Hand-written sentences come before corpus sentences.
        assertTrue(examples.first().curated)
        assertTrue(examples.zipWithNext().none { (a, b) -> !a.curated && b.curated })
        assertTrue(content.sentencesWith("学生").size > 20)
    }

    @Test fun everyLevelHasGrammar() {
        for (level in 1..6) assertTrue("HSK $level", content.grammarOf(level).isNotEmpty())
        for (g in content.grammar) {
            assertTrue(g.title, g.explanation.isNotBlank() && g.examples.isNotEmpty())
            assertTrue(g.title, g.structure.isNotEmpty() && g.structure.all { it.isNotEmpty() })
        }
    }

    @Test fun grammarStructureSplitsIntoBlocks() {
        val shi = content.grammar.first { it.title.contains("是") }
        assertEquals(listOf(listOf("A", "是", "B")), shi.structure.map { row -> row.map { it.text } })
        assertEquals(listOf(false, true, false), shi.structure[0].map { it.isKeyword })
        assertTrue(shi.isKeyword("是"))
        assertTrue(!shi.isKeyword("学生"))

        val because = content.grammar.first { it.title.startsWith("Because") }
        assertEquals(2, because.structure.size)
        assertEquals(setOf("因为", "所以"), because.keywords)
        // Every structure's keywords show up in at least one of its examples.
        for (g in content.grammar.filter { it.keywords.isNotEmpty() }) {
            assertTrue(g.title, g.examples.any { s -> s.words.any { g.isKeyword(it) } })
        }
    }

    @Test fun corpusSentencesUseOnlyKnownWords() {
        val names = setOf("汤姆", "玛丽")
        for (level in 1..6) for (s in content.sentencesOf(level).filter { !it.curated }) {
            assertTrue(s.text, s.words.size in 2..14)
            val known = s.words.count { content.lookup(it) != null || it in names }
            assertTrue("unknown words in ${s.tokens}", known >= 1)
        }
    }

    @Test fun thereAreAHundredGrammarStructuresWithCorpusExamples() {
        assertEquals(100, content.grammar.size)
        assertEquals(100, content.grammar.map { it.title }.distinct().size)
        val because = content.grammar.first { it.title.startsWith("Because") }
        val examples = content.grammarExamples(because, 10)
        assertTrue(examples.size >= 5)
        assertEquals(because.examples, examples.take(because.examples.size))
        assertTrue(examples.all { it.text.contains("因为") && it.text.contains("所以") })
        // Each structure's own search pattern finds its hand-written examples.
        for (g in content.grammar) g.match?.let { regex ->
            assertTrue(g.title, g.examples.any { regex.containsMatchIn(it.text) })
        }
        val total = content.grammar.sumOf { content.grammarExamples(it, 50).size }
        assertTrue("only $total grammar sentences", total >= 1000)
    }

    @Test fun wordMapCoversMoreThanTenThousandWords() {
        assertTrue(content.mapWordCount >= 10_000)
        assertEquals(6000, content.extraWords.size)
        assertTrue(content.extraWords.none { content.word("1:" + it.hanzi) != null })
        assertTrue(content.wordWeb("学", 999).any { it.level == Content.EXTRA_LEVEL })
        assertEquals("Beyond HSK", content.extraWords.first().levelLabel)
    }

    @Test fun pinyinIsFoundForAnyChineseText() {
        assertEquals("le", content.pinyinFor("了"))
        assertEquals("yīnwèi", content.pinyinFor("因为"))
        assertEquals("shì de", content.pinyinFor("是 … 的"))
        assertEquals("yīnwèi suǒyǐ", content.pinyinFor("因为 ... 所以 ..."))
        // An unknown compound falls back to its pieces.
        assertEquals("wǒ péngyou", content.pinyinFor("我朋友"))
        assertEquals("", content.pinyinFor("verb"))
        assertEquals("Completed actions: 了 (le)", content.annotate("Completed actions: 了"))
        assertEquals("Because ... so ...: 因为 (yīnwèi) ... 所以 (suǒyǐ) ...", content.annotate("Because ... so ...: 因为 ... 所以 ..."))
        assertEquals("no Chinese here", content.annotate("no Chinese here"))
        // Every grammar formula's fixed words can be given a reading.
        for (g in content.grammar) for (block in g.structure.flatten().filter { it.isKeyword }) {
            assertTrue("${g.title}: ${block.text}", content.pinyinFor(block.text).isNotBlank())
        }
    }

    @Test fun sentencesGiveAPinyinForEachWord() {
        val s = content.sentences.first { it.text == "我是学生。" }
        assertEquals(listOf("wǒ", "shì", "xuésheng", ""), content.tokenPinyin(s))
        // A name keeps its capital letter.
        val named = content.sentences.first { it.text == "我叫大卫。" }
        assertEquals(listOf("wǒ", "jiào", "Dàwèi", ""), content.tokenPinyin(named))
        // When the written pinyin joins words differently, dictionary readings are used.
        val joined = content.sentences.first { it.text == "这个多少钱？" }
        assertEquals(listOf("zhè", "gè", "duōshao", "qián", ""), content.tokenPinyin(joined))
        var missing = 0
        var total = 0
        val sample = content.sentences.filter { it.curated } + content.sentences.filter { !it.curated }.take(3000) +
            content.phraseBooks.flatMap { b -> b.topics.flatMap { it.phrases } }
        for (sentence in sample) {
            val py = content.tokenPinyin(sentence)
            assertEquals(sentence.tokens.size, py.size)
            sentence.tokens.forEachIndexed { i, token ->
                if (Sentence.isPunctuation(token)) assertEquals("", py[i])
                else { total++; if (py[i].isBlank()) missing++ }
            }
        }
        assertTrue("$missing of $total words have no pinyin", missing * 200 < total)
    }

    @Test fun dialoguesParse() {
        assertTrue(content.dialogues.size >= 8)
        assertTrue(content.dialogues.all { it.lines.size >= 4 && it.title.isNotBlank() })
        assertTrue(content.dialoguesOf(1).isNotEmpty())
    }

    @Test fun wordWebLinksWordsThatShareACharacter() {
        val web = content.wordWeb("学")
        assertTrue(web.size >= 4)
        assertTrue(web.all { it.hanzi.contains("学") && it.hanzi.length > 1 })
        assertTrue(web.map { it.hanzi }.containsAll(listOf("学生", "学习", "学校")))
        assertTrue(web.zipWithNext().all { (a, b) -> a.level <= b.level })
        for (level in 1..6) assertTrue(content.webCharacters(level).size >= 10)
        assertTrue("学" in content.webCharacters(1))
    }

    @Test fun characterBreakdownExplainsCompoundWords() {
        val parts = content.characterBreakdown(content.lookup("电脑")!!)
        assertEquals(listOf("电", "脑"), parts.map { it.hanzi })
        assertTrue(parts.all { it.definitions.isNotEmpty() })
        assertTrue(content.characterBreakdown(content.lookup("我")!!).isEmpty())
    }

    @Test fun searchFindsWordsByCharactersPinyinAndMeaning() {
        assertEquals("你", content.search("你").first().hanzi)
        assertEquals("朋友", content.search("pengyou").first().hanzi)
        assertEquals("朋友", content.search("péngyou").first().hanzi)
        assertTrue(content.search("friend").any { it.hanzi == "朋友" })
        assertEquals("吃", content.search("eat").first().hanzi)
        assertEquals("他们", content.search("tamen").first().hanzi)
        assertTrue(content.search("  ").isEmpty())
        assertTrue(content.search("zzzzqq").isEmpty())
    }
}
