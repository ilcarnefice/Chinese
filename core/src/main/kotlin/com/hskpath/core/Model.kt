package com.hskpath.core

/** One vocabulary item of an HSK level. */
data class Word(
    /** HSK level 1-6; 7 for common words beyond HSK; 0 for a bare character. */
    val level: Int,
    val hanzi: String,
    val traditional: String,
    /** Pinyin with tone numbers, one syllable per character: "xue2 sheng5". */
    val pinyinNumbered: String,
    val definitions: List<String>,
) {
    /** Stable key used to store progress. */
    val id: String get() = "$level:$hanzi"
    val pinyin: String by lazy { Pinyin.toMarked(pinyinNumbered) }
    /** Short meaning shown in exercises. */
    val gloss: String get() = definitions.take(2).joinToString("; ")
    /** The first meaning without notes in brackets: "(particle) of" becomes "of". */
    val shortGloss: String get() {
        val first = definitions.firstOrNull().orEmpty()
        val bare = first.replace(Regex("\\([^)]*\\)"), "").trim().trim(',', ';').trim()
        return bare.ifEmpty { first.trim('(', ')') }
    }
    val levelLabel: String get() = when (level) {
        0 -> "Character"
        in 1..6 -> "HSK $level"
        else -> "Beyond HSK"
    }
}

/** A sentence split into words, with its pinyin and English translation. */
data class Sentence(
    val level: Int,
    /** Words and punctuation in order, e.g. [我, 是, 学生, 。]. */
    val tokens: List<String>,
    val pinyin: String,
    val english: String,
    /** True for the hand-written sentences, which are shown before corpus sentences. */
    val curated: Boolean = false,
) {
    val text: String = tokens.joinToString("")
    /** The words only, without punctuation. */
    val words: List<String> get() = tokens.filter { !isPunctuation(it) }

    companion object {
        fun isPunctuation(token: String) = token.all { it in "。，？！、：；.,?!" }
    }
}

/** One building block of a grammar pattern, e.g. "subject" or the fixed word "是". */
data class PatternBlock(val text: String, val isKeyword: Boolean)

data class GrammarPoint(
    val level: Int,
    val title: String,
    val pattern: String,
    val explanation: String,
    val examples: List<Sentence>,
    /** Finds further sentences that use this structure in the sentence corpus. */
    val match: Regex? = null,
) {
    /**
     * The pattern as rows of blocks: "因为 + reason ， 所以 + result" becomes two
     * rows, one per clause. Blocks written in Chinese are the fixed keywords.
     */
    val structure: List<List<PatternBlock>> by lazy {
        pattern.split('，').map { clause ->
            clause.split('+', '/').map { it.trim() }.filter { it.isNotEmpty() && it != "？" }
                .map { PatternBlock(it, it.any { c -> isHanzi(c) }) }
        }.filter { it.isNotEmpty() }
    }

    /** The fixed Chinese words of the pattern, to highlight in the examples. */
    val keywords: Set<String> by lazy {
        structure.flatten().filter { it.isKeyword }
            .flatMap { it.text.split(' ', '.', '…') }.map { w -> w.filter { isHanzi(it) } }
            .filter { it.isNotEmpty() }.toSet()
    }

    /** Whether a word of an example sentence belongs to the pattern itself. */
    fun isKeyword(token: String): Boolean =
        token in keywords || keywords.any { it.length > 1 && token.length == 1 && it.contains(token) } ||
            keywords.any { token.length > it.length && token.startsWith(it) && it.length > 1 }

    private fun isHanzi(c: Char) = c in '\u4e00'..'\u9fff'
}

data class DialogueLine(val speaker: String, val sentence: Sentence)

data class Dialogue(val level: Int, val title: String, val lines: List<DialogueLine>)
