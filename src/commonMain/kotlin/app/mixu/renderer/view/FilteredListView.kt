// SPDX-License-Identifier: Apache-2.0
package app.mixu.renderer.view

import app.mixu.graph.EpistemicStatus
import app.mixu.graph.RevealedAssertion
import app.mixu.graph.RevealedEntity

/**
 * Ranked item in the evidence-ordered list view.
 */
data class RankedAssertionItem(
    val assertion: RevealedAssertion,
    val score: Double,
    val rank: Int,
)

/**
 * Ranked entity in the filtered entity list.
 */
data class RankedEntityItem(
    val entity: RevealedEntity,
    val assertionCount: Int,
    val primaryAssertion: RevealedAssertion?,
    val rank: Int,
)

object FilteredListViewEngine {
    /**
     * Compute evidence weight for an epistemic status.
     * CORROBORATED > OBSERVED > CLAIMED > INFERRED > UNRESOLVED > CONTRADICTED > RETRACTED.
     */
    fun epistemicWeight(status: EpistemicStatus): Double =
        when (status) {
            EpistemicStatus.CORROBORATED -> 1.0
            EpistemicStatus.OBSERVED -> 0.85
            EpistemicStatus.CLAIMED -> 0.60
            EpistemicStatus.INFERRED -> 0.50
            EpistemicStatus.UNRESOLVED -> 0.30
            EpistemicStatus.CONTRADICTED -> 0.15
            EpistemicStatus.RETRACTED -> 0.0
        }

    /**
     * Compute a deterministic evidence score for an assertion.
     */
    fun computeAssertionScore(revealed: RevealedAssertion): Double {
        val baseWeight = epistemicWeight(revealed.currentStatus)
        val confidence = revealed.assertion.extractionConfidence ?: 1.0
        return baseWeight * 0.7 + confidence * 0.3
    }

    /**
     * Order assertions by evidence strength, deterministically.
     * Higher score first, then earlier firstReveal, then assertionId ascending.
     */
    fun orderAssertions(assertions: List<RevealedAssertion>): List<RankedAssertionItem> {
        val comparator =
            compareByDescending<RevealedAssertion> { computeAssertionScore(it) }
                .thenBy { it.assertion.firstReveal }
                .thenBy { it.assertion.assertionId.value }

        val sorted = assertions.sortedWith(comparator)
        return sorted.mapIndexed { index, assertion ->
            RankedAssertionItem(
                assertion = assertion,
                score = computeAssertionScore(assertion),
                rank = index + 1,
            )
        }
    }

    /**
     * Order revealed entities by total evidence count and primary assertion strength.
     */
    fun orderEntities(
        entities: List<RevealedEntity>,
        assertions: List<RevealedAssertion>,
    ): List<RankedEntityItem> {
        val assertionsByEntity = mutableMapOf<String, MutableList<RevealedAssertion>>()
        for (assertion in assertions) {
            val src = assertion.assertion.subjectId.value
            assertionsByEntity.getOrPut(src) { mutableListOf() }.add(assertion)
        }

        val comparator =
            compareByDescending<RevealedEntity> { entity ->
                val entityAssertions = assertionsByEntity[entity.entity.entityId.value].orEmpty()
                entityAssertions.sumOf { computeAssertionScore(it) }
            }.thenBy { it.entity.firstReveal }
                .thenBy { it.entity.entityId.value }

        val sorted = entities.sortedWith(comparator)
        return sorted.mapIndexed { index, entity ->
            val entityAssertions = assertionsByEntity[entity.entity.entityId.value].orEmpty()
            val primary = entityAssertions.maxByOrNull { computeAssertionScore(it) }
            RankedEntityItem(
                entity = entity,
                assertionCount = entityAssertions.size,
                primaryAssertion = primary,
                rank = index + 1,
            )
        }
    }
}
