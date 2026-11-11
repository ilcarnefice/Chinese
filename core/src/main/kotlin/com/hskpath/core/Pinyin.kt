package com.hskpath.core

/** Helpers for pinyin written with tone numbers ("ni3 hao3") or tone marks ("nǐ hǎo"). */
object Pinyin {
    private val marks = mapOf(
        'a' to "āáǎà", 'e' to "ēéěè", 'i' to "īíǐì",
        'o' to "ōóǒò", 'u' to "ūúǔù", 'ü' to "ǖǘǚǜ",
    )
    private val unmark: Map<Char, Char> =
        marks.flatMap { (plain, marked) -> marked.map { it to plain } }.toMap()

    val toneNames = listOf("1st tone (high, level)", "2nd tone (rising)", "3rd tone (low, dipping)",
        "4th tone (falling)", "neutral tone (light)")

    /** Tone of a numbered syllable: 1-4, or 5 for the neutral tone. */
    fun tone(syllable: String): Int = syllable.lastOrNull()?.digitToIntOrNull()?.takeIf { it in 1..5 } ?: 5

    /** Letters of a numbered syllable without its tone digit, with v written as ü. */
    fun base(syllable: String): String =
        syllable.trimEnd { it.isDigit() }.lowercase().replace("u:", "ü").replace('v', 'ü')

    /** "nv3" -> "nǚ", "hao3" -> "hǎo", "ma5" -> "ma". */
    fun markSyllable(syllable: String): String = applyTone(base(syllable), tone(syllable))

    fun applyTone(base: String, tone: Int): String {
        if (tone !in 1..4) return base
        val index = when {
            'a' in base -> base.indexOf('a')
            'e' in base -> base.indexOf('e')
            "ou" in base -> base.indexOf('o')
            else -> base.indexOfLast { it in marks }
        }
        if (index < 0) return base
        return base.substring(0, index) + marks.getValue(base[index])[tone - 1] + base.substring(index + 1)
    }

    /** "wo3 men5" -> "wǒmen", "nv3 er2" -> "nǚ'ér", "na3 r5" -> "nǎr". */
    fun toMarked(numbered: String): String {
        val out = StringBuilder()
        for (syllable in numbered.trim().split(' ').filter { it.isNotEmpty() }) {
            val marked = markSyllable(syllable)
            if (out.isNotEmpty() && base(syllable).first() in "aeo") out.append('\'')
            out.append(marked)
        }
        return out.toString()
    }

    /** Lowercase letters only, tones removed, ü as v: used to match typed searches. */
    fun searchKey(text: String): String = buildString {
        for (c in text.lowercase()) {
            val plain = unmark[c] ?: c
            when {
                plain == 'ü' -> append('v')
                plain in 'a'..'z' -> append(plain)
            }
        }
    }

    /** The same syllables with different tones, for "pick the right tones" questions. */
    fun toneVariants(numbered: String, count: Int, random: kotlin.random.Random): List<String> {
        val syllables = numbered.trim().split(' ').filter { it.isNotEmpty() }
        val correct = toMarked(numbered)
        val variants = LinkedHashSet<String>()
        var attempts = 0
        while (variants.size < count && attempts < 200) {
            attempts++
            val changed = syllables.map { s ->
                if (base(s) == "r") s
                else if (random.nextInt(syllables.size + 1) == 0) s
                else base(s).replace('ü', 'v') + (1 + random.nextInt(4))
            }
            val marked = toMarked(changed.joinToString(" "))
            if (marked != correct) variants.add(marked)
        }
        return variants.toList()
    }
}
