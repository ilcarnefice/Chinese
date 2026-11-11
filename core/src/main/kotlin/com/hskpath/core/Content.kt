package com.hskpath.core

/** All learning material, parsed from the text files in the app's assets. */
class Content(
    val words: Map<Int, List<Word>>,
    /** Characters used inside words that are not vocabulary items themselves (level 0). */
    val characters: List<Word>,
    val sentences: List<Sentence>,
    val grammar: List<GrammarPoint>,
    val dialogues: List<Dialogue>,
) {
    val levels: List<Int> = words.keys.sorted()

    private val allWords: List<Word> = levels.flatMap { words.getValue(it) }
    private val byId: Map<String, Word> = allWords.associateBy { it.id }
    private val byHanzi: Map<String, Word> =
        (characters + allWords.asReversed()).associateBy { it.hanzi } // the lowest level wins
    private val sentencesByToken: Map<String, List<Sentence>> = buildMap<String, MutableList<Sentence>> {
        for (s in sentences) for (t in s.words.distinct()) getOrPut(t) { mutableListOf() }.add(s)
    }

    fun word(id: String): Word? = byId[id]

    fun lookup(hanzi: String): Word? = byHanzi[hanzi]

    fun wordsOf(level: Int): List<Word> = words[level].orEmpty()

    /** Example sentences that use this word, easiest first. */
    fun examplesFor(word: Word, max: Int = 3): List<Sentence> {
        val exact = sentencesByToken[word.hanzi].orEmpty()
        val loose = if (exact.size >= max) emptyList() else
            sentences.filter { it !in exact && it.text.contains(word.hanzi) }
        return (exact + loose).sortedBy { it.level * 100 + it.tokens.size }.take(max)
    }

    /** The meaning of each character of a multi-character word, where known. */
    fun characterBreakdown(word: Word): List<Word> =
        if (word.hanzi.length < 2) emptyList()
        else word.hanzi.map { it.toString() }.distinct().mapNotNull { byHanzi[it] }

    /**
     * The "word web" of a character: the words built with it, easiest first,
     * with the character itself (when it is a word of its own) left out.
     */
    fun wordWeb(character: String, max: Int = 8): List<Word> =
        allWords.filter { it.hanzi.length > 1 && it.hanzi.contains(character) }
            .distinctBy { it.hanzi }.sortedBy { it.level }.take(max)

    /** Characters first met in [level] that build the most words, for browsing word webs. */
    fun webCharacters(level: Int, max: Int = 60): List<String> {
        val earlier = (1 until level).flatMap { wordsOf(it) }.flatMap { w -> w.hanzi.map { it.toString() } }.toSet()
        return wordsOf(level).flatMap { w -> w.hanzi.map { it.toString() } }.distinct()
            .filter { it !in earlier }
            .map { it to wordWeb(it, 99).size }
            .filter { it.second >= 3 }
            .sortedByDescending { it.second }.take(max).map { it.first }
    }

    fun grammarOf(level: Int) = grammar.filter { it.level == level }

    fun dialoguesOf(level: Int) = dialogues.filter { it.level == level }

    /** Finds words by characters, pinyin (tones optional) or English meaning. */
    fun search(query: String, limit: Int = 50): List<Word> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val hasHanzi = q.any { it.code > 0x2E80 }
        val key = Pinyin.searchKey(q)
        val lower = q.lowercase()
        val scored = allWords.mapNotNull { w ->
            val score = when {
                hasHanzi -> when {
                    w.hanzi == q || w.traditional == q -> 0
                    w.hanzi.startsWith(q) -> 1
                    w.hanzi.contains(q) || w.traditional.contains(q) -> 2
                    else -> -1
                }
                key.isNotEmpty() && Pinyin.searchKey(w.pinyinNumbered) == key -> 0
                w.definitions.any { it.lowercase() == lower || it.lowercase() == "to $lower" } -> 1
                key.isNotEmpty() && Pinyin.searchKey(w.pinyinNumbered).startsWith(key) -> 2
                w.definitions.any { d -> d.lowercase().split(Regex("[^a-z']+")).contains(lower) } -> 3
                lower.length >= 3 && w.definitions.any { it.lowercase().contains(lower) } -> 4
                else -> -1
            }
            if (score < 0) null else score to w
        }
        return scored.sortedWith(compareBy({ it.first }, { it.second.level })).map { it.second }.take(limit)
    }

    companion object {
        const val LEVEL_COUNT = 6

        /** Loads everything through [read], which returns the text of an asset path. */
        fun load(read: (String) -> String): Content {
            val words = (1..LEVEL_COUNT).associateWith { parseWords(it, read("words/hsk$it.tsv")) }
            return Content(
                words = words,
                characters = parseWords(0, read("words/chars.tsv")),
                sentences = parseSentences(read("content/sentences.tsv")),
                grammar = parseGrammar(read("content/grammar.txt")),
                dialogues = parseDialogues(read("content/dialogues.txt")),
            )
        }

        fun parseWords(level: Int, text: String): List<Word> =
            text.lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }.map { line ->
                val f = line.split('\t')
                require(f.size == 4) { "Bad word line in HSK $level: $line" }
                Word(level, f[0], f[1], f[2], f[3].split("; ").filter { it.isNotBlank() })
            }.toList()

        private fun sentence(level: Int, fields: List<String>): Sentence {
            require(fields.size == 3) { "Bad sentence: $fields" }
            return Sentence(level, fields[0].trim().split(' ').filter { it.isNotEmpty() },
                fields[1].trim(), fields[2].trim())
        }

        fun parseSentences(text: String): List<Sentence> =
            text.lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }.map { line ->
                val f = line.split('|')
                sentence(f[0].trim().toInt(), f.drop(1))
            }.toList()

        fun parseGrammar(text: String): List<GrammarPoint> {
            val points = mutableListOf<GrammarPoint>()
            var header: List<String>? = null
            val explanation = StringBuilder()
            val examples = mutableListOf<Sentence>()
            fun flush() {
                val h = header ?: return
                points += GrammarPoint(h[0].toInt(), h[1], h[2], explanation.toString().trim(), examples.toList())
                explanation.clear(); examples.clear(); header = null
            }
            for (raw in text.lines()) {
                val line = raw.trim()
                val parts = line.removePrefix("#").split('|').map { it.trim() }
                when {
                    line.startsWith("#") && parts.size == 3 && parts[0].toIntOrNull() != null -> {
                        flush(); header = parts
                    }
                    line.startsWith("#") || line.isEmpty() -> Unit
                    line.startsWith(">") -> examples += sentence(header!![0].toInt(), line.drop(1).split('|'))
                    else -> explanation.append(line).append(' ')
                }
            }
            flush()
            return points
        }

        fun parseDialogues(text: String): List<Dialogue> {
            val dialogues = mutableListOf<Dialogue>()
            var level = 0
            var title: String? = null
            val lines = mutableListOf<DialogueLine>()
            fun flush() {
                val t = title ?: return
                dialogues += Dialogue(level, t, lines.toList())
                lines.clear(); title = null
            }
            for (raw in text.lines()) {
                val line = raw.trim()
                val parts = line.removePrefix("#").split('|').map { it.trim() }
                when {
                    line.startsWith("#") && parts.size == 2 && parts[0].toIntOrNull() != null -> {
                        flush(); level = parts[0].toInt(); title = parts[1]
                    }
                    line.startsWith("#") || line.isEmpty() -> Unit
                    else -> lines += DialogueLine(parts[0], sentence(level, parts.drop(1)))
                }
            }
            flush()
            return dialogues
        }
    }
}
