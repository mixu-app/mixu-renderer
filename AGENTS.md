# mixu-renderer agent guide

## Scope and authority

- Governs only this repository. Workspace rules in `../AGENTS.md` apply.
- Public repository, **Apache-2.0**; new files start with
  `// SPDX-License-Identifier: Apache-2.0`.
- Compose Multiplatform canvas library. Depends on `mixu-graph` (model) and
  consumes `mixu-spoiler` decisions. Must not depend on `mixu-client`,
  `mixu-server` or Android app code.
- Design contract: `docs/design.md`.

## Non-negotiable rules

1. **No filtering here.** The renderer receives an already-filtered
   `Projection`. A "hidden but present" node is an architecture defect, not a
   performance trick.
2. **Stable positions.** Coordinates derive from `(edition_id, entity_id,
   layout_version)`. Adding nodes never reshuffles existing ones beyond a bounded
   adjustment; a full relayout is an explicit user action.
3. **Incremental reveal only.** New nodes grow from existing neighbours; a rewind
   is a deliberate discontinuity and may snap.
4. **Colour is never the only encoding.** Shape, stroke and label carry the same
   information for colour-blind readers and E-Ink.
5. **E-Ink is a policy, not a fork.** Animation off by default, redraw only on
   interaction end or explicit refresh, page-step navigation, discrete emphasis.
6. **Semantic tree first.** Every node exposes role, revealed name and status
   summary; keyboard focus follows spatial reading order; at 200% text scale the
   canvas degrades to a grouped list.

## Performance

- Never hardcode a budget. Record a measurement in `docs/design.md` (first frame
  for a 300-entity projection, single-node redraw, pan/zoom jank, E-Ink refresh)
  and fail the gate when a measured regression exceeds the recorded threshold.
- Named constants for iteration caps, relaxation budgets and animation
  durations. No magic numbers inside a composable.

## Testing

- Semantic assertions are primary (node set, interactive set, focus order, label
  presence). Golden screenshots are secondary and never the only evidence.
- Spoiler regression: forbidden ids from `mixu-fixtures` must be absent from the
  semantic tree, not merely invisible.
- E-Ink: assert zero animation ticks for a static frame.

## Gates

```sh
just check      # markdownlint + gradle (phase exception until wrapper lands)
```

## CarryCtx

- `cd` here before `carryctx` or Git; branches `ctx-XXXX/<type>-<slug>`.
- Export `CARRYCTX_TASK` / `CARRYCTX_AGENT` before committing.
- No commit, push or release without authorization.

## Review checklist

1. Did layout stability survive this change?
2. Is anything hidden rendered but untappable (leak by position/count)?
3. Does E-Ink mode avoid continuous redraw?
4. Is information encoded by colour alone anywhere?
5. Does a measured regression have a recorded threshold to fail against?
