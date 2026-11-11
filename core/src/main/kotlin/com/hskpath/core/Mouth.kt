package com.hskpath.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * One drawing layer of a mouth diagram. `image` layers are pieces of the real
 * picture; `path` (fixed) and `shape` (moving outline) layers are line art.
 */
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
    /** For `image` layers: the picture file, as a path inside the app's assets. */
    val src: String? = null,
    /** The part of the picture file to use (x, y, width, height in its pixels); all of it when absent. */
    val crop: List<Int>? = null,
    /** Where the picture sits in the view when nothing has moved: x, y, width, height. */
    val rect: List<Float>? = null,
    /** The grid laid over the picture, in cells across and down. 0 means the picture never moves. */
    val cols: Int = 0,
    val rows: Int = 0,
) {
    val isShape: Boolean get() = type == "shape"
    val isImage: Boolean get() = type == "image"
    /** An image that is stretched over a grid whose corners move from pose to pose. */
    val isMesh: Boolean get() = isImage && cols > 0 && rows > 0
    val gridSize: Int get() = (cols + 1) * (rows + 1) * 2
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

/** One position of the mouth: where each moving layer is, plus markers. */
@Serializable
data class MouthPose(
    /** Outline points of `shape` layers. */
    val points: Map<String, List<List<Float>>> = emptyMap(),
    /** How far each grid corner of an `image` layer has moved (dx, dy, ...), in whole numbers of `meshUnit`. */
    val mesh: Map<String, List<Int>> = emptyMap(),
    val contact: List<List<Float>> = emptyList(),
    val gap: List<List<Float>> = emptyList(),
    val airPath: List<List<Float>>? = null,
)

@Serializable
data class MouthArt(
    val version: Int = 1,
    /** Where the picture comes from, for the credits. */
    val source: String = "",
    val meshUnit: Float = 1f,
    /** Named parts of the side view that can be shown on their own: x, y, width, height. */
    val windows: Map<String, List<Float>> = emptyMap(),
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
    val side: MouthView = art.views.getValue(SIDE)
    /** A view of the lips from the front, if the artwork has one. */
    val front: MouthView? = art.views[FRONT]

    private val layers: List<MouthLayer> = art.views.values.flatMap { it.layers }
    private val shapeIds: List<String> = layers.filter { it.isShape }.map { it.id }
    private val meshes: List<MouthLayer> = layers.filter { it.isMesh }
    private val grids: Map<String, FloatArray> = meshes.associate { it.id to grid(it) }
    private val frames = HashMap<String, Map<String, FloatArray>>()

    val poseNames: Set<String> get() = art.poses.keys

    fun pose(name: String): MouthPose = art.poses[name] ?: art.poses.getValue(REST)

    /**
     * Where every moving layer is in a pose: outline points for `shape` layers,
     * grid corners for `image` layers. Layers the pose does not mention stay as in rest.
     */
    fun frame(pose: String): Map<String, FloatArray> = synchronized(frames) {
        frames.getOrPut(pose) {
            val own = art.poses[pose] ?: art.poses.getValue(REST)
            val rest = art.poses.getValue(REST)
            val out = LinkedHashMap<String, FloatArray>()
            for (id in shapeIds) out[id] = flatten(own.points[id] ?: rest.points.getValue(id))
            for (layer in meshes) {
                val grid = grids.getValue(layer.id)
                val moved = own.mesh[layer.id] ?: rest.mesh[layer.id]
                out[layer.id] = if (moved == null || moved.size != grid.size) grid
                else FloatArray(grid.size) { i -> grid[i] + moved[i] * art.meshUnit }
            }
            out
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

    /**
     * The part of the side view to show (x, y, width, height). [MOUTH] is the normal
     * close view of lips, teeth and tongue; an unknown name gives the whole view.
     */
    fun window(name: String): FloatArray {
        val w = art.windows[name]
        return if (w != null && w.size == 4 && w[2] > 0f && w[3] > 0f) w.toFloatArray()
        else floatArrayOf(0f, 0f, side.width, side.height)
    }

    /** Every picture file the artwork uses. */
    val pictures: Set<String> get() = layers.mapNotNull { it.src }.toSet()

    /** Everything wrong with the data, for tests: empty when the file is sound. */
    fun problems(requiredPoses: Collection<String>): List<String> {
        val out = mutableListOf<String>()
        val rest = art.poses[REST] ?: return listOf("no rest pose")
        for (name in requiredPoses) if (name !in art.poses) out += "pose $name is missing"
        for (id in shapeIds) if (rest.points[id].isNullOrEmpty()) out += "rest has no points for $id"
        val byId = layers.associateBy { it.id }
        if (byId.size != layers.size) out += "layer ids are not unique"
        for ((name, pose) in art.poses) {
            for ((id, pts) in pose.points) {
                if (id !in shapeIds) out += "pose $name moves unknown layer $id"
                else if (pts.size != rest.points[id]?.size) out += "pose $name.$id has ${pts.size} points, rest has ${rest.points[id]?.size}"
                if (pts.any { it.size != 2 }) out += "pose $name.$id has a point that is not [x, y]"
            }
            for ((id, moved) in pose.mesh) {
                val layer = byId[id]
                if (layer == null || !layer.isMesh) out += "pose $name moves unknown picture $id"
                else if (moved.size != layer.gridSize) out += "pose $name.$id has ${moved.size} numbers, its grid needs ${layer.gridSize}"
            }
        }
        for (layer in layers) {
            when (layer.type) {
                "path" -> if (layer.d.isNullOrBlank()) out += "path layer ${layer.id} has no data"
                "shape" -> {}
                "image" -> {
                    if (layer.src.isNullOrBlank()) out += "image layer ${layer.id} has no picture"
                    if (layer.rect?.size != 4) out += "image layer ${layer.id} needs rect [x, y, width, height]"
                    if (layer.crop != null && layer.crop.size != 4) out += "image layer ${layer.id} has a bad crop"
                    if ((layer.cols > 0) != (layer.rows > 0) || layer.cols < 0) out += "image layer ${layer.id} has a bad grid"
                }
                else -> out += "layer ${layer.id} has unknown type ${layer.type}"
            }
            for (c in listOfNotNull(layer.fill, layer.stroke)) {
                if (c !in art.palette && !HEX.matches(c)) out += "layer ${layer.id} uses unknown colour $c"
            }
        }
        if (art.meshUnit <= 0f) out += "meshUnit must be positive"
        for ((name, w) in art.windows) {
            if (w.size != 4 || w[2] <= 0f || w[3] <= 0f) out += "window $name must be [x, y, width, height]"
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
        /** The normal window: lips, teeth, tongue and the roof of the mouth. */
        const val MOUTH = "mouth"
        /** The same with room for the names of the parts. */
        const val NAMES = "names"
        val OVERLAY_COLOURS = listOf("contact", "air", "voice", "label")
        private val HEX = Regex("#[0-9A-Fa-f]{6}")
        private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

        fun parse(text: String): Mouth = Mouth(json.decodeFromString<MouthArt>(text))

        fun flatten(points: List<List<Float>>): FloatArray =
            FloatArray(points.size * 2) { i -> points[i / 2][i % 2] }

        /** The corners of an image layer's grid before anything moves, row by row. */
        fun grid(layer: MouthLayer): FloatArray {
            val r = layer.rect ?: return FloatArray(0)
            if (!layer.isMesh || r.size != 4) return FloatArray(0)
            val out = FloatArray(layer.gridSize)
            var i = 0
            for (row in 0..layer.rows) for (col in 0..layer.cols) {
                out[i++] = r[0] + r[2] * col / layer.cols
                out[i++] = r[1] + r[3] * row / layer.rows
            }
            return out
        }
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
