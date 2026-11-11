package com.hskpath.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.hskpath.core.Content
import com.hskpath.core.CourseUnit
import com.hskpath.core.Curriculum
import com.hskpath.core.Lesson
import com.hskpath.core.Progress

/** The learning path: pick a level, then work down its units lesson by lesson. */
@Composable
fun HomeScreen(app: AppState, content: Content) {
    val level = app.level
    val progress = app.progress
    val today = app.today()
    val units = remember(level) { Curriculum.units(level, content.wordsOf(level)) }
    val due = progress.dueWordIds(today).size

    LazyColumn(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("HSK Path", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f))
                    Text("🔥 ${progress.currentStreak(today)}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(16.dp))
                    Text("⚡ ${progress.xp}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = SpacedSmall) {
                    for (l in content.levels) {
                        val selected = l == level
                        Pill(
                            "HSK $l",
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        ) { app.selectLevel(l) }
                    }
                }
                DailyGoal(progress, today)
                val total = content.wordsOf(level).size
                Text("HSK $level · ${progress.learnedCount(level)} of $total words learned",
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 10.dp))
                LinearProgressIndicator(
                    progress = { progress.learnedCount(level).toFloat() / total.coerceAtLeast(1) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.secondary,
                )
                Button(onClick = { app.open(Screen.Run(SessionKind.Practice)) },
                    enabled = progress.cards.isNotEmpty(), modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    Text(when {
                        progress.cards.isEmpty() -> "Practice (finish a lesson first)"
                        due > 0 -> "Practice · $due words due"
                        else -> "Practice learned words"
                    })
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = SpacedSmall) {
                    Shortcut("🕸 Word webs") { app.open(Screen.Webs(level)) }
                    Shortcut("🧩 Grammar") { app.open(Screen.Grammar(level)) }
                    Shortcut("💬 Dialogues") { app.open(Screen.Stories(level)) }
                    Shortcut("🗣 Pinyin") { app.open(Screen.PinyinGuide) }
                    Shortcut("🔍 Dictionary") { app.open(Screen.Search) }
                    Shortcut("ℹ About") { app.open(Screen.About) }
                }
                VoiceNotice(app)
            }
        }
        items(units, key = { it.number }) { unit -> UnitBlock(app, unit, units, progress) }
        item { LevelTestCard(app, level, progress) }
    }
}

@Composable
private fun DailyGoal(progress: Progress, today: Long) {
    val xp = progress.xpToday(today)
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(
                if (xp >= Progress.DAILY_GOAL_XP) "Daily goal reached! $xp XP today"
                else "Daily goal: $xp / ${Progress.DAILY_GOAL_XP} XP",
                style = MaterialTheme.typography.labelLarge,
            )
            LinearProgressIndicator(
                progress = { (xp.toFloat() / Progress.DAILY_GOAL_XP).coerceAtMost(1f) },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = Amber,
            )
        }
    }
}

@Composable
private fun Shortcut(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick) { Text(label) }
}

/** One unit: a banner, then its lessons as stepping stones that wind left and right. */
@Composable
private fun UnitBlock(app: AppState, unit: CourseUnit, units: List<CourseUnit>, progress: Progress) {
    Column(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Column(Modifier.padding(14.dp)) {
                Text("Unit ${unit.number}", color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(unit.words.take(8).joinToString(" · ") { it.hanzi },
                    color = MaterialTheme.colorScheme.onPrimary, maxLines = 1)
            }
        }
        val sway = listOf(0, 44, 64, 44, 0, -44, -64, -44)
        unit.lessons.forEachIndexed { i, lesson ->
            val shift = sway[((unit.number - 1) * unit.lessons.size + i) % sway.size]
            Box(Modifier.padding(top = 14.dp).offset(x = shift.dp)) {
                LessonStone(app, lesson, progress.isCompleted(lesson), progress.isUnlocked(lesson, units))
            }
        }
    }
}

@Composable
private fun LessonStone(app: AppState, lesson: Lesson, completed: Boolean, unlocked: Boolean) {
    val color = when {
        completed -> Amber
        unlocked -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(color = color, shape = CircleShape, shadowElevation = if (unlocked) 4.dp else 0.dp,
            modifier = Modifier.size(72.dp).clip(CircleShape)
                .clickable(enabled = unlocked) { app.open(Screen.Run(SessionKind.LessonRun(lesson))) }) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    when {
                        !unlocked -> "🔒"
                        lesson.isReview -> "★"
                        completed -> "✓"
                        else -> lesson.words.first().hanzi.take(2)
                    },
                    fontSize = 24.sp, fontWeight = FontWeight.Bold,
                    color = if (unlocked) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            if (lesson.isReview) "Unit review" else lesson.words.joinToString(" ") { it.hanzi },
            style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 1,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp).width(200.dp),
        )
    }
}

@Composable
private fun LevelTestCard(app: AppState, level: Int, progress: Progress) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("HSK $level test", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            val best = progress.testBest[level]
            Text(
                if (best == null) "Listening, reading and sentence questions from the whole level. " +
                    "Score ${Progress.TEST_PASS_PERCENT}% to unlock every lesson and jump ahead."
                else "Best score: $best%" + if (progress.passedTest(level)) " · passed, all lessons unlocked" else
                    " · ${Progress.TEST_PASS_PERCENT}% needed to pass",
            )
            Button(onClick = { app.open(Screen.Run(SessionKind.LevelTest(level))) }) { Text("Take the test") }
        }
    }
}
