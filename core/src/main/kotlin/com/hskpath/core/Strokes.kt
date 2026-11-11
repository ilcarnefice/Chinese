package com.hskpath.core

import kotlin.math.hypot

/** One stroke of a character as a centre line on a 1024 x 1024 grid (origin top left). */
data class Stroke(val points: List<Pair<Float, Float>>) {
    val start get() = points.first()
    val end get() = points.last()
    val length: Float get() = points.zipWithNext().sumOf { (a, b) -> dist(a, b).toDouble() }.toFloat()

    companion object {
        const val GRID = 1024f
        fun dist(a: Pair<Float, Float>, b: Pair<Float, Float>) = hypot(a.first - b.first, a.second - b.second)
    }
}

/** Stroke order of every character taught, read from strokes.tsv. */
class StrokeBook(text: String) {
    private val raw: Map<String, String> = text.lineSequence().filter { '\t' in it }
        .associate { it.substringBefore('\t') to it.substringAfter('\t') }

    val size: Int get() = raw.size

    fun has(character: String) = character in raw

    /** The strokes of [character] in writing order, or null if it is not covered. */
    fun strokes(character: String): List<Stroke>? = raw[character]?.split(';')?.map { stroke ->
        Stroke(stroke.split(' ').map { p -> p.substringBefore(',').toFloat() to p.substringAfter(',').toFloat() })
    }

    companion object {
        /**
         * Whether a finger-drawn line is a fair attempt at [stroke]: it must start
         * and end near the right places and be of roughly the right length.
         * [drawn] uses the same 1024 grid.
         */
        fun matches(drawn: List<Pair<Float, Float>>, stroke: Stroke, tolerance: Float = 260f): Boolean {
            if (drawn.size < 2) return false
            val startOk = Stroke.dist(drawn.first(), stroke.start) <= tolerance
            val endOk = Stroke.dist(drawn.last(), stroke.end) <= tolerance
            val drawnLength = Stroke(drawn).length
            val lengthOk = drawnLength >= stroke.length * 0.35f && drawnLength <= stroke.length * 2.6f + 120f
            // A dot is too short to judge by its end point alone.
            val tiny = stroke.length < 140f
            return startOk && lengthOk && (endOk || tiny)
        }
    }
}
