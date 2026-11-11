package com.hskpath.core

/** All learning material, parsed from the text files in the app's assets. */
class Content(
    val words: Map<Int, List<Word>>,
    /** Characters used inside words that are not vocabulary items themselves (level 0). */
    val characters: List<Word>,
    /** Common words beyond the HSK lists (level 7): shown in word maps and the dictionary. */
    val extraWords: List<Word>,
    /** Hand-written sentences first, then the corpus ordered by level and length. */
    val sentences: List<Sentence>,
    val grammar: List<GrammarPoint>,
    val dialogues: List<Dialogue>,
    val phraseBooks: List<PhraseBook> = emptyList(),
    /** Themed course outline per level, where one has been written. */
    val outlines: Map<Int, List<UnitOutline>> = emptyMap(),
) {
    val levels: List<Int> = words.keys.sorted()

    private val hskWords: List<Word> = levels.flatMap { words.getValue(it) }
    private val mapWords: List<Word> = hskWords + extraWords
    private val byId: Map<String, Word> = hskWords.associateBy { it.id }
    private val byHanzi: Map<String, Word> =
        (characters + mapWords.asReversed()).associateBy { it.hanzi } // the lowest level wins
    private val sentencesByToken: Map<String, List<Sentence>> = buildMap<String, MutableList<Sentence>> {
        for (s in sentences) for (t in s.words.distinct()) getOrPut(t) { mutableListOf() }.add(s)
    }
    private val sentencesByLevel: Map<Int, List<Sentence>> = sentences.groupBy { it.level }
    private val webByCharacter: Map<Char, List<Word>> = buildMap<Char, MutableList<Word>> {
        for (w in mapWords) if (w.hanzi.length > 1) for (c in w.hanzi.toSet()) getOrPut(c) { mutableListOf() }.add(w)
    }
    private val grammarExamples = HashMap<GrammarPoint, List<Sentence>>()

    /** Number of words that can appear in a word map. */
    val mapWordCount: Int get() = mapWords.size

    fun word(id: String): Word? = byId[id]

    fun lookup(hanzi: String): Word? = byHanzi[hanzi]

    fun wordsOf(level: Int): List<Word> = words[level].orEmpty()

    fun sentencesOf(level: Int): List<Sentence> = sentencesByLevel[level].orEmpty()

    /** Sentences in which [hanzi] appears as a word of its own. */
    fun sentencesWith(hanzi: String): List<Sentence> = sentencesByToken[hanzi].orEmpty()

    /** Example sentences that use this word: hand-written ones first, then the easiest. */
    fun examplesFor(word: Word, max: Int = 3): List<Sentence> {
        val exact = sentencesByToken[word.hanzi].orEmpty()
        val loose = if (exact.size >= max || word.hanzi.length < 2) emptyList() else
            sentences.asSequence().filter { it.text.contains(word.hanzi) && it !in exact }.take(max).toList()
        return (exact + loose).sortedBy { (if (it.curated) 0 else 1000) + it.level * 100 + it.tokens.size }.take(max)
    }

    /** The meaning of each character of a multi-character word, where known. */
    fun characterBreakdown(word: Word): List<Word> =
        if (word.hanzi.length < 2) emptyList()
        else word.hanzi.map { it.toString() }.distinct().mapNotNull { byHanzi[it] }

    /**
     * The word map of a character: the words built with it, easiest first,
     * with the character itself (when it is a word of its own) left out.
     */
    fun wordWeb(character: String, max: Int = 8): List<Word> =
        webByCharacter[character.firstOrNull()].orEmpty().distinctBy { it.hanzi }.sortedBy { it.level }.take(max)

    /** Characters first met in [level] that build the most words, for browsing word maps. */
    fun webCharacters(level: Int, max: Int = 60): List<String> {
        val earlier = (1 until level).flatMap { wordsOf(it) }.flatMap { w -> w.hanzi.map { it.toString() } }.toSet()
        return wordsOf(level).flatMap { w -> w.hanzi.map { it.toString() } }.distinct()
            .filter { it !in earlier }
            .map { it to wordWeb(it, 999).size }
            .filter { it.second >= 3 }
            .sortedByDescending { it.second }.take(max).map { it.first }
    }

    fun grammarOf(level: Int) = grammar.filter { it.level == level }

    /**
     * Sentences that show a grammar structure: its own examples, then sentences
     * from the corpus that match it and are not above the learner's level.
     */
    fun grammarExamples(point: GrammarPoint, max: Int = 10): List<Sentence> {
        val all = synchronized(grammarExamples) {
            grammarExamples.getOrPut(point) {
                val regex = point.match
                val own = point.examples.map { it.text }.toSet()
                val found = if (regex == null) emptyList() else sentences.asSequence()
                    .filter { !it.curated && it.level <= point.level + 2 && it.words.size in 3..12 }
                    .filter { regex.containsMatchIn(it.text) && it.text !in own }
                    .take(CORPUS_EXAMPLES).toList()
                point.examples + found.sortedBy { it.words.size }
            }
        }
        return all.take(max)
    }

    fun outline(level: Int): List<UnitOutline>? = outlines[level]

    /**
     * Pinyin for a piece of Chinese text: the dictionary reading when the text is
     * a known word, otherwise the readings of its known pieces. Anything that is
     * not Chinese is dropped. "因为" gives "yīnwèi"; "是 … 的" gives "shì de".
     */
    fun pinyinFor(text: String): String {
        val out = mutableListOf<String>()
        for (run in HANZI_RUN.findAll(text).map { it.value }) {
            var i = 0
            while (i < run.length) {
                // Longest known word starting here.
                var end = run.length
                var word: Word? = null
                while (end > i) {
                    word = byHanzi[run.substring(i, end)]
                    if (word != null) break
                    end--
                }
                if (word == null) { i++; continue }
                out += word.pinyin
                i = end
            }
        }
        return out.joinToString(" ")
    }

    /** The same text with the pinyin of each Chinese run added in brackets after it. */
    fun annotate(text: String): String = HANZI_RUN.replace(text) { m ->
        val py = pinyinFor(m.value)
        if (py.isEmpty()) m.value else "${m.value} ($py)"
    }

    /**
     * Pinyin for each token of a sentence, in step with [Sentence.tokens];
     * punctuation gets an empty string. The sentence's own pinyin is used when
     * it is written one word per token, otherwise dictionary readings.
     */
    fun tokenPinyin(sentence: Sentence): List<String> {
        val written = sentence.pinyin.split(' ').map { it.trim { c -> c in PINYIN_PUNCTUATION } }.filter { it.isNotEmpty() }
        val aligned = written.size == sentence.words.size
        var next = 0
        return sentence.tokens.map { token ->
            if (Sentence.isPunctuation(token)) return@map ""
            val own = if (aligned) written[next++] else null
            val known = byHanzi[token]
            when {
                // Keep the capital of names; other words start in lower case.
                own != null -> if (known != null) own.replaceFirstChar { it.lowercase() } else own
                else -> pinyinFor(token).replace(" ", "")
            }
        }
    }

    fun phraseBook(id: String): PhraseBook? = phraseBooks.firstOrNull { it.id == id }

    fun dialoguesOf(level: Int) = dialogues.filter { it.level == level }

    /** Finds words by characters, pinyin (tones optional) or English meaning. */
    fun search(query: String, limit: Int = 60): List<Word> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val hasHanzi = q.any { it.code > 0x2E80 }
        val key = Pinyin.searchKey(q)
        val lower = q.lowercase()
        val wordRegex = Regex("[^a-z']+")
        val scored = mapWords.mapNotNull { w ->
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
                w.definitions.any { d -> d.lowercase().split(wordRegex).contains(lower) } -> 3
                lower.length >= 3 && w.definitions.any { it.lowercase().contains(lower) } -> 4
                else -> -1
            }
            if (score < 0) null else score to w
        }
        return scored.sortedWith(compareBy({ it.first }, { it.second.level })).map { it.second }.take(limit)
    }

    companion object {
        const val LEVEL_COUNT = 6
        const val EXTRA_LEVEL = 7
        /** Phrase-book sentences are not graded; this level picks the spare tiles in drills. */
        const val PHRASE_LEVEL = 2
        private val HANZI_RUN = Regex("[\\u4e00-\\u9fff]+")
        private const val PINYIN_PUNCTUATION = ".,?!;:\u2026\"()"
        private const val CORPUS_EXAMPLES = 40

        /** Loads everything through [read], which returns the text of an asset path. */
        fun load(read: (String) -> String): Content {
            val words = (1..LEVEL_COUNT).associateWith { parseWords(it, read("words/hsk$it.tsv")) }
            val corpus = (1..LEVEL_COUNT).flatMap { parseCorpus(it, read("sentences/hsk$it.tsv")) }
            return Content(
                words = words,
                characters = parseWords(0, read("words/chars.tsv")),
                extraWords = parseWords(EXTRA_LEVEL, read("words/extra.tsv")),
                sentences = parseSentences(read("content/sentences.tsv")) + corpus,
                grammar = parseGrammar(read("content/grammar.txt")),
                dialogues = parseDialogues(read("content/dialogues.txt")),
                phraseBooks = listOf(
                    PhraseBook("everyday", "💬", "Everyday phrases", "The sentences you will say and hear every day",
                        parsePhrases(read("content/phrases_everyday_1.txt")) + parsePhrases(read("content/phrases_everyday_2.txt"))),
                    PhraseBook("travel", "🧳", "Travel phrases", "Airports, hotels, taxis, restaurants and sights",
                        parsePhrases(read("content/phrases_travel.txt"))),
                    PhraseBook("business", "💼", "Business phrases", "From first meetings to contracts",
                        parsePhrases(read("content/phrases_business.txt"))),
                ),
                outlines = (1..LEVEL_COUNT).mapNotNull { level ->
                    // Outlines are optional: a level without one is taught in list order.
                    runCatching { read("content/outline_hsk$level.txt") }.getOrNull()?.let { level to parseOutline(it) }
                }.toMap(),
            )
        }

        fun parseWords(level: Int, text: String): List<Word> =
            text.lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }.map { line ->
                val f = line.split('\t')
                require(f.size == 4) { "Bad word line in level $level: $line" }
                Word(level, f[0], f[1], f[2], f[3].split("; ").filter { it.isNotBlank() })
            }.toList()

        private fun sentence(level: Int, fields: List<String>, curated: Boolean): Sentence {
            require(fields.size == 3) { "Bad sentence: $fields" }
            return Sentence(level, fields[0].trim().split(' ').filter { it.isNotEmpty() },
                fields[1].trim(), fields[2].trim(), curated)
        }

        /** Hand-written sentences: "level|words|pinyin|English". */
        fun parseSentences(text: String): List<Sentence> =
            text.lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }.map { line ->
                val f = line.split('|')
                sentence(f[0].trim().toInt(), f.drop(1), curated = true)
            }.toList()

        /** Corpus sentences of one level: "words|pinyin|English". */
        fun parseCorpus(level: Int, text: String): List<Sentence> =
            text.lineSequence().filter { it.isNotBlank() }.map { sentence(level, it.split('|'), curated = false) }.toList()

        /** Course outline: "## unit title", lines of words (one lesson each), "> grammar title". */
        fun parseOutline(text: String): List<UnitOutline> {
            val units = mutableListOf<UnitOutline>()
            var title: String? = null
            val items = mutableListOf<OutlineItem>()
            fun flush() {
                val t = title ?: return
                units += UnitOutline(t, items.toList())
                items.clear()
            }
            for (raw in text.lines()) {
                val line = raw.trim()
                when {
                    line.startsWith("## ") -> { flush(); title = line.removePrefix("## ").trim() }
                    line.startsWith("#") || line.isEmpty() || title == null -> Unit
                    line.startsWith(">") -> items += OutlineItem.Grammar(line.removePrefix(">").trim())
                    else -> items += OutlineItem.Words(line.split(' ', '\t').filter { it.isNotEmpty() })
                }
            }
            flush()
            return units
        }

        /** Phrase books: "## topic" lines followed by "words | pinyin | English" lines. */
        fun parsePhrases(text: String): List<PhraseTopic> {
            val topics = mutableListOf<PhraseTopic>()
            var title: String? = null
            val phrases = mutableListOf<Sentence>()
            fun flush() {
                val t = title ?: return
                topics += PhraseTopic(t, phrases.toList())
                phrases.clear()
            }
            for (raw in text.lines()) {
                val line = raw.trim()
                when {
                    line.startsWith("## ") -> { flush(); title = line.removePrefix("## ").trim() }
                    line.startsWith("#") || line.isEmpty() -> Unit
                    else -> phrases += sentence(PHRASE_LEVEL, line.split('|'), true)
                }
            }
            flush()
            return topics
        }

        fun parseGrammar(text: String): List<GrammarPoint> {
            val points = mutableListOf<GrammarPoint>()
            var header: List<String>? = null
            val explanation = StringBuilder()
            val examples = mutableListOf<Sentence>()
            fun flush() {
                val h = header ?: return
                val regex = h.getOrNull(3)?.takeIf { it.isNotEmpty() && it != "-" }?.let { Regex(it) }
                points += GrammarPoint(h[0].toInt(), h[1], h[2], explanation.toString().trim(), examples.toList(), regex)
                explanation.clear(); examples.clear(); header = null
            }
            for (raw in text.lines()) {
                val line = raw.trim()
                val parts = line.removePrefix("#").split('|').map { it.trim() }
                when {
                    line.startsWith("#") && parts.size in 3..4 && parts[0].toIntOrNull() != null -> {
                        flush(); header = parts
                    }
                    line.startsWith("#") || line.isEmpty() -> Unit
                    line.startsWith(">") -> examples += sentence(header!![0].toInt(), line.drop(1).split('|'), true)
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
                    else -> lines += DialogueLine(parts[0], sentence(level, parts.drop(1), true))
                }
            }
            flush()
            return dialogues
        }
    }
}
