# Checkpoint before the cumulus one-body rebuild (28 Oct 2026)

Taken right before cumulus moves from "union of solid lobes" to one continuous cloud body, so a regression from
the rebuild can be compared with or rolled back to this state. Nothing is committed (user rule).

## Restore

- Base commit: `e631d28349083435e1471c69cb34476869daca1a` (branch `quality/t217-deep-interior-lighting`).
- Tracked changes: `pre-one-body-2026-10-28.patch` (`git diff` at the checkpoint).
  Restore on a clean checkout of the base commit: `git apply pre-one-body-2026-10-28.patch`.
- Untracked source files: `pre-one-body-2026-10-28.untracked-src.tgz` (extract at the repo root); the full untracked
  list is in `pre-one-body-2026-10-28.untracked.txt`.

## What production renders at this checkpoint

- Storms: S11 seeded structure (PaStormStructure 15), continuous storm lighting height (PaStormMaterialFix 2),
  Storm Shadow Detail menu option (FAST 0 / BALANCED 1 / HIGH 2 / ULTRA 8 exact light taps, live uniform).
- Lighting: E20 colour pipeline (PaColorPipeline 1, octave normalise 0, ambient transmission 1, optical thickness
  gain 2).
- Performance: P1 puff occupancy, P2 field rebuilt in 4 bands, P3 empty-space jump (capped at the storm span),
  C1 1024 weather cells, C2 256 detailed lobes (8 candidates per 16-block tile, overflow keeps the 8 most central).
- Cumulus shape: `CloudMorphologyGenerators.puffLayout = 0` -> humilis / mediocris CLUSTERED (S14, accepted),
  congestus TIERED. The clustered congestus tower (`createClusteredCongestusLobes`, with the s23 no-overhang rule)
  exists but is not production (capture with `#v2`).
- Cumulus lighting: light tap 0 is the exact density walk for non-storm samples (T247), taps 1+ read the shared
  16-block field.

## Known open issues at this checkpoint (the reason for the rebuild)

- Cumulus rings / vertical cuts = hard self-shadows (E29, E30): tap 0 reads solid hard-edged lobes; lobe interiors
  are uniform max density. Not tile capacity (S20), not height01 (S22), not overhangs (s23), not the weather-map
  fallback (s16).
- v2b vertical cut present with every light-tap arm (real geometry shadow, source untraced).
- Rare congestus "box" artifact, seed-dependent, unexplained (E26).

## Temporary instrumentation in the tree (remove when its question is closed)

- `PaLightTap0Field` (S25-TEMP, live uniform, default 0) + `VolumetricCloudDebugConfig.lightTap0Field`.
- `PaPerfArm` (PERF-TEMP), lighting diagnostic modes 40 / 41 (L-TEMP), S11-TEMP skeleton log.
- S12 `PaPuffWarp` and S13 `PaPuffBlend` (live, default 0, not adopted).
- Capture sets s19-s25 in `ProductionCloudCapture`.

## Reference images

- `validation/stabilization/s14-cumulus-layout/` (accepted clustered humilis / mediocris).
- `validation/stabilization/s24-congestus-rings/` (ring diagnosis: s21, s22, s23, s24, s25).
- Re-run the same comparison after the rebuild with capture sets `s24` / `s25` (same instance names).
