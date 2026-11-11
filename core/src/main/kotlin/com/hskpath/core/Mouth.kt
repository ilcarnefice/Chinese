package com.hskpath.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** One drawing layer of a mouth diagram: fixed artwork (`path`) or a moving outline (`shape`). */
@Serializable
data class MouthLayer(
    val id: String,
    val type: String,
    /** SVG path data, for `path` layers. */
    val d: String? = null,
    val closed: Boolean = true,
    val smooth: Boolean = true,
    val fill: String? = null,
    val stroke: String? = null,
    val strokeWidth: Float = 0f,
    val opacity: Float = 1f,
    val dash: List<Float>? = null,
) {
    val isShape: Boolean get() = type == "shape"
}

/** A name for a part of the mouth, with a pointer line. */
@Serializable
data class MouthLabel(
    val text: String,
    val at: List<Float>,
    val to: List<Float>,
    val from: List<Float>? = null,
    val align: String = "middle",
)

@Serializable
data class MouthView(
    val width: Float,
    val height: Float,
    val layers: List<MouthLayer>,
    val labels: List<MouthLabel> = emptyList(),
)

@Serializable
data class MouthAnchors(val larynx: List<Float>, val mouthOut: List<Float>, val nasalPath: List<List<Float>>)

/** One position of the mouth: where each moving layer's points are, plus markers. */
@Serializable
data class MouthPose(
    val points: Map<String, List<List<Float>>> = emptyMap(),
    val contact: List<List<Float>> = emptyList(),
    val gap: List<List<Float>> = emptyList(),
    val airPath: List<List<Float>>? = null,
)

@Serializable
data class MouthArt(
    val version: Int = 1,
    val palette: Map<String, String>,
    val views: Map<String, MouthView>,
    val anchors: MouthAnchors,
    val poses: Map<String, MouthPose>,
)

/**
 * The mouth diagrams: artwork and every pose, read from `mouth.json`
 * (format in tools/mouth/SPEC.md). Coordinates are flat arrays x0, y0, x1, y1, ...
 */
class Mouth(val art: MouthArt) {
    val side: MouthView = art.views.getValue("side")
    val front: MouthView = art.views.getValue("front")

    private val shapeIds: List<String> = art.views.values.flatMap { v -> v.layers.filter { it.isShape }.map { it.id } }
    private val frames = HashMap<String, Map<String, FloatArray>>()

    val poseNames: Set<String> get() = art.poses.keys

    fun pose(name: String): MouthPose = art.poses[name] ?: art.poses.getValue(REST)

    /** Points of every moving layer in a pose; layers the pose does not mention stay as in rest. */
    fun frame(pose: String): Map<String, FloatArray> = synchronized(frames) {
        frames.getOrPut(pose) {
            val own = art.poses[pose]?.points.orEmpty()
            val rest = art.poses.getValue(REST).points
            shapeIds.associateWith { id -> flatten(own[id] ?: rest.getValue(id)) }
        }
    }

    /** The in-between position a fraction [t] of the way from one pose to another. */
    fun blend(from: String, to: String, t: Float): Map<String, FloatArray> {
        val a = frame(from)
        if (from == to || t <= 0f) return a
        val b = frame(to)
        if (t >= 1f) return b
        return a.mapValues { (id, pa) ->
            val pb = b.getValue(id)
            FloatArray(pa.size) { i -> pa[i] + (pb[i] - pa[i]) * t }
        }
    }

    fun colour(name: String?): String? = if (name == null) null else art.palette[name] ?: name

    /** Everything wrong with the data, for tests: empty when the file is sound. */
    fun problems(requiredPoses: Collection<String>): List<String> {
        val out = mutableListOf<String>()
        val rest = art.poses[REST]?.points
        if (rest == null) return listOf("no rest pose")
        for (name in requiredPoses) if (name !in art.poses) out += "pose $name is missing"
        for (id in shapeIds) if (rest[id].isNullOrEmpty()) out += "rest has no points for $id"
        for ((name, pose) in art.poses) for ((id, pts) in pose.points) {
            if (id !in shapeIds) out += "pose $name moves unknown layer $id"
            else if (pts.size != rest[id]?.size) out += "pose $name.$id has ${pts.size} points, rest has ${rest[id]?.size}"
            if (pts.any { it.size != 2 }) out += "pose $name.$id has a point that is not [x, y]"
        }
        for (view in art.views.values) for (layer in view.layers) {
            if (layer.type != "path" && layer.type != "shape") out += "layer ${layer.id} has unknown type ${layer.type}"
            if (layer.type == "path" && layer.d.isNullOrBlank()) out += "path layer ${layer.id} has no data"
            for (c in listOfNotNull(layer.fill, layer.stroke)) {
                if (c !in art.palette && !HEX.matches(c)) out += "layer ${layer.id} uses unknown colour $c"
            }
        }
        for (c in OVERLAY_COLOURS) if (c !in art.palette) out += "palette lacks $c"
        for ((name, value) in art.palette) if (!HEX.matches(value)) out += "palette.$name is not #RRGGBB"
        if (art.anchors.nasalPath.size < 2) out += "nasalPath is too short"
        return out
    }

    companion object {
        const val REST = "rest"
        const val SIDE = "side"
        const val FRONT = "front"
        val OVERLAY_COLOURS = listOf("contact", "air", "voice", "label")
        private val HEX = Regex("#[0-9A-Fa-f]{6}")
        private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

        fun parse(text: String): Mouth = Mouth(json.decodeFromString<MouthArt>(text))

        fun flatten(points: List<List<Float>>): FloatArray =
            FloatArray(points.size * 2) { i -> points[i / 2][i % 2] }
    }
}

/** Curve maths shared by everything that draws a mouth diagram. */
object Spline {
    /**
     * A smooth curve through [points] (x0, y0, x1, y1, ...) as cubic Bezier
     * segments. Each segment is six numbers: control 1, control 2, end point.
     * The curve starts at the first point. Uniform Catmull-Rom, as in SPEC.md.
     */
    fun cubics(points: FloatArray, closed: Boolean): FloatArray {
        val n = points.size / 2
        if (n < 2) return FloatArray(0)
        fun index(i: Int) = if (closed) ((i % n) + n) % n else i.coerceIn(0, n - 1)
        fun x(i: Int) = points[index(i) * 2]
        fun y(i: Int) = points[index(i) * 2 + 1]
        val segments = if (closed) n else n - 1
        val out = FloatArray(segments * 6)
        for (i in 0 until segments) {
            out[i * 6] = x(i) + (x(i + 1) - x(i - 1)) / 6f
            out[i * 6 + 1] = y(i) + (y(i + 1) - y(i - 1)) / 6f
            out[i * 6 + 2] = x(i + 1) - (x(i + 2) - x(i)) / 6f
            out[i * 6 + 3] = y(i + 1) - (y(i + 2) - y(i)) / 6f
            out[i * 6 + 4] = x(i + 1)
            out[i * 6 + 5] = y(i + 1)
        }
        return out
    }

    /** Points along a circular arc, angles in degrees (0 = right, 90 = down). */
    fun arc(cx: Float, cy: Float, radius: Float, fromDegrees: Float, toDegrees: Float, steps: Int): FloatArray {
        val out = FloatArray((steps + 1) * 2)
        for (i in 0..steps) {
            val a = (fromDegrees + (toDegrees - fromDegrees) * i / steps) * PI / 180.0
            out[i * 2] = cx + radius * cos(a).toFloat()
            out[i * 2 + 1] = cy + radius * sin(a).toFloat()
        }
        return out
    }
}
