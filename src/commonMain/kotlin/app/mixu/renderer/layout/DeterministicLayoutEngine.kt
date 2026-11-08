// SPDX-License-Identifier: Apache-2.0
package app.mixu.renderer.layout

import kotlin.math.abs
import kotlin.math.max

data class LayoutNode(
    val entityId: String,
    val cluster: String? = null,
    val size: Size = Size(DEFAULT_NODE_WIDTH, DEFAULT_NODE_HEIGHT),
) {
    companion object {
        const val DEFAULT_NODE_WIDTH = 120.0
        const val DEFAULT_NODE_HEIGHT = 60.0
    }
}

data class NodePosition(
    val entityId: String,
    val position: Point,
    val bounds: Rect,
)

data class LayoutConfig(
    val layoutVersion: Int = 1,
    val padding: Double = 40.0,
    val minNodeDistance: Double = 30.0,
    val maxRelaxationIterations: Int = 20,
    val dampingFactor: Double = 0.85,
)

object DeterministicLayoutEngine {
    /**
     * Compute stable, deterministic positions for nodes within canvasBounds.
     * Nodes present in [previousPositions] retain their exact location.
     */
    fun computePositions(
        editionId: String,
        nodes: List<LayoutNode>,
        canvasBounds: Rect,
        previousPositions: Map<String, Point> = emptyMap(),
        config: LayoutConfig = LayoutConfig(),
    ): Map<String, NodePosition> {
        val usableLeft = canvasBounds.left + config.padding
        val usableTop = canvasBounds.top + config.padding
        val usableRight = canvasBounds.right - config.padding
        val usableBottom = canvasBounds.bottom - config.padding

        val effectiveWidth = max(100.0, usableRight - usableLeft)
        val effectiveHeight = max(100.0, usableBottom - usableTop)

        val positions = mutableMapOf<String, Point>()
        val fixedEntities = mutableSetOf<String>()

        // 1. Anchor previously placed nodes (stability invariant)
        for (node in nodes) {
            val prev = previousPositions[node.entityId]
            if (prev != null) {
                positions[node.entityId] = prev
                fixedEntities.add(node.entityId)
            }
        }

        // 2. Compute initial deterministic positions for new nodes
        for (node in nodes) {
            if (node.entityId in fixedEntities) continue

            val hash = hash64(editionId, config.layoutVersion, node.entityId, node.cluster)
            val normalizedX = (hash and 0xFFFFL).toDouble() / 0xFFFF.toDouble()
            val normalizedY = ((hash ushr 16) and 0xFFFFL).toDouble() / 0xFFFF.toDouble()

            val initX = usableLeft + normalizedX * (effectiveWidth - node.size.width)
            val initY = usableTop + normalizedY * (effectiveHeight - node.size.height)
            positions[node.entityId] = Point(initX, initY)
        }

        // 3. Bounded relaxation pass on newly revealed nodes only
        val newNodes = nodes.filter { it.entityId !in fixedEntities }
        var damping = 1.0

        for (iter in 0 until config.maxRelaxationIterations) {
            var anyMoved = false
            for (node in newNodes) {
                val currentPos = positions[node.entityId] ?: continue
                var forceX = 0.0
                var forceY = 0.0

                for (other in nodes) {
                    if (other.entityId == node.entityId) continue
                    val otherPos = positions[other.entityId] ?: continue

                    val minRequiredDistX =
                        (node.size.width + other.size.width) / 2.0 + config.minNodeDistance
                    val minRequiredDistY =
                        (node.size.height + other.size.height) / 2.0 + config.minNodeDistance

                    val dx = currentPos.x - otherPos.x
                    val dy = currentPos.y - otherPos.y

                    if (abs(dx) < minRequiredDistX && abs(dy) < minRequiredDistY) {
                        val pushX = if (dx >= 0) minRequiredDistX - dx else -minRequiredDistX - dx
                        val pushY = if (dy >= 0) minRequiredDistY - dy else -minRequiredDistY - dy
                        forceX += pushX * 0.5 * damping
                        forceY += pushY * 0.5 * damping
                        anyMoved = true
                    }
                }

                if (forceX != 0.0 || forceY != 0.0) {
                    val maxX = max(usableLeft, usableRight - node.size.width)
                    val maxY = max(usableTop, usableBottom - node.size.height)
                    val clampedX = (currentPos.x + forceX).coerceIn(usableLeft, maxX)
                    val clampedY = (currentPos.y + forceY).coerceIn(usableTop, maxY)
                    positions[node.entityId] = Point(clampedX, clampedY)
                }
            }

            if (!anyMoved) break
            damping *= config.dampingFactor
        }

        // 4. Construct final NodePosition objects
        return nodes.associate { node ->
            val pos = positions[node.entityId] ?: Point(usableLeft, usableTop)
            val bounds = Rect(pos.x, pos.y, pos.x + node.size.width, pos.y + node.size.height)
            node.entityId to NodePosition(node.entityId, pos, bounds)
        }
    }

    private fun hash64(vararg parts: Any?): Long {
        var h = 0xcbf29ce484222325UL.toLong()
        val prime = 0x100000001b3L
        for (part in parts) {
            val s = part?.toString() ?: ""
            for (ch in s) {
                h = h xor ch.code.toLong()
                h *= prime
            }
            h = h xor 0xFFL
            h *= prime
        }
        return h and 0x7FFFFFFFFFFFFFFFL
    }
}
