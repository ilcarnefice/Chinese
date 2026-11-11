package com.hskpath.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hskpath.app.AppState
import com.hskpath.app.LiveSession
import com.hskpath.app.Screen
import com.hskpath.app.SessionKind
import com.hskpath.core.Arrange
import com.hskpath.core.Choice
import com.hskpath.core.ChoiceKind
import com.hskpath.core.Cloze
import com.hskpath.core.Content
import com.hskpath.core.Exercise
import com.hskpath.core.ExerciseBuilder
import com.hskpath.core.Match
import com.hskpath.core.Progress
import com.hskpath.core.Sentence
import com.hskpath.core.SentenceChoice
import com.hskpath.core.SentenceKind
import com.hskpath.core.SessionResult
import com.hskpath.core.Teach
import com.hskpath.core.TeachGrammar
import com.hskpath.core.Word

/**
 * Plays one lesson, practice round, drill or level test from start to results.
 * The lesson's position lives on its back-stack [entry], so opening a word map or
 * a writing screen from inside a lesson and coming back continues where it was.
 */
@Composable
fun SessionScreen(app: AppState, content: Content, kind: SessionKind, planKey: String? = null, entry: AppState.Entry? = null) {
    val live = remember(entry) {
        entry?.live ?: LiveSession(buildExercises(app, content, kind), retryMistakes = kind !is SessionKind.LevelTest)
            .also { entry?.live = it }
    }
    val session = live.session
    val haptics = LocalHapticFeedback.current

    val finished = live.result
    if (finished != null) {
        ResultView(app, kind, finished)
        return
    }
    if (live.exercises.isEmpty()) {
        Column(Modifier.fillMaxSize()) {
            TopBar(app, "Practice")
            PandaSays(Mood.THINK, "Nothing to practise yet. Finish a lesson first.", Modifier.padding(16.dp))
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).clickable { app.back() }, contentAlignment = Alignment.Center) {
                Text("✕", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Reading the step keeps the bar in step with the session.
            val target = if (live.step >= 0) session.progress else 0f
            val shown by animateFloatAsState(target, tween(350), label = "progress")
            Meter(shown, Jade, Modifier.weight(1f).padding(horizontal = 8.dp), height = 16.dp)
            if (live.combo >= 2) Text("🔥 ${live.combo}", fontWeight = FontWeight.ExtraBold, color = Tangerine,
                modifier = Modifier.padding(end = 6.dp))
            PinyinSwitch(app)
        }
        val exercise = session.current
        val onDone: (Boolean) -> Unit = { correct ->
            session.submit(correct)
            live.step++
            if (session.isFinished) {
                val r = session.result()
                var p = app.progress.afterSession(r, app.today(), (kind as? SessionKind.LessonRun)?.lesson?.id)
                if (kind is SessionKind.LevelTest) p = p.withTestScore(kind.level, r.percent)
                if (planKey != null) p = p.withPlanTask(planKey)
                app.save(p)
                live.result = r
            }
        }
        // Called when an answer is judged, before the learner taps Continue.
        val onJudged: (Boolean) -> Unit = { correct ->
            live.combo = if (correct) live.combo + 1 else 0
            haptics.performHapticFeedback(if (correct) HapticFeedbackType.TextHandleMove else HapticFeedbackType.LongPress)
        }
        key(live.step) {
            when (exercise) {
                is Teach -> TeachView(app, content, exercise) { onDone(true) }
                is TeachGrammar -> TeachGrammarView(app, content, exercise) { onDone(true) }
                is Choice -> ChoiceView(app, content, exercise, onJudged, onDone)
                is Match -> MatchView(app, exercise, onJudged, onDone)
                is Arrange -> ArrangeView(app, content, exercise, onJudged, onDone)
                is Cloze -> ClozeView(app, content, exercise, onJudged, onDone)
                is SentenceChoice -> SentenceChoiceView(app, content, exercise, onJudged, onDone)
                null -> Unit
            }
        }
    }
}

/** Small switch in the lesson bar: pinyin above the characters on or off. */
@Composable
fun PinyinSwitch(app: AppState) {
    val on = app.showPinyin
    Surface(
        color = if (on) Sky else MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(50),
        modifier = Modifier.clip(RoundedCornerShape(50)).clickable { app.togglePinyin() },
    ) {
        Text(if (on) "pīn ✓" else "pīn", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp,
            color = if (on) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
    }
}

private fun buildExercises(app: AppState, content: Content, kind: SessionKind): List<Exercise> {
    val builder = ExerciseBuilder(content)
    return when (kind) {
        is SessionKind.LessonRun -> builder.lesson(kind.lesson)
        is SessionKind.LevelTest -> builder.levelTest(kind.level)
        is SessionKind.GrammarRun -> builder.grammarPractice(kind.point)
        is SessionKind.SentenceDrill -> builder.sentenceDrill(kind.level)
        is SessionKind.PhraseDrill -> content.phraseBook(kind.bookId)?.topics?.getOrNull(kind.topic)
            ?.let { builder.phraseDrill(it) }.orEmpty()
        SessionKind.Practice -> {
            val due = app.progress.dueWordIds(app.today())
            val ids = due.ifEmpty { app.progress.cards.keys.shuffled() }.take(12)
            val words = ids.mapNotNull { content.word(it) }
            if (words.isEmpty()) emptyList() else builder.practice(words)
        }
    }
}

private val Praise = listOf("太棒了! Great!", "很好! Nice!", "对了! Correct!", "真厉害! Amazing!", "不错! Well done!")

/** Shared layout: scrolling exercise on top, feedback and the main button at the bottom. */
@Composable
private fun ExerciseFrame(
    instruction: String,
    canCheck: Boolean,
    verdict: Boolean?,
    answerText: String,
    onCheck: () -> Unit,
    onContinue: () -> Unit,
    showCheck: Boolean = true,
    body: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Text(instruction, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(vertical = 12.dp))
            body()
            Spacer(Modifier.height(12.dp))
        }
        if (verdict == null) {
            if (showCheck) BigButton("Check", Modifier.padding(16.dp), enabled = canCheck, onClick = onCheck)
        } else {
            val color = if (verdict) Good else Bad
            val praise = remember { Praise.random() }
            Surface(color = color.copy(alpha = 0.14f), shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Panda(if (verdict) Mood.CHEER else Mood.SAD, size = 56.dp)
                        Column(Modifier.padding(start = 10.dp)) {
                            Text(if (verdict) praise else "Not quite. 再试试!", color = color,
                                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                            if (answerText.isNotEmpty()) Text(answerText, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                    BigButton("Continue", Modifier.padding(top = 12.dp), color = color, onClick = onContinue)
                }
            }
        }
    }
}

@Composable
private fun TeachView(app: AppState, content: Content, teach: Teach, onNext: () -> Unit) {
    val word = teach.word
    LaunchedEffect(word) { app.speaker.speak(word.hanzi) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Pill("✨ NEW WORD", Gold, Color(0xFF4A3500))
            Spacer(Modifier.height(8.dp))
            WordCard(app, content, word, teach.examples, teach.breakdown)
        }
        BigButton("Got it", Modifier.padding(16.dp), onClick = onNext)
    }
}

/** Shows a grammar formula as building blocks, with its explanation and examples, before it is practised. */
@Composable
private fun TeachGrammarView(app: AppState, content: Content, teach: TeachGrammar, onNext: () -> Unit) {
    val point = teach.point
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Pill("🧩 NEW GRAMMAR", Grape, Color.White)
            Text(content.annotate(point.title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(top = 10.dp))
            FormulaBlocks(app, content, point)
            Text(point.explanation, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp), fontSize = 17.sp, lineHeight = 25.sp)
            SectionTitle("Examples")
            for (example in teach.examples) SentenceRow(app, example) { point.isKeyword(it) }
            Spacer(Modifier.height(16.dp))
        }
        BigButton("Got it", Modifier.padding(16.dp), color = Grape, onClick = onNext)
    }
}

/** Everything about one word: sound, meaning, examples, its characters, and links to explore it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordCard(
    app: AppState,
    content: Content,
    word: Word,
    examples: List<Sentence> = content.examplesFor(word),
    breakdown: List<Word> = content.characterBreakdown(word),
) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(word.hanzi, fontSize = if (word.hanzi.length > 3) 56.sp else 80.sp, fontWeight = FontWeight.Medium)
        Text(word.pinyin, fontSize = 26.sp, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
        Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SpeakButton(app, word.hanzi)
            SpeakButton(app, word.hanzi, slow = true)
        }
        Text(word.definitions.take(3).joinToString("; "), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (word.traditional != word.hanzi) {
            Text("Traditional: ${word.traditional}", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
    }
    if (examples.isNotEmpty()) {
        SectionTitle("Examples")
        for (s in examples) SentenceRow(app, s) { it == word.hanzi }
    }
    if (breakdown.isNotEmpty()) {
        SectionTitle("Character by character")
        for (part in breakdown) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { app.speaker.speak(part.hanzi) }
                .padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(part.hanzi, fontSize = 28.sp, modifier = Modifier.width(48.dp))
                Column {
                    Text(part.pinyin, color = MaterialTheme.colorScheme.secondary)
                    Text(part.gloss, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
    val characters = word.hanzi.map { it.toString() }.distinct()
    val webs = characters.filter { content.wordWeb(it).size >= 2 }.take(3)
    val writable = characters.filter { app.strokes?.has(it) == true }.take(3)
    val total = content.sentencesWith(word.hanzi).size
    if (webs.isNotEmpty() || writable.isNotEmpty() || total > examples.size) {
        SectionTitle("Explore")
        FlowRow(horizontalArrangement = SpacedSmall, verticalArrangement = SpacedSmall) {
            for (c in webs) {
                Pill("🕸 Word map $c", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer) {
                    app.open(Screen.Web(c))
                }
            }
            for (c in writable) {
                Pill("✍ Write $c", Coral.copy(alpha = 0.16f), Coral.darker()) { app.open(Screen.Write(c)) }
            }
            if (total > examples.size) {
                Pill("🗺 All $total sentences", MaterialTheme.colorScheme.secondaryContainer,
                    MaterialTheme.colorScheme.onSecondaryContainer) {
                    app.open(Screen.SentenceMap(word.level.coerceIn(1, Content.LEVEL_COUNT), word.hanzi))
                }
            }
        }
    }
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun ChoiceView(app: AppState, content: Content, choice: Choice, onJudged: (Boolean) -> Unit, onDone: (Boolean) -> Unit) {
    var selected by remember { mutableIntStateOf(-1) }
    var verdict by remember { mutableStateOf<Boolean?>(null) }
    if (choice.kind == ChoiceKind.LISTEN) LaunchedEffect(choice) { app.speaker.speak(choice.word.hanzi) }
    val word = choice.word
    ExerciseFrame(
        instruction = choice.instruction,
        canCheck = selected >= 0,
        verdict = verdict,
        answerText = "${word.hanzi}  ${word.pinyin}  —  ${word.gloss}",
        onCheck = {
            verdict = selected == choice.correctIndex
            onJudged(verdict == true)
            app.speaker.speak(word.hanzi)
        },
        onContinue = { onDone(verdict == true) },
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            when (choice.kind) {
                ChoiceKind.LISTEN -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        SpeakButton(app, word.hanzi, big = true)
                        SpeakButton(app, word.hanzi, slow = true)
                    }
                    VoiceNotice(app)
                }
                ChoiceKind.HANZI -> Text(choice.prompt, fontSize = 26.sp, textAlign = TextAlign.Center)
                ChoiceKind.MEANING -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Ruby(choice.prompt, word.pinyin.takeIf { app.showPinyin }, 64)
                    SpeakButton(app, word.hanzi)
                }
                // The question is the pronunciation, so no pinyin on the prompt.
                ChoiceKind.PINYIN -> Text(choice.prompt, fontSize = 64.sp)
            }
        }
        val characters = choice.kind == ChoiceKind.HANZI || choice.kind == ChoiceKind.LISTEN
        // When listening, pinyin on the options would give the answer away until it is checked.
        val pinyinOnOptions = characters && app.showPinyin && (choice.kind == ChoiceKind.HANZI || verdict != null)
        choice.options.forEachIndexed { i, option ->
            val sub = if (!pinyinOnOptions) null else if (i == choice.correctIndex) word.pinyin else content.pinyinFor(option).replace(" ", "")
            OptionTile(option, if (characters) 28 else 18, selected = i == selected, sub = sub,
                state = optionState(verdict, i == choice.correctIndex, i == selected), enabled = verdict == null) { selected = i }
        }
    }
}

/** null = not judged yet, true = show as right, false = show as the wrong pick. */
private fun optionState(verdict: Boolean?, isAnswer: Boolean, isSelected: Boolean): Boolean? = when {
    verdict == null -> null
    isAnswer -> true
    isSelected -> false
    else -> null
}

/** One answer option: a raised tile that turns green or red once judged. [sub] is pinyin shown above it. */
@Composable
private fun OptionTile(text: String, size: Int, selected: Boolean, state: Boolean?, enabled: Boolean, sub: String? = null, onClick: () -> Unit) {
    val accent = when (state) {
        true -> Good
        false -> Bad
        null -> if (selected) Sky else MaterialTheme.colorScheme.outlineVariant
    }
    val face = if (selected || state != null) lerp(MaterialTheme.colorScheme.surface, accent, 0.14f) else MaterialTheme.colorScheme.surface
    Raised(face, Modifier.fillMaxWidth().padding(vertical = 5.dp), edge = 4.dp, edgeColor = accent, enabled = enabled, onClick = onClick) {
        Column(Modifier.fillMaxWidth().border(2.dp, accent, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = if (sub.isNullOrEmpty()) 14.dp else 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            if (!sub.isNullOrEmpty()) Text(sub, fontSize = 13.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.secondary)
            Text(text, fontSize = size.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Medium)
        }
    }
}

/** Fill the gap in a sentence. */
@Composable
private fun ClozeView(app: AppState, content: Content, cloze: Cloze, onJudged: (Boolean) -> Unit, onDone: (Boolean) -> Unit) {
    var selected by remember { mutableIntStateOf(-1) }
    var verdict by remember { mutableStateOf<Boolean?>(null) }
    val sentence = cloze.sentence
    ExerciseFrame(
        instruction = "Fill in the gap",
        canCheck = selected >= 0,
        verdict = verdict,
        answerText = "${sentence.text}\n${sentence.pinyin}",
        onCheck = {
            verdict = selected == cloze.correctIndex
            onJudged(verdict == true)
            app.speaker.speak(sentence.text)
        },
        onContinue = { onDone(verdict == true) },
    ) {
        val gapColor = if (verdict == null) Sky else if (verdict == true) Good else Bad
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                RubySentence(app, content, sentence, 28, blank = cloze.blank, accent = gapColor,
                    blankText = if (verdict != null) cloze.answer else if (selected >= 0) cloze.options[selected] else null)
                Text(sentence.english, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }
        }
        Spacer(Modifier.height(14.dp))
        for (row in cloze.options.indices.chunked(2)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (i in row) Box(Modifier.weight(1f)) {
                    OptionTile(cloze.options[i], 24, selected = i == selected,
                        sub = if (app.showPinyin) content.pinyinFor(cloze.options[i]).replace(" ", "") else null,
                        state = optionState(verdict, i == cloze.correctIndex, i == selected), enabled = verdict == null) { selected = i }
                }
            }
        }
    }
}

/** Read or hear a sentence and pick its meaning, or what was said. */
@Composable
private fun SentenceChoiceView(app: AppState, content: Content, item: SentenceChoice, onJudged: (Boolean) -> Unit, onDone: (Boolean) -> Unit) {
    var selected by remember { mutableIntStateOf(-1) }
    var verdict by remember { mutableStateOf<Boolean?>(null) }
    val sentence = item.sentence
    val listening = item.kind == SentenceKind.LISTEN
    if (listening) LaunchedEffect(item) { app.speaker.speak(sentence.text) }
    ExerciseFrame(
        instruction = if (listening) "Which sentence do you hear?" else "What does this sentence mean?",
        canCheck = selected >= 0,
        verdict = verdict,
        answerText = if (listening) "${sentence.pinyin}\n${sentence.english}" else sentence.pinyin,
        onCheck = {
            verdict = selected == item.correctIndex
            onJudged(verdict == true)
            if (!listening) app.speaker.speak(sentence.text)
        },
        onContinue = { onDone(verdict == true) },
    ) {
        if (listening) {
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically) {
                SpeakButton(app, sentence.text, big = true)
                SpeakButton(app, sentence.text, slow = true)
            }
            VoiceNotice(app)
        } else {
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    RubySentence(app, content, sentence, 28, Modifier.weight(1f))
                    SpeakButton(app, sentence.text)
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        item.options.forEachIndexed { i, option ->
            OptionTile(option, if (listening) 22 else 17, selected = i == selected,
                state = optionState(verdict, i == item.correctIndex, i == selected), enabled = verdict == null) { selected = i }
        }
    }
}

@Composable
private fun MatchView(app: AppState, match: Match, onJudged: (Boolean) -> Unit, onDone: (Boolean) -> Unit) {
    val solved = remember { mutableStateListOf<Int>() }
    var left by remember { mutableIntStateOf(-1) }
    var right by remember { mutableIntStateOf(-1) }
    var mistakes by remember { mutableIntStateOf(0) }
    val complete = solved.size == match.words.size

    fun tryPair() {
        if (left < 0 || right < 0) return
        if (left == right) {
            solved.add(left)
            app.speaker.speak(match.words[left].hanzi)
            if (solved.size == match.words.size) onJudged(mistakes == 0)
        } else {
            mistakes++
        }
        left = -1
        right = -1
    }

    ExerciseFrame(
        instruction = "Match the pairs",
        canCheck = false,
        showCheck = false,
        verdict = if (complete) mistakes == 0 else null,
        answerText = if (mistakes == 0) "" else "You needed $mistakes extra ${if (mistakes == 1) "try" else "tries"}.",
        onCheck = {},
        onContinue = { onDone(mistakes == 0) },
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                match.words.forEachIndexed { i, w ->
                    MatchTile(w.hanzi, 26, selected = left == i, done = i in solved, sub = w.pinyin.takeIf { app.showPinyin }) {
                        left = i; tryPair()
                    }
                }
            }
            Column(Modifier.weight(1f)) {
                for (i in match.meaningOrder) {
                    MatchTile(match.words[i].gloss, 15, selected = right == i, done = i in solved) { right = i; tryPair() }
                }
            }
        }
    }
}

@Composable
private fun MatchTile(text: String, size: Int, selected: Boolean, done: Boolean, sub: String? = null, onClick: () -> Unit) {
    val accent = if (done) Good.copy(alpha = 0.45f) else if (selected) Sky else MaterialTheme.colorScheme.outlineVariant
    val face = if (done || selected) lerp(MaterialTheme.colorScheme.surface, accent, 0.16f) else MaterialTheme.colorScheme.surface
    Raised(face, Modifier.fillMaxWidth().padding(vertical = 5.dp).height(80.dp), edge = 4.dp, edgeColor = accent,
        enabled = !done, onClick = onClick) {
        Column(Modifier.fillMaxSize().border(2.dp, accent, RoundedCornerShape(16.dp)).padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            if (!sub.isNullOrEmpty()) Text(sub, fontSize = 13.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.secondary, maxLines = 1)
            Text(text, fontSize = size.sp, textAlign = TextAlign.Center, maxLines = 3, lineHeight = (size * 1.2f).sp,
                color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ArrangeView(app: AppState, content: Content, arrange: Arrange, onJudged: (Boolean) -> Unit, onDone: (Boolean) -> Unit) {
    // Indices into the word bank, in the order the learner tapped them.
    val chosen = remember { mutableStateListOf<Int>() }
    var verdict by remember { mutableStateOf<Boolean?>(null) }
    val sentence = arrange.sentence
    // Pinyin for each tile: the sentence's own reading for its words, the dictionary's for the spare tiles.
    val tilePinyin = remember(arrange) {
        val own = sentence.tokens.zip(content.tokenPinyin(sentence)).toMap()
        arrange.bank.map { own[it]?.takeIf { p -> p.isNotEmpty() } ?: content.pinyinFor(it).replace(" ", "") }
    }
    ExerciseFrame(
        instruction = "Build this sentence in Chinese",
        canCheck = chosen.isNotEmpty(),
        verdict = verdict,
        answerText = "${sentence.text}\n${sentence.pinyin}",
        onCheck = {
            verdict = arrange.isCorrect(chosen.map { arrange.bank[it] })
            onJudged(verdict == true)
            app.speaker.speak(sentence.text)
        },
        onContinue = { onDone(verdict == true) },
    ) {
        Text(sentence.english, fontSize = 22.sp, modifier = Modifier.padding(bottom = 16.dp))
        Surface(shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 130.dp)) {
            FlowRow(Modifier.padding(10.dp), horizontalArrangement = SpacedSmall, verticalArrangement = SpacedSmall) {
                for (i in chosen.toList()) {
                    Tile(arrange.bank[i], tilePinyin[i].takeIf { app.showPinyin }, enabled = verdict == null, filled = true) { chosen.remove(i) }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = SpacedSmall) {
            arrange.bank.forEachIndexed { i, token ->
                val used = i in chosen
                Tile(token, tilePinyin[i].takeIf { app.showPinyin }, enabled = !used && verdict == null, filled = false, faded = used) {
                    chosen.add(i)
                    app.speaker.speak(token)
                }
            }
        }
    }
}

@Composable
private fun Tile(text: String, pinyin: String?, enabled: Boolean, filled: Boolean, faded: Boolean = false, onClick: () -> Unit) {
    if (faded) {
        // Leave a hollow where the tile was, so the bank does not jump around.
        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Box(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) { Ruby(text, pinyin, 24, color = Color.Transparent, pinyinColor = Color.Transparent) }
        }
        return
    }
    val accent = if (filled) Sky else MaterialTheme.colorScheme.outlineVariant
    Raised(if (filled) lerp(MaterialTheme.colorScheme.surface, Sky, 0.14f) else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp), edge = 4.dp, edgeColor = accent, enabled = enabled, onClick = onClick) {
        Box(Modifier.border(2.dp, accent, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 8.dp)) {
            Ruby(text, pinyin, 24)
        }
    }
}

@Composable
private fun ResultView(app: AppState, kind: SessionKind, result: SessionResult) {
    val isTest = kind is SessionKind.LevelTest
    val passed = result.percent >= Progress.TEST_PASS_PERCENT
    val happy = !isTest || passed
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Panda(if (happy) Mood.CHEER else Mood.THINK, size = 150.dp)
            Text(
                when {
                    isTest && passed -> "Test passed! 恭喜!"
                    isTest -> "Keep going! 加油!"
                    kind is SessionKind.LessonRun && kind.lesson.isGrammar -> "Grammar learned! 学会了!"
                    kind is SessionKind.LessonRun -> if (result.percent == 100) "Perfect lesson! 完美!" else "Lesson complete!"
                    else -> "Practice complete!"
                },
                style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = if (happy) Gold.darker(0.15f) else Sky,
                textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp),
            )
            Row(Modifier.padding(vertical = 24.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("⚡ +${result.xp}", "XP earned", Gold)
                Stat("🎯 ${result.percent}%", "accuracy", Good)
                Stat("🔥 ${app.progress.currentStreak(app.today())}", "day streak", Tangerine)
            }
            if (isTest) {
                Text(
                    if (passed) "Every lesson in this level is now unlocked."
                    else "You need ${Progress.TEST_PASS_PERCENT}% to pass. Work through more lessons and try again.",
                    textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 16.dp),
                )
            }
            BigButton("Continue") { app.back() }
        }
        if (happy) Confetti()
    }
}

@Composable
private fun Stat(value: String, label: String, color: Color) {
    Raised(color, Modifier.width(102.dp), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(3.dp).clip(RoundedCornerShape(15.dp)).background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = color.darker(0.15f))
            Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
        }
    }
}
