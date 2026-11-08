// SPDX-License-Identifier: Apache-2.0
package app.mixu.renderer.view

import app.mixu.graph.BoundedNeighbourhood
import app.mixu.graph.ObjectRef
import app.mixu.graph.RevealedAssertion
import app.mixu.graph.RevealedEntity
import app.mixu.renderer.layout.LayoutConfig
import app.mixu.renderer.layout.LayoutNode
import app.mixu.renderer.layout.NodePosition
import app.mixu.renderer.layout.Point
import app.mixu.renderer.layout.Rect
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Renderable edge representing an assertion connecting two revealed entities.
 */
data class RenderEdge(
    val assertion: RevealedAssertion,
    val sourceEntityId: String,
    val targetEntityId: String,
    val start: Point,
    val end: Point,
)

/**
 * Complete render model for a focus-centred neighbourhood view.
 */
data class NeighbourhoodViewModel(
    val focusEntity: RevealedEntity,
    val focusPosition: NodePosition,
    val neighbourPositions: Map<String, NodePosition>,
    val edges: List<RenderEdge>,
    val bounds: Rect,
)

object NeighbourhoodViewEngine {
    /**
     * Compute a deterministic, focus-centred layout for a bounded neighbourhood.
     * Consumes Revealed* types only; hidden entities never receive a layout slot.
     */
    fun computeLayout(
        editionId: String,
        neighbourhood: BoundedNeighbourhood,
        canvasBounds: Rect,
        previousPositions: Map<String, Point> = emptyMap(),
        config: LayoutConfig = LayoutConfig(),
    ): NeighbourhoodViewModel {
        val centerX = (canvasBounds.left + canvasBounds.right) / 2.0
        val centerY = (canvasBounds.top + canvasBounds.bottom) / 2.0

        val focusId = neighbourhood.focusEntity.entity.entityId.value
        val focusPoint = previousPositions[focusId] ?: Point(centerX, centerY)

        val focusNode = LayoutNode(focusId)
        val focusBounds =
            Rect(
                focusPoint.x - focusNode.size.width / 2.0,
                focusPoint.y - focusNode.size.height / 2.0,
                focusPoint.x + focusNode.size.width / 2.0,
                focusPoint.y + focusNode.size.height / 2.0,
            )
        val focusPosition = NodePosition(focusId, focusPoint, focusBounds)

        val neighbours = neighbourhood.neighbours
        val neighbourPositions = mutableMapOf<String, NodePosition>()

        if (neighbours.isNotEmpty()) {
            val radius = min(canvasBounds.width, canvasBounds.height) * 0.35
            val angleStep = (2.0 * PI) / neighbours.size
            val sortedNeighbours = neighbours.sortedBy { it.entity.entityId.value }

            for ((index, neighbour) in sortedNeighbours.withIndex()) {
                val nId = neighbour.entity.entityId.value
                val prev = previousPositions[nId]
                val point =
                    if (prev != null) {
                        prev
                    } else {
                        val angle = index * angleStep
                        Point(
                            x = centerX + radius * cos(angle),
                            y = centerY + radius * sin(angle),
                        )
                    }

                val nNode = LayoutNode(nId)
                val nBounds =
                    Rect(
                        point.x - nNode.size.width / 2.0,
                        point.y - nNode.size.height / 2.0,
                        point.x + nNode.size.width / 2.0,
                        point.y + nNode.size.height / 2.0,
                    )
                neighbourPositions[nId] = NodePosition(nId, point, nBounds)
            }
        }

        val allPositions = mutableMapOf<String, NodePosition>()
        allPositions[focusId] = focusPosition
        allPositions.putAll(neighbourPositions)

        val edges = mutableListOf<RenderEdge>()
        for (assertion in neighbourhood.assertions) {
            val srcId = assertion.assertion.subjectId.value
            val targetId = (assertion.assertion.objectRef as? ObjectRef.Entity)?.entityId?.value

            if (targetId != null) {
                val srcPos = allPositions[srcId]
                val targetPos = allPositions[targetId]
                if (srcPos != null && targetPos != null) {
                    edges.add(
                        RenderEdge(
                            assertion = assertion,
                            sourceEntityId = srcId,
                            targetEntityId = targetId,
                            start = srcPos.position,
                            end = targetPos.position,
                        ),
                    )
                }
            }
        }

        return NeighbourhoodViewModel(
            focusEntity = neighbourhood.focusEntity,
            focusPosition = focusPosition,
            neighbourPositions = neighbourPositions,
            edges = edges,
            bounds = canvasBounds,
        )
    }
}
