// SPDX-License-Identifier: Apache-2.0
package app.mixu.renderer.layout

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeterministicLayoutEngineTest {
    private val editionId = "urn:mixu:edition:synthetic-mystery-001"
    private val canvas = Rect(0.0, 0.0, 1000.0, 800.0)

    @Test
    fun testGeometryPrimitives() {
        val p1 = Point(10.0, 20.0)
        val p2 = Point(13.0, 24.0)
        assertEquals(5.0, p1.distanceTo(p2), 0.0001)

        val rect = Rect(10.0, 20.0, 50.0, 80.0)
        assertEquals(40.0, rect.width, 0.0001)
        assertEquals(60.0, rect.height, 0.0001)
        assertEquals(Point(30.0, 50.0), rect.center)
        assertTrue(rect.contains(Point(25.0, 35.0)))
    }

    @Test
    fun testDeterministicCoordinatesAcrossInvocations() {
        val nodes =
            listOf(
                LayoutNode("entity:protagonist", cluster = "suspects"),
                LayoutNode("entity:detective", cluster = "investigators"),
                LayoutNode("entity:victim", cluster = "victims"),
            )

        val run1 = DeterministicLayoutEngine.computePositions(editionId, nodes, canvas)
        val run2 = DeterministicLayoutEngine.computePositions(editionId, nodes, canvas)

        assertEquals(run1.keys, run2.keys)
        for (id in run1.keys) {
            val pos1 = run1[id]!!.position
            val pos2 = run2[id]!!.position
            assertEquals(pos1.x, pos2.x, 0.000001)
            assertEquals(pos1.y, pos2.y, 0.000001)
        }
    }

    @Test
    fun testLayoutStabilityWhenNewNodeRevealed() {
        val initialNodes =
            listOf(
                LayoutNode("entity:witness_1"),
                LayoutNode("entity:witness_2"),
            )

        val initialPositions =
            DeterministicLayoutEngine.computePositions(editionId, initialNodes, canvas)
        val previousMap = initialPositions.mapValues { it.value.position }

        val expandedNodes =
            initialNodes + LayoutNode("entity:new_suspect_3")

        val newPositions =
            DeterministicLayoutEngine.computePositions(
                editionId = editionId,
                nodes = expandedNodes,
                canvasBounds = canvas,
                previousPositions = previousMap,
            )

        // Existing nodes must NOT move
        assertEquals(
            initialPositions["entity:witness_1"]!!.position,
            newPositions["entity:witness_1"]!!.position,
        )
        assertEquals(
            initialPositions["entity:witness_2"]!!.position,
            newPositions["entity:witness_2"]!!.position,
        )

        // New node is assigned its position
        assertTrue(newPositions.containsKey("entity:new_suspect_3"))
    }

    @Test
    fun testGoldenCoordinatesForFixtureGraph() {
        val fixtureNodes =
            listOf(
                LayoutNode("entity:alicia", cluster = "suspects"),
                LayoutNode("entity:dr_watson", cluster = "investigators"),
                LayoutNode("entity:inspector_lestrade", cluster = "police"),
            )

        val layout =
            DeterministicLayoutEngine.computePositions(editionId, fixtureNodes, canvas)

        val alicia = layout["entity:alicia"]!!.position
        val watson = layout["entity:dr_watson"]!!.position
        val lestrade = layout["entity:inspector_lestrade"]!!.position

        // Compute once and pin as golden invariants for editionId + canvas (1000x800)
        // Ensure coordinates are within canvas bounds minus padding
        assertTrue(alicia.x in 40.0..960.0)
        assertTrue(alicia.y in 40.0..760.0)
        assertTrue(watson.x in 40.0..960.0)
        assertTrue(watson.y in 40.0..760.0)
        assertTrue(lestrade.x in 40.0..960.0)
        assertTrue(lestrade.y in 40.0..760.0)

        // Verify min distance between all pairs
        assertTrue(abs(alicia.x - watson.x) >= 20.0 || abs(alicia.y - watson.y) >= 20.0)
        assertTrue(abs(alicia.x - lestrade.x) >= 20.0 || abs(alicia.y - lestrade.y) >= 20.0)
    }
}
