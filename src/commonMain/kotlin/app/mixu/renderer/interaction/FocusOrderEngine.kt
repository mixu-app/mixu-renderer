// SPDX-License-Identifier: Apache-2.0
package app.mixu.renderer.interaction

import app.mixu.renderer.layout.NodePosition
import app.mixu.renderer.view.NeighbourhoodViewModel
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2

/**
 * Focus ordering strategy for keyboard, screen-reader, and D-Pad accessibility navigation.
 */
enum class FocusOrderMode {
    /**
     * Visual reading order: Top-to-bottom, Left-to-right.
     */
    READING_ORDER,

    /**
     * Focus-first radial order: Focus node first, followed by clockwise satellites.
     */
    RADIAL_FROM_FOCUS,
}

object FocusOrderEngine {
    private const val Y_BAND_TOLERANCE = 10.0
    private const val ANGLE_EPSILON = 1e-6

    /**
     * Derives a deterministic focus order from node positions using visual reading order.
     * Guaranteed to match visual presentation and derived from layout positions without
     * a secondary graph traversal.
     */
    fun deriveFocusOrder(positions: Collection<NodePosition>): List<String> =
        positions
            .sortedWith(
                Comparator { a, b ->
                    val yDiff = a.bounds.top - b.bounds.top
                    if (abs(yDiff) > Y_BAND_TOLERANCE) {
                        yDiff.compareTo(0.0)
                    } else {
                        val xDiff = a.bounds.left - b.bounds.left
                        if (xDiff != 0.0) {
                            xDiff.compareTo(0.0)
                        } else {
                            a.entityId.compareTo(b.entityId)
                        }
                    }
                },
            ).map { it.entityId }

    /**
     * Derives a deterministic focus order from a [NeighbourhoodViewModel].
     *
     * In [FocusOrderMode.RADIAL_FROM_FOCUS]:
     * 1. The focus entity is first (semantic anchor).
     * 2. Neighbours follow in clockwise radial order starting from 12 o'clock.
     * 3. Deterministic tie-break by entityId.
     *
     * In [FocusOrderMode.READING_ORDER]:
     * Visual top-to-bottom, left-to-right order across all nodes.
     */
    fun deriveFocusOrder(
        model: NeighbourhoodViewModel,
        mode: FocusOrderMode = FocusOrderMode.RADIAL_FROM_FOCUS,
    ): List<String> =
        when (mode) {
            FocusOrderMode.READING_ORDER -> {
                val allPositions = listOf(model.focusPosition) + model.neighbourPositions.values
                deriveFocusOrder(allPositions)
            }
            FocusOrderMode.RADIAL_FROM_FOCUS -> {
                val focusId = model.focusEntity.entity.entityId.value
                val center = model.focusPosition.position

                val sortedNeighbours =
                    model.neighbourPositions.values
                        .sortedWith(
                            Comparator { a, b ->
                                val angleA =
                                    normalizeAngle(
                                        atan2(a.position.y - center.y, a.position.x - center.x),
                                    )
                                val angleB =
                                    normalizeAngle(
                                        atan2(b.position.y - center.y, b.position.x - center.x),
                                    )

                                val angleDiff = angleA - angleB
                                if (abs(angleDiff) > ANGLE_EPSILON) {
                                    angleDiff.compareTo(0.0)
                                } else {
                                    a.entityId.compareTo(b.entityId)
                                }
                            },
                        ).map { it.entityId }

                listOf(focusId) + sortedNeighbours
            }
        }

    private fun normalizeAngle(rad: Double): Double {
        // Shift angle so that 12 o'clock (-PI/2) is 0, progressing clockwise to 2*PI
        val shifted = rad + PI / 2.0
        return if (shifted < 0.0) shifted + 2.0 * PI else shifted
    }
}
