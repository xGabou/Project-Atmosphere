# T238 - Lobe Shape Replacement on the V2 Layout

**The question:** with the T237 V2 layout already directional, do a slab anvil and a column tower
remove the mushroom?

**The answer: no. REJECTED (case E); production unchanged.**

T238 built the candidate as the brief specified: the V2 layout, a slab anvil profile and a column
tower profile, compared with production on the same storm in ABBA order. It also used its one
allowed transition/overlap revision (R1).

- **The slab** makes the deck flatter: anvil thickness / width 0.34-0.42 -> 0.23-0.27, crest
  curvature → 0. But in every downwind view:
  - it separates from the tower, leaving a 10-22-row sky gap under the deck;
  - its broad underside tears into 9-16 fragments at LOWSIDE (production: 2-3).
- **The column** never reads as a column:
  - inside the base/core lobes it is buried;
  - above them it is a thin, ragged stalk, even at a 250-280-block radius.
- **The lower body** is still a round ball in every arm.

Across the wind (R1), the storm now reads as base ball + ragged stalk + flat deck offset downwind.
That is the same ball / neck / cap anatomy with a flatter cap. From downwind it is worse than
production: a floating deck over a ball.

The same-storm attribution (storm 2, one layout, shapes switched per arm) shows why neither role can
be fixed by reshaping its own lobe:

- **Tower:** production's visible "tower" *is* the 1.25× root bulge, i.e. the ball.
  - Take the bulge away (column profile) and the tower's lower rows go to BASE grey: they sit inside
    base and cores, which are as wide as the column at those heights.
  - Make the cores narrower (R1) and what remains is a narrow stalk.
- **Anvil:** a flat slab's broad bottom removes tower material under it. With the tower geometry
  unchanged, the tower's top rows vanish (FAR 434 -> 452).
  - Keeping the lower-deck relief off the tower junction (R1) did not close the gap. The mechanism
    past that was not isolated.

In this model the visible form at each height is the fattest overlapping lobe, and a lone narrow lobe
is eroded to wisps. A "column" and a "deck" are not reachable by reshaping lobes.

**Correction to T237 and the T238 brief.** `radii.y *= 1.56` in `directStormLobeDistanceFromData`
widens the anvil's **short horizontal axis**. It is not a vertical stretch. The anvil's cap shape
comes from its height profile alone: 0.70 at the lobe bottom flaring to 2.10 by 62% of its height,
a narrow-rooted cup.

## RECOVERY

1. **Starting HEAD:** `3b520e9f` (T237).
2. **Branch:** `quality/t217-deep-interior-lighting`, not merged.
   - `PAchangelog.md` preserved; T226 untouched.
   - campaignGate PASS before every client run.
   - No Gradle ran while a client was up. Every run waited for `Stopping!`, then confirmed only the
     Gradle daemons remained.
3. **V2 restoration:** `git apply` of `t237-campaign/t237-template-candidates.diff`, exact:
   - `stormTemplateVersion` 2;
   - the in-place relayout hook;
   - client descriptors identical to T237 storm 3's (`t238-storm*-trace.log`).
4. **Production morphology:**
   - old layout;
   - role profiles in `stormRoleProfile`: BASE / CORE tapered, TOWER 1.25 -> 0.60, ANVIL 0.70 -> 2.10;
   - smooth ordered union;
   - T213 macro envelope, T230/T231 lower-deck relief, base noise + erosion.

**Candidate plumbing (temporary, removed):**
- a `PaLobeShape` uniform (production 0), baked to `const 0` in `leanFinalConstants`; any other
  value takes the monolith;
- modes: 1 both shapes, 2 anvil only, 3 tower only, 4 both + the R1 relief gate.

**Why the harness is sound:**
- The monolith builds its storm field in the same program with the same uniforms (`PaStormFieldPass`
  1), so the field matches the shape.
- The rain-gate build copies every monolith uniform.
- Both new profiles stay inside the old per-role conservative ranges (tower 0.60-1.43, anvil
  0.70-2.18), so every culling bound stayed valid.

## ANVIL

5. **Old profile:** `mix(0.70, 2.10, smoothstep(0, 0.62, h)) + 0.08 sin(πh)^0.55 − 0.10 smoothstep(0.88, 1, h)`.
   - A narrow root flaring 3× by 62% of the lobe's height: a cup, i.e. a mushroom cap.
   - Plus ×1.56 on the short horizontal axis.
6. **New profile (slab):** `1.80 − 0.45 |2h − 1|³`.
   - Widest at mid-deck (1.80), thinning to 1.35 at the flat top and bottom faces: a disc with a
     thinning rim.
   - The ×1.56 short-axis widening is unchanged.
7. **Flare:** root-to-canopy ratio 3.0 -> 1.33; the bottom face is 1.35 wide instead of 0.70.
8. **Vertical thickness:**
   - The descriptor span is unchanged (the V2 deck, ±0.08 T + the 12 / 16-block handoff); the
     profile is horizontal-vs-height.
   - Measured anvil thickness / width (mode 17, cross-wind, storm 3): 0.34-0.42 -> 0.23-0.27.
9. **Visual result:** a flatter, flat-bottomed deck. But from downwind:
   - it floats over a sky gap (10-22 empty rows, all three storms);
   - its whole underside tears into veins (LOWSIDE components 2-3 -> 9-16, perimeter/√area
     8.7-9.3 -> 14.8-17.4).

   Anvil-only (storm 2, S2) shows both on its own, so it is the slab and not an interaction.

## TOWER

10. **Old taper:** `mix(1.25, 0.60, h) + 0.18 sin(πh)^0.65`, i.e. 1.25 root, ~1.1 mid, 0.60 top.
    The root bulge is what renders: storm 2's S3 attribution moves the tower's bottom row from 524
    to 468 and hands those rows to BASE grey.
11. **New profile (column):** `0.82 + 0.18 (1 − smoothstep(0, 0.22, h)) + 0.08 smoothstep(0.82, 1, h)`.
    - 1.00 at the root, where it overlaps the base;
    - a near-constant 0.82 body;
    - 0.90 at the top, where it feeds the anvil.

12-14. **Width at 25 / 50 / 75% of the visible tower height** (pixels, mode 17, R1, storm 3; the
       production tower is not visible in these views, < 0.2% of pixels):

| view | 25% | 50% | 75% | width CV |
|---|---|---|---|---|
| LATERAL | 47 | 104 | 76 | 0.60 |
| FAR_LAT | 21 | 61 | 36 | 0.64 |
| TOWERLOW | 73 | 88 | 90 | 0.53 |

The low end is hidden in the base. It is not a constant-width column but a mid-bulging, ragged stalk.

15. **Visible tower height** (tower-role rows / silhouette height):
    - production 0.00-0.03;
    - R1 cross-wind 0.45-0.47, FAR 0.17, SIDE 0.09;
    - B without R1 (storm 1) ≤ 0.25 cross-wind, ≤ 0.05 downwind.

## PURE SHAPE (neutral mode 1 + role mode 17 + height mode 18, same storm, ABBA)

16. **SIDE:** the deck fills the upper frame and a ball sits below with sky between them. neck/anvil
    0.16-0.20 -> 0.00, largest component 1.00 -> 0.84-0.88. **FAIL.**
17. **LOWSIDE:** the underside is torn into streaks (9-16 components); the tower appears as a small
    green stub between them. **FAIL.**
18. **FAR / downwind:** a flat slab floating over the ball (components 1 -> 2-3, largest 0.59-0.63).
    The nearest anvil lobe no longer reads as a bulb, but the storm is two pieces. **FAIL.**
19. **LATERAL:**
    - the deck spreads downwind (mirror 0.84 -> 0.33, right/left 0.73 -> 4.96), and the storm is one
      body in R1;
    - but the anatomy is ball + thin ragged stalk + plank. **FAIL** (the brief's "narrow neck",
      "round ball").
20. **ABOVE:** minor/major 0.63-0.73 -> 0.74-0.80. Rounder; near-lobe perspective as in T237.

## METRICS (`t238_shape.py`, `t238-s*.json`)

21. **Anvil / tower width** (mode 17, anvil row width / tower 50% width), R1: 3.9 (LATERAL), 4.0
    (FAR_LAT), 2.4 (TOWERLOW). Production: undefined, the tower is not visible.
22. **Neck width:** downwind 0.00 in every B arm (production 0.16-0.32); cross-wind R1 0.13
    (production 0.14).
23. **Cap curvature:**
    - SIDE +0.050 -> +0.001 (storm 1) and +0.209 -> +0.171 (storm 3);
    - LATERAL +0.100 -> +0.069;
    - FAR unchanged (+0.11).
24. **Symmetry (mirror IoU):** cross-wind 0.79-0.84 -> 0.33-0.36; downwind 0.78-0.86 -> 0.71-0.84.
25. **Downwind / upwind area:** LATERAL 0.73-1.15 -> 4.96-7.42 (V2 layout, as T237).
26. **Connected components:**
    - downwind: split, largest 0.58-0.88;
    - LOWSIDE: 9-16 components;
    - cross-wind R1: 1.

## MULTI-STORM

27. **Storm 1** (production vs V2 + both shapes): floating, torn deck; tower ≤ 3% of pixels; ball.
28. **Storm 2** (attribution: all arms on V2, only the shape switched):

    | arm | tower pixels | what it shows |
    |---|---|---|
    | S0, production shapes | 7-21% | the tower is its root bulge |
    | S2, anvil only | 4-16% | gap + torn underside |
    | S3, tower only | 1-2.5% | buried in base / cores |
    | S1, both | — | all of the above |

29. **Storm 3** (production vs R1 = V2 with cores ×0.42 / 0.40 below the column, both shapes, relief
    gated off the tower junction):
    - cross-wind: column visible (15-16% of pixels) and deck flat;
    - downwind: gap (15-22 rows), torn underside (11 components) and the ball remain.
30. **Mushroom removed: NO.** It holds on all three storms, and in R1 the round lower mass is
    unchanged.

## INTEGRATION

31-34. **Not run.** The shape failed the static test, and the brief gates bounds / field / rain /
sandbox work on a pass.
- The candidate stayed inside the conservative profile ranges.
- The field and the rain gate saw the same shape as the ray (see RECOVERY).
- `stormMorphologySandbox`'s source pins caught an early draft that put the R1 gate inside
  `stormLowerDeckWeight`. It was moved to the call site; the pinned function stayed unchanged.
- T127 / T133 bands: untouched (production unchanged).

## MOTION

35-36. **Not run** (static shape failed).

## PERFORMANCE

37-41. **Not run** (visual acceptance first).
- Production is unchanged: `src/main` and `build.gradle` are byte-identical to `76a7fb9f`.
- Old SIDE ~8.98 ms and PVN ~8.54 ms (T235) stand; ≤ 10 ms.

## DECISION

42. **REJECT (case E).** Neither role improves in the pass views:
    - the anvil flattens but detaches and tears;
    - the tower is either buried or a ragged stalk;
    - the ball remains;
    - the one transition / overlap revision (R1) did not change any of that downwind.
43. **Exact shape change:** none.
    - Archived: `t238-candidate.diff` (profiles, the switch, the layout version 3 cores, the R1
      gate) and `t238-capture-arms.diff`.
44. **Next shape task - T239, a new storm geometry basis.** Stop tuning the lobe model.
    - **Findings it must address:**
      - with a union of role lobes, the visible form at each height is the fattest lobe there;
      - a lone narrow lobe is eroded to wisps;
      - a flat deck in this model detaches from the lobes under it and exposes the relief / erosion
        veins across its whole underside.
    - **Direction:** one continuous storm field instead of ten role lobes, e.g.:
      - a skeleton: an updraft axis with radius(h), feeding a deck sheet with thickness(x) along the
        wind;
      - density fill that does not depend on which roles overlap. Hypothesis to check first:
        `stormCoverageLowerBound` fills differently when base + core + tower overlap
        (`embeddedConvectiveOverlap`), which would explain the wispy lone tower. Not verified here.
    - **Judged by:** the same same-storm ABBA harness, SIDE / LOWSIDE / FAR as the pass views.

## VALIDATION

45. **campaignGate:** PASS (`gate-t238.log`, reverted tree; also before each client run).
46. **check build:** PASS (`check-build-t238.log`).
47. **stormMorphologySandbox:** PASS (10 / 0, `sandbox-t238.log`).
48. **git status:**
    - `PAchangelog.md` modified and uncommitted;
    - candidate and arms reverted;
    - markers `run/t211-captures.txt` / `run/t212-edge-trace.txt` retired.
49. **Commit:** on `quality/t217-deep-interior-lighting`, not merged.

## Evidence (`t238-campaign/`)

| file | what |
|---|---|
| `T238_S1_prod_vs_B_FAR.png`, `T238_S1_prod_vs_B_SIDE_LOWSIDE.png` | storm 1: production N/R vs B N/R/H/P |
| `T238_S2_attribution_S0_S1_S2_S3.png`, `T238_S2_junction_FAR.png` | storm 2: one layout, shape switched (production / both / anvil / tower), SIDE / LOWSIDE / FAR, and the deck-tower junction |
| `T238_S3_prod_vs_R1_pass_views.png`, `T238_S3_prod_vs_R1_crosswind.png` | storm 3: production vs R1, pass views and cross-wind views |
| `t238-s*.json`, `t238_shape.py` | silhouette + role metrics (tower widths, anvil thickness) |
| `t238-storm{1,2,3}-trace.log` | relayouts, descriptor geometry, fixture loss |
| `t238-candidate.diff`, `t238_shape_switch.py` | the candidate (shader profiles, switch, layout v3, R1 gate) |
| `t238-capture-arms.diff`, `t238_capture.py` | the ABBA capture set with per-arm layout + shape |
