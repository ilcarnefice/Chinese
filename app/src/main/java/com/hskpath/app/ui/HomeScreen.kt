package com.hskpath.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hskpath.app.AppState
import com.hskpath.app.Screen
import com.hskpath.app.SessionKind
import com.hskpath.core.Achievements
import com.hskpath.core.Content
import com.hskpath.core.CourseUnit
import com.hskpath.core.Curriculum
import com.hskpath.core.Lesson
import com.hskpath.core.Progress
import com.hskpath.core.TrainingPlan

private val Tabs = listOf("🏠" to "Learn", "🧭" to "Explore", "🎯" to "Practice", "🐼" to "Me")

/** The four main tabs with the bottom bar. */
@Composable
fun HomeScreen(app: AppState, content: Content) {
    Column(Modifier.fillMaxSize()) {
        Header(app, content)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (app.tab) {
                0 -> key(app.level) { LearnTab(app, content) }
                1 -> ExploreTab(app, content)
                2 -> PracticeTab(app, content)
                else -> MeTab(app)
            }
        }
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 12.dp) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
                Tabs.forEachIndexed { i, (icon, label) ->
                    val selected = app.tab == i
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                            .clickable { app.tab = i }.padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(icon, fontSize = 22.sp)
                        Text(label, style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** Level picker, streak and XP, shown above every tab. */
@Composable
private fun Header(app: AppState, content: Content) {
    val progress = app.progress
    val today = app.today()
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 10.dp, bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("HSK Path", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.weight(1f))
            Counter("🔥", progress.currentStreak(today).toString(), Tangerine)
            Spacer(Modifier.width(8.dp))
            Counter("⚡", progress.xp.toString(), Gold.darker(0.1f))
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = SpacedSmall) {
            for (l in content.levels) {
                val selected = l == app.level
                val color = Rainbow[(l - 1) % Rainbow.size]
                Raised(if (selected) color else MaterialTheme.colorScheme.surface, edge = 4.dp,
                    edgeColor = if (selected) color.darker() else MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(14.dp), onClick = { app.selectLevel(l) }) {
                    Text("HSK $l", fontWeight = FontWeight.ExtraBold,
                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun Counter(icon: String, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(icon, fontSize = 20.sp)
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = color, modifier = Modifier.padding(start = 2.dp))
    }
}

/** The learning path of the selected level. */
@Composable
private fun LearnTab(app: AppState, content: Content) {
    val level = app.level
    val progress = app.progress
    val today = app.today()
    val units = remember(level) { Curriculum.units(level, content.wordsOf(level)) }
    val next = units.flatMap { it.lessons }.firstOrNull { !progress.isCompleted(it) && progress.isUnlocked(it, units) }
    // Open the path at the unit the learner is working on.
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = ((next?.unit ?: 1) - 1).coerceAtLeast(0))

    LazyColumn(Modifier.fillMaxSize(), state = listState, horizontalAlignment = Alignment.CenterHorizontally) {
        items(units, key = { it.number }) { unit ->
            if (unit.number == (next?.unit ?: 1)) {
                Column(Modifier.padding(horizontal = 16.dp).padding(top = 8.dp)) {
                    DailyGoal(progress, today)
                    PlanBanner(app, content, level)
                    VoiceNotice(app)
                }
            }
            UnitBlock(app, unit, units, progress, next)
        }
        item { LevelTestCard(app, level, progress) }
    }
}

@Composable
private fun DailyGoal(progress: Progress, today: Long) {
    val xp = progress.xpToday(today)
    val reached = xp >= Progress.DAILY_GOAL_XP
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Panda(if (reached) Mood.CHEER else Mood.HAPPY, size = 64.dp)
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(
                    when {
                        reached -> "Daily goal smashed! 太棒了！"
                        xp > 0 -> "Nice start. Keep going! 加油！"
                        else -> "Ready for today's Chinese? 你好！"
                    },
                    fontWeight = FontWeight.Bold,
                )
                Meter(xp.toFloat() / Progress.DAILY_GOAL_XP, Gold, Modifier.padding(top = 6.dp))
                Text("$xp / ${Progress.DAILY_GOAL_XP} XP today", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Entry to the day-by-day training plan, showing which day the learner is on. */
@Composable
private fun PlanBanner(app: AppState, content: Content, level: Int) {
    val days = remember(level) { TrainingPlan.days(level, content) }
    val current = TrainingPlan.today(days, app.progress)
    Raised(MaterialTheme.colorScheme.surface, Modifier.fillMaxWidth().padding(top = 10.dp), shape = RoundedCornerShape(20.dp),
        edgeColor = Tangerine, onClick = { app.open(Screen.Plan(level)) }) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("📅", fontSize = 28.sp)
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text("HSK $level in ${days.size} days", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                Text(if (current == null) "Plan complete! 太棒了!" else "Today: day ${current.number} of ${days.size}",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", fontSize = 28.sp, color = Tangerine)
        }
    }
}

/** One unit: a banner, then its lessons as stepping stones that wind left and right. */
@Composable
private fun UnitBlock(app: AppState, unit: CourseUnit, units: List<CourseUnit>, progress: Progress, next: Lesson?) {
    val color = Rainbow[(unit.number - 1) % Rainbow.size]
    Column(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Raised(color, Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                val done = unit.lessons.count { progress.isCompleted(it) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("HSK ${unit.level} · UNIT ${unit.number}", color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.weight(1f))
                    Text("$done/${unit.lessons.size} lessons", color = Color.White, fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.labelLarge)
                }
                Text(unit.words.take(6).joinToString(", ") { it.shortGloss }, color = Color.White, fontSize = 18.sp,
                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(unit.words.take(8).joinToString(" ") { it.hanzi }, color = Color.White.copy(alpha = 0.9f),
                    fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        val sway = listOf(0, 52, 76, 52, 0, -52, -76, -52)
        unit.lessons.forEachIndexed { i, lesson ->
            val shift = sway[((unit.number - 1) * unit.lessons.size + i) % sway.size]
            Box(Modifier.padding(top = 16.dp).offset(x = shift.dp)) {
                LessonStone(app, lesson, color, progress.isCompleted(lesson), progress.isUnlocked(lesson, units),
                    isNext = lesson.id == next?.id)
            }
        }
    }
}

@Composable
private fun LessonStone(app: AppState, lesson: Lesson, unitColor: Color, completed: Boolean, unlocked: Boolean, isNext: Boolean) {
    val color = when {
        completed -> Gold
        unlocked -> unitColor
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val bounce by rememberInfiniteTransition(label = "start").animateFloat(
        0f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "start")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (isNext) {
            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp), shadowElevation = 4.dp,
                modifier = Modifier.offset(y = (-6 * bounce).dp).padding(bottom = 6.dp)) {
                Text("START", color = unitColor, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
            }
        }
        Raised(color, Modifier.size(width = 84.dp, height = 80.dp), shape = CircleShape, edge = 8.dp, enabled = unlocked,
            onClick = { app.open(Screen.Run(SessionKind.LessonRun(lesson))) }) {
            Text(
                when {
                    !unlocked -> "🔒"
                    lesson.isReview -> if (completed) "🏆" else "⭐"
                    completed -> "✓"
                    else -> lesson.words.first().hanzi.take(1)
                },
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (unlocked) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            if (lesson.isReview) "Unit review" else lesson.words.joinToString(", ") { it.shortGloss },
            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp).width(210.dp),
        )
        Text(
            if (lesson.isReview) "All ${lesson.words.size} words" else lesson.words.joinToString(" ") { it.hanzi },
            style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 1,
            overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(210.dp),
        )
    }
}

@Composable
private fun LevelTestCard(app: AppState, level: Int, progress: Progress) {
    Card(Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("🏁 HSK $level test", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
            val best = progress.testBest[level]
            Text(
                if (best == null) "30 listening, reading and sentence questions from the whole level. " +
                    "Score ${Progress.TEST_PASS_PERCENT}% to unlock every lesson and jump ahead."
                else "Best score: $best%" + if (progress.passedTest(level)) " · passed, all lessons unlocked" else
                    " · ${Progress.TEST_PASS_PERCENT}% needed to pass",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BigButton("Take the test", color = Grape) { app.open(Screen.Run(SessionKind.LevelTest(level))) }
        }
    }
}

/** A big colourful entry point to one feature. */
@Composable
private fun FeatureCard(icon: String, title: String, subtitle: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Raised(color, modifier, shape = RoundedCornerShape(22.dp), edge = 6.dp, onClick = onClick) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text(icon, fontSize = 34.sp)
            Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(top = 6.dp))
            Text(subtitle, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodySmall,
                minLines = 2, maxLines = 2)
        }
    }
}

@Composable
private fun ExploreTab(app: AppState, content: Content) {
    val level = app.level
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PandaSays(Mood.THINK, "Pick a map to explore. Everything here follows HSK $level; switch level at the top.",
            Modifier.padding(top = 4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FeatureCard("🕸", "Word Map", "%,d words linked by their characters".format(content.mapWordCount), Jade,
                Modifier.weight(1f)) { app.open(Screen.Webs(level)) }
            FeatureCard("🗺", "Sentence Map", "%,d sentences grouped by key word".format(content.sentences.size), Sky,
                Modifier.weight(1f)) { app.open(Screen.SentenceMap(level)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FeatureCard("🧩", "Grammar Map", "${content.grammar.size} formulas as building blocks", Grape,
                Modifier.weight(1f)) { app.open(Screen.Grammar(level)) }
            FeatureCard("✍", "Writing", "Stroke order for ${app.strokes?.size ?: 0} characters", Coral,
                Modifier.weight(1f)) { app.open(Screen.WriteList(level)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FeatureCard("💬", "Dialogues", "Short conversations with audio", Tangerine,
                Modifier.weight(1f)) { app.open(Screen.Stories(level)) }
            FeatureCard("👄", "Sounds & Mouth", "Lips, teeth and tongue for every sound", Pink,
                Modifier.weight(1f)) { app.open(Screen.PinyinGuide) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FeatureCard("📖", "Phrase Books", "%,d everyday, travel and business phrases".format(content.phraseBooks.sumOf { it.size }),
                Color(0xFF20C997), Modifier.weight(1f)) { app.open(Screen.PhraseBooks) }
            FeatureCard("📅", "Training Plan", "HSK $level in ${TrainingPlan.lengths.getValue(level)} days, day by day", Gold.darker(0.12f),
                Modifier.weight(1f)) { app.open(Screen.Plan(level)) }
        }
        FeatureCard("🔍", "Dictionary", "Search by characters, pinyin or English", Color(0xFF5C7CFA),
            Modifier.fillMaxWidth()) { app.open(Screen.Search) }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PracticeTab(app: AppState, content: Content) {
    val level = app.level
    val progress = app.progress
    val due = progress.dueWordIds(app.today()).size
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PandaSays(
            if (due > 0) Mood.CHEER else Mood.HAPPY,
            when {
                progress.cards.isEmpty() -> "Finish a lesson and your words will show up here for review."
                due > 0 -> "$due words are ready for review. A few minutes keeps them fresh!"
                else -> "All caught up! Try a sentence workout while you wait."
            },
            Modifier.padding(top = 4.dp),
        )
        PracticeCard("🔁", "Word review", if (due > 0) "$due words due now" else "${progress.cards.size} words learned so far",
            Jade, enabled = progress.cards.isNotEmpty()) { app.open(Screen.Run(SessionKind.Practice)) }
        PracticeCard("🧱", "Sentence workout", "Translate, listen and build HSK $level sentences " +
            "(%,d to draw from)".format(content.sentencesOf(level).size), Sky) {
            app.open(Screen.Run(SessionKind.SentenceDrill(level)))
        }
        PracticeCard("🧩", "Grammar drills", "Build sentences for each HSK $level formula", Grape) {
            app.open(Screen.Grammar(level))
        }
        PracticeCard("✍", "Handwriting", "Trace characters stroke by stroke", Coral) { app.open(Screen.WriteList(level)) }
        PracticeCard("🏁", "HSK $level test", progress.testBest[level]?.let { "Best score $it%" } ?: "30 mixed questions", Tangerine) {
            app.open(Screen.Run(SessionKind.LevelTest(level)))
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PracticeCard(icon: String, title: String, subtitle: String, color: Color, enabled: Boolean = true, onClick: () -> Unit) {
    Raised(MaterialTheme.colorScheme.surface, Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        edgeColor = MaterialTheme.colorScheme.outlineVariant, enabled = enabled, onClick = onClick) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(if (enabled) color else color.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center) { Text(icon, fontSize = 28.sp) }
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", fontSize = 28.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Profile: rank, this week's activity, totals and achievements. */
@Composable
private fun MeTab(app: AppState) {
    val progress = app.progress
    val today = app.today()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Panda(Mood.HAPPY, size = 84.dp)
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text("Level ${Achievements.xpLevel(progress.xp)} learner", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Meter(Achievements.xpIntoLevel(progress.xp) / Achievements.XP_PER_LEVEL.toFloat(), Jade,
                        Modifier.padding(top = 6.dp))
                    Text("${Achievements.XP_PER_LEVEL - Achievements.xpIntoLevel(progress.xp)} XP to the next level",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("🔥", progress.currentStreak(today).toString(), "day streak", Tangerine, Modifier.weight(1f))
            StatTile("📖", progress.cards.size.toString(), "words", Jade, Modifier.weight(1f))
            StatTile("⭐", progress.completed.values.count { it > 0 }.toString(), "lessons", Sky, Modifier.weight(1f))
            StatTile("⚡", progress.xp.toString(), "XP", Gold.darker(0.1f), Modifier.weight(1f))
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text("This week", fontWeight = FontWeight.ExtraBold)
                WeekChart(progress, today)
            }
        }
        Text("Achievements", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
        for (a in Achievements.all(progress, today)) {
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).clip(CircleShape)
                        .background(if (a.done) Gold else MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center) { Text(a.icon, fontSize = 24.sp) }
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(a.title, fontWeight = FontWeight.Bold)
                        Text(a.description, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!a.done) Meter(a.fraction, Gold, Modifier.padding(top = 4.dp), height = 8.dp)
                    }
                    Text(if (a.done) "✓" else "${a.current.coerceAtMost(a.target)}/${a.target}",
                        fontWeight = FontWeight.ExtraBold, color = if (a.done) Good else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        Pill("About and licences", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant) {
            app.open(Screen.About)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun StatTile(icon: String, value: String, label: String, color: Color, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(vertical = 12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(icon, fontSize = 22.sp)
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** XP earned on each of the last seven days. */
@Composable
private fun WeekChart(progress: Progress, today: Long) {
    val days = (6 downTo 0).map { today - it }
    val values = days.map { progress.xpToday(it) }
    val top = (values.maxOrNull() ?: 0).coerceAtLeast(Progress.DAILY_GOAL_XP)
    val names = listOf("Th", "Fr", "Sa", "Su", "Mo", "Tu", "We") // day 0 (1 Jan 1970) was a Thursday
    val track = MaterialTheme.colorScheme.surfaceVariant
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        days.forEachIndexed { i, day ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(Modifier.size(width = 26.dp, height = 84.dp)) {
                    val w = size.width
                    drawLine(track, Offset(w / 2, w / 2), Offset(w / 2, size.height - w / 2), strokeWidth = w, cap = StrokeCap.Round)
                    if (values[i] > 0) {
                        val h = (size.height - w) * values[i] / top
                        drawLine(if (values[i] >= Progress.DAILY_GOAL_XP) Gold else Jade, Offset(w / 2, size.height - w / 2),
                            Offset(w / 2, size.height - w / 2 - h), strokeWidth = w, cap = StrokeCap.Round)
                    }
                }
                Text(names[(day % 7).toInt()], style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (day == today) FontWeight.ExtraBold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(values[i].toString(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}
