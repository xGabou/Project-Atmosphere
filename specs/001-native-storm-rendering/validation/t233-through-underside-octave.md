# T233 - THROUGH-Scale Underside Octave

**The question:** can one second, finer but still macro-scale underside field give THROUGH enough
form to read, without making the other underside views busier or breaking the ~9.3 ms SIDE
baseline?

**The answer: no. REJECTED (CASE C).**

- **The candidate.** A 200-block second octave: the base texture's B channel, already returned by
  T213's macro fetch and independent of the 400-block G field.
- **It does add signal at THROUGH:** SD 0.68 against the primary's 0.37.
- **It does not make THROUGH readable:**
  - THROUGH flat fraction 0.98 -> **0.99** with the relief and its matched shading;
  - local contrast 0.36 -> 0.34;
  - F0, F1 and F2 are visually indistinguishable.
- **Three measured reasons:**
  1. THROUGH still sees about one feature of the 200-block field (it does not decorrelate across
     the region).
  2. Two independent fields combine differently in each view; here they anti-correlate (-0.40),
     so the form the shading sees rises only from SD 0.372 to 0.393.
  3. At THROUGH the relief's own luminance response opposes the matched shading: form energy
     2.65 -> 3.09 with relief alone, then 2.35 with the shading.
- **Meanwhile the views T231 already fixed get busier:** LOWSIDE flat 0.51 -> 0.45, SIDE
  0.56 -> 0.45.
- **Production is unchanged.** Mode 15 (the B-channel signal) is kept as an instrument; the lean
  FINAL program's NV assembly is byte-identical to T232's, so it costs nothing.

## RECOVERY

1. **Starting HEAD:** `0808a8c0` (T232). **2. Branch:** `quality/t217-deep-interior-lighting` (not
   merged; T226 separate). `PAchangelog.md` preserved. campaignGate PASS before the client run.
3. **Production baseline:**
   - T231 relief + form shading, with the T232 transport: lean constants plus the form packed
     into the primary density bits;
   - SIDE ~9.3 ms / PVN ~8.4 ms (T232 ABAB).
4. **Packed transport state:**
   - `cloudDensityImpl(..., out reliefForm)`; `paPackDensityForm` into 8 low mantissa bits,
     step 1/50.8, range ±2.5, flag `localStorm + 4`;
   - `GL_ARB_shader_bit_encoding` path, with a plain fallback;
   - modes 13 (primary signal) and 14 (decode check) present.

## SIGNAL

5. **Primary macro scale.**
   - G channel: `worleyFbm(period 8)` over the 3200-block tile (`stormMacroEnvelopeDomain` =
     0.0025 × 0.125 per block), so **400-block cells**.
   - Its autocorrelation (baked volume, `NoiseStat.java`) is 0.5 at ~135 blocks and 0 at ~300.
   - Measured mean 0.4790, SD 0.1184 (shipped calibration 0.4794 / 0.1163).
6. **Secondary scale.**
   - B channel: `worleyFbm(period 16)`, independent seed, so **200-block cells**.
   - Autocorrelation 0.5 at ~65 blocks and 0 at ~150.
   - Mean 0.4795, SD 0.1182, percentiles p1 / p50 / p99 = -2.23 / -0.01 / +2.28 SD.
   - It arrives in the SAME fetch as G (`.gb`), so it needs no extra texture work.
7. **THROUGH primary spread:** SD **0.372** (T231: 0.38); p5 / p50 / p95 -0.10 / 0.52 / 1.02;
   5-95 spread 1.12.
8. **THROUGH secondary spread:** SD **0.682**; p5 / p50 / p95 -1.76 / -0.40 / 0.21; spread 1.97;
   mean -0.61 (the region is small against the cells).
   - Its horizontal autocorrelation never falls below 0.5 across the region, while the primary's
     does at 150 px: THROUGH shows one broad gradient of B, not multiple phases.
9. **Other-view spread** (lower-anvil region, primary / secondary SD):
   - NEAREDGE 0.70 / 0.85;
   - LOWSIDE 0.80 / 0.79;
   - SIDE 0.75 / 0.73;
   - LATERAL 0.82 / 0.64.
10. **Field correlation.**
    - Over the baked volume: **0.001**.
    - In-view: THROUGH **-0.40**, NEAREDGE +0.23, LOWSIDE +0.26, SIDE +0.29, LATERAL -0.10.
    - The combined form `m1 + 0.5·m2` has SD THROUGH 0.393 (vs 0.372), NEAREDGE 0.90, LOWSIDE
      0.97, SIDE 0.93, LATERAL 0.85.
11. **Vein / fine score** (share of the signal's variance above ~2 px): secondary 0.000-0.028,
    equal to the primary's (0.000-0.026). There is no fine, cellular or vein content: both are
    broad lobes.
12. **Signal: technical PASS, effective FAIL.** The raw secondary has materially more spread at
    THROUGH (1.97 vs 1.12), but:
    - it is one gradient across the view;
    - in combination it adds only 6% to the form's SD there.

## CANDIDATE (the rejected diff is kept as `t233-candidate.diff`)

13. **Exact secondary equation:**
    - `m2 = clamp((B - 0.4795) / 0.1182, ±2.5)`;
    - relief `shift += lowerDeckWeight · 19 · m2` blocks at both stage-5 sites (primary density
      and the field-build / rain-gate storm-final density), lower-anvil window only;
    - uniform `PaUnderOctave`: 0 off, 1 relief, 2 relief + shading.
14. **Geometry amplitude:** 19 blocks per SD, half the primary's 38. The T231 primary term is
    unchanged.
15. **Shading amplitude:** the same field, in the same 0.5 ratio: `form = lowerWeight · (m1 + 0.5
    m2)`, times T231's 0.12 in shadow.
16. **Packing strategy:** no new density output. The two octaves are combined inside
    `cloudDensityImpl` into the ONE existing `reliefForm`. The density-bit packing was widened
    from 8 to 10 bits (step 1/136, range ±3.76) so the combined form does not clip; the density
    change stays below 2^-13 relative.

## QUALITY (one storm, adjacent arms, all on the monolith; `t233-captures.log`, `t233.json`)

Arms: **F0** production, **F1** + secondary relief, **F2** + secondary relief and matched shading.
Region = cloud interior with lower-deck weight > 0.2.

17. **THROUGH flat:** F0 0.98 -> F1 0.98 -> F2 **0.99**. Whole frame 0.73 in every arm.
18. **THROUGH contrast:** 0.36 -> 0.37 -> **0.34**.
19. **THROUGH form energy** (8-px-blurred SD): 2.65 -> **3.09** (relief alone) -> **2.35** (with
    shading). The relief's own luminance response and the matched shading have opposite signs
    here; everywhere else F2 adds to F1.
20. **Relief / shading correlation** (blurred image vs -form): THROUGH +0.91 -> +0.87; other views
    unchanged or up (SIDE +0.86 -> +0.89).
    - The geometry alone changes the THROUGH region by 0.74 levels mean |ΔL|, against 0.008
      outside the lower deck, so the relief is confined as intended.

## REGRESSION (F0 -> F2, lower-anvil region)

21. **NEAREDGE:** flat 0.84 -> 0.82, contrast 0.92 -> 1.02, form energy 4.66 -> 5.58.
22. **LOWSIDE:** flat 0.51 -> 0.45, contrast 1.78 -> 1.98. Lower-mass veins 0.257 -> 0.257.
23. **SIDE:** flat 0.56 -> 0.45, contrast 1.80 -> 2.04. Lower-mass vein score 0.49 -> 0.38.
24. **LATERAL:** flat 0.20 -> 0.23, contrast 2.99 -> 3.38. Veins 0.50 -> 0.50.
25. **ABOVE:** identical (no lower-deck region in view).

The already-good views move by as much as or more than THROUGH: the octave makes them busier,
not THROUGH readable. Frame means shift ≤ 0.7 levels.

## TEMPORAL

26-29. **Not run.** The brief gates motion on a static pass, and the static test failed at
THROUGH.

## BUILD-TO-BUILD PERFORMANCE

30-36. **Not run**, for the same reason: the candidate is not a production candidate.
- **The kept instrument is verified free without a timing run.** The lean FINAL program with
  mode 15 compiles to a byte-identical NV intermediate (`!!NVfp5.0`, 6,775 instructions, 121
  temporaries, 73 `CAL`s) to T232's. The driver back end gets the same input.
- **No runtime toggle was used for any cost statement.**
- **SIDE ≤ 10 ms:** unchanged from T232 (YES).

## DECISION

37. **REJECT (CASE C).** The second octave does not give THROUGH materially more usable form, and
    it makes the views T231 already fixed busier.
38. **Mechanism conclusion:** THROUGH's flat underside is not only a field-scale problem.
    - **Scale.** THROUGH views a grazing, perspective-compressed band of the underside. Even the
      200-block field reads there as one broad gradient.
    - **Correlation.** Two independent fields add per view with a random correlation. At THROUGH
      it happens to be -0.40, so the combined form barely grows.
    - **Sign.** At THROUGH the relief's own luminance response opposes the form shading.
      T231's sign ("hollows darker") was verified at the NEAREDGE underside edge, not at grazing
      incidence from below.
    - **Implication.** A finer macro octave would only repeat the busier-elsewhere trade. The
      next usable lever is a lighting term that follows the underside's orientation to the viewer
      or sky at grazing incidence, which this model does not have (T230).
39. **Production change:** none. Instrument kept:
    - mode 15, the B-channel signal at the shaded sample, same encoding as mode 13;
    - the primary stage-5 fetch reads `.gb`;
    - diagnostic clamp 0..15.

    The candidate uniform, relief, shading and 10-bit packing are removed.
40. **Exact next task: T234, the lean-vs-monolith motion gap that T232 found.**
    - **The gap.** Under motion the lean FINAL program shows 12-15% more flicker than the
      monolith (OUTSIDE 1.8-2.2×), equally on HEAD and T232's shader. It is pre-existing and
      concerns what players see.
    - **The method.** Attribute it with same-route arms per program difference: lean constants,
      split rain composite, temporal path.
    - **The underside line.** THROUGH is closed as view-geometry-limited until the lighting gains
      an orientation / sky-visibility term.
    - **Parked.** T226 remains parked.

## VALIDATION

41. **campaignGate:** PASS (`gate-t233.log`). 42. **check build:** PASS (`check-build-t233.log`).
43. **stormMorphologySandbox:** PASS (10 / 0). 44. **Markers:** `t211-captures.txt` retired; the
    T233 capture arms removed (driver restored to HEAD). 45. **git status:** `PAchangelog.md`
    modified and uncommitted; everything else committed. 46. **Commit:** see the branch log (not
    merged).

## Evidence (`t233-campaign/`)

| file | what |
|---|---|
| `t233-captures.log` | the capture set: per pose W (mode 12), M1 (13), M2 (15), F1, F2, F0 |
| `t233.json`, `t233.py` | signal, quality, regression and vein metrics |
| `T233_THROUGH_W_M1_M2_F0.png` | THROUGH: lower-deck weight, primary signal, secondary signal, production |
| `T233_F0_F1_F2_THROUGH_LOWSIDE.png` | production / + relief / + relief and shading at THROUGH and LOWSIDE |
| `NoiseStat.java` | channel statistics, correlation and autocorrelation of the baked base texture |
| `t233-candidate.diff` | the rejected candidate |
