package com.hskpath.core

/**
 * One sound of Mandarin with instructions for the mouth.
 * [steps] names the mouth shapes to move through, e.g. "ai" glides from "a" to "i".
 */
data class Sound(
    val letters: String,
    val isInitial: Boolean,
    /** A character to hear the sound in. */
    val example: String,
    val steps: List<String>,
    /** What the lips, teeth and tongue do. */
    val how: String,
    /** The nearest English sound, with its difference. */
    val like: String,
)

object Sounds {
    /** Mouth shapes the app can draw. */
    val shapes = setOf("rest", "lips", "lipteeth", "ridge", "velar", "velarOpen", "palatal", "retro", "dental",
        "a", "o", "e", "i", "u", "ü", "er", "n", "ng")

    private const val PUFF = " Hold a sheet of paper in front of your mouth: it should flutter."
    private const val NO_PUFF = " No puff of air: a sheet of paper in front of your mouth should stay still."

    val initials: List<Sound> = listOf(
        Sound("b", true, "八", listOf("lips", "a"), "Press both lips together, then let them pop open.$NO_PUFF",
            "Like p in \"spy\": softer than English b, and not voiced."),
        Sound("p", true, "怕", listOf("lips", "a"), "Press both lips together, build up air, then release it with a strong puff.$PUFF",
            "Like p in \"pie\", with extra breath."),
        Sound("m", true, "妈", listOf("lips", "a"), "Close both lips and hum through your nose, then open.",
            "Same as English m."),
        Sound("f", true, "发", listOf("lipteeth", "a"), "Rest your upper teeth lightly on your lower lip and blow air through the gap.",
            "Same as English f."),
        Sound("d", true, "大", listOf("ridge", "a"), "Touch the tip of your tongue to the ridge just behind your upper teeth, then drop it.$NO_PUFF",
            "Like t in \"sty\": not voiced like English d."),
        Sound("t", true, "他", listOf("ridge", "a"), "Tongue tip on the ridge behind your upper teeth, then release with a strong puff.$PUFF",
            "Like t in \"tie\", with extra breath."),
        Sound("n", true, "你", listOf("ridge", "i"), "Tongue tip on the ridge behind your upper teeth; let the sound come out through your nose.",
            "Same as English n."),
        Sound("l", true, "来", listOf("ridge", "a"), "Tongue tip on the ridge behind your upper teeth; let air flow around the sides of your tongue.",
            "Like l in \"let\": always a clear l, never the dark l of \"full\"."),
        Sound("g", true, "哥", listOf("velar", "e"), "Lift the back of your tongue to the soft palate at the back of the roof of your mouth, then release.$NO_PUFF",
            "Like k in \"sky\"."),
        Sound("k", true, "看", listOf("velar", "a"), "Back of the tongue against the soft palate, then release with a strong puff.$PUFF",
            "Like k in \"kite\", with extra breath."),
        Sound("h", true, "好", listOf("velarOpen", "a"), "Raise the back of your tongue close to the soft palate without touching, and let air rub through the gap.",
            "Rougher than English h, closer to ch in Scottish \"loch\"."),
        Sound("j", true, "鸡", listOf("palatal", "i"), "Keep the tongue tip down behind your lower teeth. Press the flat front of the tongue to the hard palate, lips spread as in a smile, then release.$NO_PUFF",
            "Close to j in \"jeep\", but made with the flat of the tongue and a smile."),
        Sound("q", true, "七", listOf("palatal", "i"), "Tongue tip down behind your lower teeth, flat front of the tongue on the hard palate, lips spread; release with a strong puff.$PUFF",
            "Close to ch in \"cheese\", with the tongue tip kept down."),
        Sound("x", true, "西", listOf("palatal", "i"), "Tongue tip down behind your lower teeth. Raise the flat front of the tongue near the hard palate and let air hiss through, lips spread.",
            "Between s in \"see\" and sh in \"she\"."),
        Sound("zh", true, "知", listOf("retro", "retro"), "Curl the tongue tip up and back to touch just behind the ridge, then release.$NO_PUFF",
            "Close to j in \"judge\", with the tongue curled back."),
        Sound("ch", true, "吃", listOf("retro", "retro"), "Curl the tongue tip up and back to touch just behind the ridge; release with a strong puff.$PUFF",
            "Close to ch in \"church\", with the tongue curled back."),
        Sound("sh", true, "是", listOf("retro", "retro"), "Curl the tongue tip up and back near the roof of the mouth without touching, and let air hiss through.",
            "Close to sh in \"shirt\", with the tongue curled back."),
        Sound("r", true, "日", listOf("retro", "retro"), "Same curled-back tongue as sh, but with your voice switched on. Keep the lips relaxed, not rounded.",
            "Between the s in \"measure\" and an English r."),
        Sound("z", true, "字", listOf("dental", "dental"), "Put the tongue tip flat against the back of your upper teeth, then release into a buzz.$NO_PUFF",
            "Like ds in \"kids\"."),
        Sound("c", true, "次", listOf("dental", "dental"), "Tongue tip against the back of your upper teeth; release with a strong puff.$PUFF",
            "Like ts in \"cats\", with extra breath. Never like English c."),
        Sound("s", true, "四", listOf("dental", "dental"), "Bring the tongue tip close to the back of your upper teeth and let air hiss through.",
            "Same as English s."),
    )

    private fun vowel(letters: String, example: String, how: String, like: String) =
        Sound(letters, false, example, listOf(letters), how, like)

    private fun glide(letters: String, example: String, steps: List<String>, like: String): Sound {
        val names = steps.map { if (it == "n") "n (tongue tip up to the ridge)" else if (it == "ng") "ng (back of the tongue up)" else it }
        return Sound(letters, false, example, steps,
            "Start with ${names.first()} and glide smoothly to ${names.drop(1).joinToString(", then ")}. It is one syllable, not two.", like)
    }

    val finals: List<Sound> = listOf(
        vowel("a", "啊", "Drop your jaw and open wide. The tongue lies low and flat.", "Like a in \"father\"."),
        vowel("o", "哦", "Round your lips into a medium circle. The back of the tongue rises halfway.", "Like o in \"more\" (British)."),
        vowel("e", "饿", "Lips relaxed and slightly open, not rounded. Pull the tongue back and raise its back halfway.", "Like the \"uh\" in \"duh\", made further back."),
        vowel("i", "一", "Spread your lips as in a smile. Raise the front of the tongue high, tip behind the lower teeth.", "Like ee in \"see\"."),
        vowel("u", "五", "Round your lips tightly and push them forward. Raise the back of the tongue high.", "Like oo in \"moon\", with rounder lips."),
        vowel("ü", "鱼", "Say i (\"ee\") and hold your tongue there, then round your lips tightly as for u.", "Like French u or German ü. English has no such sound."),
        Sound("er", false, "二", listOf("e", "er"), "Say e, then curl the tongue tip up and back without touching the roof of the mouth.", "Like er in American \"her\"."),
        glide("ai", "爱", listOf("a", "i"), "Like \"eye\"."),
        glide("ei", "黑", listOf("e", "i"), "Like ay in \"say\"."),
        glide("ao", "好", listOf("a", "u"), "Like ow in \"cow\"."),
        glide("ou", "狗", listOf("o", "u"), "Like o in \"go\"."),
        glide("an", "安", listOf("a", "n"), "Like \"ahn\"."),
        glide("en", "很", listOf("e", "n"), "Like un in \"under\"."),
        glide("ang", "忙", listOf("a", "ng"), "Like ung in \"hung\" with a wider a."),
        glide("eng", "冷", listOf("e", "ng"), "Like ung in \"lung\"."),
        glide("ong", "红", listOf("u", "ng"), "Like \"oong\", with rounded lips."),
        glide("ia", "家", listOf("i", "a"), "Like \"ya\"."),
        glide("ie", "写", listOf("i", "e"), "Like \"ye\" in \"yes\"."),
        glide("iao", "小", listOf("i", "a", "u"), "Like \"yow\"."),
        glide("iu", "六", listOf("i", "o", "u"), "Like \"yo\"."),
        glide("ian", "天", listOf("i", "e", "n"), "Like \"yen\"."),
        glide("in", "新", listOf("i", "n"), "Like een in \"seen\", shorter."),
        glide("iang", "想", listOf("i", "a", "ng"), "Like \"yahng\"."),
        glide("ing", "听", listOf("i", "ng"), "Like ing in \"sing\"."),
        glide("iong", "用", listOf("ü", "ng"), "Like \"yoong\"."),
        glide("ua", "花", listOf("u", "a"), "Like \"wa\"."),
        glide("uo", "多", listOf("u", "o"), "Like \"wo\" in \"wore\"."),
        glide("uai", "快", listOf("u", "a", "i"), "Like \"why\"."),
        glide("ui", "对", listOf("u", "e", "i"), "Like \"way\"."),
        glide("uan", "晚", listOf("u", "a", "n"), "Like \"wahn\"."),
        glide("un", "问", listOf("u", "e", "n"), "Like \"when\" with a short uh."),
        glide("uang", "黄", listOf("u", "a", "ng"), "Like \"wahng\"."),
        glide("üe", "月", listOf("ü", "e"), "ü followed by e as in \"yes\"."),
        glide("üan", "远", listOf("ü", "e", "n"), "ü followed by \"en\"."),
        glide("ün", "云", listOf("ü", "n"), "ü followed by n."),
    )

    val all: List<Sound> = initials + finals

    fun find(letters: String, isInitial: Boolean): Sound? = all.firstOrNull { it.letters == letters && it.isInitial == isInitial }

    private val initialLetters = initials.map { it.letters }.sortedByDescending { it.length }

    /**
     * Splits a numbered syllable into its initial and final, undoing the spelling
     * rules: "yi" is the final i, "wo" is uo, "ju" is j + ü, "yuan" is üan.
     */
    fun split(syllable: String): Pair<String, String> {
        var s = Pinyin.base(syllable)
        if (s == "r") return "" to "r" // the -r suffix of 哪儿
        if (s.startsWith("y")) {
            s = when {
                s.startsWith("yu") -> "ü" + s.drop(2)
                s.startsWith("yi") -> s.drop(1)
                s == "you" -> "iu"
                else -> "i" + s.drop(1)
            }
            return "" to s
        }
        if (s.startsWith("w")) {
            s = when {
                s.startsWith("wu") -> s.drop(1)
                s == "wei" -> "ui"
                s == "wen" -> "un"
                else -> "u" + s.drop(1)
            }
            return "" to s
        }
        val initial = initialLetters.firstOrNull { s.startsWith(it) } ?: ""
        var final = s.drop(initial.length)
        if (initial in setOf("j", "q", "x") && final.startsWith("u")) final = "ü" + final.drop(1)
        return initial to final
    }

    /** Words whose first syllable uses this sound, easiest first. */
    fun examples(content: Content, sound: Sound, max: Int = 6): List<Word> =
        (1..Content.LEVEL_COUNT).asSequence().flatMap { content.wordsOf(it).asSequence() }.filter { w ->
            val (initial, final) = split(w.pinyinNumbered.substringBefore(' '))
            if (sound.isInitial) initial == sound.letters else final == sound.letters
        }.take(max).toList()
}
