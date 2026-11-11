package com.hskpath.app.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hskpath.app.AppState
import com.hskpath.app.Screen
import com.hskpath.app.SessionKind
import com.hskpath.core.Arrange
import com.hskpath.core.Choice
import com.hskpath.core.ChoiceKind
import com.hskpath.core.Content
import com.hskpath.core.Exercise
import com.hskpath.core.ExerciseBuilder
import com.hskpath.core.Match
import com.hskpath.core.Progress
import com.hskpath.core.Session
import com.hskpath.core.SessionResult
import com.hskpath.core.Teach
import com.hskpath.core.Word

/** Plays one lesson, practice round, grammar drill or level test from start to results. */
@Composable
fun SessionScreen(app: AppState, content: Content, kind: SessionKind) {
    val exercises = remember { buildExercises(app, content, kind) }
    val session = remember { Session(exercises, retryMistakes = kind !is SessionKind.LevelTest) }
    var step by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<SessionResult?>(null) }

    val finished = result
    if (finished != null) {
        ResultView(app, kind, finished)
        return
    }
    if (exercises.isEmpty()) {
        Column(Modifier.fillMaxSize()) {
            TopBar(app, "Practice")
            Text("Nothing to practise yet. Finish a lesson first.", Modifier.padding(16.dp))
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).clickable { app.back() }, contentAlignment = Alignment.Center) {
                Text("✕", fontSize = 20.sp)
            }
            // Reading `step` keeps the bar in step with the session.
            val shown = if (step >= 0) session.progress else 0f
            LinearProgressIndicator(
                progress = { shown },
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp).height(12.dp).clip(RoundedCornerShape(6.dp)),
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        val exercise = session.current
        val onDone: (Boolean) -> Unit = { correct ->
            session.submit(correct)
            step++
            if (session.isFinished) {
                val r = session.result()
                var p = app.progress.afterSession(r, app.today(), (kind as? SessionKind.LessonRun)?.lesson?.id)
                if (kind is SessionKind.LevelTest) p = p.withTestScore(kind.level, r.percent)
                app.save(p)
                result = r
            }
        }
        key(step) {
            when (exercise) {
                is Teach -> TeachView(app, content, exercise) { onDone(true) }
                is Choice -> ChoiceView(app, exercise, onDone)
                is Match -> MatchView(app, exercise, onDone)
                is Arrange -> ArrangeView(app, exercise, onDone)
                null -> Unit
            }
        }
    }
}

private fun buildExercises(app: AppState, content: Content, kind: SessionKind): List<Exercise> {
    val builder = ExerciseBuilder(content)
    return when (kind) {
        is SessionKind.LessonRun -> builder.lesson(kind.lesson)
        is SessionKind.LevelTest -> builder.levelTest(kind.level)
        is SessionKind.GrammarRun -> builder.grammarPractice(kind.point)
        SessionKind.Practice -> {
            val due = app.progress.dueWordIds(app.today())
            val ids = due.ifEmpty { app.progress.cards.keys.shuffled() }.take(12)
            val words = ids.mapNotNull { content.word(it) }
            if (words.isEmpty()) emptyList() else builder.practice(words)
        }
    }
}

/** Shared layout: scrolling exercise on top, feedback and the main button at the bottom. */
@Composable
private fun ExerciseFrame(
    instruction: String,
    canCheck: Boolean,
    verdict: Boolean?,
    answerText: String,
    onCheck: () -> Unit,
    onContinue: () -> Unit,
    body: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Text(instruction, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 12.dp))
            body()
        }
        if (verdict == null) {
            Button(onClick = onCheck, enabled = canCheck, modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp)) {
                Text("Check", fontSize = 18.sp)
            }
        } else {
            val color = if (verdict) Good else Bad
            Surface(color = color.copy(alpha = 0.14f), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(if (verdict) "Correct!" else "Not quite", color = color,
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (answerText.isNotEmpty()) Text(answerText, modifier = Modifier.padding(top = 4.dp))
                    Button(onClick = onContinue, colors = ButtonDefaults.buttonColors(containerColor = color),
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(52.dp)) {
                        Text("Continue", fontSize = 18.sp, color = Color.White)
                    }
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
            Text("New word", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(vertical = 12.dp))
            WordCard(app, content, word, teach.examples, teach.breakdown)
        }
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp)) {
            Text("Got it", fontSize = 18.sp)
        }
    }
}

/** Everything about one word: sound, meaning, its characters, its word web and examples. */
@Composable
fun WordCard(
    app: AppState,
    content: Content,
    word: Word,
    examples: List<com.hskpath.core.Sentence> = content.examplesFor(word),
    breakdown: List<Word> = content.characterBreakdown(word),
) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(word.hanzi, fontSize = if (word.hanzi.length > 3) 56.sp else 80.sp, fontWeight = FontWeight.Medium)
        Text(word.pinyin, fontSize = 26.sp, color = MaterialTheme.colorScheme.secondary)
        Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SpeakButton(app, word.hanzi)
            SpeakButton(app, word.hanzi, slow = true)
        }
        Text(word.definitions.joinToString("; "), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (word.traditional != word.hanzi) {
            Text("Traditional: ${word.traditional}", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
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
    val webCharacters = word.hanzi.map { it.toString() }.distinct().filter { content.wordWeb(it).size >= 2 }
    if (webCharacters.isNotEmpty()) {
        SectionTitle("Word webs")
        Row(horizontalArrangement = SpacedSmall) {
            for (c in webCharacters.take(4)) {
                Pill("🕸 $c", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer) {
                    app.open(Screen.Web(c))
                }
            }
        }
    }
    if (examples.isNotEmpty()) {
        SectionTitle("Examples")
        for (s in examples) SentenceRow(app, s) { it == word.hanzi }
    }
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun ChoiceView(app: AppState, choice: Choice, onDone: (Boolean) -> Unit) {
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
            if (choice.kind != ChoiceKind.LISTEN || verdict == true) app.speaker.speak(word.hanzi)
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
                else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(choice.prompt, fontSize = 64.sp)
                    if (choice.kind == ChoiceKind.MEANING) SpeakButton(app, word.hanzi)
                }
            }
        }
        val largeOptions = choice.kind == ChoiceKind.HANZI || choice.kind == ChoiceKind.LISTEN
        choice.options.forEachIndexed { i, option ->
            val isAnswer = i == choice.correctIndex
            val border = when {
                verdict != null && isAnswer -> Good
                verdict == false && i == selected -> Bad
                i == selected -> MaterialTheme.colorScheme.secondary
                else -> MaterialTheme.colorScheme.outlineVariant
            }
            Surface(
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(2.dp, border),
                color = if (i == selected) border.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(14.dp))
                    .clickable(enabled = verdict == null) { selected = i },
            ) {
                Text(option, fontSize = if (largeOptions) 28.sp else 18.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp))
            }
        }
    }
}

@Composable
private fun MatchView(app: AppState, match: Match, onDone: (Boolean) -> Unit) {
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
        } else {
            mistakes++
        }
        left = -1
        right = -1
    }

    ExerciseFrame(
        instruction = "Match the pairs",
        canCheck = false,
        verdict = if (complete) mistakes == 0 else null,
        answerText = if (mistakes == 0) "" else "You needed $mistakes extra ${if (mistakes == 1) "try" else "tries"}.",
        onCheck = {},
        onContinue = { onDone(mistakes == 0) },
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                match.words.forEachIndexed { i, w ->
                    MatchTile(w.hanzi, 26, selected = left == i, done = i in solved) { left = i; tryPair() }
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
private fun MatchTile(text: String, size: Int, selected: Boolean, done: Boolean, onClick: () -> Unit) {
    val border = when {
        done -> Good.copy(alpha = 0.4f)
        selected -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(2.dp, border),
        color = if (done) Good.copy(alpha = 0.10f) else if (selected) border.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).height(72.dp).clip(RoundedCornerShape(14.dp))
            .clickable(enabled = !done, onClick = onClick),
    ) {
        Box(Modifier.padding(6.dp), contentAlignment = Alignment.Center) {
            Text(text, fontSize = size.sp, textAlign = TextAlign.Center, maxLines = 3,
                color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ArrangeView(app: AppState, arrange: Arrange, onDone: (Boolean) -> Unit) {
    // Indices into the word bank, in the order the learner tapped them.
    val chosen = remember { mutableStateListOf<Int>() }
    var verdict by remember { mutableStateOf<Boolean?>(null) }
    val sentence = arrange.sentence
    ExerciseFrame(
        instruction = "Build this sentence in Chinese",
        canCheck = chosen.isNotEmpty(),
        verdict = verdict,
        answerText = "${sentence.text}\n${sentence.pinyin}",
        onCheck = {
            verdict = arrange.isCorrect(chosen.map { arrange.bank[it] })
            app.speaker.speak(sentence.text)
        },
        onContinue = { onDone(verdict == true) },
    ) {
        Text(sentence.english, fontSize = 22.sp, modifier = Modifier.padding(bottom = 16.dp))
        Surface(shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 120.dp)) {
            FlowRow(Modifier.padding(10.dp), horizontalArrangement = SpacedSmall, verticalArrangement = SpacedSmall) {
                for (i in chosen.toList()) {
                    Tile(arrange.bank[i], enabled = verdict == null, filled = true) { chosen.remove(i) }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = SpacedSmall) {
            arrange.bank.forEachIndexed { i, token ->
                val used = i in chosen
                Tile(token, enabled = !used && verdict == null, filled = false, faded = used) {
                    chosen.add(i)
                    app.speaker.speak(token)
                }
            }
        }
    }
}

@Composable
private fun Tile(text: String, enabled: Boolean, filled: Boolean, faded: Boolean = false, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant),
        color = if (filled) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(enabled = enabled, onClick = onClick),
    ) {
        Text(text, fontSize = 24.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            color = if (faded) Color.Transparent else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun ResultView(app: AppState, kind: SessionKind, result: SessionResult) {
    val isTest = kind is SessionKind.LevelTest
    val passed = result.percent >= Progress.TEST_PASS_PERCENT
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Text(if (isTest && !passed) "📘" else "🎉", fontSize = 72.sp)
        Text(
            when {
                isTest && passed -> "Test passed!"
                isTest -> "Keep going"
                kind is SessionKind.LessonRun -> "Lesson complete!"
                else -> "Practice complete!"
            },
            style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
        )
        Row(Modifier.padding(vertical = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat("⚡ +${result.xp}", "XP earned")
            Stat("${result.percent}%", "first-try accuracy")
            Stat("🔥 ${app.progress.currentStreak(app.today())}", "day streak")
        }
        if (isTest) {
            Text(
                if (passed) "Every lesson in this level is now unlocked."
                else "You need ${Progress.TEST_PASS_PERCENT}% to pass. Work through more lessons and try again.",
                textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 16.dp),
            )
        }
        Button(onClick = { app.back() }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("Continue", fontSize = 18.sp)
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp).width(84.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
        }
    }
}
