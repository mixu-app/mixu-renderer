# Design: stable-layout graph canvas

Status: **Active** · Owner: mixu-core · Consumes: `mixu-graph`, `mixu-spoiler`

## Why layout stability is a requirement

A reader builds spatial memory: "the inspector is top-left, the sister is next to
her". If a new disclosure reshuffles the canvas, the feature costs more attention
than it saves, and the reader stops using it. Therefore:

- Positions are derived from a deterministic function of `(edition_id,
  entity_id, layout_version)`, not from the current node set.
- Adding nodes never moves existing nodes beyond a bounded adjustment.
- A full relayout is an explicit user action ("整理布局"), never automatic.

## Layout model

```text
seed = hash(edition_id, layout_version)
position(entity) = place(seed, entity_id, cluster(entity), bounds)
```

Deterministic placement has a known weakness: it can produce collisions and poor
clusters. Mitigation, in order:

1. Deterministic initial placement (hash-based, cluster-aware).
2. Bounded relaxation pass, applied to *new* nodes only, capped at N iterations.
3. Forced relayout available on demand, which is the only path that may move
   existing nodes.

The relaxation budget and iteration cap are named constants, not literals
scattered through the composable.

## Incremental reveal

Rendering input is a `Projection` (already filtered by `mixu-spoiler`) plus the
previous frame's state. The diff drives the animation:

```text
added   → grow from the nearest existing neighbour
removed → never happens for a forward reveal; only for a rewind, which is a
          deliberate view change and may snap
changed → cross-fade label / status badge only
```

Rewinding the reveal slider is a legitimate discontinuity: snap to the target
state rather than animating backwards through intermediate graphs, which would
be both slow and misleading.

## E-Ink policy

E-Ink devices share the same APK. The policy object decides:

| Decision | Rule |
|---|---|
| Animation | disabled by default in E-Ink mode |
| Redraw trigger | interaction end, explicit refresh, or view change only |
| Colour | never the sole encoding; high-contrast palette and distinct shapes |
| Page step | discrete pan by viewport, not free drag |
| Highlight | stroke/shape weight, not glow or fill animation |

Detection may *suggest* E-Ink mode; it must never force it. A vendor refresh API
is an optional adapter — basic operation cannot depend on one vendor.

## Accessibility contract

- Each node exposes a semantic label: type, revealed name, status summary.
- Focus order follows spatial reading order (top-to-bottom, left-to-right within
  a row), so a screen reader narrates the board predictably.
- At 200% text scale the canvas degrades to a grouped list; overlapping labels are
  a defect, not a trade-off.
- Motion respects the system reduced-motion setting independently of E-Ink mode.

## Performance budget

No numbers are asserted here. Measure first, then write the threshold:

| Metric | Instrumentation |
|---|---|
| First frame for a 300-entity projection | macrobenchmark on a mid-range device |
| Redraw cost of one node appearing | frame timing, invalidation count |
| Pan/zoom frame stability | jank percentage during a 5 s drag |
| E-Ink refresh duration | one measured full refresh on a physical device |

The gate fails when a measured regression exceeds the recorded threshold; the
threshold is updated by a reviewed change, never silently.

## Testing strategy

1. **Semantic tests** (primary): node set, interactive set, focus order, label
   presence at a given ordinal. Failures are readable and platform-stable.
2. **Golden screenshots** (secondary): layout regression on one reference device;
   allow font-metric tolerance, and never the only evidence for correctness.
3. **Spoiler regression**: forbidden ids from `mixu-fixtures` must not be in the
   semantic tree, not merely invisible.
4. **No-animation assertion**: in E-Ink mode, a static frame must be produced with
   zero animation clock ticks.

## Non-goals

- Force-directed free physics (unpredictable, expensive on E-Ink).
- 3D or perspective rendering.
- Editing the graph from the canvas (editing is an overlay operation with its own
  undo semantics).
- Deciding what may be shown — that decision arrives already made.
