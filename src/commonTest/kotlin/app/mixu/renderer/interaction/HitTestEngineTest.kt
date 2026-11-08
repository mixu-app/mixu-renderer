// SPDX-License-Identifier: Apache-2.0
package app.mixu.renderer.interaction

import app.mixu.renderer.layout.NodePosition
import app.mixu.renderer.layout.Point
import app.mixu.renderer.layout.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class HitTestEngineTest {
    @Test
    fun returnsNullWhenNoNodesPresentOrMiss() {
        val click = Point(100.0, 100.0)
        assertNull(HitTestEngine.hitTest(click, emptyList()))

        val node =
            NodePosition(
                entityId = "node:1",
                position = Point(200.0, 200.0),
                bounds = Rect(150.0, 150.0, 250.0, 250.0),
            )
        assertNull(HitTestEngine.hitTest(click, listOf(node)))
    }

    @Test
    fun directHitReturnsTargetNode() {
        val node =
            NodePosition(
                entityId = "node:hero",
                position = Point(100.0, 100.0),
                bounds = Rect(70.0, 80.0, 130.0, 120.0),
            )
        val click = Point(95.0, 105.0)
        val hit = HitTestEngine.hitTest(click, listOf(node))

        assertNotNull(hit)
        assertEquals("node:hero", hit.entityId)
        assertEquals(node, hit.position)
    }

    @Test
    fun expandsHitBoxToSatisfyMinimumTouchTarget() {
        // Visual node is very small: 10x10 at (100, 100)
        val tinyNode =
            NodePosition(
                entityId = "node:tiny",
                position = Point(100.0, 100.0),
                bounds = Rect(95.0, 95.0, 105.0, 105.0),
            )

        // Click at (120, 100) is outside 10x10 bounds (95..105), but inside 48x48 min touch target (76..124)
        val click = Point(120.0, 100.0)
        val hit =
            HitTestEngine.hitTest(
                point = click,
                positions = listOf(tinyNode),
                config = HitTestConfig(minTouchTargetSize = 48.0),
            )

        assertNotNull(hit)
        assertEquals("node:tiny", hit.entityId)
    }

    @Test
    fun resolvesOverlappingNodesWithClosestCenterTieBreak() {
        // Node A center is at (100, 100)
        val nodeA =
            NodePosition(
                entityId = "node:A",
                position = Point(100.0, 100.0),
                bounds = Rect(50.0, 50.0, 150.0, 150.0),
            )
        // Node B center is at (120, 100)
        val nodeB =
            NodePosition(
                entityId = "node:B",
                position = Point(120.0, 100.0),
                bounds = Rect(70.0, 50.0, 170.0, 150.0),
            )

        // Click at (105, 100) is inside both Node A (dist 5) and Node B (dist 15)
        val click = Point(105.0, 100.0)
        val hit = HitTestEngine.hitTest(click, listOf(nodeA, nodeB))

        assertNotNull(hit)
        assertEquals("node:A", hit.entityId)
    }

    @Test
    fun breaksExactDistanceTieDeterministically() {
        // Two nodes symmetrical around X=100
        val nodeA =
            NodePosition(
                entityId = "node:alpha",
                position = Point(90.0, 100.0),
                bounds = Rect(40.0, 50.0, 140.0, 150.0),
            )
        val nodeZ =
            NodePosition(
                entityId = "node:zebra",
                position = Point(110.0, 100.0),
                bounds = Rect(60.0, 50.0, 160.0, 150.0),
            )

        // Click at (100, 100) is exactly 10 units away from both
        val click = Point(100.0, 100.0)

        // Lexicographical tie-break: "node:alpha" < "node:zebra"
        val hit1 = HitTestEngine.hitTest(click, listOf(nodeZ, nodeA))
        assertNotNull(hit1)
        assertEquals("node:alpha", hit1.entityId)

        // Semantic preference override: if nodeZ is preferred, it wins
        val hit2 =
            HitTestEngine.hitTest(
                point = click,
                positions = listOf(nodeZ, nodeA),
                preferredEntityId = "node:zebra",
            )
        assertNotNull(hit2)
        assertEquals("node:zebra", hit2.entityId)
    }

    @Test
    fun rejectsClicksOutsideViewportBoundary() {
        val viewport = Rect(0.0, 0.0, 500.0, 500.0)
        val edgeNode =
            NodePosition(
                entityId = "node:edge",
                position = Point(490.0, 250.0),
                bounds = Rect(450.0, 200.0, 530.0, 300.0),
            )

        // Click at (510, 250) is inside node bounds, but outside viewport (width 500)
        val clickOutside = Point(510.0, 250.0)
        val hitOutside =
            HitTestEngine.hitTest(
                point = clickOutside,
                positions = listOf(edgeNode),
                config = HitTestConfig(viewportBounds = viewport),
            )
        assertNull(hitOutside)

        // Click at (495, 250) is inside both node bounds and viewport
        val clickInside = Point(495.0, 250.0)
        val hitInside =
            HitTestEngine.hitTest(
                point = clickInside,
                positions = listOf(edgeNode),
                config = HitTestConfig(viewportBounds = viewport),
            )
        assertNotNull(hitInside)
        assertEquals("node:edge", hitInside.entityId)
    }
}
