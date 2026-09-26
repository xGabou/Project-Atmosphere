# T234 - FINAL vs Diagnostic Motion Divergence

**The question:** why does the shipped FINAL shader flicker 12-15% more than the full diagnostic
shader (the monolith), and up to ~2.5× OUTSIDE, when both represent the same production renderer?

**The answer: one compile-time switch, T172's descriptor precompute (`PA_ARM_DESC_PRECOMPUTE`).
FIX KEPT (CASE B), at +0.45 ms at SIDE.**

> **T235 correction (26 Sep 2026): this attribution is WRONG.** Only
> `LEAN_FINAL` runs the T207 temporal reconstruction (`normalProductionOutput()`), so the monolith
> and every bridge program below rendered **non-temporally** whatever the route arm requested.
> The bridges did not match the monolith because of code shape. They matched because none of
> them was temporal.
>
> T235 ran FINAL with and without the precompute on the same temporal path: their motion is
> identical within the repeat floor (OUTSIDE 1.005×, ENTRY 0.999×), and the precompute is
> cheaper. With temporal off, current FINAL matches the monolith (OUTSIDE 1.037×).
>
> **The FINAL/diagnostic gap is temporal reconstruction on versus off** - an intended
> architecture difference. Restoring the precompute is recommended (SIDE ~1.1 ms cheaper, identical
> motion) but not applied, pending a decision; see `t235-precompute-recovery.md`. The measurements
> below stand; only their interpretation (items 29-32, 36-37) does not.

- **The mechanism is code shape, not values.**
  - The precompute replaces two per-descriptor derivations in the storm walk (edge width and
    ownership radii) with CPU-computed copies from texel 3.
  - Those copies are correct: relative error 1.3e-7, one float ULP, at p50 and p99 in every
    view.
  - But a FINAL without the derivation code in the loop is measurably less stable under motion.
- **The decisive bridges.** Any program that keeps the derivation compiled in matches the
  monolith's motion to within 0.2% in every route segment, whichever value it uses:
  - `bridge_a`: no precompute;
  - `bridge_c`: FINAL's precomputed values, derivation compiled behind a never-true branch;
  - `bridge_d`: the derived values, precomputed read compiled in.
- **What the gap is not:**
  - not rain (it survives with precipitation 0);
  - not the frame-time governor (step scale 0.5 constant in every arm; pinning changes nothing);
  - not temporal resolve (ghosting equal; the route repeat floor ±1%);
  - not a static image delta amplified (the static FINAL/monolith difference is the same before
    and after the fix).
- **The fix.** Drop the precompute from FINAL. The new FINAL's NV assembly is byte-identical to
  `bridge_a`, the measured motion-equivalent program.
- **The cost.** SIDE 9.33 -> 9.77 ms (paired ABAB), PVN unchanged. It stays ≤ 10 ms but loses
  the 9.5 ms margin.

## RECOVERY

1. **Starting HEAD:** `92861e9d` (T233). **2. Branch:** `quality/t217-deep-interior-lighting` (not
   merged; T226 separate). `PAchangelog.md` preserved. campaignGate PASS before every client run.
3. **Production state:**
   - T231 relief + shading with T232's packed transport;
   - FINAL = lean program, `leanFinalDefines` = `PA_PRECIPITATION_ABSENT`,
     `PA_ARM_DESC_PRECOMPUTE`, `PA_ARM_NO_RAIN_SEGMENT`, `PA_RAIN_SPLIT_COMPOSITE`, plus 30 baked
     constants;
   - SIDE ~9.3 ms.
4. **Route wiring.**
   - The route is the T152 moving-camera fixture, 2200 frames per arm, segments APPROACH /
     ENTRY / INTERIOR / OUTSIDE (the fixture has no separate EXIT; OUTSIDE is the far leg).
   - The T234 arms (temporary campaign `T234`, marker `t234-final-motion.txt`) switched the
     program per arm through `setFinalProgramOverride`, all on one storm.
   - The route is deterministic: a FINAL repeat reads 0.996-1.008× in every segment.

## STATIC EQUIVALENCE (one storm, adjacent shots, 1600×900; `t234s.json`, `t234s-postfix.json`)

5-9. **FINAL vs monolith** at NEAREDGE / LOWSIDE / SIDE / LATERAL / ABOVE:
- colour RMS 0.49-1.13 levels;
- maxAbs 63-107;
- 0.7-2.4% of pixels differ by > 1 level;
- luminance delta ≤ 0.01 levels;
- silhouette IoU 0.977-0.997.

The programs are **not bit-identical statically (case B of Task 1)**:
- **Where the differing pixels are:** on the silhouette band, on horizontal step-contour lines
  across the anvil (march-step boundaries), and under the lower mass where rain differs
  (`T234_FINAL_vs_MONO_diff_FAR_SIDE.png`).
- **Against the noise floor:** each program's own 8-frame static floor is RMS 0.01-0.19.
- **Unchanged by the fix:** the difference is the same size before (RMS 0.92-0.96 at the flank
  views) and after it (0.94-1.13). So it is not what drives the motion gap.
- **Unusable views:** FAR (first shot of the session, mismatched capture) and THROUGH (both
  programs unstable there, monolith static RMS 5.3).
- **Depth:** the composite's DEPTH / ALPHA aux views do not apply on the forced monolith, so
  depth was not compared.

## CONFIG DIFF (build.gradle `leanFinalDefines` / `leanFinalConstants`, runtime upload)

10. **Defines:**
    - FINAL adds `PA_PRECIPITATION_ABSENT`, `PA_ARM_NO_RAIN_SEGMENT` and
      `PA_RAIN_SPLIT_COMPOSITE`: rain marched by a separate current-frame, unjittered rain pass
      and composited at the rain's depth, instead of inside the jittered cloud march;
    - plus `PA_ARM_DESC_PRECOMPUTE` (the T172 hoist).
11. **Uniforms:**
    - 30 uniforms are baked as constants in FINAL, all at the values the renderer uploads to the
      monolith in production.
    - Field bits: the monolith gets `stormFieldForcedOnMonolith()`, which must equal FINAL's
      43 for FINAL eligibility.
12. **Branch / path:**
    - the diagnostic branches (modes, traces, oracles) fold away in FINAL;
    - the rain path differs (above);
    - the descriptor walk differs only by the precompute's two substitutions.
13. **Textures / bindings:** the same, plus the rain-pass target sampled by FINAL. Descriptors
    are RGBA32F, so the precomputed texel-3 values are full floats.

## RAW INPUTS (item 14-18)

14-18. **Not instrumented per input.**
- **The difference arises before resolve.** It is a property of the march program, established
  by swapping only programs on one route with everything else fixed: the governor step scale is
  logged per frame and constant at 0.5, and jitter and phase are the same code.
- **Rain (18) is excluded by measurement.** With precipitation forced to 0 in both programs the
  gap is unchanged (flicker 1.093× overall, 2.72× OUTSIDE). Rain adds 1-2% to both programs
  equally.

## TEMPORAL

19-20. **History acceptance** (ghost mean on the route, ALL / OUTSIDE):
- FINAL 0.00478 / 0.00036;
- monolith 0.00476 / 0.00039;
- `bridge_a` 0.00498 / 0.00039.

There is no systematic acceptance difference.

21. **Reject classes:** not separately logged; ghost / disocclusion equal across programs per
    segment (the largest gap is INTERIOR, 0.0093 vs 0.0090, which is noise).
22. **Phase / jitter:** the same code and the same governor state in every arm. Pinning the
    governor at 0.5 for both programs reproduces the gap exactly (flicker 1.077× ALL, 1.602×
    OUTSIDE).

## ROUTE (FINAL / monolith, one storm; the value-vs-codegen run, repeat floor ≤ 0.8%)

| segment | flicker FINAL / mono | alpha dMean | luma dLuma | FINAL without precompute / mono (flicker) |
|---|---|---|---|---|
| 23. OUTSIDE | **2.551×** | 1.697× | 1.511× | **1.001×** |
| 24. ENTRY | 1.275× | 1.261× | 1.094× | 1.000× |
| 25. INTERIOR | 1.049× | 1.044× | 0.946× | 1.028× |
| APPROACH | 1.286× | 1.128× | 1.030× | 0.999× |
| ALL | 1.134× | 1.120× | 1.074× | 1.019× |

26. **EXIT:** the fixture has no exit segment; OUTSIDE is the post-storm leg.
27-28. **Alpha delta / luma delta:** in the table (dMean, dLumaMean).

The same pattern held in all four route runs: FINAL/mono OUTSIDE flicker 2.27-2.72×, ENTRY
1.15-1.28×.

## ATTRIBUTION

29. **Minimal causal switch: `PA_ARM_DESC_PRECOMPUTE` in FINAL.**
    - Removing it alone (`bridge_a`) turns FINAL's route motion into the monolith's: OUTSIDE
      flicker 0.0001093 vs 0.0001091, ENTRY 0.001237 vs 0.001237.
    - Keeping everything else FINAL-specific (rain split, baked constants, rain gate) changes
      nothing.
30. **Codegen-only effect: YES.**
    - `bridge_c` renders with FINAL's exact CPU values but has the GPU derivation compiled into
      the loop behind a runtime test that is never true. It matches the monolith (OUTSIDE
      1.000×).
    - `bridge_d` is the mirror (derived values, precomputed read compiled in). It matches too.
    - So the values are not the cause; the presence of the derivation code in the walk is.
    - In NV assembly the precompute FINAL is 6,771-6,775 instructions / 121-125 temporaries,
      and every stable variant is 6,804-6,816 / 126.
31. **Numeric precision finding:**
    - The precomputed invariants agree with the GPU derivation to a relative 1.3e-7 at p50 and
      p99 in every view (diagnostic mode 16 during the attribution, removed after).
    - The static image differences FINAL/monolith are threshold crossings at silhouettes and
      march-step boundaries, and are the same with or without the precompute.
    - The motion difference therefore comes from how the driver compiles the walk. Without the
      derivation it produces a program whose samples near the surface are less temporally
      coherent. This is inferred from the bridges; the NV intermediate does not show the
      back-end schedule.
32. **Mechanism conclusion.**
    - The FINAL/diagnostic gap was never FINAL's intended specialization (rain split, baked
      constants), the temporal system, or the governor.
    - It was T174's productionization of the T172 hoist. The hoist is value-exact, but it
      changes the driver's code for the storm walk into a motion-noisier program. T172/T173
      judged it on static images and timing only.
    - As in T232, source-level equivalence says nothing about the compiled program. Motion has
      to be judged program against program.

## PERFORMANCE (paired ABAB, one session: HEAD, T234, HEAD, T234; T208 totals, ms)

| | A1 HEAD | B1 T234 | A2 HEAD | B2 T234 | HEAD mean | T234 mean | delta |
|---|---|---|---|---|---|---|---|
| 33-34. SIDE total | 9.30 | 9.94 | 9.35 | 9.60 | 9.33 | **9.77** | **+0.45 (1.048×)** |
| SIDE raw p50 / p95 | 6.50 / 6.73 | 7.06 / 7.56 | 6.56 / 6.79 | 6.71 / 7.00 | 6.53 / 6.76 | 6.89 / 7.28 | +0.36 / +0.52 |
| PVN total | 8.77 | 8.84 | 8.76 | 8.50 | 8.77 | **8.67** | -0.10 |
| PVN raw p50 / p95 | 5.41 / 5.66 | 5.30 / 5.71 | 5.41 / 5.56 | 5.11 / 5.33 | 5.41 / 5.61 | 5.21 / 5.52 | -0.20 / -0.09 |

34. **The monolith** (for reference, route GPU time): 45 ms against FINAL's 5.8 (8×). It is not
    a production candidate.
35. **Delta:**
    - +0.45 ms at SIDE (+4.8%); PVN within noise.
    - On the route the stable programs cost +0.13-0.16 ms of cloud GPU time on average (5.82 ->
      5.95-5.98) and +0.3 ms at ENTRY.
    - T173 had measured the hoist at 1.133×; after T199-T232 it is worth ~5% at SIDE.

## DECISION

36. **FIX (CASE B), kept.** The switch is found and minimal. The fix has no visual change beyond
    the motion improvement, and SIDE stays ≤ 10 ms (9.60-9.94).
    - **The trade:** the ≤ 9.5 ms margin is gone at SIDE (headroom ~0.2 ms).
    - **If the budget matters more than edge stability,** reverting is one define.
37. **Production change:**
    - `PA_ARM_DESC_PRECOMPUTE` removed from `leanFinalDefines`, so FINAL and its identity
      controls derive edge width and ownership radii in the walk again.
    - The CPU still writes texel 3; historical programs (T140 oracles, T199/T206 anchors, rain
      pass, gate build) keep their defines.
    - Sandbox:
      - `validateT172PrecomputeArms` now requires FINAL NOT to define it;
      - the T206 FINAL-specialization check lists it as absent;
      - the T206 reference comparison discounts the define.
    - Instrument kept: the T152 route logs `stepScale` and `gpuMs` per frame.
    - Removed: the temporary T234 campaign, bridges, route arms, capture arms and diagnostic
      mode 16. mode 16's walk hook itself changed FINAL's codegen, so it could not stay.
38. **Exact next task: T235, recover the ~0.45 ms at SIDE without the precompute's code shape.**
    - Candidates must be judged ABAB and on the T152 route against the monolith as the motion
      reference, never on static images alone.
    - Start from the derivation's cost in the walk: it is evaluated per descriptor per density
      sample. A per-group hoist that keeps an in-loop computation, or a precompute validated for
      motion before shipping, are the two directions.
    - T226 remains parked.

## VALIDATION

39. **campaignGate:** PASS (`gate-t234.log`). 40. **check build:** PASS (`check-build-t234.log`).
41. **stormMorphologySandbox:** PASS (10 / 0). 42. **Markers:** `t234-final-motion.txt`,
    `t211-captures.txt` and `t208-temporal-cleanup.txt` retired. 43. **git status:**
    `PAchangelog.md` modified and uncommitted; everything else committed. 44. **Commit:** see the
    branch log (not merged).

## Evidence (`t234-campaign/`)

| file | what |
|---|---|
| `t234-route-program-x-rain.log` | FINAL / monolith × rain on / off, plus a FINAL repeat |
| `t234-route-bridge-a.log` | FINAL / monolith / FINAL-without-precompute (the `bridge_b` constants-live arm crashed the driver, 0xC0000409, and was dropped) |
| `t234-route-governor.log` | the same three plus FINAL and monolith with the governor pinned at 0.5 |
| `t234-route-value-vs-codegen.log` | FINAL / monolith / bridges a, c, d / FINAL repeat |
| `t234-static-prefix.log`, `t234s.json` | static FINAL vs monolith with precompute error (mode 16), 7 poses, 8-frame re-grabs |
| `t234-static-postfix.log`, `t234s-postfix.json` | the same after the fix |
| `T234_FINAL_vs_MONO_diff_FAR_SIDE.png` | where the static difference is |
| `t234-timing-abab.log` | the paired timing |
| `t234_route.py`, `t234s.py` | analysis |
