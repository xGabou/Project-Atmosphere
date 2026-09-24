# T223 - Smoothed Base-Noise Form Signal

**The question:** can a cheap low-pass / coarser-LOD sample of the existing base-noise texture
separate large billow structure from the fine vein network well enough to drive shadow lighting?

**The answer: no. The signal FAILS - the density-derived shadow-form line is CLOSED.** One coarser
version of the base field (an emulated mip-2 fetch, the only low-pass that can be one fetch in
production) keeps the dome's form, as any mild low-pass would, but it does **not** reduce the vein
correlation (it rises slightly at every pose), it improves the billow-to-vein ratio by only
**1.04-1.17x**, it broadens features by ~1 block, and its ridge map sits on the current base field's
at **0.93-0.98** - the same vein topology, slightly blurred. No lighting candidate was built.

The reason is structural, and it is why no low-pass of this texture can succeed: the base field is
`smoothstep(0.713, 0.845, carrier)` - a steep threshold on a carrier whose own features are ~109
blocks. **The veins are that threshold's iso-contours, not a high-frequency component.** A low-pass
of the texture barely moves a smooth 109-block carrier, so the contours stay where they were and the
smoothstep draws them thin again.

## RECOVERY

1. Starting HEAD: `99bc732` (T222, "pre-erosion density carries the veins too; hypothesis
   disproved").
2. Branch: `quality/t217-deep-interior-lighting`.
3. Production state, verified and unchanged at exit: `PRODUCTION_TEMPORAL_MODE=1`,
   `PRODUCTION_SPATIAL_UPSCALE=-1`, `PRODUCTION_RAIN_LATTICE_MODE=0`,
   `PRODUCTION_ORIGIN_JITTER_MODE=0`, `PRODUCTION_MACRO_ENVELOPE_MODE=3`,
   `PRODUCTION_LOWER_BODY_MODE=1`, `PRODUCTION_DEEP_SCATTER_MODE=1`, field bits 43.
   `PAchangelog.md` the only working-tree change. `campaignGate` PASS before client work
   (`gate-t223.log`).

### The base-noise sample (Task 1)

4. **Texture / sample path.** `BaseNoiseSampler`: a 128^3 RGBA8 3-D texture. The storm primary
   sample is `texture(BaseNoiseSampler, baseNoiseDomain(samplePos, STORM_BASE_NOISE_SCALE),
   mipBias)` - a rotated world position times 0.0025 plus a slow warp - so one tile spans **400
   blocks** and one texel **3.125 blocks**. Channels: R the Perlin-Worley carrier; G/B/A the Worley
   octaves at periods 8/16/32 per tile, i.e. **50 / 25 / 12.5 blocks**, combined as `lowFbm = 0.625
   G + 0.25 B + 0.125 A`. Then `carrier = remap(R, -(1 - lowFbm), 1, 0, 1)` and the base field is
   **`smoothstep(0.7128, 0.8451, carrier)`** - a 0.13-wide threshold.
5. **Current LOD behaviour: there is none.** The texture is uploaded with `GL_LINEAR` min and mag
   filters, **level 0 only**, no `glGenerateMipmap`. With a non-mipmapped min filter the GL never
   selects a level, so:

   - explicit LOD / a "coarser mip" **cannot be fetched** - `textureLod` returns level 0;
   - **every `mipBias` argument on this texture in production is inert** - including the light
     cone's per-tap `i * 0.6` bias. The detail texture goes through the same `upload3d` and is in
     the same state, so T218's "forward probe at LOD 1.2" was in fact sampling level 0;
   - mipmapping the shared texture is **not** a diagnostic: it would activate every one of those
     dormant biases, with implicit derivatives evaluated inside a raymarch loop, across all of
     production. It was not done.

6. **Diagnostic smoothing method.** One coarser version only, as the goal requires: **an emulated
   mip-2 fetch.** Nine reads (the sample plus the eight corners at +/- one texel; each read is itself
   trilinear over a texel, so together they cover a 4-texel box) average the **raw channels first**,
   and the production carrier math and remap are applied after - exactly what a shader reading a
   real mip level would compute. This is the only low-pass that could become **one fetch** in
   production (from a dedicated mipmapped copy). An average taken *after* the remap would need the
   nine fetches in production too, so it fails the "cheap" requirement by construction and was not
   carried as a candidate signal.
7. **World-space scale, B0:** the production base field - 3.125-block texels, finest Worley period
   12.5 blocks, carrier feature ~109 blocks (T100's measurement).
8. **World-space scale, B1:** B0 low-passed over a **12.5-block** box (4 texels) - wide enough to
   remove the finest Worley octave, narrow enough to keep 25-50-block billows.
9. **B2: not built.** It was conditional on B1 establishing a useful trend; B1 established none
   (items 11-20).
10. **Mismatch count: 0** in every region of every pose. Signal and position are recorded together
    in one `cloudDensity` call (T222's invariant), and a view paints magenta on a mismatch.

One run, fixture `2575e7d1` (radius 689.2), **no fixture loss**: SIDE, LOWSIDE, LATERAL and
NEAR_EDGE all complete, arms adjacent per pose, B0/B1 on the monolith and production on lean FINAL
per the CONTROLS audit. Regions split at the neck from the composite's ALPHA view. Bands: vein =
Hessian ridge at sigma 1.5 px; form = band-pass between Gaussian sigma 4 and 24 px.

## UPPER DOME

| pose | vein corr vs production B0 -> B1 | form corr B0 -> B1 | billow:vein B0 -> B1 | feature scale B0 -> B1 (blocks) | B1 ridges on B0's |
|---|---|---|---|---|---|
| SIDE | +0.083 -> **+0.102** | -0.187 -> -0.193 | 5.38 -> 6.28 (1.17x) | 36.0 -> 37.3 | **0.947** |
| LOWSIDE | +0.109 -> **+0.129** | -0.257 -> -0.256 | 3.88 -> 4.32 (1.11x) | 27.5 -> 28.5 | **0.962** |
| LATERAL | +0.002 -> -0.001 | -0.178 -> -0.179 | 2.65 -> 2.82 (1.07x) | 19.6 -> 21.2 | **0.981** |
| NEAR_EDGE | +0.329 -> **+0.332** | -0.252 -> -0.259 | 5.54 -> 5.96 (1.08x) | 29.3 -> 29.7 | **0.978** |

11. B0 vein correlation: **+0.083 / +0.109 / +0.002 / +0.329**.
12. B1 vein correlation: **+0.102 / +0.129 / -0.001 / +0.332** - not lower anywhere; higher at
    three of four poses.
13. B0 form correlation: **-0.187 / -0.257 / -0.178 / -0.252**.
14. B1 form correlation: **-0.193 / -0.256 / -0.179 / -0.259** - retained, as any mild low-pass of
    a signal whose power is already in the form band would be.
15. B0 billow:vein: **5.38 / 3.88 / 2.65 / 5.54**.
16. B1 billow:vein: **6.28 / 4.32 / 2.82 / 5.96** - a 1.07-1.17x change, not a clear improvement.
17. Dominant feature scale: **B0 19.6-36.0 blocks, B1 21.2-37.3** - broader by ~1 block. The
    fine band falls only ~10% (SIDE 0.0138 -> 0.0120), the low band not at all (0.1274 -> 0.1264).

## LOWER MASS

18. B0 vein correlation: **-0.029 / +0.392 / +0.131 / +0.425** (SIDE / LOWSIDE / LATERAL /
    NEAR_EDGE).
19. B1 vein correlation: **-0.011 / +0.408 / +0.137 / +0.432** - again equal or higher.
20. Billow:vein change: **2.28 -> 2.43, 3.39 -> 3.66, 1.91 -> 1.98, 3.57 -> 3.80 (1.04-1.08x)**;
    ridge-map agreement B1 vs B0 **0.927-0.983**.

## SIGNAL DECISION

21. **Signal: FAIL**, against every criterion the goal sets:

| criterion | result |
|---|---|
| clearly lower vein correlation | **fails** - equal or higher at all eight region-poses |
| preserved or improved dome form | passes, trivially (within 0.01) |
| clearly better billow-to-vein ratio | **fails** - 1.04-1.17x |
| broader world-space features, not the same veins blurred | **fails** - ~1 block broader; ridge maps 0.93-0.98 identical |
| deterministic and cheap | would pass (one fetch from a mipmapped copy) - moot |

22. **Visual diagnostic.** At 1:1 (`t223-campaign/T223_SIDE_B0_vs_B1_1to1.png`) B1 is marginally
    softer in the finest texture and otherwise identical: the same lobes, the same dark cellular
    pockets, the same edge network. A numerically smoother version of the same vein topology - the
    outcome the brief names as not success.

## IF CANDIDATE BUILT

Not built - the goal allows a candidate only if the signal passes.

23. Exact lighting equation: none.
24. Dome flat fraction before/after: not applicable.
25. Dome billow score: not applicable.
26. Dome vein score: not applicable.
27. Lower-mass vein delta: not applicable.
28. Silhouette invariance: not applicable to a candidate; production repeat P0B reproduces P0, and
    no change that could move alpha was made.
29. Motion flicker: not run - no candidate.
30. Billow crawl: not run - no candidate.
31. Added cost: none in production. The diagnostic B1 view costs nine base-texture reads per lit
    sample and runs only while view 8 is selected; a productionized B1 would have been one fetch
    from a dedicated mipmapped copy (~9.4 MB with its chain). Not built, not priced further.
32. Whole renderer <= 10 ms: **yes, unchanged** (gameplay-phase `gpuMs` p50 1.541, T218).
33. KEEP/REJECT: **no candidate** - the signal failed first.

## FINAL

34. **Mechanism conclusion.** T221 found the cells are lighting; T222 found every density stage
    that carries the dome's form also carries the veins; T223 finds that low-passing the base
    texture cannot separate them either - because the veins are not a frequency band.

    The base field thresholds the carrier through `smoothstep(0.7128, 0.8451, ...)`, a window 0.13
    wide. Its thin lines are **the iso-contours of that threshold on a smooth ~109-block carrier**.
    A 12.5-block average barely changes a 109-block field (low band 0.1274 -> 0.1264), so the
    contours do not move, and the steep smoothstep redraws them thin; the fine band drops only
    ~10%, and the ridge maps stay 0.93-0.98 identical. A wider box would erode the billows long
    before it moved the contours. **Any signal read through that smoothstep inherits the veins;
    any signal not read through it is not the dome's form** (T222's macro envelope, billow
    correlation ~0).

    **Density-derived shadow-form lighting is closed as a path.** Three tasks, three independent
    signals (local final density, pre-erosion body, low-passed base field), and one mechanism that
    explains all three results.

35. **Exact next task: T224, the camera-altitude light reach** - the separate finding T221 made and
    T222/T223 deliberately left alone. A camera vertically inside the cloud layer gets a 4-tap,
    102-block light cone; below the layer, 6 taps and 240 blocks; so the lower mass is lit under a
    ~400-block anvil only when the camera is up there. It is an architectural and cost question -
    the cap exists for cost - and it should be priced before anything changes. Of the two remaining
    visual defects it is the one with a known, measurable mechanism.

    Recorded for any later look at the dome: if its flatness is revisited, the lever is **the
    shadowed lighting model itself** (what ambient/multiple-scattering does at high optical depth),
    not a density-derived modulation of it - T221-T223 exhaust the latter.

## VALIDATION

36. `campaignGate` PASS: before client work (`gate-t223.log`), with the arms (`gate-t223b.log`),
    after the collapse (`gate-t223c.log`).
37. `check build` PASS (`check-build-t223.log`).
38. Morphology sandbox PASS - 10 `PHASE4S_RESULT ... PASSED`, standalone after the collapse.
39. Markers: `run/t132-autorun-exit.txt` armed throughout; `run/t211-captures.txt` the only
    campaign marker, retired. No motion marker.
40. `git status`: `PAchangelog.md` left modified and uncommitted as required. No evidence, campaign
    log or unrelated log deleted.
41. Committed on `quality/t217-deep-interior-lighting`. **Not merged.**

### Program scope

The capture driver is byte-identical to `99bc732` (the shot block and arm plumbing removed). Kept as
instruments, in T221/T222's pattern: views **7** (B0, the production base field) and **8** (B1, the
emulated mip-2) on `PaDiagnosticLightingMode`, with their stage globals and the diagnostic helper.
Their writes sit behind `PaDiagnosticLightingMode >= 7`, inside the existing `>= 4` gate, so
**production never runs them**.

An interrupted edit is recorded for completeness: an earlier version of the arm set (B1 after the
remap, B1 before it, a 25-block B2, plus reference stages) was partly written before being stopped;
it was narrowed to the single coarser signal before any gate or client run, and nothing from it was
captured.

## HONEST LIMITS

- **B1 is an emulation of a mip-2 fetch**, not a real mip level: nine trilinear reads around the
  sample approximate a 4-texel box filter. A true mip is a box over aligned texels; the difference is
  a sub-texel shift in footprint and does not bear on a result whose ridge maps agree at 0.93-0.98.
- **One coarser level was tested**, as the goal requires. The mechanism (item 34) is what licenses
  generalizing beyond it: a coarser level smooths the carrier further but leaves the threshold's
  contours in place until it has also erased the billows.
- **One storm.** Four poses of it, all valid, all agreeing; the numbers are per-storm, the
  direction is not.
- **The vein/form bands are screen-space** (sigma 1.5 and 4-24 px) at one capture scale, as in
  T222.
