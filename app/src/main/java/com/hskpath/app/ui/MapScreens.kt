package com.hskpath.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hskpath.app.AppState
import com.hskpath.app.Screen
import com.hskpath.core.Content
import com.hskpath.core.Stroke
import com.hskpath.core.StrokeBook

/**
 * Sentence map: pick a key word of the level and see the sentences that grow
 * from it, shortest first, each on its own coloured branch.
 */
@Composable
fun SentenceMapScreen(app: AppState, content: Content, level: Int, start: String?) {
    val keyWords = remember(level) {
        content.wordsOf(level).map { it to content.sentencesWith(it.hanzi).size }
            .filter { it.second >= 3 }.sortedByDescending { it.second }.take(80).map { it.first }
    }
    var selected by rememberSaveable { mutableStateOf(start ?: keyWords.firstOrNull()?.hanzi ?: "") }
    val word = content.lookup(selected)
    val sentences = remember(selected) {
        content.sentencesWith(selected).sortedBy { (if (it.curated) 0 else 1000) + it.level * 100 + it.words.size }.take(60)
    }
    Column(Modifier.fillMaxSize()) {
        TopBar(app, "Sentence map · HSK $level")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = SpacedSmall) {
            for (w in keyWords) {
                val on = w.hanzi == selected
                Pill(w.hanzi, if (on) Sky else MaterialTheme.colorScheme.surfaceVariant,
                    if (on) Color.White else MaterialTheme.colorScheme.onSurfaceVariant) { selected = w.hanzi }
            }
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            item {
                Raised(Sky, Modifier.fillMaxWidth().padding(top = 8.dp), shape = RoundedCornerShape(22.dp)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(selected, fontSize = 40.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            if (word != null) {
                                Text(word.pinyin, color = Color.White, fontWeight = FontWeight.Bold)
                                Text(word.gloss, color = Color.White.copy(alpha = 0.9f))
                            }
                            Text("${content.sentencesWith(selected).size} sentences", color = Color.White.copy(alpha = 0.9f),
                                style = MaterialTheme.typography.labelMedium)
                        }
                        SpeakButton(app, selected)
                    }
                }
            }
            itemsIndexed(sentences) { i, sentence ->
                val color = Rainbow[i % Rainbow.size]
                Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
                    // The trunk of the map with a branch to each sentence.
                    Canvas(Modifier.width(34.dp).fillMaxSize()) {
                        val x = 12.dp.toPx()
                        drawLine(Sky.copy(alpha = 0.5f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 4.dp.toPx())
                        drawLine(color, Offset(x, size.height / 2), Offset(size.width, size.height / 2),
                            strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
                        drawCircle(color, 6.dp.toPx(), Offset(x, size.height / 2))
                    }
                    Card(Modifier.weight(1f).padding(vertical = 5.dp)) {
                        Box(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                            SentenceRow(app, sentence, color.darker(0.1f)) { it == selected }
                        }
                    }
                }
            }
            item {
                if (sentences.isEmpty()) PandaSays(Mood.THINK, "No sentences use this word on its own yet.", Modifier.padding(top = 12.dp))
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** Pick a character of the level to practise writing. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WriteListScreen(app: AppState, content: Content, level: Int) {
    val characters = remember(level) {
        val earlier = (1 until level).flatMap { content.wordsOf(it) }.flatMap { w -> w.hanzi.map { it.toString() } }.toSet()
        content.wordsOf(level).flatMap { w -> w.hanzi.map { it.toString() } }.distinct().filter { it !in earlier }
    }
    Column(Modifier.fillMaxSize()) {
        TopBar(app, "Writing · HSK $level")
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            PandaSays(Mood.HAPPY, "${characters.size} new characters in HSK $level. Tap one to watch its stroke order, then trace it yourself.")
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = SpacedSmall, verticalArrangement = SpacedSmall) {
                characters.forEachIndexed { i, c ->
                    val color = Rainbow[i % Rainbow.size]
                    Raised(MaterialTheme.colorScheme.surface, Modifier.size(width = 64.dp, height = 76.dp), edge = 4.dp,
                        edgeColor = color, onClick = { app.open(Screen.Write(c)) }) {
                        Ruby(c, content.pinyinFor(c).takeIf { app.showPinyin }, 30)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Watch a character being written, then trace it stroke by stroke. */
@Composable
fun WriteScreen(app: AppState, content: Content, character: String) {
    val strokes = remember(character) { app.strokes?.strokes(character) }
    val word = content.lookup(character)
    // Strokes finished so far, the stroke being animated (0..1), and misses on the current stroke.
    var done by remember { mutableIntStateOf(0) }
    var misses by remember { mutableIntStateOf(0) }
    var replay by remember { mutableIntStateOf(1) }
    var demo by remember { mutableStateOf(true) }
    val pen = remember { Animatable(0f) }
    val trail = remember { mutableStateListOf<Offset>() }
    var flash by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopBar(app, "Write $character")
        if (strokes == null) {
            PandaSays(Mood.SAD, "Stroke order is not available for this character.", Modifier.padding(16.dp))
            return@Column
        }
        // Demo: draw every stroke in order, then hand over to the learner.
        LaunchedEffect(replay) {
            if (!demo) return@LaunchedEffect
            done = 0
            for (i in strokes.indices) {
                pen.snapTo(0f)
                pen.animateTo(1f, tween((250 + strokes[i].length * 0.9f).toInt(), easing = LinearEasing))
                done = i + 1
            }
            pen.snapTo(0f)
            kotlinx.coroutines.delay(500)
            demo = false
            done = 0
            misses = 0
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            if (word != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(word.pinyin, fontSize = 24.sp, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                    SpeakButton(app, character)
                }
                Text(word.gloss, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val ink = MaterialTheme.colorScheme.onSurface
            val ghost = MaterialTheme.colorScheme.outlineVariant
            val gridColor = Coral.copy(alpha = 0.35f)
            Box(Modifier.padding(top = 12.dp).fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .pointerInput(character, demo) {
                    if (demo) return@pointerInput
                    detectDragGestures(
                        onDragStart = { trail.clear(); trail.add(it) },
                        onDrag = { change, _ -> trail.add(change.position) },
                        onDragEnd = {
                            val scale = Stroke.GRID / size.width
                            val drawn = trail.map { it.x * scale to it.y * scale }
                            if (done < strokes.size && StrokeBook.matches(drawn, strokes[done])) {
                                done++
                                misses = 0
                            } else if (done < strokes.size) {
                                misses++
                                flash = true
                            }
                            trail.clear()
                        },
                    )
                }) {
                Canvas(Modifier.fillMaxSize()) {
                    val s = size.width / Stroke.GRID
                    val dash = PathEffect.dashPathEffect(floatArrayOf(14f, 14f))
                    // Practice grid.
                    drawLine(gridColor, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2f, pathEffect = dash)
                    drawLine(gridColor, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 2f, pathEffect = dash)
                    drawLine(gridColor, Offset(0f, 0f), Offset(size.width, size.height), 2f, pathEffect = dash)
                    drawLine(gridColor, Offset(size.width, 0f), Offset(0f, size.height), 2f, pathEffect = dash)
                    val width = 46f * s
                    strokes.forEachIndexed { i, stroke ->
                        when {
                            i < done -> drawStroke(stroke, 1f, ink, width, s)
                            else -> drawStroke(stroke, 1f, ghost, width, s)
                        }
                    }
                    if (demo && done < strokes.size) drawStroke(strokes[done], pen.value, Coral, width, s)
                    // After two misses, show where the stroke starts and which way it goes.
                    if (!demo && done < strokes.size && misses >= 2) {
                        drawStroke(strokes[done], 1f, Gold.copy(alpha = 0.8f), width * 0.5f, s)
                        drawCircle(Coral, width * 0.45f, Offset(strokes[done].start.first * s, strokes[done].start.second * s))
                    }
                    if (trail.size > 1) for (k in 1 until trail.size) {
                        drawLine(if (flash) Bad else Sky, trail[k - 1], trail[k], strokeWidth = width * 0.8f, cap = StrokeCap.Round)
                    }
                }
            }
            LaunchedEffect(flash) { if (flash) { kotlinx.coroutines.delay(250); flash = false } }
            val finished = !demo && done == strokes.size
            Text(
                when {
                    demo -> "Watch the stroke order…"
                    finished -> "写得好! All ${strokes.size} strokes done."
                    misses >= 2 -> "Follow the gold line from the red dot."
                    misses == 1 -> "Not that one. Try stroke ${done + 1} again."
                    else -> "Your turn: draw stroke ${done + 1} of ${strokes.size}"
                },
                fontWeight = FontWeight.Bold, color = if (finished) Good else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 12.dp),
            )
            if (finished) Panda(Mood.CHEER, size = 72.dp)
            Spacer(Modifier.height(12.dp))
        }
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigButton("▶ Watch", Modifier.weight(1f), color = Sky) { demo = true; replay++ }
            BigButton("↺ Retry", Modifier.weight(1f), color = Coral, enabled = !demo) { done = 0; misses = 0 }
        }
    }
}

/** Draws the first [fraction] of a stroke's centre line as a round brush line. */
private fun DrawScope.drawStroke(stroke: Stroke, fraction: Float, color: Color, width: Float, scale: Float) {
    val target = stroke.length * fraction
    var travelled = 0f
    val pts = stroke.points
    if (pts.size == 1) {
        drawCircle(color, width / 2, Offset(pts[0].first * scale, pts[0].second * scale))
        return
    }
    for (i in 1 until pts.size) {
        val a = pts[i - 1]
        val b = pts[i]
        val seg = Stroke.dist(a, b)
        if (travelled >= target) break
        val part = ((target - travelled) / seg).coerceAtMost(1f)
        drawLine(color, Offset(a.first * scale, a.second * scale),
            Offset((a.first + (b.first - a.first) * part) * scale, (a.second + (b.second - a.second) * part) * scale),
            strokeWidth = width, cap = StrokeCap.Round)
        travelled += seg
    }
}
