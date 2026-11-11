package com.hskpath.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.RectF
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
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
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

/** The pieces of the real picture that the mouth diagrams are made of, decoded once and kept. */
object MouthPictures {
    private val cache = HashMap<String, Bitmap>()

    fun get(context: Context, layer: MouthLayer): Bitmap? = synchronized(cache) {
        val src = layer.src ?: return null
        val crop = layer.crop?.takeIf { it.size == 4 }
        val key = if (crop == null) src else src + crop.joinToString(",", "#")
        cache[key]?.let { return it }
        val whole = cache[src] ?: runCatching { context.assets.open(src).use { BitmapFactory.decodeStream(it) } }
            .getOrNull()?.also { cache[src] = it } ?: return null
        if (crop == null) return whole
        val part = runCatching { Bitmap.createBitmap(whole, crop[0], crop[1], crop[2], crop[3]) }.getOrNull() ?: return null
        cache[key] = part
        part
    }

    /** Decodes every picture of the diagrams ahead of time, off the main thread. */
    fun warm(context: Context, mouth: Mouth) {
        for (layer in mouth.side.layers) if (layer.isImage) get(context, layer)
    }
}

/**
 * Draws the mouth for the given positions of its moving parts, following the
 * rules in tools/mouth/SPEC.md. Only the [window] of the picture is shown: by
 * default the close view of lips, teeth and tongue. [flow] (0..1, repeating)
 * makes the breath arrow move.
 */
@Composable
fun MouthCanvas(
    mouth: Mouth,
    frame: Map<String, FloatArray>,
    modifier: Modifier = Modifier,
    overlay: MouthOverlay? = null,
    labels: Boolean = false,
    flow: Float = 0f,
    window: String = Mouth.MOUTH,
) {
    val view = mouth.side
    val context = LocalContext.current
    val fixed = remember(mouth) {
        view.layers.filter { it.type == "path" }.associate { it.id to PathParser().parsePathString(it.d.orEmpty()).toPath() }
    }
    val pictures = remember(mouth) {
        view.layers.filter { it.isImage }.mapNotNull { layer -> MouthPictures.get(context, layer)?.let { layer.id to it } }.toMap()
    }
    val paint = remember { Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG) }
    val w = remember(mouth, window) { mouth.window(window) }
    Canvas(modifier.aspectRatio(w[2] / w[3])) {
        val s = size.width / w[2]
        // Markers keep the same size on screen however much of the picture is shown.
        val mark = w[2] / view.width
        clipRect(0f, 0f, size.width, size.height) {
            drawRect(Color.White)
            scale(s, s, pivot = Offset.Zero) {
                translate(-w[0], -w[1]) {
                    for (layer in view.layers) {
                        if (layer.isImage) {
                            pictures[layer.id]?.let { drawPicture(layer, it, frame[layer.id], paint) }
                        } else {
                            val path = fixed[layer.id] ?: shapePath(frame[layer.id] ?: FloatArray(0), layer.closed, layer.smooth)
                            drawLayer(mouth, layer, path, view)
                        }
                    }
                    if (overlay != null && overlay.alpha > 0.01f) drawOverlay(mouth, overlay, flow, mark)
                    if (labels) drawLabels(mouth, view, mark)
                }
            }
        }
    }
}

/** A piece of the real picture: stretched over its grid if it moves, drawn as it is if not. */
private fun DrawScope.drawPicture(layer: MouthLayer, bitmap: Bitmap, grid: FloatArray?, paint: Paint) {
    paint.alpha = (layer.opacity.coerceIn(0f, 1f) * 255).toInt()
    drawIntoCanvas { canvas ->
        if (layer.isMesh && grid != null && grid.size == layer.gridSize) {
            canvas.nativeCanvas.drawBitmapMesh(bitmap, layer.cols, layer.rows, grid, 0, null, 0, paint)
        } else {
            val r = layer.rect ?: return@drawIntoCanvas
            if (r.size == 4) canvas.nativeCanvas.drawBitmap(bitmap, null, RectF(r[0], r[1], r[0] + r[2], r[1] + r[3]), paint)
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

/** A short arrow where the breath comes out: the last stretch of its route. It nudges forward as it plays. */
private fun DrawScope.arrow(points: FloatArray, color: Color, alpha: Float, flow: Float, mark: Float) {
    val n = points.size / 2
    if (n < 2) return
    val dx = points[(n - 1) * 2] - points[(n - 2) * 2]
    val dy = points[(n - 1) * 2 + 1] - points[(n - 2) * 2 + 1]
    val len = kotlin.math.hypot(dx, dy).takeIf { it > 0f } ?: 1f
    val ux = dx / len
    val uy = dy / len
    val push = 7f * mark * kotlin.math.sin(flow * 2f * Math.PI.toFloat())
    val tx = points[(n - 1) * 2] + ux * push
    val ty = points[(n - 1) * 2 + 1] + uy * push
    val start = Offset(tx - ux * 80f * mark, ty - uy * 80f * mark)
    val base = Offset(tx - ux * 30f * mark, ty - uy * 30f * mark)
    val head = Path().apply {
        moveTo(tx + ux * 6f * mark, ty + uy * 6f * mark)
        lineTo(base.x - uy * 21f * mark, base.y + ux * 21f * mark)
        lineTo(base.x + uy * 21f * mark, base.y - ux * 21f * mark)
        close()
    }
    drawLine(Color.White, start, base, strokeWidth = 21f * mark, cap = StrokeCap.Round, alpha = alpha)
    drawPath(head, Color.White, alpha = alpha, style = Stroke(9f * mark, join = StrokeJoin.Round))
    drawLine(color, start, base, strokeWidth = 12f * mark, cap = StrokeCap.Round, alpha = alpha)
    drawPath(head, color, alpha = alpha)
}

/**
 * The markers: a solid dot where two parts touch, a ring at a narrow gap, a short
 * arrow where the breath comes out (mouth or nose) and arcs for a strong puff.
 * The voice is not drawn; the screen says it in words.
 */
private fun DrawScope.drawOverlay(mouth: Mouth, overlay: MouthOverlay, flow: Float, mark: Float) {
    val pose = mouth.pose(overlay.pose)
    val a = overlay.alpha.coerceIn(0f, 1f)
    val air = hex(mouth.colour("air") ?: "#339AF0")
    val contact = hex(mouth.colour("contact") ?: "#FFB703")
    val anchors = mouth.art.anchors
    if (overlay.air == Air.ORAL || overlay.air == Air.PUFF) pose.airPath?.let { arrow(flat(it), air, a, flow, mark) }
    if (overlay.air == Air.NASAL) arrow(flat(anchors.nasalPath), air, a, flow, mark)
    if (overlay.air == Air.PUFF) {
        // The burst swells outwards as it plays.
        for ((i, r) in listOf(46f, 70f, 94f).withIndex()) {
            val pulse = (1f - ((flow + 1f - i / 3f) % 1f)) * 0.6f + 0.4f
            polyline(Spline.arc(anchors.mouthOut[0], anchors.mouthOut[1], r * mark, -38f, 38f, 12), air, 10f * mark, a * pulse)
        }
    }
    for (p in pose.gap) {
        drawCircle(Color.White, 17f * mark, Offset(p[0], p[1]), alpha = 0.35f * a)
        drawCircle(contact, 17f * mark, Offset(p[0], p[1]), alpha = a, style = Stroke(7f * mark))
    }
    for (p in pose.contact) {
        drawCircle(contact, 17f * mark, Offset(p[0], p[1]), alpha = a)
        drawCircle(Color.White, 17f * mark, Offset(p[0], p[1]), alpha = a, style = Stroke(5f * mark))
    }
}

private fun DrawScope.drawLabels(mouth: Mouth, view: MouthView, mark: Float) {
    val ink = hex(mouth.colour("label") ?: "#1F2333")
    // Pointers get a white edge so they stay readable on the dark parts of the picture.
    for (white in listOf(true, false)) for (label in view.labels) {
        val from = label.from ?: label.at
        val a = Offset(from[0], from[1])
        val b = Offset(label.to[0], label.to[1])
        drawLine(if (white) Color.White else ink, a, b, strokeWidth = (if (white) 8f else 3.5f) * mark, cap = StrokeCap.Round)
        drawCircle(if (white) Color.White else ink, (if (white) 9.5f else 7f) * mark, b)
    }
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 32f * mark
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
            text.strokeWidth = 7f * mark
            text.strokeJoin = Paint.Join.ROUND
            text.color = android.graphics.Color.WHITE
            canvas.nativeCanvas.drawText(label.text, label.at[0], baseline, text)
            text.style = Paint.Style.FILL
            text.color = ink.toArgb()
            canvas.nativeCanvas.drawText(label.text, label.at[0], baseline, text)
        }
    }
}

/**
 * The mouth picture as used on the sound screen: the moving close view of the
 * mouth, one sentence saying what it shows, and what the breath and the voice
 * are doing. With [names] the relaxed mouth is shown instead, with its parts named.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MouthPicture(
    mouth: Mouth,
    frame: Map<String, FloatArray>,
    modifier: Modifier = Modifier,
    overlay: MouthOverlay? = null,
    flow: Float = 0f,
    names: Boolean = false,
    caption: String? = null,
) {
    Column(modifier) {
        MouthCanvas(mouth, if (names) mouth.frame(Mouth.REST) else frame,
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)), if (names) null else overlay, labels = names, flow = flow,
            window = if (names) Mouth.NAMES else Mouth.MOUTH)
        if (names) {
            Text("The parts of the mouth named in the instructions.", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
        } else if (caption != null) {
            // What the picture is showing right now, in words, right under it.
            Text(caption, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 10.dp))
        }
        if (overlay != null && !names) {
            val air = hex(mouth.colour("air") ?: "#339AF0")
            val voice = hex(mouth.colour("voice") ?: "#845EF7")
            val quiet = MaterialTheme.colorScheme.onSurfaceVariant
            FlowRow(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                when (overlay.air) {
                    Air.NONE -> Tag("Air held back", quiet)
                    Air.ORAL -> Tag("Air out through the mouth", air)
                    Air.NASAL -> Tag("Air out through the nose", air)
                    Air.PUFF -> Tag("Strong puff of air", air)
                }
                if (overlay.voiced) Tag("Voice on", voice) else Tag("No voice", quiet)
            }
        } else {
            Text("A real mouth seen from the side. Lips are on the right.", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/** A small coloured label saying what the breath or the voice is doing. */
@Composable
private fun Tag(text: String, color: Color) {
    Text(text, color = color, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 5.dp))
}

const val PICTURE_CREDIT = "Picture adapted from OpenStax, Anatomy & Physiology (CC BY 3.0)."

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
        var names by remember { mutableStateOf(false) }
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
                Box {
                    MouthPicture(mouth, frame, Modifier.padding(10.dp), overlay, flow, names,
                        caption = step?.let { "${at + 1}. ${it.caption}" } ?: "Relaxed: lips together, breathing through the nose")
                    Box(Modifier.align(Alignment.TopStart).padding(18.dp)) {
                        Pill(if (names) "Hide names" else "Name the parts", if (names) Sky else Color.White.copy(alpha = 0.92f),
                            if (names) Color.White else Sky.darker(0.15f)) { names = !names }
                    }
                }
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
            Text(PICTURE_CREDIT, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 18.dp))
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
            MouthCanvas(mouth, mouth.frame(Mouth.REST), Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)),
                labels = true, window = Mouth.NAMES)
            Text("A real mouth seen from the side, with the parts named in the instructions. " +
                "Tap a sound below to watch them move.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            Text(PICTURE_CREDIT, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
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
