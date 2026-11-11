package com.hskpath.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hskpath.app.AppState
import com.hskpath.app.Screen
import com.hskpath.app.SessionKind
import com.hskpath.core.Content
import com.hskpath.core.Curriculum
import com.hskpath.core.PlanDay
import com.hskpath.core.PlanTask
import com.hskpath.core.TrainingPlan

/** The day-by-day plan through one HSK level, with today's tasks ready to start. */
@Composable
fun PlanScreen(app: AppState, content: Content, level: Int) {
    val days = remember(level) { TrainingPlan.days(level, content) }
    val units = remember(level) { Curriculum.units(level, content) }
    val progress = app.progress
    val current = TrainingPlan.today(days, progress)
    val doneDays = days.count { TrainingPlan.isDone(it, progress) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = ((current?.number ?: 1) - 1).coerceAtLeast(0))
    Column(Modifier.fillMaxSize()) {
        TopBar(app, "HSK $level in ${days.size} days")
        Column(Modifier.padding(horizontal = 16.dp)) {
            PandaSays(
                if (current == null) Mood.CHEER else Mood.HAPPY,
                when {
                    current == null -> "You finished the whole plan. 恭喜!"
                    doneDays == 0 -> "One day at a time: about ${content.wordsOf(level).size / (days.size - 1).coerceAtLeast(1) + 1} new words a day, plus grammar and reviews, gets you through HSK $level."
                    else -> "$doneDays of ${days.size} days done. Day ${current.number} is waiting."
                },
            )
            Meter(doneDays.toFloat() / days.size, Tangerine, Modifier.padding(top = 8.dp, bottom = 4.dp))
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), state = listState) {
            items(days, key = { it.number }) { day ->
                DayCard(app, day, isToday = day.number == current?.number, done = TrainingPlan.isDone(day, progress)) { task ->
                    when (task) {
                        is PlanTask.Learn -> if (progress.isUnlocked(task.lesson, units)) app.open(Screen.Run(SessionKind.LessonRun(task.lesson)))
                        is PlanTask.Sentences -> app.open(Screen.Run(SessionKind.SentenceDrill(task.level), task.key))
                        is PlanTask.Write -> app.open(Screen.Write(task.characters.first()))
                        is PlanTask.Test -> app.open(Screen.Run(SessionKind.LevelTest(task.level)))
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun DayCard(app: AppState, day: PlanDay, isToday: Boolean, done: Boolean, onTask: (PlanTask) -> Unit) {
    val accent = if (done) Good else if (isToday) Tangerine else MaterialTheme.colorScheme.outlineVariant
    Raised(MaterialTheme.colorScheme.surface, Modifier.fillMaxWidth().padding(vertical = 6.dp), shape = RoundedCornerShape(20.dp),
        edgeColor = accent) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
                    Text(if (done) "✓" else day.number.toString(), color = if (done || isToday) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.ExtraBold)
                }
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text("Day ${day.number}" + if (isToday) "  ·  TODAY" else "", fontWeight = FontWeight.ExtraBold,
                        color = if (isToday) Tangerine else MaterialTheme.colorScheme.onSurface)
                    Text(
                        when {
                            day.tasks.any { it is PlanTask.Test } -> "Final test"
                            day.grammarLessons > 0 && day.newWords > 0 -> "${day.newWords} new words · ${day.grammarLessons} grammar"
                            day.grammarLessons > 0 -> "${day.grammarLessons} grammar ${if (day.grammarLessons == 1) "lesson" else "lessons"}"
                            day.newWords > 0 -> "${day.newWords} new words"
                            else -> "Review"
                        },
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            for (task in day.tasks) {
                // Writing by hand can be switched off in the settings; the plan then leaves it out.
                if (task is PlanTask.Write && !app.showWriting) continue
                val taskDone = task !is PlanTask.Write && TrainingPlan.isDone(task, app.progress)
                val (icon, label) = when (task) {
                    is PlanTask.Learn -> when {
                        task.lesson.isReview -> "⭐" to "Unit ${task.lesson.unit} review"
                        task.lesson.isGrammar -> "🧩" to "Grammar: ${task.lesson.grammar?.title}"
                        else -> "📘" to "Learn: " + task.lesson.words.joinToString(", ") { it.shortGloss }
                    }
                    is PlanTask.Sentences -> "🧱" to "Sentence workout"
                    is PlanTask.Write -> "✍" to "Write: ${task.characters.joinToString(" ")} (optional)"
                    is PlanTask.Test -> "🏁" to "HSK ${task.level} test"
                }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)).clickable { onTask(task) }
                    .padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(icon, fontSize = 18.sp)
                    Text(label, modifier = Modifier.padding(start = 8.dp).weight(1f).alpha(if (taskDone) 0.55f else 1f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                    Text(if (taskDone) "✓" else "›", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp,
                        color = if (taskDone) Good else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** The phrase books and their topics. */
@Composable
fun PhraseBooksScreen(app: AppState, content: Content) {
    Column(Modifier.fillMaxSize()) {
        TopBar(app, "Phrase books")
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            content.phraseBooks.forEachIndexed { b, book ->
                val color = Rainbow[(b * 2 + 6) % Rainbow.size]
                item {
                    Raised(color, Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), shape = RoundedCornerShape(20.dp)) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(book.icon, fontSize = 34.sp)
                            Column(Modifier.padding(start = 12.dp)) {
                                Text("${book.title} · ${book.size}", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                                Text(book.blurb, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                itemsIndexed(book.topics) { i, topic ->
                    Raised(MaterialTheme.colorScheme.surface, Modifier.fillMaxWidth().padding(vertical = 4.dp), edge = 4.dp,
                        edgeColor = MaterialTheme.colorScheme.outlineVariant, onClick = { app.open(Screen.Phrases(book.id, i)) }) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(30.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                                Text("${i + 1}", fontWeight = FontWeight.ExtraBold, color = color.darker(0.15f), fontSize = 13.sp)
                            }
                            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                Text(topic.title, fontWeight = FontWeight.Bold)
                                Text(topic.phrases.firstOrNull()?.text.orEmpty(), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text("${topic.phrases.size} ›", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** One topic: every phrase with audio, and a drill on them. */
@Composable
fun PhrasesScreen(app: AppState, content: Content, bookId: String, topicIndex: Int) {
    val topic = content.phraseBook(bookId)?.topics?.getOrNull(topicIndex)
    Column(Modifier.fillMaxSize()) {
        TopBar(app, topic?.title ?: "Phrases")
        if (topic == null) return@Column
        VoiceNotice(app)
        LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            items(topic.phrases) { phrase ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Box(Modifier.padding(horizontal = 12.dp, vertical = 2.dp)) { SentenceRow(app, phrase) }
                }
            }
        }
        BigButton("Practise these phrases", Modifier.padding(16.dp)) {
            app.open(Screen.Run(SessionKind.PhraseDrill(bookId, topicIndex)))
        }
    }
}
