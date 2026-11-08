// SPDX-License-Identifier: Apache-2.0
package app.mixu.renderer.layout

import kotlin.math.sqrt

/**
 * Pure 2D coordinate point without Android/Compose dependencies.
 */
data class Point(
    val x: Double,
    val y: Double,
) {
    fun distanceTo(other: Point): Double {
        val dx = x - other.x
        val dy = y - other.y
        return sqrt(dx * dx + dy * dy)
    }

    fun translate(
        dx: Double,
        dy: Double,
    ): Point = Point(x + dx, y + dy)
}

/**
 * 2D dimensions.
 */
data class Size(
    val width: Double,
    val height: Double,
) {
    init {
        require(width >= 0.0) { "width must be non-negative: $width" }
        require(height >= 0.0) { "height must be non-negative: $height" }
    }
}

/**
 * 2D bounding rectangle.
 */
data class Rect(
    val left: Double,
    val top: Double,
    val right: Double,
    val bottom: Double,
) {
    init {
        require(right >= left) { "right ($right) must be >= left ($left)" }
        require(bottom >= top) { "bottom ($bottom) must be >= top ($top)" }
    }

    val width: Double get() = right - left
    val height: Double get() = bottom - top
    val center: Point get() = Point(left + width / 2.0, top + height / 2.0)

    fun contains(point: Point): Boolean = point.x in left..right && point.y in top..bottom

    companion object {
        fun fromOrigin(size: Size): Rect = Rect(0.0, 0.0, size.width, size.height)
    }
}

/**
 * Viewport state representing camera window.
 */
data class Viewport(
    val visibleRect: Rect,
    val scale: Double = 1.0,
) {
    init {
        require(scale > 0.0) { "scale must be positive: $scale" }
    }
}
