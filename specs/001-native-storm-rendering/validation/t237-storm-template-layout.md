# T237 - Storm Template Layout Redesign

**The question:** does moving the storm's lobes at the template level - a broader, flatter base,
a tall and wide tower bridging base and anvil, and an anvil displaced downwind with varied lobes -
stop the storm reading as "a big round ball + a thin stick + a giant symmetric mushroom cap"?

**The answer: only from the side. REJECTED (case C); production unchanged.**

- **Cross-wind, the new template works.** Both candidates (V1, and its one revision V2) give the
  storm a clear downwind axis on three storms:
  - the anvil spreads downwind as a plate, its upwind edge near the updraft;
  - mirror IoU falls 0.83-0.85 -> 0.48-0.55, and right/left anvil area rises ~1.1 -> 2.6-3.8;
  - the tower is visible over ~50% of the silhouette height, where production shows none.
- **Along-wind and from below, it is still a mushroom.** From downwind (FAR, SIDE, LOWSIDE):
  - the nearest anvil lobe reads as a round bulb over the lower mass;
  - the lower mass is still a ball. Mode 17 shows it is now the fat TOWER lobe (radius 346,
    390 blocks tall, tapered by the shader's tower profile) instead of the BASE lobe.
- **Moving lobes cannot fix this.** The residual form is each lobe's own shape:
  - the anvil primitive flares 0.70 -> 2.10 and is stretched 1.56× vertically, so every anvil lobe
    is a small cap;
  - the tower primitive tapers 1.25 -> 0.60 with height, so a wide tower lobe is a dome, not a
    column.

  In the old layout the union buried these primitives (T236: lobe-profile edits changed ≤ 5% of the
  silhouette). The new layout exposes them.

**Correction to T236.** T236 attributed the defect to `CloudletLayout.stormLayout`. That layout is
**not** used by fixture / world storms (PA_CLUSTER): `addFieldCloudletCells` skips PA_CLUSTER
regions, so it only feeds non-cluster fields, `CloudFieldValidation` and the debug renderer. The
storm template is the server-side spawner, `CloudMorphologyGenerators.stormCell` (placement) and
`stormLobeSpec` (radius / vertical envelope). T237 edited that.

## CURRENT TEMPLATE

1. **Where it lives.**
   - `CloudGroupSpawner.spawnRequestedCloud` calls
     `CloudMorphologyGenerators.createClusterCenter` -> `stormCell`, then
     `tuneSpawnedCluster` -> `applyStormLobeEnvelope` -> `stormLobeSpec`.
   - The result syncs to the client: `VolumetricRenderCell.fromCanonicalClusterSnapshot`
     (per-stage radius scale / aspect) -> `StormLobeDescriptor` -> the GPU smooth union.
2. **Plan.**
   - 10 members, plan radius 450, group radius 400, base drop 120, top rise T = 780.
   - Members 0-1 BASE, 2-3 CORE, 4-5 TOWER, 6-9 ANVIL.
   - Member 0 is the origin.
   - The anvil row lies along world +X (the downwind axis; the plan orientation is unused).
3. **Lobes (descriptor trace, base y ≈ 136):**

   | role | y span | radii | placement |
   |---|---|---|---|
   | BASE ×2 | 136-600 (≈ 460 tall) | 457 × 412, 444 × 400 | ~30 blocks from the origin |
   | CORE ×2 | 348-702 | 267 / 241 | random angle, ≤ 30 blocks |
   | TOWER ×2 | 438-925 | 195 / 167 | random angle, ≤ 30 blocks, small +X shear |
   | ANVIL ×4 | 771-1000 | 430-440 × 250-255 | −73 … +205 along X, ±67 across |

4. **Why it is a mushroom.**
   - The four anvil lobes (radius ~440, flaring to ~900 in the shader) sit within ±200 blocks of
     the tower, so their union is a symmetric cap centred over it. The downwind spread (~280)
     is small against the cap radius.
   - The BASE pair is a 460-tall ball.
   - The TOWER is exposed only in the ~160-block gap between base top (600) and anvil root (770).
5. **Relationship guards.** `CloudMorphologyTopologySandbox` pins the T127 / T133 proportions of
   this template:
   - lowerTower / core 0.65-0.75;
   - upperTower / lowerTower 0.60-0.95;
   - anvil union span 1150-1450 and 1.20-1.35× base;
   - anvil crown ≥ 0.06 T above the tower crown;
   - aspect 0.55-0.70.

   These bands describe the mushroom's proportions, so a real redesign has to re-derive them
   (see 34).

## NEW TEMPLATE

6. **V1** (`stormTemplateVersion = 1`; `t237-template-candidates.diff`). Downwind = +X, fractions of
   plan radius R, group radius G and top rise T:
   - **BASE:**
     - placement: two lobes, the second at +0.22 R along / −0.14 R across;
     - size: radius ×1.30 / ×1.02;
     - height: top at +0.20 / +0.16 T, so ~280 blocks tall instead of 460.
   - **CORE:** radius ×0.62 / 0.58 at 0.10 / 0.18 T.
   - **TOWER:** a column-scale pair, radius ×0.60 / 0.52 (was 0.39 / 0.33):
     - centres at 0.30 / 0.58 T, leaning downwind (+0.08 / +0.18 R);
     - spanning 0.04 T (inside the base) -> 0.86 T (inside the anvil).
   - **ANVIL:** four varied lobes, radius ×0.42 / 0.62 / 0.72 / 0.60:
     - along 0.15 / 0.55 / 0.95 / 1.35 G (±12% jitter);
     - across 0.10 / −0.18 / 0.20 / −0.08 G;
     - deck at 0.80 T ± 0.10 T.
7. **V2 (the one revision, case B).** V1 left two along-wind defects:
   - a thin sky line between the tower's visible top and the anvil;
   - small anvil lobes that read as balls.

   V2 changes only the anvil: deck lowered to 0.74 T, thinner (±0.08 T), and broader lobes
   (radius ×0.55 / 0.72 / 0.86 / 0.80, along 0.10-1.30 G).
8. **Client descriptors confirmed** (`t237-storm2-trace.log`, `t237-storm3-trace.log`):

   | role | V1 | V2 |
   |---|---|---|
   | BASE | 136-412 (r 585) and 164-379 (r 459) | same as V1 |
   | CORE | 270-633 | same as V1 |
   | TOWER | 285-676 (r 346) and 553-929 (r 300) | same as V1 |
   | ANVIL deck | 777-958 | 747-894 |
   | ANVIL lobes (along X) | +57 … +580 | +38 … +485 |
   | ANVIL radii | 189-324 | 248-387 |

   - Anvil reach downwind / upwind of the tower: ~1.3 (production) -> ~5.4 (V1) / ~4.0 (V2).
   - Exposed tower: ~160 blocks (production) -> ~365 (V1) / ~335 (V2).
9. **Same-storm A/B.**
   - `CloudMorphologyGenerators.relayoutStormRegions` (TEMP) re-lays a live storm in place with a
     template version:
     - member 0 fixed;
     - every other member placed from a random seeded by region and member index;
     - radii set at target.
   - The capture driver alternates layouts per view in ABBA order, with a 150-frame extra settle.
   - `StormLobeSpatialIndex.gridSignature` hashes the tile bounds, so every relayout rebuilt the
     candidate grid (descriptors changed on every switch in the trace).
10. **Determinism / variation.**
    - Placement is a pure function of (region id, member index, version).
    - Variation comes from jitter in anvil along / cross position and lobe radius (0.42-0.72 V1,
      0.55-0.86 V2), not from new randomness in the renderer.

## SHAPE RESULT (neutral mode 1, role mode 17, height mode 18, lit)

- Sheets: `T237_S1_L0_vs_V1_neutral_role.png`, `T237_S1_FARLAT_SIDE.png`,
  `T237_S2_L0_vs_V1_sheet.png`, `T237_S2_FAR_NEAREDGE.png`, `T237_S3_L0_vs_V2_sheet.png`,
  `T237_S3_V2_FAR.png`.
- The key views are LATERAL / FAR_LAT (across the wind) and FAR / SIDE (along it).

11. **SIDE** (camera downwind at 1.7 R, looking upwind): **worse or equal.**
    - The nearest anvil lobe (now at +480 … +580) is only ~200 blocks from the camera. It fills
      the top of the frame as a round red bulb.
    - rise / width 0.36-0.38 -> 0.47-0.51; top curvature +0.22 -> +0.32 / +0.39 (more domed).
12. **LATERAL:** **better.**
    - The anvil is a plate that extends downwind from above the tower.
    - Mirror IoU 0.84 -> 0.48 (V1) / 0.50 (V2); right/left 1.1-1.7 -> 3.2-3.7.
    - The tower (green) is visible under the upwind end of the anvil.
13. **ABOVE:** not better. Minor/major 0.64-0.67 -> 0.75-0.80 (rounder). The camera sits above
    the downwind lobes, so the near lobes dominate by perspective. Storm 1's ABOVE was captured
    after its fixture was lost and is excluded.
14. **LOWSIDE:** **mushroom persists.** The lower mass is a ball and the anvil is a bulb above it.
15. **NEAREDGE:** the tower column fills the lower frame (mode 17: green where production is grey
    BASE), but the anvil underside is the same broad dark ceiling.
16. **THROUGH:** V1 only (storm 1). The tower mass is in front; otherwise similar.
17. **Distant** (FAR from four sides):
    - cross-wind (FAR_LAT, FAR_LAT_OPP): better, the same as 12;
    - along-wind (FAR, FAR_OPP): still cap-over-mass, with a thin sky line between tower and
      anvil. The neutral mask splits there (components 1-3 -> 2-3, largest 0.50), partly from the
      horizon band.
18. **Tower view** (TOWERLOW, low upwind camera): the lower storm reads as one broad foamy mass.
    The tower is not a distinct column from the ground.

## METRICS (`t237_shape.py`, `t237-metrics-*.json`)

19. **Anvil / tower (neck / anvil width):**
    - cross-wind 0.13-0.29 -> 0.25-0.29;
    - along-wind 0.17-0.23 -> 0.02-0.07. The metric falls into the sky line under the anvil.
20. **Base / tower:** base radius / tower radius 2.4 (457 / 195) -> 1.7 (585 / 346).
21. **Visible tower height** (mode 17, pure tower-role rows over silhouette height):

    | view | production | V1 / V2 |
    |---|---|---|
    | LATERAL | 0.00 | 0.47 |
    | FAR_LAT | 0.00 | 0.51-0.54 |
    | FAR | 0.00 | 0.38-0.43 |
    | SIDE | 0.00 | 0.28-0.29 |

    Tower-role pixels: 0 -> 11-30% of the silhouette.
22. **Neck width:** see 19. Wider across the wind; along the wind the neck is the sky line.
23. **Symmetry (mirror IoU):**
    - cross-wind 0.80-0.85 -> 0.48-0.55;
    - along-wind 0.80-0.84 -> 0.72-0.79.
24. **Ellipse fit (ABOVE):** error 0.05-0.08 -> 0.06; minor/major rounder (13).
25. **Downwind / upwind:**
    - right/left anvil area, cross-wind: 0.87-1.72 -> 2.6-3.8, with the mirror view reading 0.50
      (the same axis seen from the other side);
    - anvil centroid offset: +0.01-0.10 -> +0.17-0.23 of the silhouette width.
26. **Components:**
    - cross-wind: 1-2 with largest ≥ 0.99, unchanged;
    - along-wind: split at the anvil root (17).
    - Silhouette area across the wind is −35% (the new anvil is smaller).

## MULTI-STORM

27. **Storm 1** (V1): cross-wind axis present, SIDE / FAR mushroom. The fixture was lost after 9.3
    min; later views are excluded.
28. **Storm 2** (V1): the same. The priority-ordered capture got every view but FAR_OPP /
    FAR_LAT_OPP.
29. **Storm 3** (V2): the same. The lowered deck did not remove the along-wind sky line or the
    bulb.
30. **The result does not depend on the realization:**
    - all three storms give the same verdict, and every metric moves the same way on each;
    - the three storms differ as much as production storms do (jitter only).
    - V2 was seen on one storm; its rejection reasons are geometric (lobe primitives), not a
      realization effect.

## INTEGRATION (evaluated for a keep; not needed after the reject)

31. **Field:** unaffected. `CloudletLayout.stormLayout` (fields, validation) was never the fixture
    template and was not touched.
32. **Rain:** not run. V1 / V2 move the BASE pair 0.22 R downwind. Precipitation support follows
    the cluster centres, so rain would follow the base, but a keep would have needed the T207 /
    T210 rain views re-checked.
33. **Bounds:** OK. Tile bounds and the grid signature follow the new radii, and the candidate
    grid rebuilt on every relayout. The anvil now reaches ~850 blocks downwind of the origin,
    against ~650 before.
34. **Build-check:** the current sandbox guards pass (production unchanged; 10 / 0). V1 / V2
    would fail these T127 / T133 bands by design:
    - lowerTower / core 1.05 against 0.65-0.75;
    - anvil crown above tower crown 0.04 T against ≥ 0.06 T (V1);
    - anvil union span ~980 against 1150-1450, and anvil / base below 1.20.

    Keeping a new template means re-deriving those relationships, not just changing numbers.

## MOTION

35-36. **Not run.** The shape did not pass (gate as in T236).
- Observed: in all three runs the storm dissipated **9.0-9.3 min after the first relayout**
  (14:09:37 -> 14:18:53, 14:37:23 -> 14:46:38, 15:02:02 -> 15:11:01).
- T236 also lost fixtures late without relayouts, so this is the fixture's lifetime, not verified
  as caused by the relayout. The priority ordering keeps the key views inside it.

## PERFORMANCE

37-41. **Not measured.** The brief gates timing on a shape worth keeping.
- Production is unchanged: `src/main` is byte-identical to `76a7fb9f`.
- SIDE ~8.98 ms and PVN ~8.54 ms (T235) stand; ≤ 10 ms.

## DECISION

42. **REJECT (case C).**
    - V1 and V2 satisfy two targets: a clear directional axis, and an anvil displaced downwind
      with varied lobes.
    - They half-satisfy one: the tower is visible, but only across the wind.
    - They fail two:
      - the base is not broad and flat in view, because the lower mass is still a ball (now the
        tower lobe);
      - the mushroom is gone only across the wind.

      From downwind and below, which is how a player under or near the storm sees it, the storm is
      still cap-over-ball, with a bulbous anvil.
43. **Production change:** none.
    - `CloudMorphologyGenerators` / `CloudGroupSpawner` are reverted.
    - The template versions, relayout hook and capture arms are archived in
      `t237-template-candidates.diff` / `t237-capture-arms.diff`.
44. **Next task - T238, storm lobe primitives on the T237 layout.** T237 shows that layout alone
    has reached its limit: with the lobes spread out, the remaining mushroom is each lobe's own
    shape, which T236 could not see because the old union hid it. Next:
    - **anvil:** a slab primitive, i.e. a flat deck without the 0.70 -> 2.10 flare and the 1.56×
      vertical stretch, so a lobe cannot be a cap;
    - **tower:** a column primitive that does not taper to a dome;
    - **layout:** apply both **with** the V2 layout, re-deriving the T127 / T133 bands (34).
    - **judging:** the same harness and ABBA relayout on three storms, with SIDE / LOWSIDE / FAR
      (downwind) as the pass views.

    This is a primitive change judged together with the template, not a return to shader-only lobe
    cuts on the old layout.

## VALIDATION

45. **campaignGate:** PASS (`gate-t237.log`; also before every client run).
46. **check build:** PASS (`check-build-t237.log`).
47. **stormMorphologySandbox:** PASS (10 / 0, `sandbox-t237.log`).
    - One run of these three started while storm 3's client was still in its post-fixture-loss
      shots (evidence already complete).
    - All three were re-run after the client exited, with the same results.
48. **git status:**
    - `PAchangelog.md` modified and uncommitted; T226 untouched.
    - Diagnostic arms removed; markers `run/t211-captures.txt` / `run/t212-edge-trace.txt`
      retired.
49. **Commit:** on `quality/t217-deep-interior-lighting`, not merged.

## Evidence (`t237-campaign/`)

| file | what |
|---|---|
| `T237_S1_L0_vs_V1_neutral_role.png` | storm 1: FAR ×4, NEAREDGE, THROUGH; neutral / role, production vs V1 |
| `T237_S1_FARLAT_SIDE.png` | storm 1: FAR_LAT and SIDE, neutral / lit / role / height |
| `T237_S2_L0_vs_V1_sheet.png` | storm 2: LATERAL, FAR_LAT, SIDE, ABOVE, LOWSIDE, TOWERLOW; production N/R/P vs V1 N/R/P |
| `T237_S2_FAR_NEAREDGE.png` | storm 2 FAR / NEAREDGE zoom (sky line, tower column) |
| `T237_S3_L0_vs_V2_sheet.png`, `T237_S3_V2_FAR.png` | storm 3, production vs V2 |
| `t237-metrics-*.json`, `t237_shape.py` | silhouette metrics |
| `t237-storm{1,2,3}-trace.log` | relayouts, descriptor geometry per layout, fixture loss |
| `t237-template-candidates.diff`, `t237_template.py`, `t237_v2.py` | V1 / V2 templates and the relayout hook |
| `t237-capture-arms.diff`, `t237_capture.py`, `t237_capture_patch.py` | the same-storm ABBA capture set |
