package com.hskpath.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MouthTest {
    private val mouth = Mouth.parse(
        File(System.getProperty("assetsDir") ?: "../app/src/main/assets", "mouth.json").readText())

    @Test fun theShippedDiagramsAreComplete() {
        assertEquals(emptyList<String>(), mouth.problems(Sounds.shapes))
        assertEquals(1000f, mouth.side.width)
        assertEquals(800f, mouth.side.height)
        assertEquals(500f, mouth.front.width)
        assertEquals(400f, mouth.front.height)
        assertTrue(mouth.side.layers.any { it.isShape } && mouth.front.layers.any { it.isShape })
        assertTrue(mouth.side.labels.size >= 8)
    }

    @Test fun everyPoseUsedByASoundCanBeDrawn() {
        for (shape in Sounds.shapes) {
            assertTrue(shape, shape in mouth.poseNames)
            val frame = mouth.frame(shape)
            for (layer in mouth.side.layers + mouth.front.layers) if (layer.isShape) {
                assertTrue("$shape.${layer.id}", frame.getValue(layer.id).size >= 4)
            }
        }
    }

    @Test fun closuresTouchAndNarrowingsLeaveAGap() {
        for (shape in listOf("lips", "lipsNasal", "ridge", "ridgeNasal", "velar", "velarNasal", "palatal", "retroflex", "dental")) {
            assertTrue(shape, mouth.pose(shape).contact.isNotEmpty())
        }
        for (shape in listOf("lipTeeth", "velarFric", "palatalFric", "retroflexFric", "dentalFric")) {
            assertTrue(shape, mouth.pose(shape).gap.isNotEmpty())
        }
        // Every pose that has breath flowing through the mouth in some sound has a route for it.
        val oral = Sounds.all.flatMap { it.steps }.filter { it.air == Air.ORAL || it.air == Air.PUFF }.map { it.shape }.toSet()
        for (shape in oral) assertTrue("$shape needs an air path", (mouth.pose(shape).airPath?.size ?: 0) >= 2)
    }

    @Test fun posesDifferFromRestAndFromEachOther() {
        val rest = mouth.frame("rest")
        for (shape in Sounds.shapes - "rest") {
            val frame = mouth.frame(shape)
            assertTrue("$shape is identical to rest", frame.any { (id, pts) -> !pts.contentEquals(rest.getValue(id)) })
        }
        fun differs(a: String, b: String) = mouth.frame(a).any { (id, pts) -> !pts.contentEquals(mouth.frame(b).getValue(id)) }
        for ((a, b) in listOf("lips" to "lipsNasal", "ridge" to "ridgeNasal", "velar" to "velarNasal", "i" to "yu",
            "u" to "yu", "o" to "e", "ridge" to "dental", "ridge" to "retroflex", "palatal" to "ridge", "a" to "schwa")) {
            assertTrue("$a and $b look the same", differs(a, b))
        }
    }

    @Test fun blendingMovesEveryPointInAStraightLine() {
        val a = mouth.frame("rest")
        val b = mouth.frame("a")
        val half = mouth.blend("rest", "a", 0.5f)
        for ((id, pts) in half) for (i in pts.indices) {
            assertEquals((a.getValue(id)[i] + b.getValue(id)[i]) / 2f, pts[i], 0.001f)
        }
        assertArrayEquals(a.getValue(a.keys.first()), mouth.blend("rest", "a", 0f).getValue(a.keys.first()), 0f)
        assertArrayEquals(b.getValue(b.keys.first()), mouth.blend("rest", "a", 1f).getValue(b.keys.first()), 0f)
        assertEquals(a, mouth.blend("rest", "rest", 0.7f))
    }

    @Test fun posesFallBackToRestForLayersTheyDoNotMove() {
        val tiny = Mouth.parse("""{"palette":{"contact":"#FFB703","air":"#339AF0","voice":"#845EF7","label":"#1F2333","t":"#FF0000"},
            "views":{"side":{"width":1000,"height":800,"layers":[{"id":"tongue","type":"shape","fill":"t"},{"id":"lip","type":"shape","fill":"t"}]},
                     "front":{"width":500,"height":400,"layers":[{"id":"hole","type":"shape","fill":"t"}]}},
            "anchors":{"larynx":[1,2],"mouthOut":[3,4],"nasalPath":[[0,0],[1,1]]},
            "poses":{"rest":{"points":{"tongue":[[0,0],[10,0],[10,10]],"lip":[[1,1],[2,2],[3,3]],"hole":[[5,5],[6,6],[7,7]]}},
                     "a":{"points":{"tongue":[[0,10],[10,10],[10,20]]},"airPath":null,"contact":[[4,4]]}}}""")
        assertEquals(emptyList<String>(), tiny.problems(listOf("rest", "a")))
        assertArrayEquals(floatArrayOf(1f, 1f, 2f, 2f, 3f, 3f), tiny.frame("a").getValue("lip"), 0f)
        assertArrayEquals(floatArrayOf(0f, 10f, 10f, 10f, 10f, 20f), tiny.frame("a").getValue("tongue"), 0f)
        assertEquals(listOf("pose b is missing"), tiny.problems(listOf("rest", "b")))
        assertEquals("#FF0000", tiny.colour("t"))
        assertEquals("#123456", tiny.colour("#123456"))
        // An unknown pose is drawn as rest rather than crashing.
        assertArrayEquals(tiny.frame("rest").getValue("tongue"), tiny.frame("nope").getValue("tongue"), 0f)
    }

    @Test fun mismatchedPointCountsAreReported() {
        val bad = Mouth.parse("""{"palette":{"contact":"#FFB703","air":"#339AF0","voice":"#845EF7","label":"#1F2333"},
            "views":{"side":{"width":1000,"height":800,"layers":[{"id":"tongue","type":"shape","fill":"contact"}]},
                     "front":{"width":500,"height":400,"layers":[]}},
            "anchors":{"larynx":[1,2],"mouthOut":[3,4],"nasalPath":[[0,0],[1,1]]},
            "poses":{"rest":{"points":{"tongue":[[0,0],[10,0],[10,10]]}},"a":{"points":{"tongue":[[0,10],[10,10]],"ghost":[[1,1]]}}}}""")
        val problems = bad.problems(listOf("rest", "a"))
        assertTrue(problems.any { it.contains("a.tongue has 2 points, rest has 3") })
        assertTrue(problems.any { it.contains("unknown layer ghost") })
    }

    @Test fun smoothCurvesPassThroughTheirPoints() {
        val square = floatArrayOf(0f, 0f, 10f, 0f, 10f, 10f, 0f, 10f)
        val closed = Spline.cubics(square, closed = true)
        assertEquals(4 * 6, closed.size)
        // Each segment ends on the next point, and the last one returns to the start.
        assertEquals(10f, closed[4]); assertEquals(0f, closed[5])
        assertEquals(0f, closed[18 + 4]); assertEquals(0f, closed[18 + 5])
        // Control points follow the Catmull-Rom rule: P1 + (P2 - P0) / 6.
        assertEquals(0f + (10f - 0f) / 6f, closed[0], 0.0001f)
        assertEquals(0f + (0f - 10f) / 6f, closed[1], 0.0001f)
        val open = Spline.cubics(square, closed = false)
        assertEquals(3 * 6, open.size)
        assertEquals(0f, open[12 + 4]); assertEquals(10f, open[12 + 5])
        assertEquals(0, Spline.cubics(floatArrayOf(1f, 1f), closed = false).size)
        // A straight open line stays straight.
        val line = Spline.cubics(floatArrayOf(0f, 0f, 10f, 0f, 20f, 0f), closed = false)
        assertTrue((1 until line.size step 2).all { line[it] == 0f })
    }

    @Test fun arcsRunBetweenTheGivenAngles() {
        val arc = Spline.arc(100f, 100f, 50f, 0f, 90f, 2)
        assertEquals(6, arc.size)
        assertEquals(150f, arc[0], 0.001f); assertEquals(100f, arc[1], 0.001f)
        assertEquals(100f, arc[4], 0.001f); assertEquals(150f, arc[5], 0.001f)
        assertNotEquals(arc[0], arc[2])
    }
}
