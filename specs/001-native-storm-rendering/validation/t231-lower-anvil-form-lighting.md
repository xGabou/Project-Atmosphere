# T231 - Lower-Anvil Relief Shading

**The question:** can the same clean macro field that shapes the lower anvil also drive a
shadow-form lighting term, so the underside relief becomes visible?

**The answer: yes - KEEP (CASE A).** Production now ships T230's lower-anvil relief together with
this shading. One standing risk surfaced on the way: **production is over the 10 ms budget at SIDE
in this session's heavier storm (11.9 ms), with or without T231** (item 38). The next task restores
the budget before any further visual cost.
- The shading: `ambient *= 1 - 0.12 * macroSigned * lowerWeight * smoothstep(2,5,od)`.
- It makes the underside's form readable. Flat fraction in the lower-anvil region: **NEAREDGE
  0.98 -> 0.86, LOWSIDE 0.82 -> 0.51, SIDE 0.89 -> 0.54, LATERAL 0.70 -> 0.26**, against T230's
  relief alone at 0.98 / 0.82 / 0.88 / 0.73.
- Local contrast doubles there, and the low sun holds.
- The shading follows the geometry's own field (coherence +0.75 to +0.95).
- Lower-mass veins unchanged; frame means within 1.4 levels.
- Motion **+1.9% / +2.6%** (luma / alpha flicker), below T228's +4-8%. Cost ~0.1 ms.
- THROUGH, the deepest underside, moves only 1.00 -> 0.97: its visible underside spans less than
  one macro cell (signal SD 0.38 there vs 0.7-1.0 elsewhere).
- The T228 curve on top pushes the undersides further but brings back T228's motion cost (+9.4%
  luma, ENTRY +29%), so the combination (CASE D) was not banked.

## RECOVERY

1. **Starting HEAD:** `61c2945a` (T230). **2. Branch:** `quality/t217-deep-interior-lighting` (not
   merged; T226 separate). `PAchangelog.md` preserved. campaignGate PASS before each client run.
3. **Production state (before):** unchanged since T225 (macro-envelope mode 3, deep scatter 1);
   instruments modes 9-12.
4. **The exact T230 geometry, restored as an arm:** `PaLowerDeckRelief` adds
   `stormLowerDeckWeight(h, roleMask) = anvil && !base ? 1 - smoothstep(0.12, 0.30, h) : 0` to
   T213's `deckWeight` at both stage-5 sites. Same macro signal, 38 blocks per SD. Unmodified.

## SIGNAL (Task 2; view mode 13 = the centred macro signal at the shaded sample)

5-8. **Measured in the lower-anvil region** (mode 12 weight > 0.2), at the samples where stage 5
     fetched it (71-93% of the region; the rest is saturated interior, where the term is 0 by
     construction):

     | view | mean | SD | min / max | p5 / p50 / p95 |
     |---|---|---|---|---|
     | THROUGH | +0.42 | **0.38** | -0.73 / +1.31 | -0.24 / +0.42 / +1.09 |
     | NEAREDGE | -0.51 | 0.72 | -2.28 / +1.28 | -1.85 / -0.28 / +0.58 |
     | LOWSIDE | -0.23 | 0.72 | -2.25 / +1.44 | -1.72 / -0.11 / +0.79 |
     | SIDE | -0.17 | 0.70 | -2.25 / +1.42 | -1.64 / -0.06 / +0.83 |
     | LATERAL | -0.35 | 0.98 | -2.34 / +2.34 | -1.74 / -0.40 / +1.58 |

     **Centring:** the field's own calibrated statistics (mean 0.4794, SD 0.1163 over the baked
     volume), the same centring as T213. Any one view's visible region is a fraction of one
     ~400-block macro cell, so its local mean is -0.5 to +0.4 SD. Re-centring per view would be
     screen-dependent, so it was not done. The consequence is measured in items 21 and 24.
9. **Sign mapping to geometry:**
   - **Analytic (certain):** a positive shift raises the inverse-smoothstep parameter, which lowers
     coverage and recedes the surface. On the underside, receding means moving up into the cloud:
     a **hollow**. Negative lets the surface grow downward: a **sagging lobe**.
   - **Hence brightness goes with -signal.**
   - **Empirical:** where the relief grows the underside edge outward (alpha up), the signal is
     negative (NEAREDGE correlation **-0.40**). THROUGH's +0.21 comes from the tower-neck
     silhouette, which is not an underside edge.
   - Sagging lobes lighter, hollows darker, as intended. The sandbox now pins the sign.

## CANDIDATE

10. **Equation** (in `evaluateLightingComponents`, after production's ambient retention):
    ```glsl
    float reliefShadow = smoothstep(2.0, 5.0, opticalDepth);
    ambient *= 1.0 - PaReliefShade * paShadeMacroSigned * paShadeLowerWeight * reliefShadow;
    ```
    `paShadeMacroSigned` is the clamped (±2.5) centred macro value stage 5 already fetched at the
    shaded sample (0 if it fetched none); `paShadeLowerWeight` is the lower-deck window at that
    sample.
11. **Shadow localization:** `smoothstep(2, 5, od)`, so the lit skin (od < 2) is untouched.
12. **Lower-anvil localization:** the T230 window: anvil lobes active, base lobes not, h < 0.30.
    It is zero on the lower mass, the tower and the upper deck (mode 12 verified).
13. **Strength:** 0.12 per SD (M2). M3 = 0.06 was tested and delivered about half the effect; no
    sweep beyond that.
14. **Bounds:** the ambient factor lies in [0.70, 1.30] (±2.5 SD clamp). No new texture fetch, no
    density evaluation, no light samples.

## UNDERSIDE RESULT (same storm, same camera, adjacent arms; M0 production, M1 T230 relief, M2 relief + shading)

| view | flat M0 / M1 / **M2** | local contrast M0 -> M2 | broad luma SD M0 -> M2 | region mean shift M2 | coherence M2 |
|---|---|---|---|---|---|
| 15. THROUGH noon / low | 1.00 / 0.98 / **0.97** ; 1.00 / 0.98 / **0.98** | 0.26 -> 0.46 ; 0.19 -> 0.44 | 1.34 -> 3.00 ; 1.62 -> 4.03 | -1.5 ; -2.3 | +0.78 ; +0.88 |
| 16. NEAREDGE noon / low | 0.98 / 0.98 / **0.86** ; 0.99 / 0.98 / **0.86** | 0.36 -> 0.89 ; 0.26 -> 0.88 | 1.33 -> 4.89 ; 1.07 -> 4.89 | +2.6 ; +2.5 | +0.93 ; +0.95 |
| 17. LOWSIDE noon / low | 0.82 / 0.82 / **0.51** ; 0.94 / 0.94 / **0.61** | 1.04 -> 1.78 ; 0.67 -> 1.56 | 2.52 -> 4.30 ; 1.56 -> 3.98 | +1.4 ; +1.3 | +0.80 ; +0.90 |

18. **Local contrast:** roughly doubles on the lower-anvil region in every view (table).
19. **Low-frequency luminance variation (8-px-blur SD):** x2.2-3.7.
20. **Relief/shading correlation:** the image's broad luminance against the relief field (-signal
    x weight): M2 **+0.75 to +0.95**, against -0.27 to +0.24 for M0 and M1. The shading reinforces
    the actual displacement (item 9), not an unrelated pattern.
21. **Mean luminance:** frame means move -1.3 to +1.4 levels; the lower-anvil region -2.3 to +2.6.
    This follows the region's local signal mean (item 8), a redistribution, not a lift.

Visual (`T231_NEAREDGE_LOWSIDE_P0_RS_RSL.png`, `T231_THROUGH_P0_R_RS_RSL.png`):
- NEAREDGE's flat underside gains broad soft bands and lobes along its perspective sweep;
- LOWSIDE's anvil flank gains mottled large masses;
- no fine artifacts, stripes, or painted-on patches unrelated to the shape;
- THROUGH gains faint broad patches only.

## REGRESSION

22. **Lower-mass veins** (vein / fine-band SD / gradient sparsity, M0 -> M2): LOWSIDE 0.247 ->
    0.248, 1.74 -> 1.74, 0.653 -> 0.653; SIDE 0.393 -> 0.398, 3.61 -> 3.65, 0.851 -> 0.843; LATERAL
    0.485 -> 0.485, 4.67 -> 4.67, 0.805 -> 0.805. **Unchanged.**
23. **SIDE:** region flat 0.89 -> 0.54, contrast 0.97 -> 2.10; frame +0.2 levels.
24. **LATERAL:** region flat 0.70 -> 0.26, contrast 1.51 -> 2.81; frame +0.5; low sun
    0.92 -> 0.32.
25. **ABOVE:** frame contrast 4.22 -> 4.19, mean +0.2 (the term cannot act on the lit top).
    ABOVE's signal and weight views and its low-sun pair were captured after the fixture was lost
    there and are void.

## T228 COMBINATION (captured as RSL; judged, not banked)

26. **The exact T228 curve**, unchanged: `ambient *= 1 + smoothstep(3,6,od)*0.7*(g(18)-g(od))`,
    `g = log(1+od)/log(41)`.
27. **Sides:** flat SIDE 0.23, LATERAL 0.03, LOWSIDE 0.23 (vs M2 0.54 / 0.26 / 0.51).
28. **Underside:** NEAREDGE 0.73 (M2 0.86), THROUGH 0.95 (M2 0.97); contrast NEAREDGE 1.27.
29. **Lit top:** ABOVE contrast 4.22 -> 4.00 (-5%, T228's known cost).
30. **Brightness balance:** frame +0.2 to +4.3 levels (T228's flank brightening).
    **Monotonicity:** analytic. The T231 term is independent of od beyond the smoothstep(2,5)
    gate, and T228's curve is monotonic (T228 item 18). The product is a positive factor in
    [0.70, 1.30] times a monotonic curve, so no ring and no double brightening. Both gates share
    the od axis without a discontinuity.

## TEMPORAL (standard route, production temporal path, 2200 frames per arm)

31. **Route flicker:** M2 vs production: dLumaMean **+1.9%**, lumaPop -0.4%, alpha flicker +2.6%.
    M2 + T228: dLumaMean +9.4%, lumaPop +3.7%.
32. **Underside crawl:** dLuma2Mean M2 +1.8% (ENTRY +6.6%, INTERIOR -1.1%); per frame, M2 is >25%
    worse on 65 frames and better on 28. M2 + T228: +7.1%, 546 worse / 24 better.
33. **Brightness pumping:** none. Frames with a > 2-level whole-frame jump: 38 -> 32 (M2) and
    -> 33 (M2 + T228). Mean offset M2 +0.08 levels.
34. **Form popping:** none observed. The field is world-space (rotated domain, no warp, no screen
    term) and the gates are smooth.

## PERFORMANCE (capture pin at production scale 0.25, ten-shot ABBA)

35. **SIDE:** M2 +0.113 +- 0.293 ms (median +0.058). M2 + T228: +0.124 +- 0.057.
36. **PVN:** M2 +0.078 +- 0.098 ms (median +0.103). M2 + T228: +0.289 +- 0.125.
37. **Added delta:** ~0.1 ms, which is the relief's geometry, not the shading's arithmetic.
    Sagging lobes add underside volume, so there is more march and light work (the region's mean
    shift is +4 to +10 blocks on the flanks). The capture sessions varied heavily (SIDE cloud
    pass 6.5 / 8.7 / 9.9 ms in three launches), so only same-session ABBA deltas were used.
38. **<= 10 ms: NO at SIDE in this session - and not because of T231.** Post-bank whole-renderer
    totals (T208 `_TEMPORAL`, lean FINAL at 0.25), each against a back-to-back control with the
    bank temporarily reverted (relief 0, shading 0):

    | | SIDE total (raw / field) | PVN total |
    |---|---|---|
    | T231 banked | **11.96** (8.82 / 1.88) | 9.88 |
    | bank reverted (control) | **11.86** (8.76 / 1.88) | 10.05 |
    | delta | +0.09 | -0.18 |

    T231 costs nothing measurable. **Production itself exceeds 10 ms at SIDE in this storm
    realization** (T224's sessions: 9.35-9.70 ms); the raw phase alone reads 8.8 ms against 6.1 ms
    there. The thin margin T224 flagged has been crossed by production alone in a heavier storm.

## DECISION

39. **KEEP (CASE A): T230 relief + T231 shading as production.** CASE D (adding T228) is rejected
    on motion.
40. **Mechanism:** the underside needed two things at once. T230 supplies the geometry, which the
    lighting could not show. T231 couples the shadowed ambient to the same zero-mean macro field
    that displaces it, a stand-in for the orientation / sky-occlusion term the model lacks. Both
    are driven by T213's accepted field, not density noise, so the lower mass is untouched.
    THROUGH stays weak because its view holds too little of the ~400-block field.
41. **Production change:**
    - `VolumetricCloudDebugConfig.PRODUCTION_LOWER_DECK_RELIEF_MODE = 1` and
      `PRODUCTION_RELIEF_SHADE = 0.12F`, as live uniforms `PaLowerDeckRelief` / `PaReliefShade`,
      with JSON defaults 1 / 0.12 and reset-to-production.
    - The shader term (item 10) and the relief window at both stage-5 sites.
    - Sandbox `validateT231LowerAnvilForm` pins both values, the window and the sign.
    - Removed: the T228 curve arm (`PaBodyDepth`), the T231M campaign and route arms, the capture
      blocks; the driver, fixture, registry and auto-driver are restored to HEAD.
    - Instruments kept: modes 9-13.
42. **Exact next task: T232, restore the <= 10 ms budget in heavy storm realizations.** Production
    without T231 measured 11.86 ms at SIDE (raw phase 8.8 ms vs 6.1 ms in T224's sessions). Before
    any further visual cost, attribute the raw-phase growth across realizations: step counts,
    light-cone work, field build, and rain on the same storm; the T201 attribution ladder is the
    method. Restore the headroom. **Then T233, the deep underside at THROUGH scale:** Its visible underside spans
    less than one macro cell, so neither relief nor its shading varies there. Measure whether a
    second, shorter macro octave in the *same* rotated domain, restricted to the lower-deck window
    (e.g. the field at 2x frequency), gives the deep underside broad form without reaching
    vein scale. Judge it on THROUGH with the T231 instruments (modes 12-13), the vein guard and
    route motion. Separately, and only if wanted: re-price T228's flank curve against its motion
    cost, since M2 now covers part of the flanks' lower region.

## VALIDATION

43. **campaignGate:** PASS (`gate-t231.log`, `T231_LOWER_ANVIL_FORM relief=1|shade=0.12|
    window=anvil&!base,h<0.30|sign=hollowDarker`). 44. **check build:** PASS
    (`check-build-t231.log`). 45. **stormMorphologySandbox:** PASS (10 / 0). 46. **Markers:**
    `t211-captures.txt`, `t231-form-motion.txt` and `t208-temporal-cleanup.txt` retired after
    their runs. 47. **git status:** `PAchangelog.md` modified and uncommitted; everything else
    committed. 48. **Commit:** see the branch log (not merged).

## Evidence (`t231-campaign/`)

| file | what |
|---|---|
| `t231-run1-static.log`, `t231.json`, `t231.py` | M0 / M1 / M2 / M3 / M2+T228, repeat, signal and weight views, both suns |
| `T231_NEAREDGE_LOWSIDE_P0_RS_RSL.png`, `T231_THROUGH_P0_R_RS_RSL.png` | the visual comparison |
| `t231-run2-motion.log`, `t231_route.py` | standard route: production vs M2 vs M2+T228 |
| `t231-run3-cost-relief-shade-t228.log`, `t231-run4-cost-relief-shade.log` | ABBA costs |
| `t231-run5-totals.log`, `t231-run6-totals-control.log` | post-bank whole-renderer totals and the reverted control (item 38) |
