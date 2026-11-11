package com.hskpath.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hskpath.app.AppState
import com.hskpath.app.Screen
import com.hskpath.core.Content
import com.hskpath.core.Word
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val BranchColors = Rainbow

/** A character in the middle with the words it builds branching out around it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordWebScreen(app: AppState, content: Content, start: String) {
    var character by rememberSaveable { mutableStateOf(start) }
    // The picked word is kept by its characters so it survives leaving and returning.
    var pickedHanzi by rememberSaveable { mutableStateOf<String?>(null) }
    val picked: Word? = pickedHanzi?.let { content.lookup(it) }
    val web = remember(character) { content.wordWeb(character) }
    val all = remember(character) { content.wordWeb(character, 999) }
    val center = content.lookup(character)

    Column(Modifier.fillMaxSize()) {
        TopBar(app, "Word map · $character")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            BoxWithConstraints(Modifier.fillMaxWidth().padding(8.dp)) {
                val side = min(maxWidth.value, 460f)
                val nodeWidth = (side * 0.25f).dp
                val nodeHeight = 84.dp
                val radius = side * 0.375f
                val mid = side / 2f
                Box(Modifier.size(side.dp).align(Alignment.Center)) {
                    Canvas(Modifier.fillMaxSize()) {
                        val c = Offset(size.width / 2, size.height / 2)
                        val r = radius.dp.toPx()
                        web.forEachIndexed { i, _ ->
                            val a = angle(i, web.size)
                            drawLine(
                                BranchColors[i % BranchColors.size],
                                c, Offset(c.x + r * cos(a), c.y + r * sin(a)),
                                strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round,
                            )
                        }
                    }
                    web.forEachIndexed { i, word ->
                        val a = angle(i, web.size)
                        val color = BranchColors[i % BranchColors.size]
                        Box(Modifier.offset(
                            x = (mid + radius * cos(a)).dp - nodeWidth / 2,
                            y = (mid + radius * sin(a)).dp - nodeHeight / 2,
                        )) {
                            WebNode(word, character, color, nodeWidth, nodeHeight, selected = pickedHanzi == word.hanzi) {
                                pickedHanzi = word.hanzi
                                app.speaker.speak(word.hanzi)
                            }
                        }
                    }
                    val hub = (side * 0.26f).dp
                    Surface(
                        color = MaterialTheme.colorScheme.primary, shape = CircleShape, shadowElevation = 6.dp,
                        modifier = Modifier.size(hub).align(Alignment.Center).clip(CircleShape)
                            .clickable { pickedHanzi = center?.hanzi; app.speaker.speak(character) },
                    ) {
                        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                            Text(character, fontSize = 40.sp, color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold)
                            if (center != null) Text(center.pinyin, color = MaterialTheme.colorScheme.onPrimary, fontSize = 14.sp)
                        }
                    }
                }
            }
            Column(Modifier.padding(horizontal = 16.dp)) {
                if (center != null) {
                    Text("$character  ${center.pinyin}  —  ${center.gloss}", style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    if (web.isEmpty()) "No other words use this character."
                    else "${all.size} words use $character. Tap a word to hear it, then tap one of its characters to jump to that map.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
                val word = picked
                if (word != null) {
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(word.hanzi, fontSize = 32.sp)
                                    Text(word.pinyin, color = MaterialTheme.colorScheme.secondary)
                                }
                                SpeakButton(app, word.hanzi)
                            }
                            Text(word.definitions.joinToString("; "), modifier = Modifier.padding(vertical = 6.dp))
                            Text(word.levelLabel, style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = SpacedSmall, verticalArrangement = SpacedSmall) {
                                for (c in word.hanzi.map { it.toString() }.distinct().filter { it != character }) {
                                    Pill("🕸 $c", MaterialTheme.colorScheme.secondaryContainer,
                                        MaterialTheme.colorScheme.onSecondaryContainer) {
                                        character = c
                                        pickedHanzi = null
                                    }
                                }
                                Pill("Full entry", MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.onPrimaryContainer) { app.open(Screen.WordInfo(word)) }
                            }
                        }
                    }
                }
                if (all.size > web.size) {
                    SectionTitle("More words with $character")
                    for (w in all.drop(web.size).take(60)) {
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { pickedHanzi = w.hanzi; app.speaker.speak(w.hanzi) }
                            .padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(w.hanzi, fontSize = 20.sp, modifier = Modifier.width(96.dp))
                            Column(Modifier.weight(1f)) {
                                Text(w.pinyin, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodyMedium)
                                Text(w.gloss, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text(w.levelLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Box(Modifier.height(24.dp))
            }
        }
    }
}

/** Angle of branch [i] of [n], starting at the top and going clockwise. */
private fun angle(i: Int, n: Int): Float = (-PI / 2 + 2 * PI * i / n.coerceAtLeast(1)).toFloat()

@Composable
private fun WebNode(word: Word, character: String, color: Color, width: Dp, height: Dp, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (selected) 4.dp else 2.dp, color),
        shadowElevation = 2.dp,
        modifier = Modifier.width(width).height(height).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick),
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
            Text(
                buildAnnotatedString {
                    for (c in word.hanzi) {
                        if (c.toString() == character) withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold)) { append(c) }
                        else append(c)
                    }
                },
                fontSize = if (word.hanzi.length > 3) 14.sp else 19.sp, lineHeight = 22.sp, maxLines = 1,
            )
            Text(word.pinyin, fontSize = 11.sp, lineHeight = 14.sp, color = MaterialTheme.colorScheme.secondary, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            Text(word.definitions.first(), fontSize = 11.sp, lineHeight = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Pick a character of the level to open its word web. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WebListScreen(app: AppState, content: Content, level: Int) {
    val characters = remember(level) { content.webCharacters(level) }
    Column(Modifier.fillMaxSize()) {
        TopBar(app, "Word map · HSK $level")
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Text(
                "Chinese words are built from a small set of characters. Each map shows one character and the " +
                    "words that grow out of it (%,d words in all), so one character unlocks many words.".format(content.mapWordCount),
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 12.dp),
            )
            FlowRow(horizontalArrangement = SpacedSmall, verticalArrangement = SpacedSmall) {
                characters.forEachIndexed { i, c ->
                    val color = BranchColors[i % BranchColors.size]
                    Raised(MaterialTheme.colorScheme.surface, Modifier.size(width = 76.dp, height = 80.dp), edge = 4.dp,
                        edgeColor = color, onClick = { app.open(Screen.Web(c)) }) {
                        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                            Text(c, fontSize = 30.sp)
                            Text("${content.wordWeb(c, 99).size} words", fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Box(Modifier.height(24.dp))
        }
    }
}
