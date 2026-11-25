// SPDX-License-Identifier: Apache-2.0
package app.mixu.renderer.view

import app.mixu.graph.Assertion
import app.mixu.graph.AssertionId
import app.mixu.graph.AssertionTransition
import app.mixu.graph.Attribution
import app.mixu.graph.AttributionKind
import app.mixu.graph.EditionId
import app.mixu.graph.Entity
import app.mixu.graph.EntityId
import app.mixu.graph.EntityName
import app.mixu.graph.EntityType
import app.mixu.graph.EpistemicStatus
import app.mixu.graph.EvidenceAnchorId
import app.mixu.graph.GraphPack
import app.mixu.graph.GraphPackManifest
import app.mixu.graph.IdentityLink
import app.mixu.graph.IdentityLinkKind
import app.mixu.graph.LinkId
import app.mixu.graph.NameId
import app.mixu.graph.NameKind
import app.mixu.graph.ObjectRef
import app.mixu.graph.Provenance
import app.mixu.graph.RevealedGraphView
import app.mixu.graph.TemporalProjectionEngine
import app.mixu.graph.TransitionId
import app.mixu.graph.WorkId
import app.mixu.renderer.layout.DeterministicLayoutEngine
import app.mixu.renderer.layout.LayoutNode
import app.mixu.renderer.layout.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProjectionLensEngineTest {
    private val editionId = EditionId("urn:edition:test-lens")
    private val workId = WorkId("urn:work:test-lens")

    private val manifest =
        GraphPackManifest(
            graphSchemaVersion = 1,
            workId = workId,
            editionId = editionId,
            graphVersion = "1.0.0",
            revealAnchorMapVersion = 1,
            checksumSha256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            packageFormat = "sqlite+zstd",
            publicationPolicy = "AUTHORIZED_PUBLIC",
            sourceExcerptPolicy = "LOCATOR_ONLY",
            createdAt = "2026-11-25T00:00:00Z",
            copyrightBasis = "SYNTHETIC",
            provenance = Provenance(validatorVersion = "1.0.0"),
        )

    private val ePerson = EntityId("entity:alpha")
    private val eLocation = EntityId("entity:manor")
    private val eAlias = EntityId("entity:stranger")

    private val nPerson = NameId("name:alpha")
    private val nLocation = NameId("name:manor")
    private val nAlias = NameId("name:stranger")

    private fun buildSyntheticPack(): GraphPack =
        GraphPack(
            manifest = manifest,
            entities =
                listOf(
                    Entity(
                        entityId = ePerson,
                        entityType = EntityType.PERSON,
                        firstReveal = 1,
                        canonicalNameId = nPerson,
                        revealCharEnd = 80,
                    ),
                    Entity(
                        entityId = eLocation,
                        entityType = EntityType.LOCATION,
                        firstReveal = 3,
                        canonicalNameId = nLocation,
                    ),
                    Entity(
                        entityId = eAlias,
                        entityType = EntityType.PERSONA,
                        firstReveal = 5,
                        canonicalNameId = nAlias,
                    ),
                ),
            names =
                listOf(
                    EntityName(
                        nameId = nPerson,
                        entityId = ePerson,
                        displayText = "Resident A",
                        firstReveal = 1,
                        nameKind = NameKind.CANONICAL,
                        revealCharEnd = 80,
                    ),
                    EntityName(
                        nameId = nLocation,
                        entityId = eLocation,
                        displayText = "East Wing",
                        firstReveal = 3,
                        nameKind = NameKind.CANONICAL,
                    ),
                    EntityName(
                        nameId = nAlias,
                        entityId = eAlias,
                        displayText = "Masked Courier",
                        firstReveal = 5,
                        nameKind = NameKind.ALIAS,
                    ),
                ),
            assertions =
                listOf(
                    Assertion(
                        assertionId = AssertionId("assertion:1"),
                        subjectId = ePerson,
                        predicate = "arrived_at",
                        objectRef = ObjectRef.Literal("noon"),
                        attribution = Attribution(AttributionKind.OBSERVATION),
                        firstReveal = 2,
                        evidenceAnchorId = EvidenceAnchorId("anchor:1"),
                        epistemicStatus = EpistemicStatus.OBSERVED,
                    ),
                    Assertion(
                        assertionId = AssertionId("assertion:2"),
                        subjectId = ePerson,
                        predicate = "alibi_claim",
                        objectRef = ObjectRef.Entity(eLocation),
                        qualifier = mapOf("time" to "21:00", "frame_id" to "frame:alibi"),
                        attribution = Attribution(AttributionKind.WITNESS_CLAIM),
                        firstReveal = 4,
                        evidenceAnchorId = EvidenceAnchorId("anchor:2"),
                        epistemicStatus = EpistemicStatus.CLAIMED,
                    ),
                ),
            transitions =
                listOf(
                    AssertionTransition(
                        transitionId = TransitionId("tr:1"),
                        assertionId = AssertionId("assertion:2"),
                        status = EpistemicStatus.CONTRADICTED,
                        revealOrdinal = 4,
                    ),
                ),
            identityLinks =
                listOf(
                    IdentityLink(
                        linkId = LinkId("link:obs"),
                        entityIds = listOf(ePerson, eAlias),
                        kind = IdentityLinkKind.CONFIRMED,
                        firstReveal = 5,
                        epistemicStatus = EpistemicStatus.OBSERVED,
                    ),
                ),
        )

    @Test
    fun emptyViewActivatesZeroLensesWithoutLeakingFuturePackStructure() {
        val empty =
            RevealedGraphView(
                editionId = editionId,
                viewOrdinal = 0,
                entities = emptyList(),
                assertions = emptyList(),
            )
        val state = ProjectionLensEngine.deriveLenses(empty)

        assertTrue(state.activeLenses.isEmpty())
        assertEquals(ProjectionLens.ENTITY_GRAPH, state.primaryLens)
        assertEquals(0, state.visibleEntityCount)
        assertEquals(0, state.visibleAssertionCount)
        assertEquals(0, state.collapsedClusterCount)
    }

    @Test
    fun progressiveHorizonsUnlockOnlyDisclosedLenses() {
        val pack = buildSyntheticPack()
        val engine = TemporalProjectionEngine(pack)

        val view1 = engine.project(1)
        val state1 = ProjectionLensEngine.deriveLenses(view1)
        assertEquals(listOf(ProjectionLens.ENTITY_GRAPH), state1.activeLenses)

        val view2 = engine.project(2)
        val state2 = ProjectionLensEngine.deriveLenses(view2)
        assertEquals(
            listOf(ProjectionLens.ENTITY_GRAPH, ProjectionLens.EVIDENCE_LEDGER),
            state2.activeLenses,
        )
        assertFalse(ProjectionLens.LOCATION_MAP in state2.activeLenses)
        assertFalse(ProjectionLens.TIMELINE in state2.activeLenses)
        assertFalse(ProjectionLens.HYPOTHESIS_FRAME in state2.activeLenses)

        val view4 = engine.project(4)
        val state4 = ProjectionLensEngine.deriveLenses(view4)
        assertTrue(ProjectionLens.TIMELINE in state4.activeLenses)
        assertTrue(ProjectionLens.HYPOTHESIS_FRAME in state4.activeLenses)
        assertTrue(ProjectionLens.LOCATION_MAP in state4.activeLenses)
        assertFalse(ProjectionLens.IDENTITY_RESOLUTION in state4.activeLenses)

        val view5 = engine.project(5)
        val state5 = ProjectionLensEngine.deriveLenses(view5)
        assertTrue(ProjectionLens.IDENTITY_RESOLUTION in state5.activeLenses)
        assertEquals(1, state5.collapsedClusterCount)
    }

    @Test
    fun prefixTruncationProducesIdenticalLensStateAndLayoutPositions_PSpoiler09() {
        val fullPack = buildSyntheticPack()
        val truncatedPack =
            GraphPack(
                manifest = manifest,
                entities = fullPack.entities.filter { it.firstReveal <= 2 },
                names = fullPack.names.filter { it.firstReveal <= 2 },
                assertions = fullPack.assertions.filter { it.firstReveal <= 2 },
            )

        val fullView =
            TemporalProjectionEngine(fullPack).project(
                viewOrdinal = 2,
                viewCharOffset = 80,
            )
        val truncView =
            TemporalProjectionEngine(truncatedPack).project(
                viewOrdinal = 2,
                viewCharOffset = 80,
            )

        assertEquals(truncView, fullView)
        assertEquals(
            ProjectionLensEngine.deriveLenses(truncView),
            ProjectionLensEngine.deriveLenses(fullView),
        )

        val bounds = Rect(0.0, 0.0, 800.0, 600.0)
        val fullNodes = fullView.entities.map { LayoutNode(it.resolvedEntityId.value) }
        val truncNodes = truncView.entities.map { LayoutNode(it.resolvedEntityId.value) }
        val fullPositions =
            DeterministicLayoutEngine.computePositions(editionId.value, fullNodes, bounds)
        val truncPositions =
            DeterministicLayoutEngine.computePositions(editionId.value, truncNodes, bounds)
        assertEquals(truncPositions, fullPositions)
    }
}
