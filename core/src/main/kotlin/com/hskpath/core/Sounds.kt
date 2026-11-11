package com.hskpath.core

/** What the breath does during one step of a sound. */
enum class Air {
    /** Blocked: nothing gets out yet. */
    NONE,
    /** Flowing out through the mouth. */
    ORAL,
    /** Flowing out through the nose. */
    NASAL,
    /** Released in a strong burst (the aspirated sounds p, t, k, q, ch, c). */
    PUFF,
}

/** One position the mouth passes through while making a sound. */
data class MouthStep(
    /** Name of a pose in the mouth diagrams. */
    val shape: String,
    val air: Air = Air.ORAL,
    val voiced: Boolean = true,
    private val note: String? = null,
) {
    /** One line telling the learner what happens at this step. */
    val caption: String get() = note ?: Sounds.describe(shape)
}

/** One sound of Mandarin with instructions for the mouth. */
data class Sound(
    val letters: String,
    val isInitial: Boolean,
    /** A character to hear the sound in. */
    val example: String,
    /** The mouth positions in order; the animation starts from rest. */
    val steps: List<MouthStep>,
    /** What the lips, teeth and tongue do. */
    val how: String,
    /** The nearest English sound, with its difference. */
    val like: String,
)

object Sounds {
    private val descriptions = mapOf(
        "rest" to "Relaxed and ready",
        "lips" to "Press both lips together",
        "lipsNasal" to "Lips closed; the air goes out through your nose",
        "lipTeeth" to "Upper teeth rest on the lower lip; air hisses through",
        "ridge" to "Tongue tip on the ridge behind the upper teeth",
        "ridgeNasal" to "Tongue tip on the ridge; the air goes out through your nose",
        "ridgeLateral" to "Tongue tip on the ridge; air slips past the sides of the tongue",
        "velar" to "Back of the tongue pressed against the soft palate",
        "velarFric" to "Back of the tongue close to the soft palate; air rubs through",
        "velarNasal" to "Back of the tongue on the soft palate; the air goes out through your nose",
        "palatal" to "Tongue tip down; the flat front of the tongue presses on the hard palate",
        "palatalFric" to "Tongue tip down; air hisses over the flat front of the tongue",
        "retroflex" to "Tongue tip curled up, touching just behind the ridge",
        "retroflexFric" to "Tongue tip curled up, close to the roof of the mouth",
        "dental" to "Tongue tip against the back of the upper teeth",
        "dentalFric" to "Tongue tip close to the back of the upper teeth; air hisses through",
        "a" to "Jaw wide open, tongue low and flat",
        "o" to "Lips rounded, back of the tongue half raised",
        "e" to "Lips relaxed, tongue pulled back and half raised",
        "eFront" to "Lips spread, front of the tongue half raised",
        "schwa" to "Everything relaxed, mouth a little open",
        "i" to "Lips spread, front of the tongue high",
        "u" to "Lips tightly rounded, back of the tongue high",
        "yu" to "Tongue as for i, lips tightly rounded",
        "er" to "Tongue tip curled up and back, touching nothing",
    )

    /** Mouth shapes the diagrams must provide. */
    val shapes: Set<String> = descriptions.keys

    fun describe(shape: String): String = descriptions.getValue(shape)

    private const val PUFF = " Hold a sheet of paper in front of your mouth: it should flutter."
    private const val NO_PUFF = " No puff of air: a sheet of paper in front of your mouth should stay still."
    private const val BURST = "Let go with a strong puff of air"
    private const val BUZZ = "Hold the tongue there and switch your voice on"

    private fun closed(shape: String) = MouthStep(shape, Air.NONE, voiced = false)
    private fun hiss(shape: String) = MouthStep(shape, Air.ORAL, voiced = false)
    private fun burst(shape: String) = MouthStep(shape, Air.PUFF, voiced = false, note = BURST)
    private fun nasal(shape: String) = MouthStep(shape, Air.NASAL)
    private fun vowel(shape: String, note: String? = null) = MouthStep(shape, Air.ORAL, voiced = true, note = note)

    private fun initial(letters: String, example: String, steps: List<MouthStep>, how: String, like: String) =
        Sound(letters, true, example, steps, how, like)

    val initials: List<Sound> = listOf(
        initial("b", "八", listOf(closed("lips"), vowel("a")),
            "Press both lips together, then let them pop open.$NO_PUFF",
            "Like p in \"spy\": softer than English b, and not voiced."),
        initial("p", "怕", listOf(closed("lips"), burst("a"), vowel("a")),
            "Press both lips together, build up air, then release it with a strong puff.$PUFF",
            "Like p in \"pie\", with extra breath."),
        initial("m", "妈", listOf(nasal("lipsNasal"), vowel("a")),
            "Close both lips and hum through your nose, then open.",
            "Same as English m."),
        initial("f", "发", listOf(hiss("lipTeeth"), vowel("a")),
            "Rest your upper teeth lightly on your lower lip and blow air through the gap.",
            "Same as English f."),
        initial("d", "大", listOf(closed("ridge"), vowel("a")),
            "Touch the tip of your tongue to the ridge just behind your upper teeth, then drop it.$NO_PUFF",
            "Like t in \"sty\": not voiced like English d."),
        initial("t", "他", listOf(closed("ridge"), burst("a"), vowel("a")),
            "Tongue tip on the ridge behind your upper teeth, then release with a strong puff.$PUFF",
            "Like t in \"tie\", with extra breath."),
        initial("n", "你", listOf(nasal("ridgeNasal"), vowel("i")),
            "Tongue tip on the ridge behind your upper teeth; let the sound come out through your nose.",
            "Same as English n."),
        initial("l", "拉", listOf(vowel("ridgeLateral"), vowel("a")),
            "Tongue tip on the ridge behind your upper teeth; let air flow around the sides of your tongue.",
            "Like l in \"let\": always a clear l, never the dark l of \"full\"."),
        initial("g", "哥", listOf(closed("velar"), vowel("e")),
            "Lift the back of your tongue to the soft palate at the back of the roof of your mouth, then release.$NO_PUFF",
            "Like k in \"sky\"."),
        initial("k", "卡", listOf(closed("velar"), burst("a"), vowel("a")),
            "Back of the tongue against the soft palate, then release with a strong puff.$PUFF",
            "Like k in \"kite\", with extra breath."),
        initial("h", "喝", listOf(hiss("velarFric"), vowel("e")),
            "Raise the back of your tongue close to the soft palate without touching, and let air rub through the gap.",
            "Rougher than English h, closer to ch in Scottish \"loch\"."),
        initial("j", "鸡", listOf(closed("palatal"), hiss("palatalFric"), vowel("i")),
            "Keep the tongue tip down behind your lower teeth. Press the flat front of the tongue to the hard palate, " +
                "lips spread as in a smile, then release.$NO_PUFF",
            "Close to j in \"jeep\", but made with the flat of the tongue and a smile."),
        initial("q", "七", listOf(closed("palatal"), burst("palatalFric"), vowel("i")),
            "Tongue tip down behind your lower teeth, flat front of the tongue on the hard palate, lips spread; " +
                "release with a strong puff.$PUFF",
            "Close to ch in \"cheese\", with the tongue tip kept down."),
        initial("x", "西", listOf(hiss("palatalFric"), vowel("i")),
            "Tongue tip down behind your lower teeth. Raise the flat front of the tongue near the hard palate and let " +
                "air hiss through, lips spread.",
            "Between s in \"see\" and sh in \"she\"."),
        initial("zh", "知", listOf(closed("retroflex"), hiss("retroflexFric"), vowel("retroflexFric", BUZZ)),
            "Curl the tongue tip up and back to touch just behind the ridge, then release.$NO_PUFF",
            "Close to j in \"judge\", with the tongue curled back."),
        initial("ch", "吃", listOf(closed("retroflex"), burst("retroflexFric"), vowel("retroflexFric", BUZZ)),
            "Curl the tongue tip up and back to touch just behind the ridge; release with a strong puff.$PUFF",
            "Close to ch in \"church\", with the tongue curled back."),
        initial("sh", "是", listOf(hiss("retroflexFric"), vowel("retroflexFric", BUZZ)),
            "Curl the tongue tip up and back near the roof of the mouth without touching, and let air hiss through.",
            "Close to sh in \"shirt\", with the tongue curled back."),
        initial("r", "日", listOf(vowel("retroflexFric", "Tongue tip curled up close to the roof of the mouth, voice on from the start")),
            "Same curled-back tongue as sh, but with your voice switched on. Keep the lips relaxed, not rounded.",
            "Between the s in \"measure\" and an English r."),
        initial("z", "字", listOf(closed("dental"), hiss("dentalFric"), vowel("dentalFric", BUZZ)),
            "Put the tongue tip flat against the back of your upper teeth, then release into a buzz.$NO_PUFF",
            "Like ds in \"kids\"."),
        initial("c", "次", listOf(closed("dental"), burst("dentalFric"), vowel("dentalFric", BUZZ)),
            "Tongue tip against the back of your upper teeth; release with a strong puff.$PUFF",
            "Like ts in \"cats\", with extra breath. Never like English c."),
        initial("s", "四", listOf(hiss("dentalFric"), vowel("dentalFric", BUZZ)),
            "Bring the tongue tip close to the back of your upper teeth and let air hiss through.",
            "Same as English s."),
    )

    private val nasalShapes = setOf("ridgeNasal", "velarNasal")
    private val shapeNames = mapOf("yu" to "ü", "eFront" to "e (as in \"yes\")", "schwa" to "a relaxed \"uh\"",
        "ridgeNasal" to "n (tongue tip up to the ridge)", "velarNasal" to "ng (back of the tongue up)")

    private fun single(letters: String, example: String, shape: String, how: String, like: String) =
        Sound(letters, false, example, listOf(vowel(shape)), how, like)

    private fun glide(letters: String, example: String, shapes: List<String>, like: String): Sound {
        val names = shapes.map { shapeNames[it] ?: it }
        val steps = shapes.map { if (it in nasalShapes) nasal(it) else vowel(it) }
        return Sound(letters, false, example, steps,
            "Start with ${names.first()} and glide smoothly to ${names.drop(1).joinToString(", then ")}. " +
                "It is one syllable, not two.", like)
    }

    val finals: List<Sound> = listOf(
        single("a", "啊", "a", "Drop your jaw and open wide. The tongue lies low and flat.", "Like a in \"father\"."),
        single("o", "哦", "o", "Round your lips into a medium circle. The back of the tongue rises halfway.", "Like o in \"more\" (British)."),
        single("e", "饿", "e", "Lips relaxed and slightly open, not rounded. Pull the tongue back and raise its back halfway.",
            "Like the \"uh\" in \"duh\", made further back."),
        single("i", "一", "i", "Spread your lips as in a smile. Raise the front of the tongue high, tip behind the lower teeth.", "Like ee in \"see\"."),
        single("u", "五", "u", "Round your lips tightly and push them forward. Raise the back of the tongue high.", "Like oo in \"moon\", with rounder lips."),
        single("ü", "鱼", "yu", "Say i (\"ee\") and hold your tongue there, then round your lips tightly as for u.",
            "Like French u or German ü. English has no such sound."),
        Sound("er", false, "二", listOf(vowel("schwa"), vowel("er")),
            "Start from a relaxed \"uh\", then curl the tongue tip up and back without touching the roof of the mouth.",
            "Like er in American \"her\"."),
        glide("ai", "爱", listOf("a", "i"), "Like \"eye\"."),
        glide("ei", "黑", listOf("eFront", "i"), "Like ay in \"say\"."),
        glide("ao", "奥", listOf("a", "u"), "Like ow in \"cow\"."),
        glide("ou", "欧", listOf("o", "u"), "Like o in \"go\"."),
        glide("an", "安", listOf("a", "ridgeNasal"), "Like \"ahn\"."),
        glide("en", "恩", listOf("schwa", "ridgeNasal"), "Like un in \"under\"."),
        glide("ang", "昂", listOf("a", "velarNasal"), "Like ung in \"hung\" with a wider a."),
        glide("eng", "冷", listOf("schwa", "velarNasal"), "Like ung in \"lung\"."),
        glide("ong", "红", listOf("u", "velarNasal"), "Like \"oong\", with rounded lips."),
        glide("ia", "牙", listOf("i", "a"), "Like \"ya\"."),
        glide("ie", "也", listOf("i", "eFront"), "Like \"ye\" in \"yes\"."),
        glide("iao", "要", listOf("i", "a", "u"), "Like \"yow\"."),
        glide("iu", "有", listOf("i", "o", "u"), "Like \"yo\"."),
        glide("ian", "烟", listOf("i", "eFront", "ridgeNasal"), "Like \"yen\"."),
        glide("in", "音", listOf("i", "ridgeNasal"), "Like een in \"seen\", shorter."),
        glide("iang", "羊", listOf("i", "a", "velarNasal"), "Like \"yahng\"."),
        glide("ing", "英", listOf("i", "velarNasal"), "Like ing in \"sing\"."),
        glide("iong", "用", listOf("yu", "velarNasal"), "Like \"yoong\"."),
        glide("ua", "哇", listOf("u", "a"), "Like \"wa\"."),
        glide("uo", "我", listOf("u", "o"), "Like \"wo\" in \"wore\"."),
        glide("uai", "外", listOf("u", "a", "i"), "Like \"why\"."),
        glide("ui", "为", listOf("u", "eFront", "i"), "Like \"way\"."),
        glide("uan", "晚", listOf("u", "a", "ridgeNasal"), "Like \"wahn\"."),
        glide("un", "问", listOf("u", "schwa", "ridgeNasal"), "Like \"when\" with a short uh."),
        glide("uang", "王", listOf("u", "a", "velarNasal"), "Like \"wahng\"."),
        glide("üe", "月", listOf("yu", "eFront"), "ü followed by e as in \"yes\"."),
        glide("üan", "远", listOf("yu", "eFront", "ridgeNasal"), "ü followed by \"en\"."),
        glide("ün", "云", listOf("yu", "ridgeNasal"), "ü followed by n."),
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
