# T236 - Cloud Shape Redesign (Morphology First)

**The question:** can the cloud shape itself - not its lighting - be made to look like a real
convective storm?

**The answer: not with a shader-side lobe change. REJECTED; production unchanged.**

- **The shape defect is found, and it is structural.** Under constant lighting every storm is a
  **mushroom**:
  - a broad domed anvil cap, 3-7× wider than the neck and near mirror-symmetric;
  - a thin, short neck;
  - a round foamy ball below.
- **The role map attributes it to the storm template's lobe placement:**
  - four anvil lobes arranged symmetrically *around* the tower, not downwind of it;
  - a single 460-block-tall base lobe that forms the ball;
  - the tower only visible in the ~160-block gap between them.
- **Six candidates changed individual lobe shapes in the shader.** Five barely moved the
  silhouette (≤ 5% of pixels), because the union of the other lobes covers each change. The one
  that did (a hard upwind cut) tears the anvil edge into fragments.
- **The lever that matches the defect is the storm layout itself** (`CloudletLayout.stormLayout`,
  CPU). That is the next task.

## RECOVERY

1. **Starting HEAD:** `20cb8dcd` (T235). **2. Branch:** `quality/t217-deep-interior-lighting` (not
   merged; T226 separate). `PAchangelog.md` preserved. campaignGate PASS before every client run.
3. **Current morphology state:**
   - direct-storm descriptors from `CloudletLayout.stormLayout`: lobe 0 CORE, 1 BASE, 2 ANVIL
     (main), 3-4 TOWER, the rest ring lobes or thin extra ANVIL outflow;
   - `stormRoleProfile` radius profiles per role; smooth union;
   - T213 macro envelope (mode 3), T215 lower-body fill, T230/T231 lower-anvil relief;
   - base noise and erosion.
   - Lighting, temporal, rain, the shared field, T231 shading and T235's edge precompute all
     untouched.
4. **Reference captures** (`t236-campaign/`, one storm per launch, adjacent arms):
   - 13 views: FAR from four sides, SIDE, SIDE_OPP, LATERAL, ABOVE, LOWSIDE, NEAREDGE, THROUGH, a
     low-angle tower view and a warm-up shot;
   - neutral shape-only lighting (diagnostic mode 1: constant radiance, no light cone) beside
     production lighting;
   - two new attribution views: **mode 17** (active storm role at the shaded sample: anvil red,
     tower green, core blue, base grey) and **mode 18** (dominant descriptor height);
   - the descriptor trace (`t236-descriptors.log`).
   - Unusable: the low-angle tower view (camera in trees) and, in the later runs, NEAREDGE /
     THROUGH (fixture dissipated at the end of the list). Both excluded.

## DEFECT AUDIT (from the neutral images first; `T236_S0_neutral_views.png`)

5. **Defect #1: mushroom cap.**
   - The anvil is a broad dome: rounded crown and shoulders.
   - It is near-symmetric left to right.
   - From ABOVE it is an almost perfect disc or ellipse.
   - It reads as a mushroom cap or lens, not a flat-topped canopy spreading downwind.
6. **Defect #2: stem, not column.** The cap connects to the lower mass through a thin, short,
   broken neck. The system reads as stacked parts (ball, neck, cap), not one updraft feeding its
   anvil.
7. **Defect #3: ball base.** The lower mass is a compact, round, foamy ball with a fragmented
   cauliflower rim, not a broad base with a rising tower.

S0 silhouette metrics (neutral FAR views from four sides, three storms):

| metric | value |
|---|---|
| neck width / anvil width | 0.15-0.30 |
| anvil crest flat share (width within 5% of the crest) | 0.06-0.37 (domed) |
| top curvature (normalised quadratic fit, > 0 = domed) | +0.07 to +0.14 |
| left/right mirror IoU | 0.75-0.88 |
| ABOVE ellipse-fit error / minor:major | 0.04-0.08 / 0.60-0.69 |

## ATTRIBUTION (role map `T236_S0_neutral_role_height_lit_FAR.png`, descriptor trace)

Descriptors of a typical storm (base y ≈ 136):

| role | y span | radii (blocks) |
|---|---|---|
| BASE ×1 | 136-600 | 470 × 424 |
| CORE ×2 | 349-701 | ~250 × 225 |
| TOWER ×2 | 436-928 | 194 × 159, 168 × 137 |
| ANVIL ×4 | 770-1000 | ~440 × 255 (×1.56 short axis, ×0.70->2.10 profile) |

8. **Controlling term #1 (cap): the anvil lobes' placement and size.**
   - **Placement.** Four ANVIL lobes are centred ±100-200 blocks around the storm centre with a
     canopy radius of ~900 blocks, so their union is a symmetric cap centred over the tower. From
     the layout, the lobes are spread across and along the wind rather than placed downwind.
   - **Rounding.** Coverage fades over each lobe's edge band (up to ~100 blocks), so the rim of the
     cap has rounded shoulders.
   - **Mode 17.** The cap is 100% ANVIL role.
9. **Controlling term #2 (neck): the gap between the base top (600) and the anvil bottom (758).**
   - Mode 17 shows the tower visible only there, as a thin band under the cap.
   - Elsewhere it lies inside the base or the anvil.
   - The neck is short and narrow because both big masses leave only this gap, not because of the
     tower's radius.
10. **Controlling term #3 (ball): the BASE lobe.**
    - It is 460 blocks tall and ~1,000 wide - the whole lower storm is one base lobe.
    - The erosion / base-noise threshold then shreds its rim into the foamy fragments.

## PRIMARY CANDIDATES (morphology only, shader-side, switch `PaShapeCandidate`, monolith arms)

11-12. The six candidates, each targeting the attributed term, with neutral-silhouette change
       against S0 on the same storm (area change / XOR share of the silhouette):

| id | change | why this lever | effect |
|---|---|---|---|
| S1 / S2 | tower profile widens with height (1.10->1.70 / 1.00->1.45 instead of 1.25->0.60) | the stem | ≤ 1.5% XOR; the tower is hidden inside base and anvil except in the short gap |
| S5 | anvil along-wind radius ×0.50 on the upwind half-plane | the cap's symmetry | **-13 to -26% area, 12-27% XOR** across the wind; mirror IoU SIDE 0.83 -> 0.49, FAR 0.82 -> 0.62. **Torn edge**: fragments 1 -> 4-6 components |
| S6 | the same at ×0.70 | a restrained S5 | ≤ 4% |
| S7 / S8 | anvil underside rises toward the rim (wedge; S8 + S6) | the cap's thick wall | ≤ 1% - the lobe is only wide in its top 38% already |
| S9 | smooth upwind shrink (0.55 -> 1.0 with bearing) | S5 without the cut | 0.2-3.3% on three storms |
| S10 | anvil lobe centre shifted downwind 0.25 R, radius 0.75 (same upwind reach as S5, a true ellipse, inside every CPU bound) | S5 without the cut | 0.4-4.5% on two storms |

13-18. **Per view:**
- **SIDE, LATERAL, ABOVE and the FAR views** are in the table and sheets.
- **LOWSIDE:** the same as S0 for S1-S10.
- **NEAREDGE / THROUGH:** captured for S0 and S1/S2, lost for the later candidates (fixture
  dissipated).
- **Only S5 changes the storm visibly:**
  - lit, its upwind side opens and the tower's turrets billow through (`T236_S5_upwind_cut_SIDE.png`);
  - under neutral lighting, the cut edge is ragged and fragmented.

## SHAPE METRICS

19-25. See `t236-s0-metrics.json` and the table above.
- **Area** changes only for S5.
- **Perimeter / √area** rises with S5 (11.5 -> 13.8 at FAR), as roughness from fragments.
- **Ellipse-fit error from ABOVE** stays 0.02-0.08 for every arm.
- **Mirror IoU** moves only with S5.
- **Curvature and crest flatness** do not move for any candidate.
- **Lobe variance** is not separable in the silhouette.
- **Tower/anvil ratio** (neck / anvil) stays 0.14-0.30 for every candidate. S1 changed the whole
  silhouette by ≤ 1.5%.

## MULTI-STORM

26-28. **Storms 1-3** (three launches, S0 / S9 / S10):
- storm 1: S9 0.2-2.3% XOR;
- storm 2: S9 1.0-3.3%, S10 1.3-4.5%;
- storm 3: S9 0.1-1.4%, S10 0.4-3.4%.

**All three S0 storms are the same mushroom:** FAR neck / anvil 0.20-0.21, mirror 0.82-0.84, ABOVE
minor:major 0.60-0.67.

29. **Identity preserved: YES** (no candidate was strong enough to homogenise anything). But
    production storms already have **low shape identity**: every realization is the same template
    with small offsets.

## NORMAL LIGHTING CHECK

30. **S5 reads more convective under production lighting than under neutral lighting.** The
    exposed upwind side shows tower turrets; the torn edge is partly hidden. The other candidates
    are invisible in both.

## TEMPORAL / PERFORMANCE

31-35. **Not run.** No candidate passed the static visual test, and the brief gates motion and
timing on a shape worth keeping.
- **Production cost is unchanged.** Only diagnostic modes 17/18 stay, and the lean FINAL program's
  NV assembly is byte-identical to T235's (6,786 instructions, 121 temporaries).
- **SIDE:** ~8.98 ms, **PVN:** ~8.54 ms (T235); ≤ 10 ms.

## DECISION

36. **REJECT (all six candidates).** None makes the storm look more like a real convective cloud:
    five are visually null, and S5 trades the mushroom for a torn, fragmented anvil edge.
37. **Exact production shape change:** none.
    - **Kept as instruments:** diagnostic lighting modes **17** (storm role at the shaded sample)
      and **18** (dominant descriptor height). They are the attribution tools for the next task.
    - **Removed:** the candidate switch and branches (`t236-candidates.diff`).
38. **Next SHAPE defect / task - T237, storm template layout.** Redesign the direct-storm lobe
    placement in `CloudletLayout.stormLayout`:
    - **anvil:** lobes placed and spread *downwind* of the tower (upwind edge near the updraft,
      long downwind canopy), varied in size;
    - **tower:** a taller, wider TOWER pair that spans from inside the base into the anvil, so the
      column is the storm's visible body;
    - **base:** a flatter, broader BASE, not a 460-block ball;
    - **judging:** the same neutral / role-map / metric harness (`t236_capture.py`,
      `t236_shape.py`, modes 17/18), three storms, same-storm A/B;
    - **scope:** it changes the storm template shared with fields, rain support and the morphology
      sandbox's pins, which is why it is its own task.

## VALIDATION

39. **campaignGate:** PASS (`gate-t236.log`). 40. **check build:** PASS (`check-build-t236.log`).
41. **stormMorphologySandbox:** PASS (10 / 0). 42. **git status:** `PAchangelog.md` modified and
    uncommitted; everything else committed; markers retired (`t211-captures.txt`,
    `t212-edge-trace.txt`). 43. **Commit:** see the branch log (not merged).

## Evidence (`t236-campaign/`)

| file | what |
|---|---|
| `T236_S0_neutral_views.png`, `T236_S0_lit_views.png` | the reference set, neutral and lit |
| `T236_S0_neutral_role_height_lit_FAR.png` | neutral / role map (mode 17) / descriptor height (mode 18) / lit, FAR from four sides + ABOVE + LOWSIDE |
| `T236_S1_S2_tower_widening.png`, `T236_S5_S6_sheet.png`, `T236_S5_upwind_cut_SIDE.png`, `T236_S7_S8_wedge.png`, `T236_S9_storm1.png`, `T236_S10_storm2.png` | candidate A/Bs |
| `t236-descriptors.log` | the storm's descriptor geometry |
| `t236-s0-metrics.json`, `t236_shape.py` | silhouette metrics |
| `t236-captures-*.log`, `t236_capture.py` | capture logs and the capture-set installer |
| `t236-candidates.diff` | the rejected candidate code |
