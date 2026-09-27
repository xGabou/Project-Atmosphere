# T239 - Continuous Storm Body

**The question:** if the storm is one continuous convective shape instead of a union of ten
role lobes, does it stop reading as ball + neck + cap?

**The answer: yes. The continuous body is the accepted shape basis (KEEP).** Production is
unchanged. The body exists only in the monolith as an experimental mode
(`PaStormGeometry`), and its production integration is the next task.

- **Same storm, ABBA, three realizations** (plus V1 on a fourth), under constant lighting, with
  mode 17 showing the new structural regions. The storm reads as:
  - a broad, flat-bottomed lower mass (base height / width 0.38-0.40, production 0.64-0.68);
  - a continuous updraft column, visible over 34-37% of the silhouette height (production
    3-9%), with near-constant width (CV 0.31-0.35, e.g. 110 / 91 / 106 px at 25 / 50 / 75%);
  - a smooth expansion into the deck;
  - a flat downwind deck (thickness / width 0.28-0.31 across the wind, production 0.41-0.46).
- It is one connected component in every view (production: 2-8 at NEAREDGE / LATERAL).
- The top-down footprint is a fan, not an ellipse.
- **Residuals:**
  - Along the wind, the silhouette is an hourglass: a column ~40% of the deck width between a
    mound and a broad deck.
  - Seen from under the deck's downwind tip (SIDE), the deck looms large and rounded.
  - Storm identity is weak, as weak as production's. Variety has to come from layering the
    macro fields (Task 7), which T239 deliberately did not start.

**Task 1: the overlap-fill hypothesis is disproved as the cause of the wispy tower.** The rule
exists, but:
- it only raises the interior floor, by 2-5% in mean density;
- it acts only at coverage ≥ 0.82, which a narrow lobe barely reaches;
- what makes a narrow lobe ragged is that its soft edge band scales with the lobe's radius, so a
  narrow lobe is mostly edge band.

The continuous body therefore uses a fixed physical edge band and owns its interior fill
explicitly.

## ATTRIBUTION (Task 1)

1. **Starting HEAD:** `32466c8e` (T238). **Branch:** `quality/t217-deep-interior-lighting`, not
   merged. `PAchangelog.md` preserved; T226 untouched. campaignGate PASS before every client
   run; no Gradle while a client was up.
2. **The rule (the hypothesis):** `stormCoverageLowerBound(coverage, strength, overlap)` forms the
   body against a lower bound whose fill depends on overlap.
   - Where `(activeRoleMask & 7) == 7` (BASE, CORE and TOWER all within their softness), it uses
     the **continuity fill** (`STORM_STRUCTURAL_CONTINUITY_DENSITY` 0.10 retained through the
     5th-percentile erosion bite, ramped in over coverage 0.82-0.90 and strength 0.84-0.87).
   - Everywhere else it uses the **strength fill** (`max(0.45, 1 / (s · 0.717) − 1 + 0.021)`).
   - Three sites apply it: `directStormFinalDensity`, the field build and the primary march.
   - Measured with `StormFillOverlapAudit` (instrument, `./gradlew stormFillOverlapAudit`):
     - the CPU composition mirror (`StormDensityModel`, identical stage 5 / 6);
     - on the real baked base / detail volumes;
     - 200,000 noise samples shared by every row, so the rule is the only variable;
     - plus a 12-block grid over the live production and V2 storms, from their traced
       descriptors.
3. **Base-only** (strength 0.89):

   | coverage | fill | pre-erosion body | post-erosion | 5th pct | holes (< 0.02) |
   |---|---|---|---|---|---|
   | 1.0 | 0.588 | 0.705 | 0.475 | 0.140 | 0 |
   | 0.9 | | | 0.442 | 0.072 | 0.5% |
   | 0.8 | | | | | 9.7% |

   In the live V2 storm the base-only interior is 1.7% of interior points, mean 0.332,
   holes 6.9%.
4. **Tower-only** (strength 0.919 / 0.877):

   | coverage | fill | post-erosion | 5th pct | holes |
   |---|---|---|---|---|
   | 1.0 | 0.539 / 0.611 | 0.465 / 0.479 | 0.121 / 0.149 | 0 |
   | 0.9 | | | 0.050 / 0.082 | 1.6% / 0.25% |

   In the live V2 storm the tower-only interior is 2.5% of interior points, mean 0.437,
   holes 3.2%. In production it is 0%.
5. **Overlap B + C + T** (strength 0.93):

   | coverage | fill | post-erosion | 5th pct | holes |
   |---|---|---|---|---|
   | 1.0 | 0.642 | 0.485 | 0.161 | 0 |
   | 0.9 | 0.822 | 0.484 | 0.160 | 0 |

   It is 48-51% of interior points in both live storms, mean 0.475-0.480, holes 0.15-0.22%.
6. **Erosion comparison:**
   - Mean erosion is identical for every rule (0.230): erosion subtracts the same detail term,
     and the fill only moves the floor it is subtracted from.
   - At coverage ≤ 0.80 every rule gives identical rows. Holes are 8-13% at 0.80 and 21-31% at
     0.60.
   - Coverage never exceeds the lobe strength (0.88-0.92 for towers).
   - The soft band is `0.62 · edgeSoftness · minRadius` (T212 trace: ~44-75 blocks for towers),
     so a narrow lobe is mostly the band where the rule does nothing.
   - Anvil-only (strength 0.78) actually gets the largest fill (0.809), because the strength fill
     compensates for low strength.
7. **Hypothesis: CONFIRMED that the rule exists, DISPROVED as the cause.** Overlap buys a
   2-5% higher interior mean and a higher erosion floor (5th percentile 0.16 vs 0.05-0.15), not
   the difference between solid and wispy. The consequence for T239 is the band, not the rule:
   the continuous body uses a fixed physical edge band and an explicit fill (14).

## CONTINUOUS MODEL (`paContinuousStormGroupField`, `PaStormGeometry` 1)

8. **Coordinate system.**
   - One per storm group, built from that storm's descriptors (which act only as skeleton data).
   - Origin: the lower updraft (the lowest TOWER descriptor's centre).
   - `û`: along the wind, from the tower centroid to the radius-weighted anvil centroid.
   - `ĉ`: across it; `y` up.
   - `w = (p − O)·û`, `c = (p − O)·ĉ`; the heights are the storm floor (lowest BASE base), the
     base crown, and the deck bottom / top (ANVIL descriptors).
   - Deterministic: a pure function of the descriptors.
   - It needs a directional skeleton (the T237 V2 layout, `stormTemplateVersion` 2). On the
     production layout the anvil lobes surround the tower and the wind axis is weak.
9. **Base:** a flat-bottomed broad slab.
   - Height: 0.75 of the base lobes' height.
   - Footprint: a superellipse (n = 3) over the base lobes' separate upwind / downwind and
     left / right extents in the wind frame. The asymmetry comes from the data.
   - Radius scale `1 − 0.20 t²` over its height; edges rounded by 0.18 of its height.
   - `d = roundedBox(horizontal (r₃ − scale) · min(extent), vertical slab)`.
10. **Tower radius:** `R(y) = Rt · (1 + 0.30 (1 − smoothstep(−0.4, 0.25, t)) + 0.30 smoothstep(0.5, 1.2, t))`.
    - `Rt` = 0.62 × the mean tower descriptor radius (~200 blocks).
    - `t` runs from the base crown (0) to the deck bottom (1).
    - So: 1.3 where it meets the base, 1.0 through the middle, ~1.25 entering the deck.
    - No cone, no root bulge.
    - The axis follows the tower descriptors' centres, so the column leans with them.
    - It spans from 0.1 of the base height to mid-deck.
11. **Transition:** smooth-min joins, `k = 0.6 Rt`, base ∪ column and then column ∪ deck. The
    column flares continuously into the deck; there is no separate primitive and no gap.
12. **Deck equation.** Along-wind `u = (w − wLo) / (wHi − wLo)`, where:
    - `wLo = min(w_i − 0.6 R_i)`: a compact upwind root;
    - `wHi = max(w_i + 1.2 R_i)`: a long downwind reach.

    Shape over `u`:
    - thickness `span · (0.85 − 0.45 u)`, thinning downwind;
    - top `deckTop − 0.05 span · u`;
    - cross half-width fanning from 1.3 Rt at the root to the full lobe cross extent
      `max(|c_i| + 1.4 minor_i)` by u = 0.45;
    - root and tip rounded (×√(1 − 0.6 root²), ×√(1 − 0.85 tip²));
    - the centreline drifts toward the lobes' mean cross offset;
    - `d = roundedBox(along, cross, vertical)`, flat top, rounding ≤ 20 blocks.
13. **Asymmetry / wind:**
    - the wind axis and all extents come from that storm's descriptors;
    - the deck is always downwind;
    - the base is asymmetric (up / down / left / right extents);
    - the column leans with the tower descriptors;
    - the deck centreline drifts across the wind.

    Macro irregularity (the T213 envelope, the T231 relief) is off in this mode, per Task 7:
    clean primitive first.
14. **Fill semantics:**
    - Edge band: `clamp(0.20 Rt, 24, 48)` blocks, a fixed physical width (deck: ≤ 0.3 ×
      thickness).
    - Interior fill:
      - base, updraft and transition interiors (`d < −band`) take the continuity fill, as the old
        overlap did;
      - the deck takes the strength fill.
    - Strength: region strengths from the descriptors, blended by the join weights.
    - Clearance: `d − band` (×0.8 when positive).
    - Regions for mode 17 (mode 18 shows region-local height):

      | region | mask | colour |
      |---|---|---|
      | BASE | 1 | grey |
      | TRANSITION | 2 | blue |
      | UPDRAFT | 4 | green |
      | ANVIL | 8 | red |

## PURE SHAPE (neutral mode 1 + regions mode 17 + height mode 18 + lit, same storm, ABBA)

- **V1** (storm 1): the first shape. One body and a real column (see 23), but a narrow deck and a
  domed head: top curvature FAR +0.12 -> +0.51, LATERAL anvil thickness / width 0.62, area 40% of
  production.
- **V2** (the one structural revision, storms 2-4) made the deck a broad sheet:
  - cross width fans to the full lobe extent;
  - 1.2 R downwind reach;
  - thickness 0.85 -> 0.40;
  - flat top;
  - smaller join and column flare;
  - broader, flatter base.

  Results below are V2 unless noted.

15. **SIDE** (camera 1.7 R downwind, under the deck tip):
    - a flat-topped deck over a thick waist over a mound; one component (production: 1-5);
    - tower rows 17-21%, column width 119 / 103 / 118 px;
    - the deck looms large and rounded from this camera. This is the closest to the old read.
16. **LOWSIDE:** the column widens into the deck overhead; the base is a low mound; tower rows
    12-18% (production ~1%). No ball.
17. **FAR / downwind:**
    - a flat deck on a column on a mound, one component;
    - tower rows 34-36% (production 3-9%);
    - base height / width 0.38-0.39 (production 0.65-0.68);
    - the anvil read as a deck, not a cap: thickness / width 0.33-0.35, crest curvature
      +0.08-0.09 (production +0.12-0.13).
18. **LATERAL:** a textbook anvil cloud.
    - The deck streams downwind from the top of the column.
    - Mirror IoU 0.52-0.57 (production 0.78-0.85); anvil thickness / width 0.28-0.31.
    - One component (production 4-7).
19. **ABOVE:** a fan / teardrop, with the transition at the updraft end and the deck widening
    downwind.
    - Ellipse-fit error 0.087-0.111 (production 0.057-0.062).
    - minor / major 0.92-0.95 (the near-lobe perspective of this camera, as in T237 / T238).
20. **NEAREDGE:** the column rises from the mound into the deck ceiling; one component
    (production 4-8).
21. **THROUGH** (valid on storms 3 and 4; lost to fixture dissipation on storm 2 and for the lit
    arm): the column rises through the transition into the deck from inside the storm's footprint.
22. **Tower view** (TOWERLOW, low and distant): base + column + deck to the downwind side; tower
    rows 12-19%.

## METRICS (`t239_shape.py`, `t239-s*.json`; production -> V2, storms 2-4)

23. **Visible tower fraction** (tower rows / silhouette height):

    | view | production | V2 | V1 |
    |---|---|---|---|
    | FAR | 0.03-0.09 | 0.34-0.36 | 0.29 |
    | LATERAL | 0.04-0.08 | 0.37-0.38 | 0.29 |
    | SIDE | 0.04-0.06 | 0.17-0.21 | — |

24. **Tower width, 25 / 50 / 75%** (px):

    | view | 25% | 50% | 75% |
    |---|---|---|---|
    | LATERAL | 104-110 | 86-91 | 102-106 |
    | FAR | 68-72 | 63-67 | 73-76 |
    | SIDE | 119-120 | 99-105 | 109-118 |

    CV 0.31-0.36. Near-constant with a mild waist, no collapse at the top. Production has no
    measurable tower.
25. **Neck width:** the minimum column width is 63-105 px. Anvil / tower width is 2.1-2.4 across
    the wind and 3.0-3.2 at FAR. Production is undefined, because its tower is invisible.
26. **Base aspect** (height / width): 0.38-0.40 at FAR / LATERAL (production 0.64-0.70).
27. **Anvil aspect** (thickness / width): 0.28-0.31 across the wind and 0.33-0.35 at FAR
    (production 0.41-0.46 / 0.38-0.41). V1 was 0.62.
28. **Symmetry (mirror IoU):**
    - LATERAL 0.78-0.85 -> 0.52-0.57;
    - FAR 0.80-0.83 -> 0.89-0.90 (seen along the axis the storm is symmetric, as a real one is).
29. **Ellipse fit (ABOVE):** error 0.057-0.062 -> 0.087-0.111.
30. **Downwind / upwind:**
    - right / left area LATERAL 0.96-1.18 -> 1.21-1.77;
    - centroid offset −0.01-+0.04 -> +0.03-+0.10;
    - the whole deck lies downwind (mode 17: red only on the downwind side).
31. **Connected components:** 1 in FAR, SIDE, LATERAL, ABOVE and NEAREDGE for every V2 storm
    (production 1-8); LOWSIDE 4-6 (production 4-7, clipped frames).

## MULTI-STORM

32. **Storm 1** (V1): one body and a real column; narrow, domed head; small deck. It prompted the
    V2 revision.
33. **Storm 2** (production / V1 / V2 on the same storm): V2 flattens V1's head into a deck and
    doubles the deck's reach and width.
34. **Storms 3 and 4** (V2): the same anatomy and metrics within ±0.03.
35. **Identity:** preserved at production's level, which is weak. Pairwise normalized-silhouette
    IoU between storms:

    | view | production | V2 |
    |---|---|---|
    | FAR | 0.82-0.89 | 0.90-0.93 |
    | LATERAL | 0.81-0.85 | 0.84-0.90 |
    | SIDE | 0.80-0.90 | 0.75-0.88 |
    | ABOVE | 0.87-0.96 | 0.90-0.95 |

    The body inherits the template's small jitter. It does not create identity, and it does not
    make storms more alike than production does. Variety is the macro-field layering (Task 7) and
    the template's variation - T240.

## INTEGRATION (static audit; the body is not in production)

36. **Field:**
    - The monolith builds its storm field in the same program with the same uniforms, so a
      continuous-mode field would be built from the continuous body. The captures ran with the
      field off for continuous arms (`setStormFieldForcedOnMonolith(0)`).
    - Not yet done: continuous mode publishes the default Lipschitz terms
      (`paLastStormMaxVerticalSlope` / `MaxAspect` = 1). The deck width law and the column flare
      need their own bound before the field may consume the body.
37. **Rain:**
    - The rain-gate build copies every monolith uniform, so it would see the body.
    - The CPU mirrors (`StormLobeEvaluator`: camera density, rain support) are still lobe-based.
    - Not run: captures were dry.
38. **Bounds:**
    - The candidate tiles and the T143 reach come from the lobes (anvil reach 2.18 × 1.56 of the
      radius), which enclose the deck.
    - The group segment test's per-lobe spheres (1.24 R) do not enclose a full-width deck, so it
      returns "may intersect" in continuous mode. Correct, but it costs.
    - No clipping was seen in any view.
39. **Sandbox:** not re-derived. Production is unchanged, and the T127 / T133 bands still describe
    the shipped lobe template. They are rewritten from the continuous anatomy (the base / column /
    deck ratios in 23-30) when the body ships (T240).

## MOTION

40-41. **Not run.** It belongs with production integration: the body must first be in FINAL with
the temporal lattice for a valid comparison (the T235 rule).
- By construction, the body is a continuous function of the descriptors, except where a min / max
  switches the lowest / highest tower or the deck's end lobe. A lifecycle change that swaps them
  can pop.

## PERFORMANCE

42-46. **Not measured build-vs-build** (the body is not in FINAL).
- **Production is unchanged.** `PA_CONTINUOUS_STORM_BODY` is a `leanFinalStrippedDefines` entry,
  so no lean program contains any of the body. The NVIDIA assembly of FINAL, the rain pass and
  the rain-gate build is **identical** to `32466c8e` (binaries 228,202 / 245,424 / 143,867
  bytes).
  - A first draft kept the code live behind a baked `const 0`. It changed FINAL's codegen
    (121 -> 151 temporaries) and the rain pass's (172 -> 210): the T234 / T235 code-shape
    effect. Hence the define.
- Old SIDE ~8.98 ms and PVN ~8.54 ms (T235) stand; ≤ 10 ms.
- **Indicative only** (capture scale, same monolith, uniform toggle - not a budget number):
  - continuous vs lobe union −9% to +14% per view (+26% at ABOVE);
  - even though the body re-reads the group's descriptors twice per sample.

## DECISION

47. **KEEP: the continuous body is the accepted storm shape basis.** It meets the success
    definition in neutral shape on three storms: broad lower mass -> visible column -> smooth
    expansion -> downwind deck, one body, no ball, no stick, no cap. Not yet production.
48. **Continuous model result:**
    - **Works:** the anatomy; connectivity; a near-constant-width column; a flat deck; a
      directional footprint; lighting still reads it (lit LATERAL / FAR are recognisable anvil
      clouds).
    - **Residuals:**
      - along-wind the silhouette is an hourglass;
      - the deck looms from under its tip;
      - the base reads as a separate darker pedestal when lit. This is lighting and is not
        touched here.
      - identity is weak (35).
49. **Production change:** none. Kept in-tree, all off in production:
    - the experimental body (monolith only, `PaStormGeometry` 1, `VolumetricCloudDebugConfig.setStormGeometryMode`);
    - the T237 V2 layout it needs as a skeleton (`stormTemplateVersion` 2 via the relayout hook,
      `CloudGroupSpawner.relayoutStorms`);
    - the fill audit instrument (`stormFillOverlapAudit`).

    Archived:
    - V1 / V2 as authored: `t239-candidate-v1-v2.diff`;
    - the capture arms: `t239-capture-arms.diff`.
50. **Next shape task - T240, continuous-body production integration:**
    - the V2 skeleton layout as the production template;
    - a per-storm skeleton precomputed on the CPU and uploaded, so a sample evaluates one SDF
      instead of re-reading ten descriptors twice;
    - the body in FINAL;
    - field Lipschitz bounds for the deck and column laws;
    - the segment-test and candidate reach bounds;
    - `StormLobeEvaluator` mirrored for camera density and rain support;
    - T127 / T133 bands re-derived from the continuous anatomy (23-30);
    - Task 7 macro-field layering for irregularity and identity;
    - then motion with the temporal lattice on both arms, and build-vs-build SIDE / PVN (≤ 10 ms,
      prefer ≤ 9.5).

## VALIDATION

51. **campaignGate:** PASS (`gate-t239.log`; also before every client run).
52. **check build:** PASS (`check-build-t239.log`).
53. **stormMorphologySandbox:** PASS (10 / 0, `sandbox-t239.log`).
54. **git status:**
    - `PAchangelog.md` modified and uncommitted;
    - capture arms removed;
    - markers `run/t211-captures.txt` / `run/t212-edge-trace.txt` retired.
    - One simplification after the captures: the experimental mode was collapsed from V1 / V2
      (modes 1 / 2) to the accepted V2 body as mode 1, mechanically, and checked by compile
      (monolith, NVIDIA) and review, not re-captured.
55. **Commit:** on `quality/t217-deep-interior-lighting`, not merged.

## Evidence (`t239-campaign/`)

| file | what |
|---|---|
| `t239-fill-audit.log` | Task 1: controlled composition table + live-storm interior classes |
| `T239_S1_prod_vs_V1_sheet.png`, `T239_S1_V1_neutral_lit_zoom.png` | storm 1: production vs V1 |
| `T239_S2_prod_V1_V2_lit.png` | storm 2: production / V1 / V2 / V2 lit, five views |
| `T239_S3_prod_V2_role_lit.png`, `T239_S4_prod_V2_role_lit.png` | storms 3-4: production / V2 / V2 regions (mode 17) / V2 lit |
| `T239_S3_S4_THROUGH.png` | THROUGH, storms 3-4 |
| `t239-s*.json`, `t239_shape.py`, `t239_identity.py` | silhouette + region metrics, identity IoU |
| `t239-storm{1..4}-trace.log` | relayouts, descriptors, fixture loss, per-shot GPU ms |
| `t239-candidate-v1-v2.diff`, `t239_continuous.py` | the body as authored (V1 + V2) |
| `t239-capture-arms.diff`, `t239_capture.py` | the ABBA capture set with per-arm layout + geometry |
