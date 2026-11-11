package com.hskpath.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hskpath.app.AppState
import com.hskpath.core.Sentence
import kotlin.random.Random

val Jade = Color(0xFF12B886)
val Coral = Color(0xFFFF5A5F)
val Gold = Color(0xFFFFB703)
val Sky = Color(0xFF339AF0)
val Grape = Color(0xFF845EF7)
val Tangerine = Color(0xFFFF922B)
val Pink = Color(0xFFF06595)
val Good = Color(0xFF2FB344)
val Bad = Color(0xFFE5484D)

/** Colours cycled through for units, map branches and cards. */
val Rainbow = listOf(Jade, Sky, Grape, Coral, Tangerine, Pink, Color(0xFF20C997), Color(0xFF5C7CFA))

/** A darker shade, used for the "edge" under raised buttons and cards. */
fun Color.darker(amount: Float = 0.22f): Color = lerp(this, Color.Black, amount)

private val LightColors = lightColorScheme(
    primary = Jade, onPrimary = Color.White,
    primaryContainer = Color(0xFFD3F9EC), onPrimaryContainer = Color(0xFF06402F),
    secondary = Sky, onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCEEFF), onSecondaryContainer = Color(0xFF0B3A66),
    tertiary = Grape,
    background = Color(0xFFF6F7FB), onBackground = Color(0xFF1F2333),
    surface = Color.White, onSurface = Color(0xFF1F2333),
    surfaceVariant = Color(0xFFECEEF5), onSurfaceVariant = Color(0xFF5B6275),
    outlineVariant = Color(0xFFDDE1EC),
    errorContainer = Color(0xFFFFE3E3),
)

private val DarkColors = darkColorScheme(
    primary = Jade, onPrimary = Color.White,
    primaryContainer = Color(0xFF0B5C45), onPrimaryContainer = Color(0xFFD3F9EC),
    secondary = Sky, onSecondary = Color.White,
    secondaryContainer = Color(0xFF12456F), onSecondaryContainer = Color(0xFFDCEEFF),
    tertiary = Grape,
    background = Color(0xFF14161F), onBackground = Color(0xFFECEEF5),
    surface = Color(0xFF1D2030), onSurface = Color(0xFFECEEF5),
    surfaceVariant = Color(0xFF2A2E42), onSurfaceVariant = Color(0xFFAEB4C7),
    outlineVariant = Color(0xFF3A3F57),
)

@Composable
fun HskTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}

/**
 * A raised surface with a darker edge underneath that sinks when pressed.
 * The building block for buttons, tiles and path stones.
 */
@Composable
fun Raised(
    color: Color,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    edge: Dp = 5.dp,
    enabled: Boolean = true,
    edgeColor: Color = color.darker(),
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val sink by animateDpAsState(if (pressed && enabled) edge else 0.dp, tween(60), label = "sink")
    Box(modifier, propagateMinConstraints = true) {
        Box(Modifier.matchParentSize().padding(top = edge).clip(shape).background(edgeColor))
        Box(
            Modifier.padding(bottom = edge).offset(y = sink).clip(shape).background(color)
                .then(if (onClick != null) Modifier.clickable(interaction, null, enabled = enabled, onClick = onClick) else Modifier),
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}

/** The main call-to-action button. */
@Composable
fun BigButton(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val shown = if (enabled) color else MaterialTheme.colorScheme.surfaceVariant
    Raised(shown, modifier.fillMaxWidth(), enabled = enabled, onClick = onClick) {
        Text(text.uppercase(), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp,
            color = if (enabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 15.dp, horizontal = 16.dp))
    }
}

/** Title row with a back arrow, used by every screen below the tabs. */
@Composable
fun TopBar(app: AppState, title: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(CircleShape).clickable { app.back() }, contentAlignment = Alignment.Center) {
            Text("←", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(start = 4.dp))
    }
}

/** Round button that reads Chinese text aloud. */
@Composable
fun SpeakButton(app: AppState, text: String, slow: Boolean = false, big: Boolean = false) {
    val size = if (big) 84.dp else 44.dp
    Raised(if (slow) Gold else Sky, Modifier.size(size), shape = CircleShape, edge = if (big) 6.dp else 4.dp,
        onClick = { app.speaker.speak(text, slow) }) {
        Text(if (slow) "🐢" else "🔊", fontSize = if (big) 34.sp else 18.sp)
    }
}

/** Shown once where sound matters, when the phone has no Mandarin voice. */
@Composable
fun VoiceNotice(app: AppState) {
    if (!app.speaker.unavailable) return
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            "No Chinese voice found on this device, so audio is off. Install Chinese under " +
                "Settings → Accessibility → Text-to-speech, then reopen the app.",
            modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = Color(0xFF7A1F1F),
        )
    }
}

/** A sentence with characters, pinyin, translation and a speak button. */
@Composable
fun SentenceRow(app: AppState, sentence: Sentence, accent: Color = Coral, highlight: (String) -> Boolean = { false }) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        SpeakButton(app, sentence.text)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(highlighted(sentence, highlight, accent), fontSize = 20.sp)
            Text(sentence.pinyin, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
            Text(sentence.english, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

fun highlighted(sentence: Sentence, highlight: (String) -> Boolean, color: Color): AnnotatedString =
    buildAnnotatedString {
        for (token in sentence.tokens) {
            if (highlight(token)) withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold)) { append(token) }
            else append(token)
        }
    }

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.padding(top = 18.dp, bottom = 6.dp))
}

@Composable
fun Pill(text: String, color: Color, onColor: Color, onClick: (() -> Unit)? = null) {
    Surface(color = color, shape = RoundedCornerShape(50),
        modifier = if (onClick != null) Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick) else Modifier) {
        Text(text, color = onColor, modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

/** White rounded card with a soft edge. */
@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier, content = content)
}

/** Thick rounded progress bar. */
@Composable
fun Meter(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 14.dp) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier.fillMaxWidth().padding(vertical = 2.dp).size(width = 10.dp, height = height)) {
        val r = size.height / 2
        drawLine(track, Offset(r, r), Offset(size.width - r, r), strokeWidth = size.height, cap = StrokeCap.Round)
        val f = fraction.coerceIn(0f, 1f)
        if (f > 0f) {
            val end = r + (size.width - 2 * r) * f
            drawLine(color, Offset(r, r), Offset(end, r), strokeWidth = size.height, cap = StrokeCap.Round)
            if (end - r > size.height) drawLine(Color.White.copy(alpha = 0.35f), Offset(r + 4, r - size.height * 0.18f),
                Offset(end - 4, r - size.height * 0.18f), strokeWidth = size.height * 0.2f, cap = StrokeCap.Round)
        }
    }
}

enum class Mood { HAPPY, CHEER, SAD, THINK }

/** The app's panda mascot, drawn from simple shapes. */
@Composable
fun Panda(mood: Mood, modifier: Modifier = Modifier, size: Dp = 96.dp) {
    val bob by rememberInfiniteTransition(label = "bob").animateFloat(
        0f, 1f, infiniteRepeatable(tween(if (mood == Mood.CHEER) 420 else 1400), RepeatMode.Reverse), label = "bob")
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val lift = (if (mood == Mood.CHEER) -0.07f else -0.025f) * w * bob
        val ink = Color(0xFF23263A)
        fun c(x: Float, y: Float) = Offset(x * w, y * w + lift)
        // ears
        drawCircle(ink, 0.15f * w, c(0.24f, 0.24f))
        drawCircle(ink, 0.15f * w, c(0.76f, 0.24f))
        // head
        drawCircle(Color.White, 0.40f * w, c(0.5f, 0.54f))
        drawCircle(ink, 0.40f * w, c(0.5f, 0.54f), style = Stroke(0.035f * w))
        // eye patches
        rotate(-20f, c(0.34f, 0.5f)) { drawOval(ink, c(0.25f, 0.40f), Size(0.18f * w, 0.22f * w)) }
        rotate(20f, c(0.66f, 0.5f)) { drawOval(ink, c(0.57f, 0.40f), Size(0.18f * w, 0.22f * w)) }
        // eyes
        when (mood) {
            Mood.CHEER -> {
                drawArc(Color.White, 200f, 140f, false, c(0.28f, 0.47f), Size(0.12f * w, 0.10f * w), style = Stroke(0.03f * w, cap = StrokeCap.Round))
                drawArc(Color.White, 200f, 140f, false, c(0.60f, 0.47f), Size(0.12f * w, 0.10f * w), style = Stroke(0.03f * w, cap = StrokeCap.Round))
            }
            Mood.SAD -> {
                drawCircle(Color.White, 0.035f * w, c(0.35f, 0.53f))
                drawCircle(Color.White, 0.035f * w, c(0.65f, 0.53f))
            }
            else -> {
                drawCircle(Color.White, 0.05f * w, c(0.35f, 0.50f))
                drawCircle(Color.White, 0.05f * w, c(0.65f, 0.50f))
                val look = if (mood == Mood.THINK) 0.015f else 0f
                drawCircle(ink, 0.025f * w, c(0.355f + look, 0.50f - look))
                drawCircle(ink, 0.025f * w, c(0.655f + look, 0.50f - look))
            }
        }
        // cheeks
        drawCircle(Pink.copy(alpha = 0.45f), 0.05f * w, c(0.24f, 0.68f))
        drawCircle(Pink.copy(alpha = 0.45f), 0.05f * w, c(0.76f, 0.68f))
        // nose and mouth
        drawOval(ink, c(0.46f, 0.62f), Size(0.08f * w, 0.05f * w))
        when (mood) {
            Mood.SAD -> drawArc(ink, 200f, 140f, false, c(0.42f, 0.74f), Size(0.16f * w, 0.10f * w), style = Stroke(0.03f * w, cap = StrokeCap.Round))
            Mood.CHEER -> drawArc(Coral, 0f, 180f, true, c(0.40f, 0.66f), Size(0.20f * w, 0.18f * w))
            else -> drawArc(ink, 20f, 140f, false, c(0.42f, 0.64f), Size(0.16f * w, 0.12f * w), style = Stroke(0.03f * w, cap = StrokeCap.Round))
        }
    }
}

/** Mascot with a speech bubble. */
@Composable
fun PandaSays(mood: Mood, text: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Panda(mood, size = 72.dp)
        Spacer(Modifier.width(8.dp))
        Card(Modifier.weight(1f)) {
            Text(text, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium)
        }
    }
}

/** A burst of falling confetti, played once. */
@Composable
fun Confetti(modifier: Modifier = Modifier) {
    val time = remember { Animatable(0f) }
    LaunchedEffect(Unit) { time.animateTo(1f, tween(2600, easing = LinearEasing)) }
    val pieces = remember {
        val r = Random(7)
        List(70) { floatArrayOf(r.nextFloat(), r.nextFloat() * 0.6f, 0.5f + r.nextFloat(), r.nextFloat() * 360f, r.nextInt(Rainbow.size).toFloat(), r.nextFloat() - 0.5f) }
    }
    Canvas(modifier.fillMaxSize()) {
        val t = time.value
        if (t >= 1f) return@Canvas
        for (p in pieces) {
            val x = (p[0] + p[5] * 0.25f * t) * size.width
            val y = (-0.1f - p[1] + t * (1.3f + p[1]) * p[2] * 1.2f) * size.height
            val color = Rainbow[p[4].toInt()].copy(alpha = (1f - t).coerceIn(0f, 1f) * 0.9f + 0.1f)
            rotate(p[3] + t * 720f * p[2], Offset(x, y)) {
                drawRect(color, Offset(x - 7.dp.toPx(), y - 4.dp.toPx()), Size(14.dp.toPx(), 8.dp.toPx()))
            }
        }
    }
}

val SpacedSmall = Arrangement.spacedBy(8.dp)
