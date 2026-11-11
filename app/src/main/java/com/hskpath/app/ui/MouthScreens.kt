package com.hskpath.app.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hskpath.app.AppState
import com.hskpath.app.Screen
import com.hskpath.core.Air
import com.hskpath.core.Content
import com.hskpath.core.Mouth
import com.hskpath.core.MouthLayer
import com.hskpath.core.MouthStep
import com.hskpath.core.MouthView
import com.hskpath.core.Sounds
import com.hskpath.core.Spline
import kotlinx.coroutines.delay

/** Which markers to draw over the side view: the pose they belong to and what the breath is doing. */
data class MouthOverlay(val pose: String, val air: Air, val voiced: Boolean, val alpha: Float = 1f)

private fun hex(value: String): Color = Color(0xFF000000 or value.removePrefix("#").toLong(16))

private fun shapePath(points: FloatArray, closed: Boolean, smooth: Boolean): Path {
    val path = Path()
    if (points.size < 4) return path
    path.moveTo(points[0], points[1])
    if (smooth) {
        val c = Spline.cubics(points, closed)
        for (i in 0 until c.size / 6) {
            path.cubicTo(c[i * 6], c[i * 6 + 1], c[i * 6 + 2], c[i * 6 + 3], c[i * 6 + 4], c[i * 6 + 5])
        }
    } else {
        for (i in 1 until points.size / 2) path.lineTo(points[i * 2], points[i * 2 + 1])
    }
    if (closed) path.close()
    return path
}

private fun flat(points: List<List<Float>>): FloatArray = Mouth.flatten(points)

/**
 * Draws one view of the mouth diagrams ("side" or "front") for the given point
 * positions, following the rules in tools/mouth/SPEC.md. [flow] (0..1, repeating)
 * makes the breath arrows move.
 */
@Composable
fun MouthCanvas(
    mouth: Mouth,
    viewName: String,
    frame: Map<String, FloatArray>,
    modifier: Modifier = Modifier,
    overlay: MouthOverlay? = null,
    labels: Boolean = false,
    flow: Float = 0f,
) {
    val view = if (viewName == Mouth.FRONT) mouth.front else mouth.side
    val fixed = remember(mouth, viewName) {
        view.layers.filter { !it.isShape }.associate { it.id to PathParser().parsePathString(it.d.orEmpty()).toPath() }
    }
    Canvas(modifier.aspectRatio(view.width / view.height)) {
        val s = size.width / view.width
        scale(s, s, pivot = Offset.Zero) {
            // The artwork may run past the edge of the view (a head is bigger than the picture of it).
            clipRect(0f, 0f, view.width, view.height) {
                for (layer in view.layers) {
                    val path = fixed[layer.id] ?: shapePath(frame[layer.id] ?: FloatArray(0), layer.closed, layer.smooth)
                    drawLayer(mouth, layer, path, view)
                }
                if (overlay != null && overlay.alpha > 0.01f) drawOverlay(mouth, overlay, flow)
                if (labels) drawLabels(mouth, view)
            }
        }
    }
}

private fun DrawScope.drawLayer(mouth: Mouth, layer: MouthLayer, path: Path, view: MouthView) {
    val fill = mouth.colour(layer.fill)?.let(::hex)
    val stroke = mouth.colour(layer.stroke)?.takeIf { layer.strokeWidth > 0f }?.let(::hex)
    val draw = {
        if (fill != null) drawPath(path, fill)
        if (stroke != null) {
            val dash = layer.dash?.takeIf { it.size == 2 }?.let { PathEffect.dashPathEffect(floatArrayOf(it[0], it[1])) }
            drawPath(path, stroke, style = Stroke(layer.strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = dash))
        }
    }
    if (layer.opacity >= 0.999f) {
        draw()
    } else {
        // Fade the layer as a whole, so its outline and fill do not show through each other.
        drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(0f, 0f, view.width, view.height), androidx.compose.ui.graphics.Paint().apply { alpha = layer.opacity })
            draw()
            canvas.restore()
        }
    }
}

private fun DrawScope.polyline(points: FloatArray, color: Color, width: Float, alpha: Float) {
    drawPath(shapePath(points, closed = false, smooth = false), color, alpha = alpha,
        style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** A dashed arrow along a route of the breath; the dashes drift towards the tip. */
private fun DrawScope.arrow(points: FloatArray, color: Color, alpha: Float, flow: Float) {
    val n = points.size / 2
    if (n < 2) return
    val path = shapePath(points, closed = false, smooth = true)
    drawPath(path, Color.White, alpha = 0.55f * alpha, style = Stroke(18f, cap = StrokeCap.Round))
    drawPath(path, color, alpha = alpha, style = Stroke(10f, cap = StrokeCap.Round,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(22f, 18f), -flow * 40f)))
    val tx = points[(n - 1) * 2]
    val ty = points[(n - 1) * 2 + 1]
    val dx = tx - points[(n - 2) * 2]
    val dy = ty - points[(n - 2) * 2 + 1]
    val len = kotlin.math.hypot(dx, dy).takeIf { it > 0f } ?: 1f
    val ux = dx / len
    val uy = dy / len
    val bx = tx - ux * 34f
    val by = ty - uy * 34f
    val head = Path().apply {
        moveTo(tx + ux * 8f, ty + uy * 8f)
        lineTo(bx - uy * 19f, by + ux * 19f)
        lineTo(bx + uy * 19f, by - ux * 19f)
        close()
    }
    drawPath(head, color, alpha = alpha)
    drawPath(head, Color.White, alpha = alpha, style = Stroke(3f, join = StrokeJoin.Round))
}

private fun DrawScope.drawOverlay(mouth: Mouth, overlay: MouthOverlay, flow: Float) {
    val pose = mouth.pose(overlay.pose)
    val a = overlay.alpha.coerceIn(0f, 1f)
    val air = hex(mouth.colour("air") ?: "#339AF0")
    val voice = hex(mouth.colour("voice") ?: "#845EF7")
    val contact = hex(mouth.colour("contact") ?: "#FFB703")
    val anchors = mouth.art.anchors
    if (overlay.air == Air.ORAL || overlay.air == Air.PUFF) pose.airPath?.let { arrow(flat(it), air, a, flow) }
    if (overlay.air == Air.NASAL) arrow(flat(anchors.nasalPath), air, a, flow)
    if (overlay.air == Air.PUFF) {
        // The burst swells outwards as it plays.
        for ((i, r) in listOf(30f, 54f, 78f).withIndex()) {
            val pulse = (1f - ((flow + 1f - i / 3f) % 1f)) * 0.6f + 0.4f
            polyline(Spline.arc(anchors.mouthOut[0], anchors.mouthOut[1], r, 140f, 220f, 12), air, 10f, a * pulse)
        }
    }
    if (overlay.voiced) {
        val x = anchors.larynx[0]
        val y = anchors.larynx[1]
        val pulse = 0.55f + 0.45f * kotlin.math.abs(1f - 2f * ((flow * 2f) % 1f))
        drawCircle(voice, 8f, Offset(x, y), alpha = a)
        for (r in listOf(22f, 38f)) {
            polyline(Spline.arc(x, y, r, -40f, 40f, 8), voice, 7f, a * pulse)
            polyline(Spline.arc(x, y, r, 140f, 220f, 8), voice, 7f, a * pulse)
        }
    }
    for (p in pose.gap) {
        drawCircle(Color.White, 17f, Offset(p[0], p[1]), alpha = 0.35f * a)
        drawCircle(contact, 17f, Offset(p[0], p[1]), alpha = a, style = Stroke(7f))
    }
    for (p in pose.contact) {
        drawCircle(contact, 17f, Offset(p[0], p[1]), alpha = a)
        drawCircle(Color.White, 17f, Offset(p[0], p[1]), alpha = a, style = Stroke(5f))
    }
}

private fun DrawScope.drawLabels(mouth: Mouth, view: MouthView) {
    val ink = hex(mouth.colour("label") ?: "#1F2333")
    for (label in view.labels) {
        val from = label.from ?: label.at
        drawLine(ink, Offset(from[0], from[1]), Offset(label.to[0], label.to[1]), strokeWidth = 3f, cap = StrokeCap.Round)
        drawCircle(ink, 6f, Offset(label.to[0], label.to[1]))
    }
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 30f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    drawIntoCanvas { canvas ->
        for (label in view.labels) {
            text.textAlign = when (label.align) {
                "start" -> Paint.Align.LEFT
                "end" -> Paint.Align.RIGHT
                else -> Paint.Align.CENTER
            }
            val baseline = label.at[1] - (text.descent() + text.ascent()) / 2f
            text.style = Paint.Style.STROKE
            text.strokeWidth = 7f
            text.strokeJoin = Paint.Join.ROUND
            text.color = android.graphics.Color.WHITE
            canvas.nativeCanvas.drawText(label.text, label.at[0], baseline, text)
            text.style = Paint.Style.FILL
            text.color = ink.toArgb()
            canvas.nativeCanvas.drawText(label.text, label.at[0], baseline, text)
        }
    }
}

/** Side view with the front view inset, as used on the sound screen and in tests. */
@Composable
fun MouthPicture(
    mouth: Mouth,
    frame: Map<String, FloatArray>,
    modifier: Modifier = Modifier,
    overlay: MouthOverlay? = null,
    flow: Float = 0f,
) {
    Column(modifier) {
        MouthCanvas(mouth, Mouth.SIDE, frame, Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)), overlay, flow = flow)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(0.42f), horizontalAlignment = Alignment.CenterHorizontally) {
                MouthCanvas(mouth, Mouth.FRONT, frame, Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)))
                Text("From the front", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
            }
            Column(Modifier.weight(0.58f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val contact = hex(mouth.colour("contact") ?: "#FFB703")
                val air = hex(mouth.colour("air") ?: "#339AF0")
                val voice = hex(mouth.colour("voice") ?: "#845EF7")
                Key(contact, filled = true, "touching: air blocked")
                Key(contact, filled = false, "narrow gap: air hisses")
                Key(air, filled = true, "path of the breath")
                Key(voice, filled = true, "voice switched on")
            }
        }
    }
}

@Composable
private fun Key(color: Color, filled: Boolean, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(14.dp).clip(CircleShape)
            .then(if (filled) Modifier.background(color) else Modifier.border(3.dp, color, CircleShape)))
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 6.dp))
    }
}

/** How to shape the mouth for one sound: an animated diagram, the steps in words, and words to practise. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SoundScreen(app: AppState, content: Content, letters: String, isInitial: Boolean) {
    val sound = Sounds.find(letters, isInitial)
    val mouth = app.mouth
    Column(Modifier.fillMaxSize()) {
        TopBar(app, if (isInitial) "Initial  $letters" else "Final  $letters")
        if (sound == null || mouth == null) return@Column
        val steps = sound.steps
        // `at` is the step being shown or moved to (-1 = the relaxed start); `from` is the pose it moves from.
        var at by remember { mutableIntStateOf(-1) }
        var from by remember { mutableStateOf(Mouth.REST) }
        var replay by remember { mutableIntStateOf(0) }
        var playing by remember { mutableStateOf(true) }
        val move = remember { Animatable(1f) }
        val flow by rememberInfiniteTransition(label = "flow").animateFloat(
            0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "flow")

        LaunchedEffect(replay) {
            playing = true
            from = Mouth.REST
            at = -1
            move.snapTo(1f)
            app.speaker.speak(sound.example, slow = true)
            delay(450)
            for (i in steps.indices) {
                from = if (i == 0) Mouth.REST else steps[i - 1].shape
                at = i
                move.snapTo(0f)
                move.animateTo(1f, tween(if (from == steps[i].shape) 300 else 700, easing = FastOutSlowInEasing))
                if (i < steps.lastIndex) delay(if (steps[i].air == Air.PUFF) 650 else 800)
            }
            playing = false
        }

        val step: MouthStep? = steps.getOrNull(at)
        val frame = mouth.blend(from, step?.shape ?: Mouth.REST, move.value)
        val overlay = step?.let { MouthOverlay(it.shape, it.air, it.voiced, ((move.value - 0.5f) / 0.5f).coerceIn(0f, 1f)) }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Card(Modifier.fillMaxWidth()) {
                MouthPicture(mouth, frame, Modifier.padding(10.dp), overlay, flow)
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(sound.letters, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, color = Sky)
                Text(sound.example, fontSize = 36.sp)
                SpeakButton(app, sound.example)
                SpeakButton(app, sound.example, slow = true)
            }
            VoiceNotice(app)
            SectionTitle("Step by step")
            steps.forEachIndexed { i, s ->
                val current = i == at
                val accent = if (current) Sky else MaterialTheme.colorScheme.outlineVariant
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp))
                        .background(if (current) Sky.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface)
                        .border(2.dp, accent, RoundedCornerShape(14.dp))
                        .clickable(enabled = !playing) { from = s.shape; at = i }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(28.dp).clip(CircleShape).background(if (current) Sky else MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center) {
                        Text("${i + 1}", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp,
                            color = if (current) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(Modifier.padding(start = 10.dp).weight(1f)) {
                        Text(s.caption, fontWeight = if (current) FontWeight.Bold else FontWeight.Normal)
                        Text(
                            when (s.air) {
                                Air.NONE -> "Air held back"
                                Air.ORAL -> "Air flows out through the mouth"
                                Air.NASAL -> "Air flows out through the nose"
                                Air.PUFF -> "Strong burst of air"
                            } + if (s.voiced) "  ·  voice on" else "  ·  no voice",
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            SectionTitle("How to make it")
            Text(sound.how)
            SectionTitle("Sounds like")
            Text(sound.like)
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

/** The relaxed mouth with every part named: the map for all the instructions. */
@Composable
fun MouthMap(mouth: Mouth, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp)) {
            MouthCanvas(mouth, Mouth.SIDE, mouth.frame(Mouth.REST), Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)),
                labels = true)
            Text("The parts of the mouth named in the instructions.", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }
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
