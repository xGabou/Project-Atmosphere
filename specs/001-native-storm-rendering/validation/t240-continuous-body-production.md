# T240 - Continuous Storm Body in Production

**The task:** make T239's accepted continuous storm body the real production storm shape - in FINAL,
in the shared field, in the bounds, in the CPU mirror, with rain agreeing, sandbox bands re-derived,
macro variation layered back, motion and budget measured build against build.

**The result: done. The continuous body is the production storm geometry.**

- **FINAL renders it**, from a skeleton derived once per storm on the CPU and uploaded as three
  extra descriptor-texture rows per storm. A sample reads a handful of texels and evaluates one SDF;
  it no longer walks ten lobes.
- **It reproduces T239's accepted shape on three storms**, same-storm A/B against the legacy lobe
  union:
  - tower rows 35-39% of the silhouette (legacy 0-4%);
  - base height / width 0.32-0.35 (legacy 0.65-0.68);
  - anvil thickness / width 0.23-0.28 across the wind (legacy 0.40-0.44);
  - LATERAL mirror IoU 0.54-0.58 (legacy 0.78-0.85);
  - one connected body at FAR, SIDE, ABOVE and NEAREDGE.
- **It is cheaper, build against build** (paired ABAB, one session):
  - SIDE 7.63 -> **5.60 ms** (-2.0), PVN 8.07 -> **4.44 ms** (-3.6);
  - shared-field build 1.54 -> 0.77 ms;
  - FINAL temporaries 121 -> 118;
  - cost now barely depends on the storm realization (5.57 / 5.63 ms at SIDE on two storms).
- **Motion, with the temporal lattice on both builds:**
  - alpha flicker -58% and ghosting almost gone;
  - interior luma stability unchanged;
  - higher luma pop in the low-coverage ENTRY / OUTSIDE segments. The route is laid out for the
    lobe union's reach, so the compact body is covered far less on it; see MOTION.

## ARCHITECTURE

1. **The V2 layout is production.** `CloudMorphologyGenerators.PRODUCTION_STORM_TEMPLATE = 2`: the
   T237 V2 template (flat two-lobe base, column-scale tower pair, varied anvil row downwind). It is
   the skeleton: the TOWER pair gives the updraft axis, the ANVIL row the deck. New storms spawn in
   it.
2. **The skeleton is derived once on the CPU:** `StormBodySkeleton`. It is a pure function of a
   group's descriptor rows, computed by `StormLobeSpatialIndex.build` and by every live refresh in
   `StormGeometryBuildCoordinator` (so it follows growth and motion). 60 floats per group:
   - frame: origin at the lower updraft, wind axis tower-centroid -> anvil-centroid;
   - storm floor, base height (0.75 of the BASE lobes') and base up / down / left / right reach;
   - updraft axis (lowest / highest TOWER centres and heights) and radius (0.62 of the mean TOWER
     radius);
   - deck along-wind reach (0.6 R upwind, 1.2 R downwind), half-width, cross drift, bottom / top;
   - region strengths and the physical edge band;
   - a conservative world AABB;
   - the deck footprint's 11 disc radii.
3. **A compact parameter block.** The descriptor texture grows from 64 to 88 rows: after the 64
   descriptor rows, three rows per group slot (`DESCRIPTOR_HEIGHT = MAX_LOBES + 3 * MAX_GROUPS`).
   - The descriptor rows, their stride and the T172 texel contract are unchanged.
   - Rain's lobe consumers keep reading them.
4. **FINAL uses the body.** `stormBodyGroupField` (`cloud_atmosphere_volume.fsh`) replaces the
   lobe-union group walk in every program.
   - The legacy lobe union survives in the monolith only, behind `PA_LEGACY_LOBE_UNION`, which
     `leanFinalStrippedDefines` strips from every lean program (FINAL, rain pass, rain-gate build,
     campaign variants).
   - This is the T239 lesson: code is removed textually from lean programs, not constant-folded.
   - `PaStormGeometry` (monolith A/B only: 0 legacy, 1 body) and `PaBodyMacro` are baked
     `const 1` / `const 2` in lean programs; any other value takes the monolith.
5. **No per-sample lobe reconstruction.**
   - A body sample reads 10-13 texels and evaluates one SDF.
   - Far samples exit after an AABB test that fetches 3 texels and returns a valid lower bound,
     the distance to the box.
6. **The equations**, mirrored by `StormBodyEvaluator` (Java), are T239 V2's (see
   `t239-continuous-storm-body.md`), with two correctness changes for production:
   - **Deck footprint:** it is now the union of 11 discs along the deck axis, with radii from
     T239's half-width profile W(u) and centres following the cross drift. T239's clamped-u box
     overestimated the distance beyond the deck's ends: the anatomy sandbox measured horizontal
     gradients up to **8.4**. The disc union is an exact distance.
     - Shape cost: the downwind tip is disc-rounded and reaches up to ~10% of the deck length
       further than T239's box end.
   - **Base:** its superellipse term is divided by its analytic gradient magnitude.
   - **Measured Lipschitz constants:** 1.03 horizontal, 1.59 vertical (the base's height taper),
     published as 1.4 / 1.7.

## FIELD, BOUNDS, CPU MIRROR, RAIN

7. **The shared field uses the same geometry.** Its build pass (`PaStormFieldPass` 1) runs
   `cloudDensity` through the same program, so the field is built from the body.
   - In build mode the body publishes its Lipschitz bounds, which the field and the rain gate
     dilate by (×1.2 headroom). The lobe SDF used to publish per-lobe slopes; the body's are
     conservative constants, checked by the sandbox.
   - Field build cost halved (1.54 -> 0.77 ms).
8. **Conservative bounds and culling.**
   - **Per-storm AABB** from the skeleton: base reach, disc reach, column reach, plus band, the
     smooth-join bulge (0.15 Rt), rounding and a pad. The anatomy sandbox samples 45,068 in-band
     points over 128 plans: **0 outside the box**.
   - **Candidate tiles:** the AABB is unioned into each group's tile bounds. The deck's downwind
     reach passes the lobes' 1.24 R tile bound.
   - **Segment test:** a slab test against the AABB replaces the lobe spheres (which a full-width
     deck exceeds).
   - **Safe advance:** the body's clearance is `d - band` (×0.8 when positive), or the distance to
     the AABB for far samples.
   - T143's reach (lobe profile maxima ×1.56 anvil widening) encloses the body and is unchanged.
9. **The CPU mirror and camera density.**
   - `StormFieldSampler.densityAt(StormLobeDescriptor[])` / `coverageAt(...[])` - the path
     `ClientCloudVisualDensity` uses for camera-inside and whiteout - now evaluate the body.
     `StormBodyEvaluator.nearestAt` rebuilds the skeletons from the descriptors exactly as the
     upload does, then applies the same stage 5 / 6 with the body's strength and interior fill.
   - The `List` API stays lobe-based for the legacy lobe regressions (T101-T106).
   - The diagnostics `StormDensityCalibrationReport`, `StormT098RoleOccupancy` and
     `StormMaterialContinuityDiagnostics` stay lobe-based.
10. **Rain and the rain gate agree with the body by construction.**
    - Rain attach, support and ownership (`directStormLocalBaseAt`, `paRainColumnOwnedExact`,
      `paCellProvablyUnowned`, `localBaseUndersideAt`) read the BASE lobes. The body's floor and
      base footprint are derived from those same lobes (floor = lowest BASE base; reach = the BASE
      lobes' radii).
    - The rain pass and the gate build are lean programs and carry the body.
    - Forced-rain capture (`T240_M1_forced_rain.png`): shafts hang directly under the body's base;
      nothing detaches.
    - Not run: the rain-gate soundness oracle.

## SANDBOX RE-DERIVATION

11. **Legacy guards are kept, pinned to template 0.** `CloudMorphologyTopologySandbox`'s
    T127 / T133 / T098 lobe-template guards now run with `stormTemplateVersion = 0` - the legacy
    template, still spawnable for monolith A/B. They are neither deleted nor widened.
    - **Why they are obsolete for production:** they describe the lobe union's silhouette - the
      mushroom:
      - core 0.45-0.50 of base;
      - lower tower 0.65-0.75 of core;
      - anvil union span 1150-1450 blocks and 1.20-1.35× base;
      - anvil crown ≥ 0.06 T above the tower crown (V2 fails it by design: its tower reaches into
        the deck);
      - aspect 0.55-0.70.
    - None of them constrains the continuous body, whose form comes from the skeleton equations.
12. **New invariants** (`StormBodyAnatomySandbox`, run from the topology sandbox). Measured on the
    evaluated body over 128 deterministic V2 plans, each built exactly as the client builds it
    (per-stage radius scale and aspect):

    | new invariant | band | measured |
    |---|---|---|
    | complete skeleton, bit-deterministic per seed | - | yes |
    | storm height | 640-860 | 757-763 |
    | base height / along-wind footprint ("no ball") | ≤ 0.25 | 0.177 |
    | exposed updraft (base crown -> deck bottom) / height | 0.40-0.75 | 0.527-0.538 |
    | column body width max / min at 25 / 50 / 75% ("no neck, no cone, no bulge") | ≤ 1.25 | 1.090 |
    | deck mean thickness / length ("sheet, not cap") | ≤ 0.15 | 0.082-0.095 |
    | deck downwind / upwind reach | ≥ 3 | 5.47-7.45 |
    | deck cross drift / half-width | ≤ 0.25 | 0.024-0.078 |
    | updraft axis solid, base to mid-deck (d + band) | < 0 | ≤ -11.6 |
    | AABB encloses the band | 0 misses | 0 / 45,068 |
    | horizontal / vertical Lipschitz | ≤ 1.4 / 1.7 | 1.03 / 1.59 |

    - The bands are set around the intended anatomy, not the measured values: e.g. "no ball" 0.25
      is a mound twice as flat as the legacy ball's 0.65.
    - T079's live-refresh buffer grows to the full texture (`StormVolumetricGeometrySandbox`),
      because the refresh now writes the skeleton rows.

## MACRO LAYERING (identity)

13. **Arms on one storm** (`T240_M1_macro_layering_0_1_2.png`): body, body + T213 macro envelope,
    body + envelope + T230 lower-deck relief.
    - Perimeter / √area at FAR: 11.4 -> 12.2 -> 12.7. The envelope roughens the deck edges and the
      relief the underside.
    - ABOVE ellipse error: 0.109 -> 0.124 / 0.117.
    - Connectivity unchanged: one component at FAR, SIDE, LATERAL and ABOVE.
    - No underside tearing, which T238's slab had.
    - **Production bakes `PaBodyMacro = 2`.** It is production's existing large-scale variation,
      and it keeps T231's relief shading alive (it reads the same lower-deck weight).
14. **Identity is weak - production's level, not worse.** Pairwise normalized-silhouette IoU
    between three storms:

    | view | body | legacy |
    |---|---|---|
    | FAR | 0.80-0.86 | 0.80-0.87 |
    | LATERAL | 0.86-0.93 | 0.80-0.87 |
    | SIDE | 0.84-0.89 | 0.81-0.89 |
    | ABOVE | 0.87-0.91 | 0.94-0.96 |

    - The body's top-down footprint varies more than the legacy disc.
    - The template's small jitter bounds it, as does a deck that always points along world +X (the
      anvil row has always been laid out along +X regardless of the wind).

## REPRODUCTION OF T239 (same-storm A/B, neutral + region + height + lit FINAL)

15. **Storms 1, 3, 4** (production body vs legacy union on the same storm, relaid out in place):

    | metric | legacy | production body | T239 accepted |
    |---|---|---|---|
    | tower rows (FAR / LATERAL) | 0.00-0.04 | 0.33-0.35 / 0.38-0.39 | 0.34-0.36 / 0.37-0.38 |
    | tower width 25 / 50 / 75% (LATERAL, px) | - | 80-86 / 83-87 / 85-90 | 104-110 / 86-91 / 102-106 |
    | column width CV | - | 0.31-0.40 | 0.31-0.36 |
    | base height / width (FAR / LATERAL) | 0.65-0.68 | 0.32-0.35 | 0.38-0.40 |
    | anvil thickness / width (LATERAL / FAR) | 0.40-0.44 / 0.35-0.38 | 0.23-0.28 / 0.35-0.38 | 0.28-0.31 / 0.33-0.35 |
    | mirror IoU (LATERAL) | 0.78-0.85 | 0.54-0.58 | 0.52-0.57 |
    | ABOVE ellipse error | 0.061-0.066 | 0.100-0.170 | 0.087-0.111 |
    | components (FAR / NEAREDGE) | 2-3 / 2-6 | 1 / 1-8 (edge debris) | 1 / 1 |

    - The anatomy reproduces.
    - The column is a little narrower at its root and top: its width is flatter, with no
      base-junction flare in the silhouette.
    - The base is slightly flatter.
    - The lit FINAL images (`T240_S3_legacy_vs_production_FINAL.png`, last column) show it with
      production lighting.
16. **Views:**
    - LATERAL: an anvil cloud streaming downwind.
    - FAR / SIDE: a flat deck on a thick column on a mound.
    - LOWSIDE: a rising plume widening into the deck. Metric component counts there include
      hillside terrain in frame.
    - ABOVE: a fan.
    - NEAREDGE: the column rising into the deck ceiling.
    - THROUGH: the column through the transition.
      - One lit THROUGH frame (storm 3) is a grey fog frame with no storm in view, with camera
        density 0 and the other arms of the same pose normal. It is unexplained and listed as a
        residual.
      - Storm 4 lost its fixture at that same shot.

## MOTION (T207M route, temporal lattice on in both builds, one route per build)

17. **Production temporal FINAL (`temporal_025`), HEAD -> T240:**

    | metric | HEAD | T240 |
    |---|---|---|
    | alpha flicker, all | 0.00225 | 0.00094 (-58%) |
    | alpha flicker, interior | 0.00462 | 0.00249 |
    | ghosting, all | 0.0032 | 0.0001 |
    | interior luma change | 0.00574 | 0.00423 |
    | interior luma pop | 0.133 | 0.104 |
    | interior temporal / 0.5 reference (dL / pop) | 1.02 / 1.07 | 0.98 / 0.97 |

    - **ENTRY / OUTSIDE luma pop is higher** (temporal / reference 1.34 / 2.1 vs HEAD 0.25 / 1.5).
    - **Confound:** the route is laid out from the descriptor reach, sized for the lobe union, so the
      compact body is covered much less on it (T240 vs HEAD coverage):

      | segment | T240 | HEAD |
      |---|---|---|
      | APPROACH | 0.055 | 0.163 |
      | ENTRY | 0.134 | 0.392 |
      | INTERIOR | 0.453 | 0.825 |
      | OUTSIDE | 0.005 | 0.019 |

      At those coverages luma pop is dominated by sky and terrain: T240's *non-temporal
      reference* already shows 3.5× HEAD's pop in APPROACH.
    - **Verdict:** silhouette / alpha stability clearly improves; luma is neutral where the body
      fills the frame; the low-coverage luma increase is not attributable until the route is
      derived from the body's own extents (next task).
    - No lobe popping is possible by construction (one SDF). Region transitions are smooth joins.

## PERFORMANCE (build against build, paired ABAB, one session, T208 totals)

18. **Results (ms):**

    | run | SIDE total | SIDE raw cloud | field | SIDE rain | PVN total | PVN raw cloud | PVN rain |
    |---|---|---|---|---|---|---|---|
    | T240 B1 | 5.57 | 3.70 | 0.77 | 0.91 | 4.31 | 2.19 | 1.17 |
    | HEAD A1 | 6.39 | 3.80 | 1.54 | 0.87 | 7.70 | 4.40 | 1.57 |
    | T240 B2 | 5.63 | 3.79 | 0.77 | 0.89 | 4.56 | 2.44 | 1.16 |
    | HEAD A2 | 8.87 | 6.07 | 1.54 | 1.07 | 8.43 | 5.03 | 1.67 |

    - **Means:** SIDE HEAD 7.63 -> **T240 5.60** (-2.0 ms); PVN 8.07 -> **4.44** (-3.6 ms).
    - Well inside ≤ 10 and the preferred ≤ 9.5.
    - HEAD's A2 matches T235's 8.98 / 8.54 reference; A1 was a light storm.
    - T240's cost is nearly storm-independent.
19. **Codegen** (NVIDIA assembly, `isa.py` / `nvstat.py`):
    - FINAL 121 -> 118 temporaries, binary 228,202 -> 225,300 bytes;
    - rain pass 172 -> 153;
    - rain-gate build compiles.

## DECISION

20. **KEEP. Production change:** the continuous storm body is the storm geometry.
    - The V2 template spawns.
    - The body is baked into every lean program with `PaBodyMacro` 2.
    - The legacy lobe union is monolith-only, for A/B.
21. **Residuals, recorded:**
    - motion luma at low coverage (route confound, 17);
    - the one unexplained lit THROUGH frame (16);
    - the disc-rounded deck tip ~10% longer than T239's box (6);
    - the rain-gate soundness oracle not re-run (10);
    - weak identity and the always-+X deck (14);
    - lobe-based diagnostics (9);
    - storms saved in existing worlds under template 0 render as bodies from a weaker skeleton
      (their anvil row spans only -0.2..+0.52 G downwind).
22. **Next shape task - T241: storm identity and body-derived harnesses.**
    - Drive the deck along the storm's actual wind (the plan's orientation, currently unused).
    - Add seeded per-storm skeleton variation: deck reach and spread, updraft lean, base asymmetry,
      within the anatomy bands (12).
    - Derive the T098 capture poses and the T152 route from the body skeleton instead of the lobe
      reach, so motion and views are judged at the body's own distances.

## VALIDATION

23. **campaignGate:** PASS (`gate-t240.log`; also before every client run).
24. **check build:** PASS (`check-build-t240.log`).
25. **stormMorphologySandbox:** PASS (10 / 0).
26. **Topology sandbox:** PASS (`cloudMorphologyTopologySandbox`: legacy guards on template 0 and
    T240 anatomy guards on production); `stormFillOverlapAudit` runs.
27. **git status:**
    - `PAchangelog.md` modified and uncommitted; T226 untouched;
    - capture arms removed;
    - markers restored (`t098-captures.txt`; `t211` / `t212` / `t207` / `t208` retired);
    - no Gradle while a client ran; the build-switch stash popped.
28. **Commit:** on `quality/t217-deep-interior-lighting`, not merged.

## Evidence (`t240-campaign/`)

| file | what |
|---|---|
| `T240_S1_legacy_vs_body_FAR_warmup.png`, `T240_S1_body_LOWSIDE_SIDE_LATERAL.png` | storm 1: first production images, FINAL arm |
| `T240_S3_legacy_vs_production_FINAL.png` | storm 3: legacy N / lit vs body N / regions / lit FINAL, 7 views |
| `T240_M1_macro_layering_0_1_2.png`, `T240_M1_forced_rain.png` | macro layering arms; forced rain |
| `T240_S3_M1_lit_THROUGH.png` | the unexplained lit THROUGH frame beside two normal ones |
| `t240-s*.json`, `t240-m1_*.json`, `t239_shape.py`, `t240_identity.py` | silhouette / region metrics, identity |
| `t240-route-head.log`, `t240-route-t240.log`, `t240_route.py` | the motion routes and their summary |
| `t240-timing-{A1,B1,A2,B2}.log` | paired timing |
| `t240-storm-*-trace.log` | relayouts, programs per shot (`leanFinal`), camera density |
| `t240-topology-sandbox.log` | the anatomy guard output |
| `t240_capture.py`, `t240_build.sh`, `t240_shader.py` | capture set, build switch, shader restructure script |
