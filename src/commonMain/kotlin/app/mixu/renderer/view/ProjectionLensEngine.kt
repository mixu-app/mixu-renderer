// SPDX-License-Identifier: Apache-2.0
package app.mixu.renderer.view

import app.mixu.graph.EntityType
import app.mixu.graph.EpistemicStatus
import app.mixu.graph.RevealedGraphView

/**
 * Available reader graph lenses activated strictly from spoiler-filtered disclosures (INV-2 / P-SPOILER-09).
 */
enum class ProjectionLens {
    ENTITY_GRAPH,
    TIMELINE,
    HYPOTHESIS_FRAME,
    IDENTITY_RESOLUTION,
    LOCATION_MAP,
    EVIDENCE_LEDGER,
    INNER_WORK,
}

/**
 * Deterministic lens activation state derived solely from a [RevealedGraphView].
 */
data class ProjectionLensState(
    val activeLenses: List<ProjectionLens>,
    val primaryLens: ProjectionLens,
    val visibleEntityCount: Int,
    val visibleAssertionCount: Int,
    val collapsedClusterCount: Int,
    val hypothesizedLinkCount: Int,
)

/**
 * Derives UI lens availability exclusively from a [RevealedGraphView] without touching raw
 * GraphPack data or un-gated profile metadata (INV-2 / P-SPOILER-09).
 */
object ProjectionLensEngine {
    private val TIMELINE_QUALIFIER_KEYS =
        setOf(
            "story_time",
            "loop_epoch",
            "time",
            "timestamp",
            "era",
            "timeline",
            "date",
            "day",
            "loop",
            "iteration",
        )

    private val HYPOTHESIS_STATUSES =
        setOf(
            EpistemicStatus.CLAIMED,
            EpistemicStatus.INFERRED,
            EpistemicStatus.CONTRADICTED,
            EpistemicStatus.RETRACTED,
        )

    /**
     * Compute active lenses and visible summary counters solely from [view].
     */
    fun deriveLenses(view: RevealedGraphView): ProjectionLensState {
        val active = mutableListOf<ProjectionLens>()

        if (view.entities.isNotEmpty()) {
            active.add(ProjectionLens.ENTITY_GRAPH)
        }

        val hasTimelineSignal =
            view.assertions.any { revealed ->
                revealed.activeTransition != null ||
                    revealed.assertion.qualifier.keys.any { key ->
                        key.lowercase() in TIMELINE_QUALIFIER_KEYS
                    }
            }
        if (hasTimelineSignal) {
            active.add(ProjectionLens.TIMELINE)
        }

        val hasHypothesisSignal =
            view.hypothesizedIdentityLinks.isNotEmpty() ||
                view.assertions.any { revealed ->
                    revealed.currentStatus in HYPOTHESIS_STATUSES ||
                        revealed.assertion.qualifier.containsKey("hypothesis_frame") ||
                        revealed.assertion.qualifier.containsKey("frame_id")
                }
        if (hasHypothesisSignal) {
            active.add(ProjectionLens.HYPOTHESIS_FRAME)
        }

        val collapsedClusterCount = view.dsuClusters.values.count { it.size > 1 }
        if (view.collapsibleIdentityLinks.isNotEmpty() ||
            view.hypothesizedIdentityLinks.isNotEmpty() ||
            collapsedClusterCount > 0
        ) {
            active.add(ProjectionLens.IDENTITY_RESOLUTION)
        }

        if (view.entities.any { it.entity.entityType == EntityType.LOCATION }) {
            active.add(ProjectionLens.LOCATION_MAP)
        }

        if (view.assertions.isNotEmpty()) {
            active.add(ProjectionLens.EVIDENCE_LEDGER)
        }

        val hasInnerWorkSignal =
            view.assertions.any { revealed ->
                revealed.assertion.qualifier["diegetic_layer"]
                    ?.equals("INNER_WORK", ignoreCase = true) == true
            }
        if (hasInnerWorkSignal) {
            active.add(ProjectionLens.INNER_WORK)
        }

        return ProjectionLensState(
            activeLenses = active,
            primaryLens = ProjectionLens.ENTITY_GRAPH,
            visibleEntityCount = view.entities.size,
            visibleAssertionCount = view.assertions.size,
            collapsedClusterCount = collapsedClusterCount,
            hypothesizedLinkCount = view.hypothesizedIdentityLinks.size,
        )
    }
}
