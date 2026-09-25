# T228 - Dual-Scale Light-Depth Response

**The question:** can the lighting keep its good near-surface response while adding a second, longer
depth response that preserves form through the storm body out to od ~40?

**The answer: partly - and the part it cannot do is not a formulation problem. CASE B: REJECTED
for production.** A monotonic log-depth body response (onset od 3-6, reference od 18, amplitude
0.7) does what T227 predicted wherever the light path carries structure:
- deep-band structure retention doubles or triples (od 8-16: 0.06-0.12 -> 0.10-0.17; od 16-40:
  0.02-0.04 -> 0.06-0.09);
- **on the anvil flanks the flat area halves or vanishes** (LATERAL anvil 0.43 -> 0.02, LOWSIDE
  0.53 -> 0.21, SIDE 0.52 -> 0.23);
- the lit skin is bit-identical below od 3 and the lit top holds (ABOVE local contrast -2% noon,
  -5% low sun, against T227's single-scale -20%);
- the mean moves +0.2 to +4.3 levels, and the cost is below the noise floor.

**But the anvil undersides stay flat** (NEAREDGE anvil 0.96 -> 0.79, THROUGH frame 0.75 -> 0.72),
**and the measurement says why no od response can fix them.** Light reaching an underside has
crossed the whole anvil, so the path integral averages local density out. The log-od view shows the
underside's od field is spatially smooth: local spread 1.3-3.9 in the deep bands, against 5.7-11
on the flanks. There is no structure in od for any transfer function to reveal. Motion rises 4-8%
(route luma instability) for a change that converts one of the three BAD views. That is the
brief's CASE B verdict, with its mechanism now measured rather than guessed.

**Next task (T229):** give the underside a structure signal that does not come from the sun's
light path. The storm union already computes each sample's distance to the storm's own macro
surface (`unionDistanceBlocks` / `paLastStormUnionDistance`, the T212-T215 envelope distance), so a
**skylight visibility term from depth-below-surface** costs no taps. Then evaluate it *together*
with this T228 body curve (constants in item 14), on this matrix and harness.

## RECOVERY

1. **Starting HEAD:** `fec5e0c2` (T227). **2. Branch:** `quality/t217-deep-interior-lighting` (not
   merged; T226 stays parked on `quality/t226-local-precipitation`, not mixed in).
   `PAchangelog.md` preserved. campaignGate PASS before every client run.
3. **Production state:** T224 full light cone, T225 rain sentinel. The lighting transfer is
   `evaluateLightingComponents`: three scatter octaves (extinction 1 / 0.42 / 0.18, weights
   1 / 0.52 / 0.27, normalized), powder, `ambient *= mix(retention 0.58-0.74, 1, exp(-od))`, and
   the T217 deep-scatter fill gated on camera density. Note for the brief's Task 15: **the
   camera-height 102/240-block light cap no longer exists on this branch** (removed in T224,
   `fd2b242`), so T228 can neither reduce nor magnify it.

## INSTRUMENT AND BASELINE

4. **Extended od view: `PaDiagnosticLightingMode` 9**, `value = log(1 + od) / log(65)`, decoded as
   `od = exp(v * ln 65) - 1`.
   - Resolution: ~30 8-bit levels per od at od 1, ~1.5 per od at od 40.
   - No clipping: no visible pixel in any T228 view exceeded od 40, because the 240-block reach
     bounds it.
   - It replaces mode 2's `exp(-od/2)`, which saturates at od 8.31.
   - **Kept** as an instrument.
5. **Production transfer against od** (exact port of `evaluateLightingComponents`, noon uniforms
   logged in-run: light (1.0, 0.97, 0.90), ambient top (0.47, 0.65, 1.0), bottom (0.30, 0.34, 0.41),
   3 octaves; dome sample, `cos 0`; `t228_transfer_table.py`):

   | od | direct | ambient | deep-scatter (exterior) | radiance | 8-bit | slope (levels/od) |
   |---|---|---|---|---|---|---|
   | 0 | 0.550 | 0.354 | 0 | 0.904 | 176.2 | -45.4 |
   | 1 | 0.312 | 0.269 | 0 | 0.581 | 135.0 | -28.6 |
   | 2 | 0.196 | 0.238 | 0 | 0.434 | 109.9 | -16.5 |
   | 4 | 0.097 | 0.222 | 0 | 0.319 | 86.4 | -6.3 |
   | 8 | 0.035 | 0.220 | 0 | 0.255 | 71.7 | -1.75 |
   | 16 | 0.007 | 0.220 | 0 | 0.227 | 65.0 | -0.31 |
   | 24 | 0.002 | 0.220 | 0 | 0.222 | 63.6 | -0.07 |
   | 32 | ~0 | 0.220 | 0 | 0.220 | 63.3 | -0.02 |
   | 40 | ~0 | 0.220 | 0 | 0.220 | 63.2 | -0.004 |

   The whole od 16-40 range spans < 2 levels. For an in-cloud camera the T217 fill adds
   0.14 / 0.24 / 0.27 at od 4 / 8 / 16, a brightness plateau, not structure.

**Baseline shares, BAD views (LOWSIDE / NEAREDGE / THROUGH, noon and low sun), storm A:**

6. od 0-1: **0.15-0.34**. 7. od 1-4: **0.01-0.06**. 8. od 4-8: **0.01-0.09**.
9. od 8-16: **0.18-0.47**. 10. od 16-40: **0.15-0.53** (and 0.00 beyond 40). The deep-shadow
   median od is 12-22.
11. **Structure retention by band** (image local contrast / density local contrast, same pixels):
    0-1 **0.20-0.42**; 1-4 **0.35-1.04**; 4-8 **0.21-0.80**; 8-16 **0.07-0.12**;
    16-40 **0.02-0.04**. Flat fraction 0.5-0.95 at 8-16 and **0.76-1.00** at 16-40.
12. **GOOD/BAD matrix:** T227's labels held on both T228 storms. GOOD: LATERAL, ABOVE, sun-facing
    SIDE. BAD: LOWSIDE, NEAREDGE, THROUGH. FAR was excluded (stale-frame bug) and PLAY_VIS_NEAR
    (tree canopy).
13. **Sun-height baseline:** the low sun (dayTime 2000) made the side views *deeper*, not
    shallower (deep median od LOWSIDE 11.9 -> 16.0, NEAREDGE 14.6 -> 19.3, THROUGH 18.8 -> 21.9),
    because the light runs through more of the storm. ABOVE and sun-facing SIDE gained the form
    band.

## CANDIDATE

14. **Equation** (applied to the ambient only, after production's near response):
    ```glsl
    float w  = smoothstep(3.0, 6.0, od);
    float g  = saturate(log(1.0 + od) / log(41.0));   // body depth, 0..1 over od 0..40
    float gR = log(1.0 + 18.0) / log(41.0);           // zero-change reference
    ambient *= 1.0 + w * 0.7 * (gR - g);
    ```
15. **Onset:** smoothstep od 3 -> 6; od < 3 is bit-identical to production.
16. **Range:** od 0-40, log-normalized. Above 40 nothing exists in any view.
17. **Maximum contribution:** ambient x1.19 at od 6, down to x0.86 at od 40. 8-bit slope at
    od 12 / 16 / 24 / 32: -1.42 / -0.89 / -0.48 / -0.34 (production -0.68 / -0.31 / -0.07 /
    -0.02).
18. **Mean preservation and shape choice.** The constants were set on the baseline storm (A) and
    judged on a different storm (B).
    - The first draft (reference 15, amplitude 1.0) was **non-monotonic**: +1.9 levels/od at
      od 4, a brighter ring inside the lit skin. It was caught analytically and never judged.
    - The shipped shape was chosen against storm A's measured deep-od distribution (3,000 BAD-view
      pixels) under two constraints: monotonic everywhere on a 0.25-od grid (worst step -0.067),
      and the deep-pixel mean within ~1 level.
    - No monotonic zero-mean shape exists with the brightening placed in od 8-17, because
      production has no slope budget there. The brightening has to come in at the onset. The
      price is a softened slope at od 4-5 (-1.3 vs -6.3 levels/od).
    - Measured on storm B the mean moved **+0.2 to +4.3 levels** (flank views brighter, since
      their deep median, ~11, is below the reference 18; undersides +0.3 to +1.9).
    - Candidate B (thickness-normalized) was **not built**: no cheap per-sample thickness *along
      the light path* exists (descriptor heights are per role).

## QUALITY (storm B, same storm, camera and sun, adjacent arms; P0B repeat = image floor)

| view | frame plateau D0 -> C | lc D0 -> C | luma shift | verdict |
|---|---|---|---|---|
| 19. SIDE noon / low sun | 0.44 -> 0.18 / 0.30 -> 0.11 | 4.26 -> 4.63 / 5.27 -> 5.57 | +4.3 / +1.6 | better, crown unchanged |
| 20. LOWSIDE noon / low | 0.42 -> **0.16** / 0.46 -> 0.21 | 3.13 -> 3.75 / 3.65 -> 4.14 | +3.6 / +1.8 | flank reads as a body; BAD -> mixed |
| 21. LATERAL noon / low | 0.28 -> **0.02** / 0.43 -> 0.08 | 5.59 -> 6.21 / 5.19 -> 5.96 | +3.5 / +1.5 | better |
| 22. NEAREDGE noon / low | 0.72 -> 0.60 / 0.72 -> 0.62 | 1.89 -> 2.29 / 1.86 -> 2.26 | +1.9 / -0.2 | slight streaking; **still BAD** |
| 23. THROUGH noon / low | 0.75 -> 0.72 / 0.70 -> 0.68 | 1.80 -> 1.95 / 2.13 -> 2.22 | +0.3 / -0.7 | **still BAD** |
| 24. ABOVE noon / low | 0.25 -> 0.25 / 0.12 -> 0.12 | 4.10 -> 4.01 / 5.24 -> 4.99 | +0.2 / +0.7 | unchanged (guard held) |

`T228_P0_vs_candidate_LOWSIDE_SIDE_NEAREDGE_ABOVE.png`

25. **Flat-shadow area, before/after:** SIDE 0.44 -> 0.18; LOWSIDE 0.42 -> 0.16; NEAREDGE 0.72 -> 0.60;
    THROUGH 0.75 -> 0.72. T227's single-scale reference was SIDE 0.48 -> 0.28 and LOWSIDE
    0.43 -> 0.26.
26. **Deep-band retention:** 8-16 **0.06-0.12 -> 0.10-0.17**, 16-40 **0.02-0.04 -> 0.06-0.09** in
    every view. Deep-band flat fraction on the flanks: 8-16 0.53-0.70 -> 0.02-0.25; 16-40
    0.69-0.95 -> 0.06-0.42. On the undersides only 0.92-0.99 -> 0.73-0.96.
27. **Lit-skin retention:** od 0-1 unchanged (0.20-0.41 both arms). od 1-4 **-5 to -9%** (e.g.
    0.90 -> 0.82) and 4-8 -5 to -15%: the price of the onset easing.
28. **Upper anvil:** flat 0.53 -> 0.21 (LOWSIDE), 0.52 -> 0.23 (SIDE), 0.43 -> 0.02 (LATERAL),
    0.96 -> 0.79 (NEAREDGE underside); local contrast +13-30% (NEAREDGE 0.55 -> 1.12, still very
    low).
29. **Lower mass:** unchanged (luma within 0.5, lc within 0.3). The anvil/lower luma gap narrows
    (LOWSIDE 12.1 -> 7.5, SIDE 4.9 -> -0.3, LATERAL 12.6 -> 7.9): different levels, both with
    structure, as Task 12 asks.

**Why the undersides do not respond (the decisive measurement).** Local spread of the od field
(60 x ln(1+od)), same pixels:

| views | od 8-16 | od 16-40 | density local contrast |
|---|---|---|---|
| NEAREDGE / THROUGH (undersides) | 1.7-3.9 | 1.3-2.9 | 6-14 |
| LOWSIDE / SIDE / LATERAL (flanks) | 6.1-11.2 | 5.7-10.5 | 20-31 |

A function of od can only redistribute structure that od has. The underside's od is smooth
because every underside sample's light path crosses the same ~210 blocks of anvil. That is a
property of single-scattering light-path lighting, not of this curve.

## SUN AXIS

30. **High sun (noon):** the results above.
31. **Low sun:** the same direction, smaller margins. Flank plateaus 0.46 -> 0.21 (LOWSIDE) and
    0.30 -> 0.11 (SIDE); undersides 0.72 -> 0.62 and 0.70 -> 0.68. Mean shift -0.7 to +1.8. The
    low sun deepens the side views (item 13), so more pixels sit past the reference.
32. **GOOD/BAD transitions:** LOWSIDE BAD -> mixed (noon and low sun). NEAREDGE and THROUGH stay
    BAD. ABOVE, LATERAL and SIDE stay GOOD (not worse).

## TEMPORAL (standard route, production temporal path, sun fixed, 2200 frames per arm)

33. **Route flicker:** alpha flicker 0.00231 -> 0.00245 (+6%). Luma dLumaMean +8.4%, lumaPop
    +4.6%. By segment dLumaMean: APPROACH +11.5%, ENTRY +21%, INTERIOR +5.7%, OUTSIDE +7%.
34. **Shadow crawl:** dLuma2Mean +4.2% (dLuma2Max +1.8%). The candidate is >25% worse on 385
    frames and >25% better on 24.
35. **Brightness pumping:** none. Frames with a > 2-level whole-frame jump: 24 vs 24 (the
    production_050 reference: 22). The mean offset is steady (+2.5 levels overall, +3.6 in the
    interior). Part of the instability rise is structural: there is now structure in
    formerly flat regions, so camera motion changes more pixels. By the project's precedents
    (T213 banked +2.5%, T212 rejected at +10.2%) it is borderline. A slow sun-motion check was not
    run (no static pass to validate).

## PERFORMANCE (capture pin at production scale 0.25, ten-shot ABBA)

36. **SIDE:** -0.015 +- 0.021 ms (median +0.003). 37. **PVN:** +0.075 +- 0.187 ms (median
    +0.051). 38. **Candidate delta:** below the noise floor, < 0.15 ms class (one `log` and one
    smoothstep per shaded sample, no taps). 39. **<= 10 ms:** yes; the whole renderer stands at
    T224's 9.35 / 8.82 ms.

## DECISION

40. **REJECT (CASE B)** for production. It is not the T227 single-scale failure: the deep bands
    gain structure, the lit skin is intact and the transfer is monotonic. But two of the three BAD
    views are unmoved, and motion rises 4-8%.
41. **Mechanism:** a second depth scale can only restore structure the light path contains. The
    flanks contain it (od spread 6-11) and the curve recovers it. The undersides do not (od spread
    1.3-3.9, because every underside path crosses the same anvil), so **no function of light-path
    od can give an anvil underside form**. The remaining flatness needs a second, non-solar
    signal: skylight/ambient visibility.
42. **Production change:** none. Kept as an instrument: diagnostic lighting mode 9 (log-od view;
    `diagnosticLightingMode` clamp raised to 9). Removed: the `PaBodyDepth` arm, its constants,
    the T228M motion arms and campaign, the capture blocks; the driver is restored to HEAD. The
    candidate's exact equation is item 14, for re-use.
43. **Exact next task: T229, skylight visibility from depth below the storm's surface.** An
    ambient-visibility factor from the union distance the density already computes
    (`paLastStormUnionDistance`: blocks below the macro envelope). Cheap, and it varies with the
    storm's large-scale shape exactly where light-path od does not. Evaluate it alone and *with*
    the T228 body curve, on the T227/T228 matrix, using:
    - the log-od view (mode 9);
    - the sun axis;
    - the T228 acceptance: undersides must move, the lit top must hold, motion <= +5%.

## VALIDATION

44. **campaignGate:** PASS (`gate-t228.log`). 45. **check build:** PASS (`check-build-t228.log`).
46. **stormMorphologySandbox:** PASS (10 / 0, `sandbox-t228.log`). 47. **Markers:**
    `t211-captures.txt` and `t228-body-motion.txt` retired; `t132-autorun*` and `t098-captures.txt`
    as before. 48. **git status:** `PAchangelog.md` modified and uncommitted; everything else
    committed. 49. **Commit:** see the branch log (not merged).

## Evidence (`t228-campaign/`)

| file | what |
|---|---|
| `t228-run1-baseline.log`, `t228_baseline.json`, `T228_baseline_logod_{NEAREDGE,THROUGH}.png` | storm A baseline: bands, lighting uniforms |
| `t228-run2-candidate.log`, `t228_candidate.json`, `T228_P0_vs_candidate_*.png` | storm B, production vs candidate, both suns, P0B floor |
| `t228-run3-motion.log`, `t228_route.py` | the standard route |
| `t228-run4-cost025.log` | ABBA cost at 0.25 |
| `t228_bands.py`, `t228_split.py`, `t228_transfer_table.py` | analysis (the transfer port is exact) |
