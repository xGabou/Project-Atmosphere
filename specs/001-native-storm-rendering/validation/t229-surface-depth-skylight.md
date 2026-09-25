# T229 - Underside Sky-Visibility / Surface-Depth Form

**The question:** can distance below the storm's own surface provide the underside form signal that
sun-path optical depth cannot?

**The answer: no - CASE B, DISPROVEN for the underside, stopped before building a lighting term.**
Surface depth is a clean signal in aggregate: low-frequency, the least veiny of the candidates, and
positively correlated with the storm's visible form. But on the deep anvil underside it is as
locally flat as light-path od, for a geometric reason:

- the underside's visible samples all sit inside the macro envelope's thin soft shell (THROUGH:
  depth p10 / p50 / p90 = -75 / -34 / -6 blocks);
- the envelope's underside is a smooth surface (the bottom of large descriptor ellipsoids,
  smooth-unioned);
- so its local variation is **2.8 blocks** at THROUGH, against 16-28 blocks on the flanks.

Under the brief's energy guard (no glowing underside, no global lift), the best bounded sky term adds
**~0.1-0.3 levels** of local contrast at THROUGH and ~0.5-1.3 at NEAREDGE. T228's curve already
moved the NEAREDGE anvil 0.55 -> 1.12. Neither lighting signal can reveal underside form the
geometry does not have.

**Mechanism conclusion:** after T228 (sun path) and T229 (surface depth), the anvil underside is
flat because **its macro surface is smooth**, not because of the lighting. The lever for undersides
is morphology: macro relief on the anvil's lower envelope. That relief would give *both* the T228
depth curve and any sky term something to show.

## RECOVERY

1. **Starting HEAD:** `c19d778d` (T228). **2. Branch:** `quality/t217-deep-interior-lighting` (not
   merged; T226 separate). `PAchangelog.md` preserved. campaignGate PASS before the client run.
3. **Production state:** unchanged since T225. Kept instruments: mode 9 (log-od view). The
   camera-height light cap stays closed (removed in T224).
4. **`paLastStormUnionDistance` semantics** (read from `directStormShape`'s group union):
   - **What it is:** the smooth-unioned (`stormSmoothMinimum`) signed distance, in blocks, from the
     sample to the storm's **descriptor macro envelope**. It is evaluated *before* base noise,
     core fill and erosion: macro form only.
   - **Sign:** negative inside, zero on the macro surface, positive outside. The envelope is
     `1 - smoothstep(-softness, +softness, d)`, so visible cloud extends into the positive (soft
     shell) side.
   - **Across lobes:** it blends between groups by the group blend radius.
   - **Reset:** it is set to 1e9 at the start of every union walk, and stays 1e9 when the column is
     outside every group's reach.
   - **The raw global does not belong to the shaded sample:** the walk runs for every density call
     (refinement, probes), so it holds whichever ran last. T229 adds `paShadeUnionDistance`, a copy
     written **only** right after the primary march's own `cloudDensity(p)` (the production
     branch; field bit 16 is off at bits 43), and reads that.

## SIGNAL (Tasks 1-3)

5. **Instrument:** `PaDiagnosticLightingMode` 10, `v = (-d + 150) / 600`: 150 blocks outside = 0,
   the surface 0.25, 450 inside = 1; magenta where no group owns the sample (0.000 of storm pixels
   in every view). Units are blocks, and "depth below the surface" is `-d`.
6. **Underside, deep shadow (od > 8), noon / low sun:**
   - NEAREDGE: SD 48 / 47 blocks, local spread **10.2 / 10.2**, autocorrelation 54-56 px;
   - THROUGH: SD 31 / 23, local spread **2.8 / 1.9**, autocorrelation 60 px (the maximum
     measured).
7. **Flanks (LOWSIDE / SIDE / LATERAL):** SD 49-58 blocks, local spread **16-28**, autocorrelation
   14-32 px.
8. **Form correlation** (billow band vs the production image's billow band): depth **+0.36 to
   +0.65** in every view. Light-path od -0.22 to -0.87 (od already drives what little underside
   form there is, inversely). Density -0.02 to -0.19.
9. **Vein correlation** (ridge map vs density's ridge map): depth +0.37 to +0.61; od +0.12 to
   +0.51; density 1 by definition. Depth's own vein score is the **lowest** of the three at the
   undersides (0.021-0.085 vs density 0.07-0.14).
10. **Against light-path od, underside:** depth has the larger low-frequency share at NEAREDGE
    (0.58 vs 0.48), lower vein scores and a positive form correlation. At THROUGH both are smooth
    (autocorrelation 60 px, local spread tiny against the range): the deep underside carries no
    local form in either signal.
11. **Signal verdict:** **PASS as a clean, non-vein signal; FAIL as an underside form source.** It
    has form where the macro envelope has form (flanks, the underside's edge at NEAREDGE) and none
    on the deep underside (THROUGH), where the shaded samples all sit in the envelope's soft shell
    (depth p10-p90 -75 to -6 blocks).

## SURFACE-ONLY CANDIDATE (Tasks 4-5) - not built; analytic bound instead

The brief stops here if the signal is flat on the underside. Before stopping, the best-case term was
bounded analytically on this storm's captured maps (linearized; the constant was calibrated so a
change of 1 in the ambient factor is 55-79 8-bit levels at the underside's luminance):

12. **Family:** `ambient *= 1 + A * smoothstep(2, 6, od) * (v(depth) - v(ref))`, with
    `v = 1 - smoothstep(lo, hi, depth)`. Monotonic and bounded, a separate indirect cue rather than
    a sun-path term, applied only in shadow so the lit skin is untouched. 13. **Search:**
    lo..hi ranges -60..80 up to -120..200, references -40..+60, A 0.2-0.8.
14. **Energy bound:** no member keeps every view's mean within 3 levels at A >= 0.4. The underside
    shadows sit at depth ~-35 to -10 and the flank shadows at ~+50 to +65, so any
    depth-to-brightness map brightens one and darkens the other. The best balanced member
    (range -120..200, reference +10):

    | A | worst view shift | NEAREDGE added lc | THROUGH added lc | flank added lc |
    |---|---|---|---|---|
    | 0.2 | 1.8 | 0.46 | **0.11** | 0.8-1.3 |
    | 0.3 | 2.7 | 0.68 | **0.16** | 1.1-2.0 |
    | 0.4 | 3.6 | 0.91 | **0.21** | 1.5-2.7 |

    (x1.4 at the calibrated underside gain.)
15-20. **Per-view results:** not rendered. The bound predicts THROUGH +0.2-0.3 levels (production
    underside lc 0.51), NEAREDGE +0.6-1.3 (T228 already +0.57 on the anvil), and the flanks most,
    where T228 already works. The T228 class is not beaten on the underside: the acceptance target
    fails by construction.

## QUALITY / COMBINED / TEMPORAL / PERFORMANCE

21-25. **Quality deltas:** none (no candidate). The lower-mass vein guard was not exercised.
26-29. **Combined T228 + T229:** not built (U1 did not pass; the brief builds U2 only after U1).
30-33. **Temporal:** not run (no static pass).
34-37. **Performance:** no candidate. The retained instrument costs nothing in FINAL: the lean
    program bakes `PaDiagnosticLightingMode` to 0, so `paShadeUnionDistance` is never read there
    and its one assignment is dead code. <= 10 ms unchanged (T224: 9.35 / 8.82 ms).

## DECISION

38. **DISPROVE (CASE B)** for the underside. No lighting curve is built from surface depth.
39. **Production change:** none. Kept instrument: mode 10 (surface depth at the shaded sample) and
    `paShadeUnionDistance`; the capture driver is restored to HEAD.
40. **Mechanism:** the anvil underside has no macro relief. Its shaded samples lie in the soft
    shell of a smooth, unioned ellipsoid surface. The sun's light path averages the anvil above it
    (T228: od spread 1.3-3.9), and the distance to its own surface is almost constant locally (T229:
    2.8 blocks at THROUGH). No lighting input carries the form, because the geometry does not.
41. **Exact next task: T230, anvil-underside macro relief (morphology).**
    - Give the anvil's lower envelope large-scale relief: the underside counterpart of T213's
      block-space deck displacement (T213 lifted the deck's *upper* macro form; the underside
      ellipsoid bottom was never displaced), applied at the envelope level so it is large-scale,
      not vein-scale.
    - Judge it with the T227-T229 instruments: GOOD/BAD matrix, mode 9 log-od and mode 10 surface
      depth, the sun axis. The acceptance: surface depth and od must gain local spread on the
      underside; NEAREDGE and THROUGH must move.
    - Re-evaluate the T228 body curve on top of it, since the curve had nothing to show on the
      underside.
    - The T227 classification changes for undersides only: morphology (A) becomes the lever there,
      and lighting (C) stays the lever for the flanks.

## VALIDATION

42. **campaignGate:** PASS (`gate-t229.log`). 43. **check build:** PASS (`check-build-t229.log`).
44. **stormMorphologySandbox:** PASS (10 / 0). 45. **Markers:** `t211-captures.txt` retired.
46. **git status:** `PAchangelog.md` modified and uncommitted; everything else committed.
47. **Commit:** see the branch log (not merged).

## Evidence (`t229-campaign/`)

| file | what |
|---|---|
| `t229-run1-signal.log` | the capture run (6 poses x FINAL / log-od / density / surface depth, two suns) |
| `T229_final_logod_surfacedepth.png` | FINAL, log-od and surface depth side by side, NEAREDGE / THROUGH / LOWSIDE / SIDE |
| `t229_signal_a.json`, `t229_sig.py` | the signal metrics |
