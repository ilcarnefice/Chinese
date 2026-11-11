package com.hskpath.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hskpath.app.AppState
import com.hskpath.app.Screen
import com.hskpath.core.Content
import com.hskpath.core.Sounds
import kotlinx.coroutines.delay

/**
 * Where everything in the mouth is for one sound, on a 0..1 grid of a head seen
 * from the side and facing left. The tongue's top surface runs tip → blade → mid → back.
 */
data class MouthPose(
    val tipX: Float, val tipY: Float,
    val bladeY: Float, val midY: Float, val backY: Float,
    /** How far the jaw drops (0 = teeth together). */
    val jaw: Float = 0.03f,
    /** 0 closed, 1 relaxed, 2 wide open. */
    val lipGap: Float = 1f,
    /** 0 spread into a smile, 1 neutral, 2 rounded and pushed forward. */
    val lipRound: Float = 1f,
    /** 1 when the upper teeth rest on the lower lip (f). */
    val lipToTeeth: Float = 0f,
    /** Where the tongue or lips make contact or squeeze the air, if anywhere. */
    val contactX: Float = -1f, val contactY: Float = -1f,
) {
    fun blend(o: MouthPose, t: Float): MouthPose {
        fun m(a: Float, b: Float) = a + (b - a) * t
        val showContact = if (t < 0.5f) this else o
        return MouthPose(m(tipX, o.tipX), m(tipY, o.tipY), m(bladeY, o.bladeY), m(midY, o.midY), m(backY, o.backY),
            m(jaw, o.jaw), m(lipGap, o.lipGap), m(lipRound, o.lipRound), m(lipToTeeth, o.lipToTeeth),
            showContact.contactX, showContact.contactY)
    }
}

/** The drawable mouth shape for each name used by [Sounds]. */
val MouthPoses: Map<String, MouthPose> = mapOf(
    "rest" to MouthPose(0.33f, 0.56f, 0.53f, 0.52f, 0.56f),
    "lips" to MouthPose(0.33f, 0.57f, 0.54f, 0.53f, 0.56f, jaw = 0.01f, lipGap = 0f, contactX = 0.165f, contactY = 0.505f),
    "lipteeth" to MouthPose(0.33f, 0.57f, 0.54f, 0.53f, 0.56f, jaw = 0.02f, lipGap = 0.5f, lipToTeeth = 1f, contactX = 0.225f, contactY = 0.50f),
    "ridge" to MouthPose(0.305f, 0.425f, 0.47f, 0.52f, 0.57f, jaw = 0.04f, contactX = 0.305f, contactY = 0.42f),
    "dental" to MouthPose(0.268f, 0.475f, 0.50f, 0.53f, 0.57f, jaw = 0.02f, lipRound = 0.4f, contactX = 0.262f, contactY = 0.47f),
    "retro" to MouthPose(0.39f, 0.365f, 0.44f, 0.54f, 0.57f, jaw = 0.05f, lipRound = 1.2f, contactX = 0.385f, contactY = 0.355f),
    "palatal" to MouthPose(0.275f, 0.575f, 0.385f, 0.40f, 0.52f, jaw = 0.03f, lipRound = 0f, contactX = 0.43f, contactY = 0.36f),
    "velar" to MouthPose(0.34f, 0.60f, 0.57f, 0.47f, 0.385f, jaw = 0.05f, contactX = 0.665f, contactY = 0.385f),
    "velarOpen" to MouthPose(0.34f, 0.60f, 0.57f, 0.48f, 0.42f, jaw = 0.06f, contactX = 0.665f, contactY = 0.395f),
    "a" to MouthPose(0.32f, 0.66f, 0.65f, 0.64f, 0.62f, jaw = 0.13f, lipGap = 2f),
    "o" to MouthPose(0.35f, 0.62f, 0.60f, 0.55f, 0.49f, jaw = 0.07f, lipGap = 1.2f, lipRound = 2f),
    "e" to MouthPose(0.36f, 0.60f, 0.57f, 0.52f, 0.47f, jaw = 0.06f, lipGap = 1.2f),
    "i" to MouthPose(0.275f, 0.57f, 0.41f, 0.41f, 0.53f, jaw = 0.02f, lipGap = 0.7f, lipRound = 0f),
    "u" to MouthPose(0.37f, 0.62f, 0.59f, 0.50f, 0.41f, jaw = 0.03f, lipGap = 0.5f, lipRound = 2f),
    "ü" to MouthPose(0.275f, 0.57f, 0.41f, 0.41f, 0.53f, jaw = 0.02f, lipGap = 0.5f, lipRound = 2f),
    "er" to MouthPose(0.40f, 0.40f, 0.47f, 0.54f, 0.52f, jaw = 0.06f, lipGap = 1.2f),
    "n" to MouthPose(0.305f, 0.425f, 0.47f, 0.52f, 0.57f, jaw = 0.03f, contactX = 0.305f, contactY = 0.42f),
    "ng" to MouthPose(0.34f, 0.60f, 0.57f, 0.47f, 0.385f, jaw = 0.04f, contactX = 0.665f, contactY = 0.385f),
)

/** Side view of the mouth for [pose]: lips, teeth, palate and tongue. */
@Composable
fun MouthDiagram(pose: MouthPose, modifier: Modifier = Modifier) {
    val skin = Color(0xFFFFE0CC)
    val line = Color(0xFF8D5B4C)
    val tongue = Color(0xFFFF7B8A)
    val lip = Color(0xFFE5576B)
    val cavity = Color(0xFFA8485A)
    Canvas(modifier.aspectRatio(1.25f)) {
        val w = size.width
        val h = size.height
        // The grid is 1 wide and 0.8 tall; (0.1..0.9) x (0.2..0.9) holds the head.
        fun p(x: Float, y: Float) = Offset(x * w, (y - 0.15f) / 0.8f * h)
        val jaw = pose.jaw
        val push = (pose.lipRound - 1f).coerceAtLeast(0f) * 0.02f // rounded lips move forward

        // Face and neck silhouette.
        val face = Path().apply {
            moveTo(p(0.34f, 0.15f).x, p(0.34f, 0.15f).y)
            quadTo(p(0.22f, 0.24f), p(0.20f, 0.33f))                    // forehead to nose bridge
            quadTo(p(0.12f, 0.40f), p(0.105f, 0.42f))                   // nose
            quadTo(p(0.13f, 0.46f), p(0.19f - push, 0.455f))            // under the nose
            lineTo(p(0.19f - push, 0.57f + jaw).x, p(0.19f - push, 0.57f + jaw).y)
            quadTo(p(0.17f, 0.66f + jaw), p(0.22f, 0.73f + jaw))        // chin
            quadTo(p(0.36f, 0.80f + jaw), p(0.62f, 0.82f + jaw * 0.5f)) // jaw line
            lineTo(p(0.62f, 0.95f).x, p(0.62f, 0.95f).y)
            lineTo(p(0.98f, 0.95f).x, p(0.98f, 0.95f).y)
            lineTo(p(0.98f, 0.15f).x, p(0.98f, 0.15f).y)
            close()
        }
        drawPath(face, skin)

        // Mouth cavity and throat.
        val hole = Path().apply {
            moveTo(p(0.20f - push, 0.50f).x, p(0.20f - push, 0.50f).y)
            lineTo(p(0.255f, 0.455f).x, p(0.255f, 0.455f).y)
            quadTo(p(0.30f, 0.40f), p(0.36f, 0.35f))                    // ridge
            quadTo(p(0.50f, 0.27f), p(0.64f, 0.34f))                    // hard palate
            quadTo(p(0.74f, 0.36f), p(0.765f, 0.455f))                  // soft palate and uvula
            quadTo(p(0.80f, 0.43f), p(0.83f, 0.50f))                    // back wall of the throat
            lineTo(p(0.83f, 0.95f).x, p(0.83f, 0.95f).y)
            lineTo(p(0.60f, 0.95f).x, p(0.60f, 0.95f).y)
            quadTo(p(0.58f, 0.80f + jaw), p(0.48f, 0.75f + jaw))        // floor of the mouth
            quadTo(p(0.32f, 0.72f + jaw), p(0.26f, 0.60f + jaw))
            lineTo(p(0.20f - push, 0.52f + jaw).x, p(0.20f - push, 0.52f + jaw).y)
            close()
        }
        drawPath(hole, cavity)
        drawPath(hole, line, style = Stroke(w * 0.006f, join = StrokeJoin.Round))

        // Tongue: a body from the root over the back, middle and blade, plus a rounded
        // tip drawn as a thick line so it can point up, forward or down.
        val drop = jaw * 0.3f
        val body = Path().apply {
            moveTo(p(0.76f, 0.95f).x, p(0.76f, 0.95f).y)
            quadTo(p(0.79f, pose.backY + 0.10f + drop), p(0.70f, pose.backY + 0.01f + drop))
            quadTo(p(0.61f, pose.backY - 0.035f + drop), p(0.53f, pose.midY + drop))
            quadTo(p(0.47f, pose.midY - 0.012f + drop), p(0.42f, pose.bladeY + drop))
            quadTo(p(0.385f, pose.bladeY + 0.05f + drop), p(0.395f, 0.665f + jaw))
            quadTo(p(0.47f, 0.75f + jaw), p(0.58f, 0.95f))
            close()
        }
        drawPath(body, tongue)
        val tipWidth = 0.062f * w
        drawLine(tongue, p(0.435f, pose.bladeY + 0.045f + drop), p(pose.tipX + 0.022f, pose.tipY + 0.028f + drop),
            strokeWidth = tipWidth, cap = StrokeCap.Round)

        // Teeth.
        val toothW = 0.028f * w
        val round = androidx.compose.ui.geometry.CornerRadius(toothW / 2)
        drawRoundRect(Color.White, p(0.236f, 0.425f), Size(toothW, 0.10f * h), round)
        drawRoundRect(Color.White, p(0.242f, 0.535f + jaw), Size(toothW, 0.085f * h), round)

        // Lips: the lower lip closes the gap, tucks under the teeth for f, and both round forward.
        val gap = (pose.lipGap * 0.022f)
        val lipW = (0.055f + (pose.lipRound - 1f).coerceAtLeast(0f) * 0.012f) * w
        val lipH = (0.060f - (1f - pose.lipRound).coerceAtLeast(0f) * 0.012f) * h
        drawOval(lip, p(0.175f - push, 0.455f - gap * 0.3f), Size(lipW, lipH))
        val lowerX = 0.178f - push + pose.lipToTeeth * 0.04f
        val lowerY = 0.505f + gap + jaw * 0.9f - pose.lipToTeeth * 0.02f
        drawOval(lip, p(lowerX, lowerY), Size(lipW, lipH))

        // Mark the point of contact or the narrow gap for the air.
        if (pose.contactX >= 0f) {
            val c = p(pose.contactX, pose.contactY)
            drawCircle(Gold, w * 0.022f, c)
            drawCircle(Color.White, w * 0.022f, c, style = Stroke(w * 0.006f))
        }
    }
}

/** How to shape the mouth for one sound, with an animated diagram and words to practise. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SoundScreen(app: AppState, content: Content, letters: String, isInitial: Boolean) {
    val sound = Sounds.find(letters, isInitial)
    Column(Modifier.fillMaxSize()) {
        TopBar(app, if (isInitial) "Initial  $letters" else "Final  $letters")
        if (sound == null) return@Column
        val poses = remember(sound) { listOf(MouthPoses.getValue("rest")) + sound.steps.map { MouthPoses.getValue(it) } }
        val blend = remember { Animatable(0f) }
        var from by remember { mutableIntStateOf(0) }
        var replay by remember { mutableIntStateOf(0) }
        var playing by remember { mutableStateOf(true) }
        // Move through the shapes: rest → each step, pause, and repeat a few times.
        LaunchedEffect(replay) {
            playing = true
            repeat(3) { round ->
                if (round == 0) app.speaker.speak(sound.example, slow = true)
                for (i in 0 until poses.size - 1) {
                    from = i
                    blend.snapTo(0f)
                    blend.animateTo(1f, tween(if (i == 0) 550 else 700, easing = FastOutSlowInEasing))
                    delay(if (i == poses.size - 2) 900 else 150)
                }
                if (round < 2) { from = 0; blend.snapTo(0f); delay(350) }
            }
            playing = false
        }
        val pose = poses[from].blend(poses[(from + 1).coerceAtMost(poses.size - 1)], blend.value)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    MouthDiagram(pose, Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant))
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Legend(Color(0xFFFF7B8A), "tongue")
                        Legend(Color(0xFFE5576B), "lips")
                        Legend(Color(0xFFC9CEDB), "teeth")
                        Legend(Gold, if (sound.steps.any { MouthPoses.getValue(it).contactX >= 0f }) "contact point" else "")
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(sound.letters, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, color = Sky)
                Text(sound.example, fontSize = 36.sp)
                SpeakButton(app, sound.example)
                SpeakButton(app, sound.example, slow = true)
            }
            VoiceNotice(app)
            SectionTitle("How to make it")
            Text(sound.how)
            SectionTitle("Sounds like")
            Text(sound.like)
            if (!isInitial && sound.steps.size > 1) {
                SectionTitle("Mouth movement")
                Text(sound.steps.joinToString("  →  "), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Grape)
            }
            val words = remember(sound) { Sounds.examples(content, sound) }
            if (words.isNotEmpty()) {
                SectionTitle("Practise with these words")
                FlowRow(horizontalArrangement = SpacedSmall, verticalArrangement = SpacedSmall) {
                    for (w in words) {
                        Raised(MaterialTheme.colorScheme.surface, edge = 4.dp, edgeColor = MaterialTheme.colorScheme.outlineVariant,
                            onClick = { app.speaker.speak(w.hanzi, slow = true) }) {
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(w.hanzi, fontSize = 22.sp)
                                Text(w.pinyin, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                                Text(w.shortGloss, style = MaterialTheme.typography.labelSmall, maxLines = 1,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        BigButton("▶ Watch again", Modifier.padding(16.dp), color = Sky, enabled = !playing) { replay++ }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    if (label.isEmpty()) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(14.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 4.dp))
    }
}

/** A tappable chip that opens the mouth guide for a sound. */
@Composable
fun SoundChip(app: AppState, letters: String, hanzi: String, isInitial: Boolean) {
    val color = if (isInitial) Sky else Pink
    Raised(MaterialTheme.colorScheme.surface, Modifier.width(64.dp), edge = 4.dp, edgeColor = color,
        onClick = { app.open(Screen.SoundGuide(letters, isInitial)) }) {
        Column(Modifier.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(letters, fontWeight = FontWeight.ExtraBold, color = color.darker(0.1f), fontSize = 18.sp)
            Text(hanzi, fontSize = 18.sp)
        }
    }
}

private fun Path.quadTo(control: Offset, end: Offset) = quadraticTo(control.x, control.y, end.x, end.y)

@Suppress("unused")
private fun DrawScope.unused() = Unit
