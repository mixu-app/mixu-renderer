// SPDX-License-Identifier: Apache-2.0
package app.mixu.renderer.interaction

import app.mixu.graph.Entity
import app.mixu.graph.EntityId
import app.mixu.graph.EntityName
import app.mixu.graph.EntityType
import app.mixu.graph.NameId
import app.mixu.graph.NameKind
import app.mixu.graph.RevealedEntity
import app.mixu.renderer.layout.NodePosition
import app.mixu.renderer.layout.Point
import app.mixu.renderer.layout.Rect
import app.mixu.renderer.view.NeighbourhoodViewModel
import kotlin.test.Test
import kotlin.test.assertEquals

class FocusOrderEngineTest {
    private fun createRevealedEntity(
        id: String,
        name: String = id,
    ): RevealedEntity {
        val entityId = EntityId(id)
        return RevealedEntity(
            entity = Entity(entityId = entityId, entityType = EntityType.PERSON, firstReveal = 0),
            canonicalName =
                EntityName(
                    nameId = NameId("name:$id"),
                    entityId = entityId,
                    displayText = name,
                    firstReveal = 0,
                    nameKind = NameKind.CANONICAL,
                ),
            allRevealedNames = emptyList(),
            viewOrdinal = 0,
        )
    }

    private fun createNodePosition(
        id: String,
        x: Double,
        y: Double,
        size: Double = 40.0,
    ): NodePosition {
        val half = size / 2.0
        return NodePosition(
            entityId = id,
            position = Point(x, y),
            bounds = Rect(x - half, y - half, x + half, y + half),
        )
    }

    @Test
    fun readingOrderSortsTopToBottomAndLeftToRightWithYBandTolerance() {
        // Row 1 (y ~ 100, within 10dp tolerance)
        val nodeB = createNodePosition("node:b", x = 150.0, y = 102.0)
        val nodeA = createNodePosition("node:a", x = 50.0, y = 100.0)
        val nodeC = createNodePosition("node:c", x = 250.0, y = 98.0)

        // Row 2 (y ~ 200)
        val nodeE = createNodePosition("node:e", x = 120.0, y = 205.0)
        val nodeD = createNodePosition("node:d", x = 40.0, y = 200.0)

        val positions = listOf(nodeE, nodeB, nodeD, nodeA, nodeC)
        val order = FocusOrderEngine.deriveFocusOrder(positions)

        assertEquals(listOf("node:a", "node:b", "node:c", "node:d", "node:e"), order)
    }

    @Test
    fun readingOrderBreaksExactXYTiesDeterministicallyByEntityId() {
        val nodeZ = createNodePosition("node:zebra", x = 100.0, y = 100.0)
        val nodeA = createNodePosition("node:alpha", x = 100.0, y = 100.0)

        val order = FocusOrderEngine.deriveFocusOrder(listOf(nodeZ, nodeA))
        assertEquals(listOf("node:alpha", "node:zebra"), order)
    }

    @Test
    fun radialFromFocusSortsFocusFirstThenClockwiseFrom12OClock() {
        val focusEntity = createRevealedEntity("entity:center")
        val focusPos = createNodePosition("entity:center", x = 200.0, y = 200.0)

        // 12 o'clock: (200, 100) -> dx = 0, dy = -100
        val node12 = createNodePosition("node:12oclock", x = 200.0, y = 100.0)
        // 3 o'clock: (300, 200) -> dx = 100, dy = 0
        val node3 = createNodePosition("node:3oclock", x = 300.0, y = 200.0)
        // 6 o'clock: (200, 300) -> dx = 0, dy = 100
        val node6 = createNodePosition("node:6oclock", x = 200.0, y = 300.0)
        // 9 o'clock: (100, 200) -> dx = -100, dy = 0
        val node9 = createNodePosition("node:9oclock", x = 100.0, y = 200.0)

        val model =
            NeighbourhoodViewModel(
                focusEntity = focusEntity,
                focusPosition = focusPos,
                neighbourPositions =
                    mapOf(
                        "node:6oclock" to node6,
                        "node:12oclock" to node12,
                        "node:9oclock" to node9,
                        "node:3oclock" to node3,
                    ),
                edges = emptyList(),
                bounds = Rect(0.0, 0.0, 400.0, 400.0),
            )

        val radialOrder =
            FocusOrderEngine.deriveFocusOrder(model, FocusOrderMode.RADIAL_FROM_FOCUS)

        assertEquals(
            listOf(
                "entity:center",
                "node:12oclock",
                "node:3oclock",
                "node:6oclock",
                "node:9oclock",
            ),
            radialOrder,
        )
    }

    @Test
    fun radialFromFocusBreaksExactAngleTiesByEntityId() {
        val focusEntity = createRevealedEntity("entity:center")
        val focusPos = createNodePosition("entity:center", x = 200.0, y = 200.0)

        // Both at 12 o'clock, different radii
        val nodeZ = createNodePosition("node:z", x = 200.0, y = 150.0)
        val nodeA = createNodePosition("node:a", x = 200.0, y = 100.0)

        val model =
            NeighbourhoodViewModel(
                focusEntity = focusEntity,
                focusPosition = focusPos,
                neighbourPositions =
                    mapOf(
                        "node:z" to nodeZ,
                        "node:a" to nodeA,
                    ),
                edges = emptyList(),
                bounds = Rect(0.0, 0.0, 400.0, 400.0),
            )

        val radialOrder =
            FocusOrderEngine.deriveFocusOrder(model, FocusOrderMode.RADIAL_FROM_FOCUS)

        assertEquals(
            listOf("entity:center", "node:a", "node:z"),
            radialOrder,
        )
    }

    @Test
    fun modelReadingOrderIncludesFocusAndNeighbours() {
        val focusEntity = createRevealedEntity("entity:center")
        // Center at y = 150
        val focusPos = createNodePosition("entity:center", x = 100.0, y = 150.0)
        // Top at y = 50
        val nodeTop = createNodePosition("node:top", x = 100.0, y = 50.0)
        // Bottom at y = 250
        val nodeBottom = createNodePosition("node:bottom", x = 100.0, y = 250.0)

        val model =
            NeighbourhoodViewModel(
                focusEntity = focusEntity,
                focusPosition = focusPos,
                neighbourPositions =
                    mapOf(
                        "node:bottom" to nodeBottom,
                        "node:top" to nodeTop,
                    ),
                edges = emptyList(),
                bounds = Rect(0.0, 0.0, 300.0, 300.0),
            )

        val readingOrder =
            FocusOrderEngine.deriveFocusOrder(model, FocusOrderMode.READING_ORDER)

        assertEquals(
            listOf("node:top", "entity:center", "node:bottom"),
            readingOrder,
        )
    }
}
