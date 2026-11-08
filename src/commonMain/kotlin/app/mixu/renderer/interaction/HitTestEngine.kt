// SPDX-License-Identifier: Apache-2.0
package app.mixu.renderer.interaction

import app.mixu.renderer.layout.NodePosition
import app.mixu.renderer.layout.Point
import app.mixu.renderer.layout.Rect
import app.mixu.renderer.view.NeighbourhoodViewModel
import kotlin.math.abs
import kotlin.math.max

/**
 * Result of a successful hit-test query against rendered nodes.
 */
data class HitTestResult(
    val entityId: String,
    val position: NodePosition,
    val distanceToCenter: Double,
    val hitBox: Rect,
)

/**
 * Configuration parameters for hit-testing and pointer resolution.
 */
data class HitTestConfig(
    /**
     * Minimum width and height of the accessible touch target.
     * In accordance with WCAG 2.5.5 and Android accessibility standards, this defaults to 48.0 dp.
     * If a visual node is smaller, its hit target boundary is expanded symmetrically.
     */
    val minTouchTargetSize: Double = DEFAULT_MIN_TOUCH_TARGET_SIZE,
    /**
     * Optional viewport boundary. If non-null, points strictly outside this boundary are ignored.
     */
    val viewportBounds: Rect? = null,
) {
    init {
        require(minTouchTargetSize >= 0.0) {
            "minTouchTargetSize must be non-negative: $minTouchTargetSize"
        }
    }

    companion object {
        const val DEFAULT_MIN_TOUCH_TARGET_SIZE = 48.0
        val DEFAULT = HitTestConfig()
    }
}

object HitTestEngine {
    private const val DISTANCE_EPSILON = 1e-6

    /**
     * Computes the effective hit boundary for a node, expanding it symmetrically if it is
     * smaller than [minTouchTargetSize].
     */
    fun computeHitBox(
        bounds: Rect,
        minTouchTargetSize: Double = HitTestConfig.DEFAULT_MIN_TOUCH_TARGET_SIZE,
    ): Rect {
        val center = bounds.center
        val halfW = max(bounds.width / 2.0, minTouchTargetSize / 2.0)
        val halfH = max(bounds.height / 2.0, minTouchTargetSize / 2.0)
        return Rect(
            left = center.x - halfW,
            top = center.y - halfH,
            right = center.x + halfW,
            bottom = center.y + halfH,
        )
    }

    /**
     * Performs a deterministic hit-test query at [point] across candidate [positions].
     *
     * Tie-break policy (normative):
     * 1. Distance to center: The node whose center is closest to [point] wins.
     * 2. Semantic priority: If [preferredEntityId] is among tied candidates, it wins.
     * 3. Deterministic tie-break: Lexicographical order of `entityId`.
     *
     * Returns at most one [HitTestResult], or null if no node was hit.
     */
    fun hitTest(
        point: Point,
        positions: Collection<NodePosition>,
        config: HitTestConfig = HitTestConfig.DEFAULT,
        preferredEntityId: String? = null,
    ): HitTestResult? {
        if (config.viewportBounds != null && !config.viewportBounds.contains(point)) {
            return null
        }

        val candidates = mutableListOf<HitTestResult>()

        for (pos in positions) {
            val hitBox = computeHitBox(pos.bounds, config.minTouchTargetSize)
            if (hitBox.contains(point)) {
                val dist = pos.bounds.center.distanceTo(point)
                candidates.add(
                    HitTestResult(
                        entityId = pos.entityId,
                        position = pos,
                        distanceToCenter = dist,
                        hitBox = hitBox,
                    ),
                )
            }
        }

        if (candidates.isEmpty()) return null
        if (candidates.size == 1) return candidates.first()

        return candidates.minWith(
            Comparator { a, b ->
                val distDiff = a.distanceToCenter - b.distanceToCenter
                if (abs(distDiff) > DISTANCE_EPSILON) {
                    return@Comparator distDiff.compareTo(0.0)
                }

                if (preferredEntityId != null) {
                    if (a.entityId == preferredEntityId && b.entityId != preferredEntityId) {
                        return@Comparator -1
                    }
                    if (b.entityId == preferredEntityId && a.entityId != preferredEntityId) {
                        return@Comparator 1
                    }
                }

                a.entityId.compareTo(b.entityId)
            },
        )
    }

    /**
     * Convenience hit-test against a [NeighbourhoodViewModel], giving semantic precedence
     * to the focus entity on exact distance ties.
     */
    fun hitTest(
        point: Point,
        model: NeighbourhoodViewModel,
        config: HitTestConfig = HitTestConfig(viewportBounds = model.bounds),
    ): HitTestResult? {
        val allPositions = listOf(model.focusPosition) + model.neighbourPositions.values
        return hitTest(
            point = point,
            positions = allPositions,
            config = config,
            preferredEntityId = model.focusEntity.entity.entityId.value,
        )
    }
}
