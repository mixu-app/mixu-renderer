// SPDX-License-Identifier: Apache-2.0
package app.mixu.renderer.view

import app.mixu.graph.Assertion
import app.mixu.graph.AssertionId
import app.mixu.graph.Attribution
import app.mixu.graph.AttributionKind
import app.mixu.graph.BoundedNeighbourhood
import app.mixu.graph.Entity
import app.mixu.graph.EntityId
import app.mixu.graph.EntityName
import app.mixu.graph.EntityType
import app.mixu.graph.EpistemicStatus
import app.mixu.graph.EvidenceAnchorId
import app.mixu.graph.NameId
import app.mixu.graph.NameKind
import app.mixu.graph.ObjectRef
import app.mixu.graph.RevealedAssertion
import app.mixu.graph.RevealedEntity
import app.mixu.renderer.layout.Point
import app.mixu.renderer.layout.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ViewsTest {
    private val holmes = EntityId("entity:holmes")
    private val watson = EntityId("entity:watson")
    private val lestrade = EntityId("entity:lestrade")

    private val holmesEntity =
        RevealedEntity(
            entity = Entity(entityId = holmes, entityType = EntityType.PERSON, firstReveal = 0),
            canonicalName =
                EntityName(
                    nameId = NameId("name:holmes"),
                    entityId = holmes,
                    displayText = "Sherlock Holmes",
                    firstReveal = 0,
                    nameKind = NameKind.CANONICAL,
                ),
            allRevealedNames = emptyList(),
            viewOrdinal = 2,
        )

    private val watsonEntity =
        RevealedEntity(
            entity = Entity(entityId = watson, entityType = EntityType.PERSON, firstReveal = 1),
            canonicalName =
                EntityName(
                    nameId = NameId("name:watson"),
                    entityId = watson,
                    displayText = "Dr. Watson",
                    firstReveal = 1,
                    nameKind = NameKind.CANONICAL,
                ),
            allRevealedNames = emptyList(),
            viewOrdinal = 2,
        )

    private val flatmatesAssertion =
        RevealedAssertion(
            assertion =
                Assertion(
                    assertionId = AssertionId("assertion:flatmates"),
                    subjectId = holmes,
                    predicate = "flatmate_of",
                    objectRef = ObjectRef.Entity(watson),
                    attribution = Attribution(kind = AttributionKind.NARRATOR),
                    firstReveal = 1,
                    evidenceAnchorId = EvidenceAnchorId("anchor:1"),
                    epistemicStatus = EpistemicStatus.OBSERVED,
                    extractionConfidence = 0.9,
                ),
            currentStatus = EpistemicStatus.OBSERVED,
            viewOrdinal = 2,
        )

    @Test
    fun neighbourhoodLayoutIsDeterministicAndStableAcrossRuns() {
        val hood =
            BoundedNeighbourhood(
                focusEntity = holmesEntity,
                neighbours = listOf(watsonEntity),
                assertions = listOf(flatmatesAssertion),
                depth = 1,
            )

        val canvas = Rect(0.0, 0.0, 800.0, 600.0)

        val layout1 = NeighbourhoodViewEngine.computeLayout("urn:edition:test", hood, canvas)
        val layout2 = NeighbourhoodViewEngine.computeLayout("urn:edition:test", hood, canvas)

        // Coordinates must match exactly
        assertEquals(layout1.focusPosition.position.x, layout2.focusPosition.position.x)
        assertEquals(layout1.focusPosition.position.y, layout2.focusPosition.position.y)

        val watsonPos1 = layout1.neighbourPositions[watson.value]
        val watsonPos2 = layout2.neighbourPositions[watson.value]
        assertNotNull(watsonPos1)
        assertNotNull(watsonPos2)
        assertEquals(watsonPos1.position.x, watsonPos2.position.x)
        assertEquals(watsonPos1.position.y, watsonPos2.position.y)
    }

    @Test
    fun hiddenEntityNeverGetsASlot() {
        // lestrade is hidden and NOT part of the neighbourhood
        val hood =
            BoundedNeighbourhood(
                focusEntity = holmesEntity,
                neighbours = listOf(watsonEntity),
                assertions = listOf(flatmatesAssertion),
                depth = 1,
            )

        val canvas = Rect(0.0, 0.0, 800.0, 600.0)
        val layout = NeighbourhoodViewEngine.computeLayout("urn:edition:test", hood, canvas)

        assertFalse(layout.neighbourPositions.containsKey(lestrade.value))
        assertEquals(1, layout.neighbourPositions.size)
    }

    @Test
    fun anchoredPositionsArePreserved() {
        val hood =
            BoundedNeighbourhood(
                focusEntity = holmesEntity,
                neighbours = listOf(watsonEntity),
                assertions = listOf(flatmatesAssertion),
                depth = 1,
            )

        val canvas = Rect(0.0, 0.0, 800.0, 600.0)
        val fixedPoint = Point(123.0, 456.0)
        val previousPositions = mapOf(watson.value to fixedPoint)

        val layout =
            NeighbourhoodViewEngine.computeLayout(
                "urn:edition:test",
                hood,
                canvas,
                previousPositions,
            )
        assertEquals(fixedPoint, layout.neighbourPositions[watson.value]?.position)
    }

    @Test
    fun filteredListViewOrdersByEvidenceStrength() {
        val aObserved = flatmatesAssertion
        val aCorroborated =
            RevealedAssertion(
                assertion =
                    Assertion(
                        assertionId = AssertionId("assertion:corroborated"),
                        subjectId = holmes,
                        predicate = "expert_consultant",
                        objectRef = ObjectRef.Literal("Consulting Detective"),
                        attribution = Attribution(kind = AttributionKind.LATER_CONFIRMATION),
                        firstReveal = 2,
                        evidenceAnchorId = EvidenceAnchorId("anchor:2"),
                        epistemicStatus = EpistemicStatus.CORROBORATED,
                    ),
                currentStatus = EpistemicStatus.CORROBORATED,
                viewOrdinal = 2,
            )
        val aClaimed =
            RevealedAssertion(
                assertion =
                    Assertion(
                        assertionId = AssertionId("assertion:claimed"),
                        subjectId = holmes,
                        predicate = "alibi",
                        objectRef = ObjectRef.Literal("at morgue"),
                        attribution = Attribution(kind = AttributionKind.WITNESS_CLAIM),
                        firstReveal = 2,
                        evidenceAnchorId = EvidenceAnchorId("anchor:2"),
                        epistemicStatus = EpistemicStatus.CLAIMED,
                    ),
                currentStatus = EpistemicStatus.CLAIMED,
                viewOrdinal = 2,
            )

        val ordered =
            FilteredListViewEngine.orderAssertions(
                listOf(aClaimed, aObserved, aCorroborated),
            )

        assertEquals(3, ordered.size)
        // Corroborated rank 1, Observed rank 2, Claimed rank 3
        assertEquals(
            AssertionId("assertion:corroborated"),
            ordered[0].assertion.assertion.assertionId,
        )
        assertEquals(AssertionId("assertion:flatmates"), ordered[1].assertion.assertion.assertionId)
        assertEquals(AssertionId("assertion:claimed"), ordered[2].assertion.assertion.assertionId)
        assertTrue(ordered[0].score > ordered[1].score)
        assertTrue(ordered[1].score > ordered[2].score)
    }
}
