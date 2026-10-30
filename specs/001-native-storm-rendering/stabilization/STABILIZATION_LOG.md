# Stabilisation log

This is the single running log of the stabilisation work approved on 26 Oct 2026. The plan is
`validation/root-cause-investigation/08_STABILIZATION_PLAN.md`; the register IDs (RC-xx) are in `07_ROOT_CAUSE_REGISTER.md`.

How to keep this file:
- **Status table:** update its row in place.
- **Entries:** append-only, newest last, one entry per attempt, using the template.
- Evidence (frames, metrics) goes to `validation/stabilization/<step>/` (local, git-ignored). The entry links it.
- A failed or abandoned attempt still gets an entry. Never delete one; mark it superseded.

## Status

| Step | Register | Status | Current state of the code | Last entry |
|---|---|---|---|---|
| S0 player-equivalent capture | RC-02, RC-03 | done | `ProductionCloudCapture` (diagnostic, OFF by default); pinned-vs-live measured | E2 |
| S1 colour pipeline | RC-01 (+ RC-21 found) | ADOPTED (user: full_g2) | production default on | E16 |
| S2 multi-scatter energy | RC-08 | ADOPTED | production default on | E16 |
| S3 tone-curve placement | RC-11 | not started | — | — |
| S4 light-march near field | RC-10 | rejected (cost, no gain) | removed (shader, config, driver) | E3 |
| S5 preview conversion | RC-04, RC-07, RC-15 | done | S5a resolution on; S5b `/cloudpreview extinction scaled` (default legacy, lattice-limited) | E5 |
| S6 march step at opaque surfaces | RC-06 | disproven (light lattice, not march) | reverted | E5 |
| S7 geometry rules | RC-13, RC-14, RC-16 | S7a built (preview candidate `t19`), not yet run; production shape root cause in SHAPE_ROOT_CAUSE.md | experimental only | E8 |
| S11 seeded storm structure (step a) | Codex CR-03 | ACCEPTED (user), production; v5 cost fix | `PaStormStructure` = 15 | E19 |
| E20 storm lighting height step | CR-02 | FIXED, production | `PaStormMaterialFix` = 2 | E20 |
| P1 puff occupancy reject (perf) | E21 | production (image-identical) | `PaPuffOccupancy` = 1 | E21 |
| S9 billow hierarchy (shape root cause) | SHAPE_ROOT_CAUSE | inconclusive: geometry works, lighting cannot show it | `PaShapeHierarchy` default 0 | E9 |
| E10/E11 light-field coverage bug | Codex CR-05 | FIXED, production default ON | `PaFieldCoverageFix` = 1 | E11 |
| E12 big-cloud darkness (optical thickness) | Codex CR-06 / user | ADOPTED gain 2 | `PaOpticalThicknessGain` = 2 | E16 |
| E13 storm material metadata lost | new; Codex CR-01 / CR-02 | ADOPTED; removes the bolted lower block | `PaStormMaterialFix` = 1 | E16 |
| S8 complexity isolation + RCA-TEMP removal | RC-19, RC-20 | RCA-TEMP removed; harness isolation awaits its own approval (plan) | B11 fix kept permanently | E6 |

## Entry template

```
### E<n> — <date> — S<k> <short title>
- Question / defect:
- Change (files, gated how):
- In-game validation (session log, profile, arms):
- Result (numbers + evidence path):
- Verdict: KEEP / REJECT / INCONCLUSIVE (+ why)
- Code state after: (kept / reverted / gated off)
```

## Entries

### E1 — 26 Oct 2026 — S0 player-equivalent capture driver
- **Question / defect.** Production visual evidence came from source-patching scripts (`cloud_gallery.py` over
  `t241_capture_repo.py`) with the governor pinned. S0 needs a reusable way to capture production clouds as a player
  sees them, with paired A/B arms in one session.
- **Change.**
  - New diagnostic `clouds/client/render/volumetric/ProductionCloudCapture.java`. It is OFF unless
    `PA_PROD_CAPTURE_SET` is set; gradle `-PpaProdCapture=<set> -PpaProdCaptureWorld=<save>`.
  - What it does:
    - spawns production cloud types (`CloudGroupSpawner.spawnRequestedCloud`) 6000 blocks apart, with movement frozen
      in memory only;
    - frames named views from the live region bounds (pose logic ported from the cloud gallery);
    - per view, lets the frame-time governor settle **live**, then holds that step scale for the paired arms of the
      view;
    - an arm may instead pin 1.0, to reproduce what older campaigns reviewed;
    - shoots `run/screenshots/prodcapture/<set>/<type>__<view>__t<time>__<arm>.png`, logs `PROD_CAPTURE_SHOT` with
      stepScale / GPU median / resolution / temporal / program, and quits.
  - `StormT132AutoDriver` also stays inert while it runs (stale campaign markers in `run/`).
  - Metrics: `stabilization/tools/ab_metrics.py <dir> <control> <arms...>`.
- **Code state after.** Kept (diagnostic tool, OFF by default).

### E2 — 26 Oct 2026 — S0 + S1 + S2 first production session (`s1`)
- **Question / defect.**
  - S0: does pinning the governor at 1.0 (all older captures) change the image players see?
  - S1: does a colour-managed pipeline (RC-01) make production storms white?
  - S2: how much do the un-normalised octaves (RC-08) add?
- **Change.** All switches are runtime, so the arms are paired in one session.
  - `PaColorPipeline` (march) + `ColorPipeline` (composite), `VolumetricCloudDebugConfig.colorPipeline()`:
    - Minecraft sun / sky colours are linearised (gamma 2.2);
    - `evaluateLightingComponents` returns linear `radiance * toneExposure`;
    - the composite applies `1 − exp(−c)` once to the reconstructed colour, then encodes it with gamma 1/2.2;
    - the stratus relief's 0.98 display cap is lifted in this mode.
  - `PaOctaveNormalize` (`octaveNormalize()`): 0 drops `scatter /= scatterWeight`.
  - Production defaults are unchanged (`PRODUCTION_COLOR_PIPELINE = 0`, `PRODUCTION_OCTAVE_NORMALIZE = 1`). These are
    live uniforms in lean FINAL until a verdict, so they cost roughly 0.7 ms; bake them as constants when one is kept
    (T232 rule).
- **In-game validation.**
  - Session `runClient-s1.out.log`, ProductionCloudCapture set `s1`, ULTRA 1600×900.
  - Clouds: calvus, supercell, mediocris. Views: side / below / anvil / far (storms), side / below / close (mediocris).
    Times 6000 and 1500.
  - Arms: `legacy`, `legacy_pin100`, `cm`, `cm_noctnorm`. 88 frames in `run/screenshots/prodcapture/s1/`.
- **Harness defects found (fixed in the driver, so later sessions are clean).**
  - (a) A "[Atmosphere] Entered region … Forecast regenerated" toast lands in the first frame per view; fixed by
    `hideGui` during the capture.
  - (b) The sky / weather tint keeps changing for seconds after a teleport or time change, and the cloud ambient reads
    the sky colour. The first arm per view and time (`legacy`) was taken mid-transition (25–70 % of its pixels differ
    at some noon views). Fixed with a 10 s settle.
  - Consequence for this session: `legacy_pin100` (the second arm, settled) is the legacy reference. That is valid
    because pinning barely changes clouds (below).
- **Result.** `validation/stabilization/s1/metrics_vs_legacy_pin100.txt`; luma on the cloud mask.
  - **S0, governor.**
    - Storms settle at stepScale 0.5 (march 2.1–3.6 ms); mediocris at 1.0 (~1.0 ms).
    - On the views not contaminated by the transition (t1500), pinned 1.0 vs live 0.5 changes 1.4–5.1 % of cloud
      pixels by > 8 levels, mean ±1.
    - **The governor floor has little visible effect at these distances.** RC-02's image impact is small. The process
      defect (reviews never saw live settings) stays, and is now fixed by this driver.
  - **S1 colour pipeline (`cm` vs legacy).**
    - Lit p90 +16..+23 levels (storm side 181 → 204; mediocris close 196 → 208).
    - **But the shadow floor rises with it: storm p10 ~94 → ~135 on every storm view.** Fine structure (lap) −10..−25 %.
    - The legacy p10 is ~94 on EVERY storm view (calvus and supercell, every angle and sun): a flat floor, not
      physics.
    - **Cause:** the ambient never falls below 58–74 % in shadow (`mix(retention, 1, exp(−od))`, RC-21, new). The
      legacy per-sample curve compressed that floor into a mid-grey; a correct display transform lifts it.
  - **S2 octaves (`cm_noctnorm` vs `cm`).** Lit p90 +17..+21 more (storm side 222–226, anvil 232–234); shadows +6..+10;
    structure +5..+10 % vs `cm`.
- **Verdict.**
  - S1 alone: **REJECT as-is.** Whiter tops, but the shadow floor is lifted and contrast falls. S1 is right in
    principle; it exposed RC-21, which must be fixed with it.
  - S2: promising, judged together with S1b.
  - S0: driver KEEP.
- **Code state after.** Switches kept at their production defaults (legacy); new arms for S1b / S4 prepared (E3).

### E3 — 26 Oct 2026 — S1b ambient transmission, S2, S4 near field (`s2`)
- **Question / defect.**
  - E2 showed a flat shadow floor (RC-21): ambient `mix(retention 0.58–0.74, 1, exp(−od))`.
  - Is the diffuse two-stream transmission of the light path, `1 / (1 + 0.75 (1 − g) od)` with g = 0.85, the missing
    term?
  - Does the S4 near-field tap (RC-10) help?
- **Change.**
  - `PaAmbientTransmission` (`ambientTransmission()`), new: replaces the retention floor with the two-stream
    transmission.
  - `PaLightNearField` (`lightNearField()`): one exact density tap at 3.5 blocks sunward covering [0, 7]; the first
    legacy tap then covers [7, 14].
  - Both are runtime switches with legacy defaults.
- **In-game validation.**
  - `runClient-s2.out.log`, set `s2`, same clouds / views as E2 (storms side / below / anvil, mediocris side / close),
    times 6000 / 1500.
  - Arms `legacy`, `cm`, `cm_at`, `cm_at_mx` (S1 + S1b + S2), `cm_at_mx_nf` (+ S4). 80 frames. GUI hidden; 10 s
    settle, so no transition artefacts this time.
- **Result.** `validation/stabilization/s2/metrics.txt`, `s2_calvus.png`, `s2_med.png`.
  - `cm_at_mx` vs legacy, storms:
    - lit p90 178–195 → 221–233;
    - shadow p10 91–96 → 104–129 (vs `cm` alone 134–137);
    - fine structure equal or higher on side / anvil / below (calvus side lap 3.76 → 4.05; supercell side 3.54 → 3.69);
    - GPU cost unchanged (calvus side 3.92 → 3.99 ms, supercell 3.86 → 3.86).
  - `cm_at_mx` vs legacy, mediocris: lit p90 190 → 228. The grey shading is kept and reads as white fair-weather
    cumulus (`s2_med.png`).
  - **New defect exposed:** the rain-classified lower storm trunk becomes a flat, pale beige block under every cm arm.
    The rain path's light depth is `rainOverheadOpticalDepth` saturated at 2.2 (T226), so with S1b it keeps ~80 % of
    the sky while the tower above (light-path od ~20) keeps ~30 %.
  - **S4:** +0.6–0.7 ms march (+17 %); lit p90 −8..−10 and structure −5..−10 % vs `cm_at_mx`. No visible relief gain at
    these distances.
- **Verdict.**
  - S1 + S1b + S2: **KEEP as the candidate**, pending the trunk fix (E4).
  - S4: **REJECT.** It costs, and it does not reveal relief. RC-10 stays a confirmed mechanism with no cheap cure;
    revisit only with a cheaper near-field source.
- **Code state after.**
  - `rainOverheadOpticalDepth` saturates at 20 instead of 2.2 when `PaAmbientTransmission` is on (legacy path
    unchanged).
  - S4 switch kept off; to be removed in S8 if no later use.

### E4 — 26 Oct 2026 — candidate confirmation across the day (`s3`)
- **Question.** Does the candidate hold at low sun, sunset and night, and at what cost? Candidate = S1 colour pipeline +
  S1b ambient transmission + S2 octave energy, with the rain-cap fix (rain path saturates at 20).
- **In-game validation.**
  - `runClient-s3.out.log`, set `s3`, arms `legacy` / `cand`, times 6000 / 1500 / 12300 (sunset) / 18000 (night).
  - Clouds: calvus, supercell, mediocris. 64 frames.
- **Result.** `validation/stabilization/s3/metrics.txt`, `s3_calvus_side.png`.
  - **Storms, day.** Lit p90 181–196 → 224–233; shadow p10 91–97 → 108–133; fine structure ≥ legacy (side lap
    3.80 → 4.02, 3.41 → 3.59).
  - **Sunset.** Richer colour (calvus side mean 73 → 94); shadows unchanged (p10 47–48).
  - **Night.** Still dark (mean 24 → 32); structure equal.
  - **Cost.** Equal within noise (calvus side 4.08 → 4.02 ms, supercell 3.87 → 3.99 ms, mediocris 1.09 → 1.13 ms).
  - **Lower storm trunk** (rain-classified body below the base; calvus side box): luma 97 → 140. It is still a flat
    block (SD 3–4 in both arms; that is the pre-existing T245 classification) and is now paler relative to the tower
    (0.74 → 0.86). B−R −0.2 → −7.1 (beige).
  - **Mediocris.** Whiter (noon p90 193 → 229) but flatter (p10 137 → 196, lap −15..−20 %). The thin cloud's light
    path barely lowers the transmitted ambient, so its shaded side loses depth.
- **Verdict.**
  - Storms: candidate better at every time of day and free.
  - Two look questions remain, and they are the user's call (T244 set the trunk level by user verdict; project rule:
    no productionising a look change before the user accepts a gallery):
    - (1) the lower-trunk level;
    - (2) the fair-weather cumulus contrast.
- **Code state after.**
  - The beige cast has a correctness cause: the display-space `rainTint` and sunset `sunTint` were used as multipliers
    in the linear pipeline. Both are now linearised by `paInputColor` when the pipeline is on; the legacy path is
    byte-identical.
  - All switches stay at their legacy defaults. Next: the review gallery (E6) and the preview session (E5).

### E5 — 26 Oct 2026 — S5 preview conversion and S6 fixture step (`/cloudpreview` demo `s5`)
- **Question / defect.**
  - S5a: the preview's 4-px grid (RC-04).
  - S5b: its geometry-compressed, extinction-uncompressed medium (RC-07).
  - S6: the contour banding that S5b exposed. Is it march-step quantisation (RC-06)?
- **Change.**
  - **S5a.** `VolumetricCloudRenderer`: with the fixture shown and no `/cloudpreview res` override, march at
    `min(1, 2 × profile scale)`. That is the temporal lattice's effective resolution, which the fixture never runs.
  - **S5b.** `ExperimentalCloudPreview.previewExtinction()` replaces the RCA-TEMP override:
    - `CELLS / scale.min()` when `scaledExtinction`;
    - command `/cloudpreview extinction scaled|legacy`;
    - the HUD / info line now states the smallest representable feature (~2 voxels in model metres) and the
      extinction.
  - **S6 (tested, then reverted).** Fixture exterior step = 0.6 mean free paths (≥ 0.5 blocks); the shader clamp was
    relaxed for the fixture only.
- **In-game validation.** `runClient-s5.out.log`.
  - Demo `s5`: frozen D2, seed 2609101 mature, sun 35, five views.
  - Arms: `legacy` (res 0.25, extinction 0.055, legacy colour), `s5_nostep` (S5a + S5b), `s5` (+ S6), `s5_cm`
    (+ S1 colour), `s5_m28` (gradient normals). Timed per arm.
- **Result.** `validation/stabilization/s5/`.
  - **S5a.** 4-px grid score eside 1.49 → 1.00, cthree 1.34 → 1.02. Pass time 0.9–1.2 → 1.4–2.1 ms.
  - **S5b.** Fine structure up as in RCA, but the lit image shows concentric contour rings (underside) and a regular
    node stipple (flanks) (`s5_sheet.png`).
  - **S6.** The 5× finer step left the rings and stipple **identical** at 4–5× the cost (pass 5.1–9.8 ms).
  - Mode 28 (normals from the density, no light lattice) is smooth at the same cameras (`s5_m28.png`).
  - **Cause of the rings:** the baked light lattice (`NeutralLightVolume`, 10–16-block spacing, od capped at 8).
    At 0.647/block the light-path od rises ~9 per lattice cell and saturates, and trilinear interpolation of a
    saturated quantity draws contours. **Not the march.**
- **Verdict.**
  - S5a: **KEEP** (default on).
  - S5b: **KEEP as an option, default OFF.** A baked lattice cannot resolve a 1.5-block mean free path. The preview
    with S5b is lattice-limited, without it optically too thin: documented in code, and the HUD states the
    representable feature size.
  - S6: **REJECT; the hypothesis is disproven** (RC-06 re-attributed to the light lattice). Code reverted completely.
  - `s5_cm`: the neutral preview model under the colour pipeline is greyer and flatter. The preview's
    single-scattering neutral model (RC-09) needs the production family once S1 lands; nothing to do in the preview
    first.
- **Code state after.** S5a on; S5b switch default legacy; S6 removed.

### E6 — 26 Oct 2026 — S8 part 1: RCA-TEMP removal, S4 removal
- **Change.**
  - Removed every RCA-TEMP insertion from the root-cause audit (`10_INGAME_SESSION.md`), approved by the user with the
    plan:
    - composite debug mode 8 and `RCA_DISPLAY_ENCODE`; the real S1 `ColorPipeline` supersedes them;
    - lighting mode 44, its generated `..._neutral_lfam_ms` variant and `CONVECTIVE_SYSTEM_NEUTRAL_LFAM_MS`;
    - the `rca` / `rcaq` demo sets with their `comp` / `quality` acts;
    - `launch-rca*.cmd`.
  - Kept, now permanent:
    - the demo bake-completion quality clause (B11 / RC-20);
    - the `ext` demo act;
    - the extinction override, which became S5's `previewExtinction()`.
  - Removed the rejected S4 near-field switch (`PaLightNearField`: shader, JSON, config, renderer, driver arms).
- **Verification.** `grep` for `RCA-TEMP|RCA_|lfam_ms|LightNearField` in `src/main` and `build.gradle` returns
  nothing. `cloud_field_composite.fsh` and `CloudFieldCompositeDebugMode.java` differ from HEAD only by the S1 change.
- **Not done (needs its own approval per plan S8).** Isolating the ~35k-line campaign harness, and deleting the rejected
  T255–T258 candidates and closed shader arms. The inventory is in `06_DEAD_CODE_AND_COMPLEXITY.md`.

### E7 — 26 Oct 2026 — review gallery for the user (`s4`)
- **What.** All 9 production families (calvus, supercell, humilis, mediocris, congestus, stratus, stratocumulus,
  nimbostratus, cirrus); legacy vs candidate (S1 + S1b + S2 with the rain-cap and tint fixes); times 6000 / 1500;
  108 frames. Session `runClient-s4.out.log`.
- **Where.** `validation/stabilization/s4-review-gallery/index.html` (one sheet per family, columns legacy | cand),
  plus `metrics.txt`.
- **Awaiting the user's verdict.** Production defaults are still legacy. On acceptance: set the PRODUCTION_* defaults,
  bake the switches as lean FINAL constants (T232 rule), re-time build-vs-build.
- **Still to run.** The S7a preview session (demo `s7`, candidate `t19`), needing a fresh build first. S7b / S7c are not
  started.

### E8 — 26 Oct 2026 — user review of s4; shape root cause; S9 arm
- **User verdict on s4.**
  - New lighting: "nice".
  - Big-cloud undersides (nimbostratus) not dark enough: the old darkness is preferred there.
  - The lighting now exposes the shape problem: mediocris looks like spheres put together; storms look like two clouds
    bolted together; the supercell lacks bumps-on-bumps, cavities and surrounding clouds; families look alike.
  - Asked for the ROOT problem first. Codex review: `validation/stabilization/codex review/` (CR-01..CR-08, T1..T5).
- **Root cause.** `stabilization/SHAPE_ROOT_CAUSE.md`.
  - Every production cloud is a small fixed-topology set of smooth analytic primitives (7 / 8 / 12 / 10 members; fixed
    tier recipes; storms reduced to base + column + deck).
  - The only sub-primitive form is fixed-scale (6–22-block) threshold noise, switched off at joins / cores / base
    (Codex CR-04).
  - **Measured in game (s4 frames):** silhouette box-counting dimension 0.96–1.01 for all cumulus (a smooth curve),
    1.03–1.13 for storms and sheets, against 1.23–1.25 for the user's supercell photo at the same pixel width.
    Tool: `stabilization/tools/silhouette_dimension.py`.
  - Codex CR-01 (rain-classified lower block), CR-02 (metadata switches), CR-05 (light-field bands) and CR-06 (ambient on
    sun-path od) are independent contributors, not the root.
- **Change (S9, runtime arm, default off).** `PaShapeHierarchy` (float, `VolumetricCloudDebugConfig.shapeHierarchy()`).
  - `paBillowHierarchy`: three octaves of Worley bumps (BaseNoise.G), cell 0.45 × local lobe radius halving per octave,
    amplitude 0.20 × 0.55^k.
  - Added to the puff envelope in `resolvePuffContinuousField`, everywhere except the base-root corridor and at half
    strength across junctions.
  - The local radius is the envelope-weighted mean of the candidates (continuous; avoids a CR-02-type seam).
  - Capture set `s9`: humilis / mediocris / congestus, arms `cand`, `cand_h1`, `cand_h2`.
- **In-game validation.** Pending (E9).

### E9 — 26 Oct 2026 — S9 billow hierarchy on cumulus (`s9`)
- **In-game validation.**
  - `runClient-s9.out.log`; humilis / mediocris / congestus; side / close / below; t6000 / t1500.
  - Arms `cand` (candidate lighting), `cand_h1`, `cand_h2` (S9 amplitude 1 / 2). 54 frames.
  - Sheets: `validation/stabilization/s9/sheets/`.
- **Result.** `validation/stabilization/s9/metrics.txt`.
  - Fine structure: amplitude 1 +30..+60 %, amplitude 2 +75..+120 % (e.g. mediocris close lap 1.03 → 1.41 → 1.76).
  - Silhouette dimension: 1.02 → 1.05 → 1.07 (still far from the photo's ~1.24).
  - Cost +0.05–0.18 ms (close views).
  - **Visually it reads as fuzzy cotton breakup, not as billows** (`s9_med.png`).
- **Why (hypothesis to test).** Real billows read through their self-shadowing. The production light path cannot
  resolve form below ~14–16 blocks: light taps start at 14 blocks, the non-storm light comes from the 16-block field
  (CR-05) and the ambient comes from global colours. A bump smaller than that is only a density change at the edge; it
  never darkens its own lee side. S9 adds the right geometry where the lighting cannot show it.
- **Found on the way.** A sharp-edged, box-shaped dark slab through mediocris in the low-sun side view, present in every
  arm (`s9_bar.png`). Candidate for the CR-05 light field; attributed in E10 (Codex T2).
- **Verdict.** INCONCLUSIVE. The geometry part works but is invisible without per-bump shading. Next: E10 (light-field
  box), then a light-resolution question for S9.
- **Code state after.** `PaShapeHierarchy` switch kept, default 0.

### E10 — 27 Oct 2026 — Codex T2: the box-shaped bands are a light-field bug (`t2`, fix arm `t2b`)
- **Question.** Is the sharp dark slab / vertical band in cumulus (E9, Codex CR-05) geometry, reconstruction or light?
- **In-game validation.**
  - `runClient-t2.out.log`; mediocris and congestus, side / close, t1500.
  - Arms: FINAL (`cand`), diagnostic monolith with production field bits 43, monolith without the field LIGHT consumer
    (bits 42), and both monoliths in lighting mode 9 (log od).
  - Evidence: `validation/stabilization/t2/t2_arms.png`.
- **Result.**
  - The box / bands are present in FINAL and mono43 and **absent in mono42**, on both clouds; the od view shows the same
    sharp column.
  - Geometry and reconstruction are excluded (mono = non-temporal, same density).
- **Cause (code).**
  - The field build writes R = −10 (uncovered) where non-storm material is possible, and R = 1.0 / covered floors
    ∈ [1 − 1.65, 1] elsewhere.
  - `paStormFieldLightDensityAt` reads R through hardware bilinear filtering and calls a tap uncovered only if
    `min(f0.r, f1.r) <= −5`. Along every covered / uncovered node boundary (the edge of every puff footprint) the
    filtered R lands in (−5, 1); the tap then takes the STORM branch with that meaningless `lowerBound` and fabricates
    storm-body density on non-storm cloud.
  - The result: 16-block, grid-aligned boxes through the full slab height. T247 fixed the A channel's version of this
    contamination; R's version remained.
- **Change (arm).** `PaFieldCoverageFix` (`fieldCoverageFix()`): a light tap is covered only if every filtered node is
  (`R > 1 − BODY_GAIN − 0.01`). Mixed taps use the existing per-node non-storm path, which excludes covered nodes.
  Session `t2b`: mediocris / congestus / stratocumulus / supercell, arms `cand`, `cand_fix`, `cand_fix_h1`.
- **Verdict.** Mechanism CONFIRMED (in-game isolation + code); fix pending validation (E11).

### E11 — 27 Oct 2026 — light-field coverage fix validated (`t2b`, `t2c`) → production default
- **`t2b`** (threshold version: covered only if filtered R > −0.66; mediocris / congestus / stratocumulus / supercell):
  - The wide boxes shrank to a thin residual line.
  - No visible storm change; GPU equal (supercell side 3.98 / 3.97 ms).
  - Metric caveat: several t2b views changed because terrain loaded differently between arms (mountain behind
    congestus, a camera inside a tree canopy for supercell below). `ab_metrics.py` masks are invalid there
    (`t2b_metrics_TERRAIN_CONTAMINATED.txt`); judged visually.
- **Exact rule (`t2c`).**
  - The rule: with no storm lobes, every covered node is exactly 1.0, so any filtered R < 0.999 means uncovered weight.
    With storms, the −0.66 threshold is kept (a residual line is possible only where a puff touches a storm).
  - Arms `cand`, `cand_fix`, `mono42` (field light off).
  - `cand_fix` = `mono42`: no boxes, no stripes on mediocris and congestus, side and close (`t2/t2c_fix_exact.png`).
- **Verdict.** **KEEP; production default ON** (`PRODUCTION_FIELD_COVERAGE_FIX = 1`, JSON default 1). It is a
  correctness fix (it removes fabricated storm density from non-storm light), not a look change.
- **Follow-up.** Bake as a lean-FINAL constant together with the lighting switches when those are decided (T232 rule).

### E12 — 27 Oct 2026 — Codex T5: why the nimbostratus underside got pale (`t5`); thickness arm (`t6`)
- **Question.** Which candidate term lifts the nimbostratus underside (user: old darkness was right)?
- **In-game validation.**
  - `runClient-t5.out.log`; nimbostratus and mediocris; below / side; t6000 / t1500.
  - Arms, one switch at a time: `legacy`, `cm` (colour pipeline), `cm_at` (+ ambient transmission), `cm_mx`
    (+ octave energy), `cand` (all).
  - Fixed interior box x 600–1000, y 300–480 (Codex's ROI). `validation/stabilization/t5/metrics.txt`.
- **Result (noon / low sun luma).**

  | Arm | Underside luma |
  |---|---|
  | legacy | 81 / 55 |
  | cm | 131 / 97 |
  | cm_at | 132 / 96 |
  | cm_mx | 158 / 114 |
  | cand | 158 / 113 |

  - The **display encode** carries most of the lift (+45..+51), the octave energy +17..+26.
  - Ambient transmission does **nothing** here: the production deck is ~56 blocks thick, so its light-path od is ~5
    and the two-stream factor stays ~0.65.
  - The legacy darkness came from the old darkening curve acting on a weak underside signal.
- **Cause.** Geometric compression. A 2–4 km rain deck is drawn ~56 blocks thick at a per-block extinction tuned for
  the look of small clouds, so its light path has a few percent of the real optical depth. A correct display transform
  shows the consequence that the old curve hid.
- **Change (arm).** `PaOpticalThicknessGain` (`opticalThicknessGain()`):
  - in `evaluateLightingComponents`, `od *= 1 + gain · smoothstep(0.30, 0.90, materialDarkness)`;
  - dark, thick families transport light like the cloud they represent;
  - cumulus (0.2) and cirrus are untouched; lit skins (od ~ 0) stay lit.
  - Session `t6`: nimbostratus / supercell / mediocris; arms `legacy`, `cand`, `cand_g4`, `cand_g10`.
- **Verdict.** Attribution CONFIRMED; the arm is pending (E13).

### E13 — 27 Oct 2026 — thickness gain result (`t6`); storm material metadata lost (new bug); arm `t7`
- **`t6` result.** `validation/stabilization/t6/`.
  - Nimbostratus underside (fixed ROI, noon / low sun): legacy 77 / 54, cand 152 / 108, **gain 4 → 67 / 47**,
    gain 10 → 44 / 34.
  - With gain 4 the deck keeps natural variation (lighter where thinner) and a white top from the side.
  - Mediocris: no cloud pixel changed (the term only touches dark families). **Gain 4 = candidate value.**
  - **Supercell: gain had no effect at all, and its lower block stayed pale.**
- **Why the supercell ignored it (code).**
  - `CloudWeatherMapRenderer` skips every member of a descriptor-owned storm group from the weather and morphology maps
    ("a complete descriptor group owns its body and material inputs").
  - But the march still reads `profileId`, `materialDarkness` (morphology.b) and the storm weight from those maps.
    On every production descriptor storm they are therefore EMPTY: profile 0, darkness 0, storm weight ~0. This matches
    T244's measured `localStorm ≤ 0.15` on storm bodies.
  - Cumulonimbus darkness, underside shade and storm darkening never applied to production storms.
  - The same empty column puts the storm's base at the slab floor, so the descriptor body below it is rain-classified
    (Codex CR-01): the pale "bolted-on" lower block.
- **Change (arm).** `PaStormMaterialFix` (`stormMaterialFix()`).
  - A sample the storm walk owns (`paShadeUnionDistance < 1e8`, already carried in FINAL, no new register cost) with no
    map category gets profile 4 and darkness 0.78.
  - Its body is rain-classified only where the rain field has precipitation.
  - Session `t7`: supercell / calvus; side / below / anvil; arms `legacy`, `cand`, `cand_sm`, `cand_sm_g4`.

### E14 — 27 Oct 2026 — storm material fix removes the "bolted" lower block (`t7`); final review gallery (`s10`)
- **In-game validation.**
  - `runClient-t7.out.log`; supercell and calvus; side / below / anvil; t6000 / t1500.
  - Arms `legacy`, `cand`, `cand_sm` (+ storm material fix), `cand_sm_g4` (+ thickness gain 4).
  - Sheets: `validation/stabilization/t7/sheets/`, `t7_sc.png`.
- **Result.**
  - With `cand_sm` the flat, pale, featureless lower block is GONE on both storm types and in every view. The lower
    body is light-marched like the rest of the storm, shows 3D billows with lit bulges and dark recesses, and joins the
    tower with no seam.
  - **Root cause of "two clouds bolted together":** the descriptor storm's metadata missing from the maps (E13) put its
    lower body on the flat rain shading below the slab floor. Not geometry.
  - `cand_sm_g4` makes the storm base much darker (closer to the reference photo's dark base; possibly too dark at low
    sun).
  - Cost: +0.1–0.23 ms at side (the lower body now runs the light march instead of the cheap rain path; calvus
    3.70 → 3.84, supercell 3.90 → 4.12).
- **Verdict.** Storm material fix: KEEP as a candidate (look change; needs the user's acceptance). Gain level: user
  choice between 2 and 4 → final gallery `s10` (all 9 families; `legacy` = current production, which already has the
  E11 field fix; `full_g2` / `full_g4` = candidate lighting + storm material fix + gain 2 / 4).

### E15 — 27 Oct 2026 — final review gallery (`s10`), awaiting the user's verdict
- **What.** All 9 families × side / below / (anvil | close) × t6000 / t1500. 162 frames, session `runClient-s10.out.log`.
  - `legacy` = current production (includes the E11 field fix).
  - `full_g2` / `full_g4` = colour pipeline + octave energy + ambient transmission + storm material fix + optical
    thickness gain 2 / 4.
  - Sheets: `validation/stabilization/s10-review-gallery/index.html`; `metrics.txt`.
- **Observed.**
  - Nimbostratus: dark underside back (both gains) with a white top.
  - Storms: the lower body is now a lit / shadowed 3D mass continuous with the tower, no seam.
  - Remaining on storms:
    - (a) small bright speckles inside the dark lower body (the light resolution cannot resolve the small billows,
      same limit as S9 / E9);
    - (b) the lower body's outline is still a box-like chunk with a flat bottom: the storm skeleton (Codex CR-03), a
      geometry item.
- **Decisions for the user.**
  - (1) Adopt the full candidate as production?
  - (2) Gain 2 or 4?
  - (3) Next target: the storm skeleton shape (CR-03), or the light resolution that keeps billows invisible (E9),
    which is what S9 needs to read as cauliflower.

### E16 — 27 Oct 2026 — user verdict on s10: `full_g2` adopted as production
- **Verdict (user).** "full g2 looks better"; next: (a) storm shape structure, then (b) light resolution.
- **Production defaults** (`VolumetricCloudDebugConfig.PRODUCTION_*` and the shader JSON defaults):

  | Switch | Value |
  |---|---|
  | colour pipeline | 1 |
  | octave normalise | 0 |
  | ambient transmission | 1 |
  | optical-thickness gain | 2.0 |
  | storm material fix | 1 |
  | field coverage fix | 1 (since E11) |

- **Capture harness.** `ProductionCloudCapture.arm(...)` now explicitly sets the storm fix 0 and gain 0, so `legacy` /
  lighting-only arms keep their pre-E16 meaning.
- **Not done yet.** Baking these as lean-FINAL constants (the T232 live-uniform cost) and a build-vs-build re-time.
  Planned after the shape work, so one timing pass covers both.

### E17 — 27 Oct 2026 — S11 seeded storm structure (step a), built, gallery `s11` running
- **Root (Codex CR-03, confirmed in source).** `stormBodyGroupField` turrets are one fixed lattice: 5 around, rows
  0.8 Rt apart, radius 0.42 Rt, and no seed / type input. `StormBodySkeleton` keeps no per-storm identity. So every
  storm is the same object; only its proportions differ.
- **Arm `PaStormStructure` = 1.** Shader helpers `paStruct*` before `stormBodyGroupField`:
  - **Seed.** Skeleton B2.z = `SEED` (30), the largest BASE member `seed01`, so it does not depend on descriptor
    order.
  - **Turrets.**
    - Per row, 4–7 turrets.
    - Per turret: radius 0.29–0.57 Rt, push 0.88–1.10 of the row radius, height ±0.2 of the spacing, angle ±0.15
      of a step.
    - 12 % are missing, which leaves pockets.
    - The nearest 2 of 2 rows × 4 angles are smooth-unioned.
  - **Bumps on bumps.** Each of the two nearest turrets carries 3 child billows (0.36–0.54 r) on its outward /
    upper face.
  - **Base rim.** 10 billows (0.5–0.95 of the base height) around the superellipse rim, cut by the flat floor.
    Smooth-unioned into the base (k = 0.3 h).
  - **Flanking line.**
    - 2–4 towers (capsule + 2 heads) on one upwind flank (180° ± 30–70°), stepping down (×0.8 radius, ×0.7 height).
    - They stay inside the unscaled ownership reach, so they are already covered by the descriptor tiles, the reach
      bound and the AABB.
    - Smooth-unioned into the lower mass (k = 0.35 Rt).
  - Every part is an exact sphere or capsule, so the distance property is kept.
- **CPU side.**
  - `COLUMN_MAX_SCALE` 1.95 → 2.35, and the AABB includes ± the ownership reach. Both apply always, so the legacy
    arm's AABB is slightly larger (cost to verify).
  - The `StormBodyEvaluator` CPU mirror is still the legacy body (camera density / whiteout / precipitation).
    Mirror it if S11 is accepted.
- **Not addressed.** Type identity: calvus and supercell use the same recipe. Real calvus has no developed anvil.
  The generator also ignores `plan.orientationRadians()` (Codex lead), so the wind axis comes from fixed +X
  offsets.
- **Gallery.** Set `s11`: 2 × calvus + 3 × supercell instances (`type#n`) × side / below / anvil at t6000, arms
  `prod` / `struct`. Session `runClient-s11.out.log`.

### E18 — 27 Oct 2026 — S11 iterations v1–v4 (sessions `runClient-s11*`)
- **Measured dimensions** (S11-TEMP skeleton log, 7 storms):
  - Rt 263–273, floor 136, base height 207, base reach ~310–400 (1.3 Rt), ownership reach 609–644, deck 747–898.
  - **Calvus and supercell skeletons are the same object within ±4 %.** This confirms CR-03 in game: the only
    per-storm difference is now the S11 seed.
- **v1** (seeded turrets + child spheres + rim billows 0.5–0.95 h + flank 0.62 Rt from 2 Rt): **rejected.**
  - The rim billows made the lower body one ball.
  - The child spheres read as more balls.
  - The flank never appeared: 2 Rt + r > own = 2.3 Rt.
- **v2** (billow displacement replaces the children; flank reach 1.6 own; reach bound / AABB extended):
  - The tower outline is broken (good).
  - The flank (0.62 Rt, smooth-joined k = 0.35 Rt) read as one smeared ridge.
  - The side camera (+Z) sees +Z flanks from ~200 blocks, so a `far` view was added.
- **Root of the remaining smoothness.** The existing T212 macro displacement only moves the envelope inside the
  24–48-block edge band, while the billows needed on a 268-block updraft are 0.1–0.3 Rt. The v2 displacement acts
  on the body SDF itself:
  - 3 Worley-bump octaves, cells 0.45 / 0.225 / 0.11 Rt, |h| ≤ 0.315 Rt;
  - the clearance subtracts 0.315 Rt, so the safe advance stays a lower bound;
  - the field slope gets +2.
- **v3.** Bit mask for attribution (1 turrets, 2 rim, 4 flank, 8 billows); integer pcg hash; separate 0.30–0.44 Rt
  flank towers. Cost, `s11c`, side view, 2 supercells:

  | Arm | GPU ms |
  |---|---|
  | prod | 4.02 / 3.28 |
  | turrets | +1.4 / +1.05 |
  | rim | +0.37 / +0.32 |
  | flank | +3.8 / +3.15 |
  | billows | +1.15 / +1.0 |
  | all | +8.3 / +7.1 |

  The flank cost is mostly real extra cloud close to the camera.
- **v4.**
  - One hash per turret, 3 angle candidates, a capsule bound on the flank, the billow zone narrowed to one band.
    **Cost unchanged** (calvus#1 side 3.05 → 7.84), so the cost is marched volume (finer stepping in the billow
    shell, extra cloud), not ALU.
  - Far views: +0.6–1.5 ms. Side / anvil / below: +1.1–5.7 ms.
  - Visual: **production storms are identical clones; S11 storms all differ** (flank direction / count, separate
    congestus cells beside the Cb).
  - Defects: flank towers shredded into specks by the full billow reach; streaks along the flank from the eroded
    shelf capsule.
- **v4b.** Billow weight 0.4 where the flank dominates (smin weight); shelf disabled. Gallery rerunning.
- **v4b result** (gallery `validation/stabilization/s11-storm-structure-gallery/`; index.html + `far_overview_prod_vs_struct.png`):
  - The flank specks and streaks are gone. Separate congestus towers stand beside the Cb (supercell#1 far,
    supercell#3 side). Each storm is different; production storms remain clones.
  - Cost, struct vs prod, same build and poses:

    | View | Extra GPU ms |
    |---|---|
    | far | +0.47–1.36 |
    | side | +1.75–5.3 |
    | below | +2.4–2.8 |
    | anvil | +1.1–3.2 |

    The highest numbers come where a flank tower sits near the camera.
  - The `prod` arm in this build pays the larger AABB (unconditional); a build-vs-build check is still to do.
- **Awaiting the user's verdict** per part: turrets / rim / flank / billows, and whether the cost is acceptable.
  If accepted:
  - mirror the body in `StormBodyEvaluator`;
  - make the AABB growth conditional;
  - bake the constants;
  - remove the S11-TEMP skeleton log;
  - re-time build vs build.

### E19 — 27 Oct 2026 — user: S11 accepted visually; investigate cost (scalability) and the top/bottom lighting split
- **Verdict (user).** S11 accepted ("its nice"). Other features paused. The two items to investigate:
  - why one storm costs +5 ms (the renderer must scale to e.g. 300 cumulus);
  - the "separated" storm: dark bottom, lighter top.
- **Harness.**
  - Perf sets `p*` run the T123 workload readback after each shot (56 diagnostic frames on the monolith, same
    arm and pose) and log `PROD_CAPTURE_WORKLOAD`.
  - `PROD_CAPTURE_SHOT` now also logs `fieldMsMedian` (storm / rain field build + gate) and `rainMsMedian` (rain
    pass + lattice). Before this, only the march timer was reported: the field build (every frame) and rain passes
    were missing from every gallery cost.
  - View `away` (side camera looking away from the storm) measures the floor.
- **p1** (2 supercells × side / far × S11 parts).
  - GPU ms ≈ 2.0 + 0.26 / M primary steps + 1.57 / M density calls + 0.78 / M light evaluations (R² 0.95).
  - The density call is the expensive unit. S11 multiplied side-view density calls ×5.6 (0.5 → 2.85 M).
- **p2** (full pass timings).

  | View | march + field + rain = total (prod) |
  |---|---|
  | `away`, no cloud on screen | 1.35–1.5 + 1.0–1.4 + 0.4 = **2.8–3.2 ms floor** |
  | side | 5.0–5.7 ms |
  | below | 6.6–7.3 ms |

  - The floor: ~35 coarse empty-space steps per pixel. The coarse stride is a fixed fraction of the ray's slab span,
    whatever the coverage.
  - The storm field atlas (256 × 256 × 32) is rebuilt every frame (T199e: reuse was never implemented).
  - The counters come from the monolith, which still carries the rain segment test. In FINAL that work lives in the
    separate rain pass (T206), so the monolith's 1.1 M rain-support storm walks in an empty view are not FINAL
    cost.
  - The field soundness audit (`stormFieldProbeFalseEmpty`) is 0 in every shot: the +2 billow slope is sound.
- **S11 v5 (cost fix, no visual change seen).**
  - Clearance = max(d_undisplaced − 0.315 Rt − soft, (d_displaced − soft) / (1 + 2)).
  - Before, the flat −0.315 Rt turned an 84-block shell around every storm into 2.5-block fine steps.

  | View | v4 total | v5 total | prod |
  |---|---|---|---|
  | supercell side | 11.56 | **8.54** | 5.71 |
  | calvus side | 10.73 | **7.26** | 5.09 |
  | far | 5.0–5.6 | 4.7–4.9 | 3.8 |
  | below | ~10.6 | unchanged (~10.7) | 6.7–7.2 |

  Side-view density calls 2.4 → 0.8 M.

### E20 — 27 Oct 2026 — the storm top/bottom "separation" is a lighting-height step (FIXED, production)
- **Evidence** (`l1`, monolith, L-TEMP diagnostic modes 40 / 41, temporary):
  - Mode 40 = `h01ForAmbient`. For storm-owned samples it is **1 above and 0 below one straight horizontal line**
    at the slab base. This holds across the whole storm and its flank tower, at noon and at low sun.
  - The march computes h01 = (y − baseY) / (topY − baseY) from the weather map, which is empty over
    descriptor-owned storms (E13 / Codex CR-02). Every height term of the lighting flips at that altitude:
    - `undersideShade` (strong at material darkness 0.78);
    - the ambient bottom / top colour;
    - the T244 low-cloud neutral.
- **Fix: `PaStormMaterialFix` = 2.**
  - Storm-owned samples take `paStormLightHeight01`: floor 0 → deck top 1, from the skeleton of the nearest
    group's AABB.
  - It costs a few texel fetches per shaded storm sample. No value leaves cloudDensity (T232 rule).
- **Result** (`l2`, lean FINAL, S11 on; validation folder `run/screenshots/prodcapture/l2`):
  - Mode 40 is a smooth vertical gradient.
  - The image loses the horizontal seam (clearest from below) and darkens continuously toward the base.
  - Cost is within noise (±0.2 ms).
  - **Production default 2.**
- **Remaining.** The bright speckle on the dark lower body is the light-resolution item (b), not this bug.

### E21 — 27 Oct 2026 — scalability: what the renderer pays per frame, and why it grows with clouds
- **Defaults.** S11 (`PaStormStructure` 15, with the v5 clearance) and `PaStormMaterialFix` 2 are production
  defaults now (the user accepted S11). The CPU mirror `StormBodyEvaluator` (camera density / whiteout /
  precipitation) is still the legacy body.
- **p3: 1 / 16 / 49 cumulus mediocris on a 650-block grid** (lean FINAL, march / field / rain):

  | Grid | Side total | Field-low total | Field-high total |
  |---|---|---|---|
  | 1 | 1.78 | 0.78 | 0.70 |
  | 16 | 2.75 | 2.02 | 1.82 |
  | 49 | 2.76 | 2.40 | 2.13 |

  - In the field views the march goes 0.17–0.24 → 1.2–1.8 ms with **the same step count and ~0 density calls**:
    the per-step cost grows with the scene's cloud count.
  - Cause: `directPuffSegmentMayIntersect` runs on every coarse step and **loops over every puff lobe** (AABB slab
    test on uniform arrays) instead of the 16-block candidate tiles the density path uses.
- **Hard caps found:**
  - `MAX_PUFF_LOBES` 32: about 4 detailed cumulus in the whole scene. The rest fall back to the weather-map field
    path, which is a different look (Codex CR-07).
  - `CloudWeatherMapRenderer.MAX_CELLS` 96: 66 spawned cumulus already filled it (rendered cells 96).
  - 8 storm groups / 64 storm lobes.
  - So "300 cumulus" cannot render as authored today: an architecture limit, not a tuning issue.
- **The floor with no cloud on screen** (p2 `away`, prod): march 1.4 + field build 1.0–1.4 + rain 0.4 ≈ 3 ms.
  - March: ~35 empty steps per pixel (the coarse stride is a fixed fraction of the slab span).
  - Field: the 256 × 256 × 32 storm field rebuilt every frame (T199e, no reuse).
  - Rain: passes run in clear weather.
- **P1, occupancy reject (built, measuring as `p4`).**
  - 32 × 32 occupancy bits (128-block tiles over the weather extent, dilated one tile on the CPU) in
    `PuffLobeSpatialIndex`, uploaded as `PuffOccupancy[32]`.
  - The per-step puff test returns false when a ≤ 1-tile segment starts in a clear tile.
  - It is conservative by construction, so the image must be identical.
- **p4 result.**
  - P1 is image-identical on the clouds; the only differing pixels are terrain chunks still loading.
  - Savings: field views 0.25 → 0.09 ms (1 cloud) and 1.40 → 1.19 ms (49 clouds); side 1.80 → 1.55 ms.
  - **Production default 1.**
  - The rest of the per-step growth with cloud count is unattributed: the counters come from the monolith, which
    is 5–6× slower than lean FINAL.
- **Summary and plan:** `stabilization/PERFORMANCE_FINDINGS.md`.
- **Temporary instrumentation still in the tree (remove when done):**
  - `L-TEMP` lighting modes 40 / 41 (shader `sampleLighting`, config clamp 41, capture `diag` / `diagFix` arms);
  - `S11-TEMP` skeleton log (`StormBodySkeleton.describeLast`, `PROD_CAPTURE_SKELETON`).

### E22 — 27 Oct 2026 — user target: a cumulus field (~100 clouds in 1–2 km, far clouds may simplify); plan items 1–3 + capacity
- **Attribution** (lean FINAL, `PaPerfArm` PERF-TEMP arms, `p5`):
  - In a 49-cumulus field the march costs 1.5 ms vs 0.1 ms for one cloud:
    - puff fine-step promotion ~0.33 ms;
    - direct-puff density ~0.3 ms;
    - cumulus density on covered empty steps only ~0.15 ms;
    - tight cumulus height bounds: no gain.
  - The rest grows with visible cloud.
  - The real limits were capacity: **`MAX_CELLS` 96 = one lobe per cell, about 12 cumulus rendered at all**, and
    32 detailed puff lobes.
- **P2, field atlas in bands (production, `PRODUCTION_FIELD_SLICES` 4).**
  - One tile row of slices per frame (scissor). Full rebuild on an origin snap, a slab or lobe-count change, a
    program change, the oracle, or every 16 frames.
  - p6: storm field 1.37 → 0.49 ms; cumulus 0.62 → 0.19 ms.
  - Field audit false-empty 0. Images differ only in loading terrain.
- **P3, empty-space jump (production, `PRODUCTION_EMPTY_SKIP` 1).**
  - The T253 coverage pass writes an occupancy flag in `.g` (weather coverage within one texel), mipmapped after
    the pass. The march walks level-3 (64-block) tiles along the ray and jumps the empty run. Puff occupancy bits
    also stop it.
  - The run is capped at the ray's storm-AABB span, computed once per ray. Storms are not in the weather map:
    without the cap the walk ran to its limit every step (+2 ms near storms).
  - p7 (final): empty view 2.2 → 1.33 ms. Storm and cumulus views neutral (within the repeat control). Images
    match the repeat control.
- **C1, capacity (production, structural).**
  - The weather-map cells moved from uniform arrays (96) to `CellDataSampler` (5 RGBA32F texels per cell) plus
    `CellTileSampler` (32 × 32 tiles of 128 blocks, ≤ 64 cells each, binned on the CPU with the passes' own cull
    radius, ascending order kept).
  - The splat / morphology / cumulus-layer passes loop only their tile's list. `MAX_CELLS` 1024. The native
    cloudlet budget is the field profile × 12 (ULTRA 768).
  - **p8, 100 cumulus mediocris on a 300-block grid:** rendered cells 800 (was capped at 96). Dozens of clouds
    to the horizon. March 1.9–2.9 ms, field 0.25 ms, no tile seams seen.
  - Validation: `validation/stabilization/p8-cumulus-field/`.
- **Not done yet.**
  - C2: the detailed puff budget (32 lobes; near clouds beyond it use the weather-map path, which looks
    stretched).
  - C3: a far layer beyond the ±2 km weather extent.
  - Cloud variety inside a field: grid-spawned copies of one type in this test.
  - Baking constants; removing PERF-TEMP / L-TEMP / S11-TEMP.

### E23 — 27 Oct 2026 — C2 detailed puffs, constants baked; population is the remaining blocker
- **C2 (production).**
  - Puff descriptors moved from uniform arrays into rows 256–258 of the puff candidate map, which is now
    **RGBA32F** (256 × 295). It was RGBA16F: the 257² pair packing and the world positions are past half-float's
    exact range.
  - Rows 259+ hold per-128-block-tile lobe lists (count + 35, dilated one tile). The per-step segment test loops
    only the start tile's list; on overflow it falls back to the full loop.
  - `MAX_LOBES` / `MAX_PUFF_LOBES` 32 → 256, `PUFF_PACK_BASE` 257.
  - Budget by quality (`PuffLobeSpatialIndex.lobeBudget`): LOW 32, LOW_24 48, MEDIUM 96, HIGH 160, ULTRA 256.
  - p9 (100 cumulus): the march goes 1.9–2.9 → 3.4–5.5 ms for ~32 detailed clouds instead of 4. Mid-distance
    clouds read as puffy cumulus instead of flat smears (`validation/stabilization/p8-cumulus-field` vs p9 frames).
    A single cloud is unchanged.
- **Constants baked** (`build.gradle` `leanFinalConstants`):
  - colour pipeline 1, octave normalise 0, ambient transmission 1, shape hierarchy 0, field coverage fix 1,
    thickness gain 2, storm material 2, storm structure 15, puff occupancy 1, empty skip 1, perf arm 0;
  - `leanFinalEligible` sends any other value to the monolith (so an arm can never silently no-op on lean).
  - Live vs baked (p9, two sessions): neutral within noise (±0.2 ms). The storm renders correctly baked.
  - **Capture note:** an arm that sets a baked switch to a non-production value now runs the monolith (5–6×
    slower). Perf A/B of those switches has to be build-vs-build.
- **Population.** `NativeAtmosphereCloudService` spawns at most 2 clouds every 600 ticks, only within 900 blocks
  of a player, and existing clouds within ~720 blocks lower the chance. Normal play therefore shows a handful of
  clouds, whatever the renderer can hold. That is simulation-side, and the user asked not to touch the simulation
  without a real bug → user decision.
- **C3 (far layer beyond ±2 km)** is on hold: sync radius, march distance and weather extent are all 2 km, and the
  sim spawns within 900 blocks, so there is little to draw far away until population grows.
- **Temp instrumentation still in tree:**
  - PERF-TEMP `PaPerfArm` (baked 0 on lean);
  - L-TEMP lighting modes 40 / 41;
  - S11-TEMP skeleton log;
  - capture sets `p*` / `l*` / `s11*`.

### E24 — 27 Oct 2026 — user: spawning stays as is; focus on looks and system limits
- **Overflow robustness (production).** A 16-block candidate tile with more than 8 puff lobes keeps its 8 most
  central lobes (the loss stays local). Before, any overflow switched every cloud to the weather-map path at once,
  and with 256 lobes two overlapping 8-lobe cumulus were enough. `PuffLobeSpatialIndex.STRICT_OVERFLOW` restores
  the old rule.
- **Box check (`l4`).** A congestus lower "box" (a cylinder-shaped base lobe) is pixel-identical with the P1 / C2
  lists / P3 jump on vs off. It is the cumulus primitive shape (Codex CR-04), not a regression; congestus also shows
  stacked lobe shelves.
- **(b) light resolution (`l3`, `l5`, monolith).** For storm-covered samples every light tap reads the field
  (thresholds interpolated between 16-block nodes, live noise), so shadows cannot resolve the S11 billows. Non-storm
  first taps have been exact since T247, which is why a congestus does not change.
  - New switch `PaExactLightTaps` (taps i < N exact; baked 0 in FINAL).
  - Relative cost: t1 +8–10 %, t2 +15–18 %, all exact +44–55 %.
  - Look: t0 has flat dark patches with bright specks on the lower body. t1 / t2 give coherent lit billows (t2
    clearer); "all" is best but too costly.
  - Gallery: `validation/stabilization/l5-storm-light-taps/`. **Awaiting the user's choice.**

### E25 — 27 Oct 2026 — Storm Shadow Detail as a player setting; costs measured in game (lean FINAL)
- **User request.** Offer cheap and expensive storm shadow detail in PA's menu, and quote in-game costs (not the
  monolith).
- **Setting.**
  - `AtmoCommonConfig.STORM_SHADOW_DETAIL` (`stormShadowDetail`): FAST (0 exact light taps, default = previous
    look), BALANCED (1), HIGH (2), ULTRA (all).
  - Menu: a cycle button "Storm Shadow Detail: …" under Cloud Render Distance in `AtmoConfigScreen`.
  - `PaExactLightTaps` is a **live** uniform in FINAL now (removed from `leanFinalConstants` and from the
    eligibility check). `VolumetricCloudDebugConfig.exactLightTaps()` returns the player's setting unless a
    capture overrides it (-1 = none).
- **In-game cost** (`l6`, lean FINAL, same session; march ms, supercell / calvus; `fast2` repeat control within
  0.1 ms):

  | View | FAST | BALANCED | HIGH | ULTRA |
  |---|---|---|---|---|
  | supercell side (noon) | 8.9 | 10.7 (+1.8) | 12.8 (+3.9) | 20.2 (+11.3) |
  | supercell below | 8.6 | 10.6 (+1.9) | 12.5 (+3.9) | 20.8 (+12.2) |
  | supercell far | 3.8 | 4.5 (+0.6) | 5.6 (+1.8) | 8.6 (+4.8) |
  | calvus side | 9.0 | 11.2 (+2.2) | 13.5 (+4.5) | 22.5 (+13.5) |
  | calvus below | 7.3 | 9.0 (+1.7) | 10.9 (+3.6) | 18.5 (+11.2) |

  Only frames with storms in view pay; cumulus first taps were already exact (T247). The monolith percentages in
  E24 understated the in-game cost.
- **Gallery:** `validation/stabilization/l6-storm-shadow-detail/` (index.html, `supercell_side_levels.png`).

### E26 — 27 Oct 2026 — cumulus shape: renderer-side fixes do not remove the stacked look; the cause is the lobe layout
- **S12 lobe domain warp** (`PaPuffWarp`, live, default 0; bounds padded for 0.25 radii on the CPU and in the
  shader): ≤ +0.1 ms. It barely changes the look: drum sides lean slightly, rims and shelves stay.
- **S12b, S9 hierarchy under today's lighting** (monolith): h1 is cotton-like, h2 frayed. It adds fuzz, not
  structure.
- **S13 smooth max of the top-two lobes** (`PaPuffBlend`, live, default 0): no visible change at k 0.15–0.5. The
  shelves are not creases.
- **Box hunts (`l4`, `s15`).** The rare "box" (straight vertical edge, flat top) did not reproduce in 6 new
  congestus. It is not P1 / P3 / exact light / the cumulus-structure path (all identical where present). Open,
  seed-dependent.
- **s16.** Hybrid == direct-only (pixel-identical): the weather-map fallback is not involved.
- **Cause of the shelves.** The tiered lobe layout (4 BASE lobes in one plane, MIDDLE, CROWN), each lobe a round
  cap lit on top and self-shadowed below, so stacked tiers read as alternating bright rims / dark bands. This is
  the cumulus generator (`CloudMorphologyGenerators` puff plans, Codex CR-04), not the renderer.
- **The renderer can now take far more lobes per cloud** (256 detailed, 1024 weather cells), so a layout with more,
  varied, clustered lobes is affordable. It lives in the cloud generation code: **user decision.**
- **Harness notes.**
  - A minimized launch can capture at 854 × 480; launch the window normally.
  - A GPU / driver crash killed `s15` mid-run on monolith arms (no hs_err).

### E27 — 27 Oct 2026 — S14 clustered cumulus layout (user approved generator changes)
- **Code.** `CloudMorphologyGenerators.puffLayout` (static, **default 1 = tiered legacy**; 2 = clustered;
  `CloudGroupSpawner.setPuffLayout` for tools; capture entries `type#v1` / `type#v2`).
  - Clustered plan: humilis 8–10, mediocris 12–15, congestus 14–17 lobes.
  - BASE: an irregular elongated cluster on one condensation level, each lobe overlapping an earlier one.
  - MIDDLE / CROWN: grow from parents near an off-centre core, roots inside the parent; sizes shrink upward.
  - Tier meanings and the persisted layout version are unchanged.
- **Iterations** (lean FINAL, `s17` ×3, `s17d`):
  - **v2 first pass:** too compact (single soft balls).
  - **Spacing pass:** base lobes spaced 0.50–0.70 of the summed radii, more upper offset. Distinct billows, but
    thin spires.
  - **Spire fix:** crowns ≥ 0.22 R and 1.15–1.55 radii tall. Humilis and mediocris v2 read as puffy cumulus with
    no stacked rims.
  - **Congestus pass:** broader upper lobes and a lower height preference. A towering column on a base mass,
    still narrow on top; one instance grew a long thin base tail.
  - Cost: v2 is within session noise of v1 (single clouds 1.2–3.2 ms).
- **Gallery:** `validation/stabilization/s14-cumulus-layout/`. **Awaiting the user's verdict.** Production stays
  on layout 1 until accepted.

### E28 — 27 Oct 2026 — clustered cumulus adopted for humilis / mediocris; congestus tower layout
- **User:** "approved continue".
- **`puffLayout` 0 = production per type:** humilis and mediocris clustered (S14); congestus tiered until its new
  layout is accepted. 1 / 2 force tiered / clustered (tools, capture `#v1` / `#v2`). Applies to newly spawned
  clouds; saved clouds keep their geometry.
- **Congestus: `createClusteredCongestusLobes`.**
  - A compact BASE cluster (5 lobes, aspect ≤ 1.3, no tails).
  - Three tower levels (3 / 2 / 2 MIDDLE lobes at 0.42 / 0.36 / 0.30 R) around a core that leans
    0.04–0.10 R per level, each level rooted at 40–55 % of the level below.
  - CROWN billows (0.24–0.32 R) fill the remaining count (14–17 lobes in all).
  - Gallery run `s17d`.

### E29 — 28 Oct 2026 — congestus cuts: the 8-candidate tile cap is NOT the cause (S20 rejected)
- **Hypothesis.** Clustered congestus (14–17 lobes) overflows the 8-candidate 16-block puff tile (logs:
  maxPerTile 12–17); dropping the least central lobes would cut the cloud along a tile edge.
- **S20 test.** 16 candidates per tile (second packed grid below the list rows), then `s21`: the same clouds in
  one session with the shader reading 16 (prod) vs 8 (`cap8`, temporary uniform).
- **Result (s21).** Per close view, at most 425 pixels differ by more than 12/255 (v1 48, v2 425, v2b 361,
  v2c 86). The vertical cut and the horizontal rings appear identically in both arms. Reading 16 costs
  +0.4–0.9 ms (same-build, indicative only).
- **Verdict.** Rejected and reverted (8 candidates, no temporary uniform). The cuts come from something every arm
  shares; next suspect: the puff height01 is taken from the single strongest lobe, so it jumps where two lobes
  trade dominance (lower lobe top ~0.8 beside upper lobe base ~0.05), and material + lighting follow it (S22).
- **Note.** `s20` vs `s19` was not an A/B: s19 predates the last congestus generator change.
- **S22 (rejected, reverted).** Puff height01 from an envelope-weighted mean of lobe heights (or the mean lobe
  interval) instead of the strongest lobe: `s22` on congestus v1/v2/v2b, mediocris and humilis v2 is visually
  unchanged in every view, cost ±0.05 ms. The height jump is not the cut either.
- **Reading of s21/s22.** Hybrid == direct (s16), so the bright soft masses beside the grey tower are lobes too.
  The sun is near-vertical at t6000 (lightDir y 0.99): every tower lobe that reaches past the lobe below shades
  a dark ring onto it, and lobes outside the tower's shadow stay bright and meet the shaded tower along a hard
  line. The rings and cuts are self-shadowed overhang geometry from the congestus tower layout.
- **s23 (generator, congestus only; production congestus is still tiered).** Each tower lobe sits inside its
  nearest supporting lobe (radius ≤ 0.85 × support, edge ≤ 0.92 × support radius) and is rooted at 30–55 % of
  that support's height; crowns obey the same rule against their parent. Captured at noon and low sun.
- **s23 result.** The no-overhang tower does not remove the rings: v2b still shows stacked rings, the tiered v1
  shows them most, and they sit in the same places under a low sun (t1500). Kept in the (non-production)
  congestus tower layout, since it removes overhangs, but it is not the fix.
- **s24 decomposition (same instances; prod, monolith m0, flat colour m1, local density m3, light path m2).**
  - m1 (silhouette / alpha): one smooth mass, no rings, no cuts.
  - m3 (local density): near uniform, only faint lobe outlines.
  - m2 (light-path transmission only): the rings and the vertical cuts are all there, crisp.
  - **Cause:** hard-edged self-shadows. Each upper lobe has a defined surface (T242: full density within 0.18
    of lobe depth), so the light march toward the sun crosses an abrupt optical-depth step at its edge, and the
    lobe below is shaded with a sharp-edged shadow (the ring). The vertical cut is the same edge seen side-on.
    Real cumulus does not show this because multiple scattering blurs self-shadows; the 3 scatter octaves here
    only lower the shadow's depth, not its edge.
  - Not geometry, height01, tile capacity, the weather-map fallback, P1/P3 or exact light taps.
- **Options (not started; lighting is shared with storms, needs a gallery and the user's call).**
  1. Soft light-march density for puffs: read a wider-isovalue (softened) puff envelope in the light march only,
     so lobe shadows get a penumbra (cost: none expected, the light taps already evaluate puff shape).
  2. Penumbra by jittering the light-march direction within a cone per pixel (temporal accumulation smooths it).
  3. Raise the octave attenuation for puffs only (brighter, flatter shadows; does not soften the edge).
- Gallery: `validation/stabilization/s24-congestus-rings/`.

### E30 — 28 Oct 2026 — why cumulus self-shadows are crisp and storms' are not (user: find the cause, no blur fix)
- **Code difference.** The light march (`lightMarchOpticalDepth`) reads every storm tap from the 16-block shared
  field. For a non-storm sample, T247 made the NEAREST tap (14 blocks up-sun) an exact `cloudDensity` walk (it
  fixed plates on stratus decks). For cumulus that tap point-samples the exact lobe union: solid, uniform
  full density with a defined surface (T242), so the tap is either fully inside the upper lobe or outside it.
  The lower lobe gets a shadow whose edge is the upper lobe's exact surface, shifted 14 blocks along the sun.
- **s25 A/B (lean FINAL, same instances, noon; `PaLightTap0Field`, S25-TEMP):**
  - `tap0field` (tap 0 on the field, as storms): the crisp, speckled ring rims are gone and the bands turn into
    soft shading; 0.3–0.4 ms CHEAPER (v1 2.75 -> 2.36, v2b 3.15 -> 2.77, mediocris 2.85 -> 2.54).
  - `allexact` (every tap exact): rings sharper and noisier, +0.5–0.9 ms.
  - So tap 0 draws the crisp rims. Not touched by it: soft stacked banding (the field taps still see solid
    lobes) and the v2b vertical cut (present in all three arms, i.e. real geometry shadow).
- **Second layer.** Cumulus density is uniform at the maximum inside every lobe (s24 m3 is flat white), so any
  light path through another lobe is opaque within ~9 blocks (extinction 0.115): shading is "lit or behind a
  lobe". Storm bodies keep density structure (T221/T227) and are one continuous body (T240), so they have no
  internal lobe surfaces to cast edges.
- Gallery: `validation/stabilization/s24-congestus-rings/s25_*.png`. Awaiting the user.

### E31 — 28 Oct 2026 — decision: cumulus become one continuous cloud body; checkpoint taken
- **User:** clouds should act as one cloud, not separate blobs; best for the future. Blobs stay as the generator's
  skeleton (where tower, turrets, base go); rendering and lighting use one continuous body, as storms do (T240).
- **Checkpoint before the rebuild:** `checkpoints/PRE_ONE_BODY_CHECKPOINT.md` (base commit, `git diff` patch,
  untracked sources, production state, open issues, temporary instrumentation, how to re-run the comparisons).

### E32 — 28 Oct 2026 — S26 one-body cumulus: first working body (experimental, live switch, not production)
- **Model (`paPuffBodyShape`, `PaPuffBody` 1, S26-TEMP uniforms).** Lobes are the skeleton only. Each lobe adds a
  smooth bump `(1 - q^2)^3` (q in lobe radii, support 1.15, flat BASE floor) and the cloud is ONE implicit surface
  where the sum crosses 0.09 (metaballs). Density rises above the surface (shell 0.45 -> 1). Height for material /
  ambient = field-weighted lobe interval (no dominant-lobe switch). Light: every tap of a body sample reads the
  shared field, as storms do.
- **Iterations (`s26`, same 5 instances per run, lean FINAL):**
  - a: log-sum-exp distance union -> 1.5-2x too big (old surface sat at iso 0.08-0.34 inside the lobes) and cut
    at tile edges (lobe influence reached past its registered tiles). Rejected.
  - b: metaball bumps with compact support inside the footprint -> right size, rings gone; cuts and stair-step
    boxes remained where a 16-block tile overflowed its 8 candidates.
  - c: 16 candidates per tile restored (S20 code) -> overflow 0 except 17-lobe tiered congestus.
  - d: multiplicative noise relief -> erodes holes / wisps (relief 2), not cauliflower. Replaced by one-sided
    bulges near the surface. Also fixed: a horizontal cut on tall lobes (segment AABB vertical padding < bump
    reach; now (support - 1) x span in body mode).
  - e/f: turret lattice (5 seeded bumps of 0.42 R on each lobe's upper half, inside the footprint) + fringe
    erosion; turret growth capped so support x growth < 1.25 footprint.
  - g: a FULL 16-block tile now reads the 128-block lobe list (or every lobe past its cap) - nothing dropped.
- **Result (s26g).** One continuous mass per cloud: no rings, no vertical cuts, no stair steps, flat base. Cost vs
  production in the same session: -0.25 to +0.13 ms per cloud.
- **Open.** The surface is too smooth ("balloon"): the world-scale relief noise is too big to read as cauliflower,
  turrets add a little. Interior shading is flat. Next: surface detail at cloud-relative scale.
- Gallery: `validation/stabilization/s26-one-body-cumulus/` (s26a-f iterations, s26g close / side).

### E33 — 28 Oct 2026 — why every cloud's surface looks flat (user: find the root, all families)
- **s27, render resolution.** Production 0.25 vs 0.5 vs 1.0 (and 1.0 with every light tap exact) look the same;
  full resolution costs 4-8x. The density alone (m3, full res) shows clear forms. Not resolution, not tap
  exactness: the shape is there and the lighting does not show it.
- **s28, lighting terms (S27-TEMP `PaLightSplit`).** Sun-only ~= production (the sky ambient is not what
  flattens); one octave / no powder change brightness, not form. Noon vs morning (lightDir y 0.99 vs
  (0.81, 0.57)): mean difference ~1 grey level - the surface barely responds to the sun direction.
- **Root cause.** The light march's first tap is 14 blocks up-sun and stands for the whole 0-14 block segment
  (then 34, 62, ... on the 16-block field). Nothing within 14 blocks of the shaded point is ever sampled, and
  that is exactly where a bulge's own lit side / shaded side is decided. Every relief smaller than ~15 blocks
  (cauliflower heads, turrets, T230 storm relief) cannot shade itself, on every family - the light march is
  shared.
- **s29 proof (S28-TEMP `PaNearLight`).** Three exact samples at 2 / 5 / 9 blocks (tap 0 shortened to 12-14):
  cauliflower heads and turrets appear with light and shadow, on the lobe union and the S26 body, noon and
  morning. Cost +1.5-2.1 ms per cloud (3 exact density walks per lit sample). s30 prices 3 / 2 / 1 samples.
- Gallery: `validation/stabilization/s27-flat-surfaces/`.
- **s30 cost vs form (S26 body, lean FINAL, same session, close / side, noon / morning):**
  - near3 (2 / 5 / 9 blocks): +1.4-2.0 ms per cloud. near2 (3 / 8.5): +0.9-1.4 ms. near1 (6, one sample for
    0-12 blocks): +0.5-0.7 ms.
  - near1 already shows most of the form (lobe heads lit on the sun side, shaded beneath, turrets readable);
    near2 / near3 add finer gradation.
  - Seen in every body arm: faint vertical light stripes on v1's big lobe - suspect the 16-block field taps
    (next to check).
- **Fix direction:** near-field light belongs in the shared light march for every family (one exact sample over
  the first 12 blocks as the default, more as a quality setting), not a cumulus-only patch.

### E34 — 28 Oct 2026 — near-field light shipped as "Cloud Surface Detail"; the general flatness has a second cause
- **User:** "fix it and then continue investigating" (near light is a good find, but not why clouds look flat
  in general).
- **Code (production, uncommitted).** `AtmoCommonConfig.CloudSurfaceDetail` OFF / LOW (default) / MEDIUM / HIGH
  = 0 / 1 / 2 / 3 exact light samples in the first 12 blocks toward the sun (6 | 3, 8.5 | 2, 5, 9), tap 0
  shortened to 12-14. Menu button "Cloud Surface Detail" under Storm Shadow Detail. Uniform `PaNearLight` (live
  in lean FINAL), `VolumetricCloudDebugConfig.nearLight()` follows the menu unless a capture arm overrides it.
  Storm-covered samples (one shared-field fetch, `paStormFieldCoveredAt`) skip it at LOW / MEDIUM, HIGH includes
  them.
- **Costs in game (lean FINAL, s31 non-storm per cloud, s32 storms, before the storm gate):**
  - Non-storm, Low: +0.1-0.5 ms (cirrus +0.1, stratus / stratocumulus +0.1-0.3, nimbostratus +0.2-0.3, cumulus
    +0.3-0.5). Medium about 2x, High about 3x.
  - Storms (ungated): Low +1.5-2.2 ms, High +4-7 ms (supercell side 10.8 -> 12.9 / 18.0) for a small visible
    change -> gated out below High. s32 rerun with the gate pending.
- **Flatness measured against photographs (`flatness.py`, same code for photos and renders):** relative local
  luminance contrast at 1 / 2 / 4 / 8 % of the cloud height.
  - Photos (congestus, mediocris, humilis, calvus): 0.031-0.068 / 0.046-0.083 / 0.077-0.114 / 0.115-0.144.
  - Renders: 0.005-0.014 / 0.011-0.030 / 0.027-0.059 / 0.056-0.10 -> 3-6x less at 1 %, ~2x at 4 %.
  - Global light-to-shadow spread is NOT short (renders 0.21-0.47 vs photos 0.23-0.32); nothing clips.
  - Render resolution changes nothing (prod 0.0115 vs full res 0.0117 at 1 %).
  - The density alone (m3, full res) holds 2-3x the lit image's 1 % detail (0.022) but still under the photos.
- **Reading:** "flat" = the fine cauliflower scale (1-4 % of the cloud) is missing - half in the density (detail
  noise is world-scale, not cloud-relative) and half lost in the lighting (a pixel's radiance is the light reaching
  the first ~1/extinction blocks of the view ray; few-block bumps barely change it). Near light fixes mid-scale
  form, not this. Next: find which density / lighting stage removes the fine scale.
- **s32 rerun with the storm gate:** storms Low +0.0-0.45 ms, Medium +0.0-0.7 ms, High +5-7 ms (unchanged).

### E35 — 28 Oct 2026 — the general flatness: clouds are optically too thin for their size
- **Hypothesis.** A bump shades itself only if it is several mean free paths thick. ExtinctionScale 0.115 x
  density ~1 gives a mean free path of ~9 blocks. Real cumulus have a similar MFP in metres but are 1-3 km with
  50-200 m bubbles (many MFPs); ours are ~300 blocks with 5-15 block bubbles (1-2 MFPs) - translucent fog balls
  at their own scale.
- **s33 (S29-TEMP `extinctionMul`, lean FINAL, morning sun, Cloud Surface Detail Low):**

  | congestus close | 1 % | 2 % | 4 % | 8 % | ms |
  |---|---|---|---|---|---|
  | x1 (production) | 0.015 | 0.027 | 0.046 | 0.070 | 4.17 |
  | x2 | 0.027 | 0.049 | 0.079 | 0.115 | 3.69 |
  | x4 | 0.044 | 0.076 | 0.115 | 0.160 | 3.23 |
  | photos | 0.031-0.068 | 0.046-0.083 | 0.077-0.114 | 0.115-0.144 | - |

  Mediocris x4: 0.037 / 0.058 / 0.094 / 0.143 (in the photo range). Stratocumulus stays far below at 1-2 %
  (0.002 / 0.014 at x4): its density has no fine structure - a separate cause. Higher extinction is CHEAPER
  (rays go opaque sooner): congestus 4.17 -> 3.23 ms, stratocumulus 1.15 -> 0.75 ms.
- **What a denser cloud exposes (visible in s33):** (1) blocky / checkerboard speckle across lit faces (the
  detail-noise erosion pattern, previously hidden in the haze); (2) flat facets and straight edges on lobes (the
  lobe primitives). Both were always there; the thin medium blurred them.
- **Status:** diagnosis only; ExtinctionScale is global (storms were tuned at 0.115), so any change needs the
  user's decision and a gallery across families.
- **User:** "change that setting if it makes the clouds better ... why cant the storm have the same setting".
- **s34 storms x1 / x2 / x3:** visually almost unchanged (slightly crisper), 0.3-0.9 ms cheaper -> no reason to
  exclude them.
- **s35 every non-storm family x1 / x2 / x3 (close, morning):** x3 puts congestus in the photo range (0.036 /
  0.064 / 0.102 / 0.151), mediocris near it (0.026 / 0.043 / 0.074 / 0.117); humilis (0.012 at 1 %), stratus
  (0.001), stratocumulus (0.013) and nimbostratus (0.013) rise but stay low - their densities lack the fine
  structure. Every family 10-35 % cheaper (congestus 4.13 -> 3.52, nimbostratus 1.51 -> 1.15, cirrus 0.83 ->
  0.55 ms). x3 also shows the checkerboard speckle most clearly (mediocris lit lobe).
- **Shipped (uncommitted):** `Tuning.FIELDS` extinction 0.115 -> 0.345 for all field clouds, storms included.
  `Tuning.CELLS` (legacy cell path, not production) untouched. S29-TEMP `extinctionMul` kept at 1 for A/B arms.
- **Next:** the checkerboard speckle on lit faces; then the missing fine structure in humilis / stratus /
  stratocumulus / nimbostratus densities.

### E36 — 28 Oct 2026 — the checkerboard speckle was detail-noise aliasing (fixed)
- **s36 attribution (mediocris / congestus close, morning):** the 4x4-pixel speckle on lit faces is unchanged
  with the temporal lattice off (T206 one-frame path), with history off, and with the T211 origin-jitter arm; at
  full resolution it becomes a 1-pixel grain; with the pre-erosion density stage (no detail noise) it is gone.
  -> per-ray aliasing of the detail noise, enlarged into blocks by the 0.25-scale target.
- **Cause.** The detail texture (32^3 RGBA8, domain 0.022 / block = ~1.4-block texels) had NO mip levels
  (`CloudNoiseTextureManager.upload3d`, MIN filter LINEAR), so every `texture(..., mipBias)` read level 0 and its
  finest octave was sampled far below its frequency (a 0.25-scale pixel spans 1-3 blocks at normal range). The
  older note "noise textures have no mips, so mipBias is inert" was this bug.
- **Fix (production, uncommitted).** The detail texture is mipmapped (LINEAR_MIPMAP_LINEAR + glGenerateMipmap);
  the base noise is unchanged (no mips). Every detail read uses `textureLod` at `paDetailLod` = log2(texels per
  cloud-target pixel) from the real projection (no implicit derivatives inside the march) plus the caller's
  bias; the rain shaft keeps level 0 (its previous look).
- **Result.** Lit faces smooth, the blocky speckle gone; a fainter grain remains in shaded / edge regions (next
  suspect: per-pixel jitter or the light taps). Cost unchanged within noise. The aliased speckle was false
  detail: with it gone, the lobes read smoother, confirming E34 - the density lacks real fine structure at the
  cloud's own scale.

### E37 — 28 Oct 2026 — fine structure per family against the reference photos
- Two more detail reads used implicit derivatives (the second "fine" lookup in the density and in the field
  density); now explicit `textureLod` at `paDetailLod + 1.44` (2.71x frequency). No implicit detail reads remain.
- **Reference photos (one per family, `cloud-review-2026-10-08/*/reference.jpg`, flatness.py 1 / 2 / 4 / 8 %):**
  stratus 0.004 / 0.011 / 0.027 / 0.051 and nimbostratus 0.010 / 0.017 / 0.029 / 0.048 are smooth by nature;
  stratocumulus 0.017 / 0.028 / 0.048 / 0.076; capillatus 0.029 / 0.044 / 0.073 / 0.112; supercell 0.016 /
  0.022 / 0.037 / 0.060; cirrus 0.069 / 0.090 / 0.139 / 0.191.
- **s37 production (E33-E36 stack, morning, close):** congestus 0.022-0.031 / 0.035-0.057 / 0.060-0.101 /
  0.095-0.153 and mediocris 0.020-0.022 / 0.034-0.037 / 0.061-0.066 / 0.096-0.115 (at / near the photo range);
  nimbostratus 0.018 / 0.034 / 0.060 / 0.101 (above its smooth photo); stratocumulus 0.012 / 0.022 / 0.040 /
  0.069 (near). Humilis (94-97 px tall), stratus (20 px) and cirrus (5 px) are too small on screen at the close
  pose for a 1-4 % metric -> `s38` telephoto view (side pose, 30-degree FOV).
- **s38 telephoto (side pose, 30-degree FOV, morning):** nimbostratus 0.013 / 0.025 / 0.037 / 0.051 and
  stratocumulus 0.018 / 0.029 / 0.050 / 0.087 match their photos; stratus 0.003 / 0.006 / 0.015 / 0.032 is a
  little under its (smooth) photo. Humilis 0.021-0.032 / 0.025-0.033 / 0.038 / 0.053-0.065 and mediocris 0.035 /
  0.038 / 0.050 / 0.074 stay under their photos at 4-8 % - the cauliflower-head scale. Cirrus is too thin for
  the mask.
- **s39, S9 cloud-relative billow hierarchy re-tested under the new lighting (monolith):** the metric rises
  (congestus 1 % 0.035 -> 0.052 / 0.063 for h1 / h2) but the images show mottled blotches, not rounded heads -
  the E26 verdict stands (fuzz, not structure). Lesson: the metric alone is fooled by noise; judge structure on
  the images too.
- **New: per-pixel grain.** At 30 degrees every surface carries a strong pixel grain, also on the baseline
  (hidden by the small on-screen size at the normal FOV). Not the detail noise (now filtered). Suspect: the
  per-pixel ray-origin jitter not being averaged by the temporal path. Next to attribute.

### E38 — 28 Oct 2026 — the pixel grain is the march's dither with nothing to average it
- **Attribution (telephoto views, mediocris / congestus; s40-s44):**
  - Fine step x0.5 removes most of the grain, x0.25 all of it (cost +1.7-4.6 ms; +80-100 %).
  - Detail noise filtered to the step length (S31 `PaDetailStepFilter`): no change -> not the noise.
  - Fixed surface-bracket phase 0.5 / 1.0 (S32 `PaIntegrationPhase`): no change.
  - No blue noise in the march at all (origin jitter + phase fixed): the grain turns into wood-grain banding ->
    the jitter is doing its job, dithering step banding into per-pixel noise.
  - Near light (E33) off: much less grain in one session (s43), still present in transitions in another (s44)
    -> near light adds strong lit/shadow transitions along the ray, so it shows more of the dither.
  - Flat colour (lighting removed): no grain -> alpha is clean; the grain is radiance banding.
- **Cause.** The sub-step jitter only animates (`jitterFrame`) when `HistoryValid == 1`, and production's T207
  temporal lattice never sets it (the lattice resolve is the only temporal combination and does not accumulate
  samples per pixel). So the dither is a frozen per-pixel pattern, enlarged to 4x4 blocks by the 0.25 target,
  and the E35 extinction (one fine step ~58-70 % opacity) and the E33 near light raise its amplitude.
- **Kept:** E33 near light samples now skip the detail noise (E37 note in the shader; cheaper, shading reads the
  same).
- **Options (architecture, user decision):**
  1. Temporal accumulation for the lattice: animate the jitter per cycle and blend each lattice pixel with its
     reprojected history (reopens T207/T208 ghosting work).
  2. A small depth/alpha-aware spatial denoise in the composite/upscale (T209 pass already exists, -1 = off).
  3. Smaller fine step near surfaces only (first few in-cloud steps), priced against the +80-100 % global cost.
- S30/S31/S32-TEMP switches stay until the decision.

### E39 — 28 Oct 2026 — per-phase accumulation: the grain fixed at no cost (production, uncommitted)
- **User:** "approved do the one that fixed really" (temporal accumulation, not a blur or more steps).
- **Design (keeps T207/T165: nothing is resampled).** Each of the 4 phase targets is blended with ITS OWN
  previous result: `new = w * march + (1 - w) * previous` by hardware blending (constant alpha) on both
  attachments, the target no longer cleared. Averaging only when the phase's rays are the same rays: same camera
  position and view-projection as the phase's last render, at most one cycle (4 frames) old, cloud material
  drifted < 2 blocks; otherwise w = 1 (replace, exactly T207). w = max(1 / (n + 1), 1/8): a static camera converges
  over 8 renders per phase (32 frames). `CloudTemporalPhaseState.accumulationWeight`, `bindPhaseForAccumulation`,
  uniform `PaPhaseAccumulate`.
- **Shader:** with accumulation on, BOTH blue-noise phases animate per frame (the sub-step phase and the coarse
  search origin). The search origin was static on purpose (a hit/miss alternation on thin silhouettes could not be
  integrated by the old history); a blended miss is just a sample, so it can move now - and its frozen pattern WAS
  the grain (s43: no blue noise at all = banding; s45 run 2: animating only the sub-step phase changed nothing).
  `main()` now defines the aux output first (90 return paths, 2 wrote it; the clear used to supply the default).
- **Bugs found on the way (s45 runs 1-2):** the age test reused the resolve's MAX_AGE_FRAMES (3) - the phase being
  re-marched is exactly 4 frames old, so it never averaged (diagnosed by a temporary E39_ACCUM log, removed).
- **Result (s45 run 3, telephoto):** the per-pixel grain is gone on mediocris, congestus and stratocumulus - cleaner
  than the half fine step (which costs +70-80 %) - at identical cost (mediocris 4.81 / 4.77, congestus 5.30 / 5.29,
  stratocumulus 2.20 / 2.20 ms). Faint horizontal lines on stratocumulus remain in every arm (separate).
- **Limits:** any camera motion replaces the phases (T207 behaviour, no ghosting by construction) and the now
  animated dither shows as temporal noise while moving; clouds drifting under a static camera are averaged over up
  to 2 blocks of drift. A moving-camera evaluation (T152 route) is still to do.
- **Gate:** `campaignGate` passes. The T123 instrumentation rule had flagged S9's `paRadiusSum +=` (pre-existing,
  matches the `pa...+=` counter pattern) - renamed to `s9RadiusSum` / `s9RadiusWeight`.
- Gallery `validation/stabilization/s27-flat-surfaces/s45_phase_accumulation.png`.

### E40 — 29 Oct 2026 — E39 under camera motion
- **s46** (`pan` / `panfast` capture views: the zoom pose yawing 0.05 / 0.4 degrees per client tick while the arm
  renders and is shot; accumulation off vs production, mediocris and congestus).
  - Static: production clean, off grainy (as s45).
  - Panning: the yaw changes once per tick (20 Hz) and the frames between ticks are static, so production
    averages 2-3 frames per tick and is partly clean; continuous per-frame mouse motion would reset every frame and
    show the raw phases with the now-animated dither (moving noise instead of a frozen pattern).
  - Cost unchanged (pan 5.45 / 5.45 ms mediocris; congestus 4.64 / 4.65 ms).
  - No ghosting / smear visible (the phases only average identical rays).
- **What a full fix under motion needs:** reprojected per-phase accumulation (a TAA-style pass per phase with
  reprojection and history rejection). T165 measured that a resampled accumulation buffer keeps only 11 % of the
  lattice gain (static); under motion that sharpness is lost anyway, so a motion-only reprojected history (static
  frames stay on the exact E39 path) is the candidate. User decision.

### E41 — 29 Oct 2026 — motion history in the temporal resolve: the grain fixed under motion too (production, uncommitted)
- **User:** "yes go for it as long as you fix it".
- **Design.** The T207 resolve gains a motion history: a COPY of last frame's output (`motionHistoryTarget`,
  RGBA16F + depth, blitted after each resolve; the resolved target itself is still never bound as a sampler). Its
  point is reprojected into the previous frame, clamped to the current phase's 3x3 neighbourhood (premultiplied
  colour + coverage, +10 % pad) and dropped on a depth disagreement; rain pixels keep the current frame. Weight =
  0.9 x (1 while the camera moves, else 1 - E39 convergence): the history carries the frames while the phases are
  being replaced and fades out as their averages converge (8 renders), so a long-static camera is back on the exact
  non-recursive T207 + E39 output. Also gated on the phase generation (cut / resize / reload drops it).
- **Sandbox (T207 contract) updated:** the resolve's samplers are the raw phases, the rain pass and exactly
  `MotionHistoryColor` / `MotionHistoryDepth`; the history must be a post-resolve copy gated on motion and the
  generation. `campaignGate` passes.
- **Capture harness:** `pan` / `panfast` views turn the camera every rendered frame (RenderTickEvent), a +-3 degree
  triangle wave at 0.03 / 0.15 degrees per frame, restarted per arm; `grain.py` = pixel-scale high-pass RMS inside
  the cloud. (Per-tick turning left 2 static frames in 3 and under-stated motion; an unbounded per-frame pan left
  the frame.)
- **s46 grain (congestus / mediocris; lower = cleaner):**

  | | pre-E39 | E39 only | E39 + E41 0.8 | E39 + E41 0.9 (production) |
  |---|---|---|---|---|
  | static zoom | 0.0258 / 0.0144 | 0.0075 / 0.0041 | 0.0075 / 0.0041 | (same: weight 0 once converged) |
  | slow pan | 0.0069 / 0.0098 | 0.0130 / 0.0184 | 0.0051 / 0.0070 | 0.0034 / 0.0046 |
  | fast pan | 0.0077 / 0.0120 | 0.0161 / 0.0227 | 0.0078 / 0.0122 | 0.0053 / 0.0085 |

  E39 alone made motion worse (its animated dither, nothing to average); E41 at 0.9 beats the pre-E39 renderer in
  every case. No visible ghosting or smear (the pre-E39 fast pan shows horizontal smear streaks; production does
  not). Cost unchanged (4.5-5.1 ms in every arm).
- Gallery `s46_motion_history_fastpan.png`; tools `flatness.py`, `grain.py`.

### E42 — 29 Oct 2026 — cauliflower heads from lobe-surface cells (S33, candidate, awaiting the user)
- **Why the earlier attempts failed:** S9 and the T242 billows read world-space 3D noise; a surface cutting through
  3D cells takes arbitrary shapes (blotches), not domes.
- **S33 (`PaPuffBubbles`, `PaPuffBubbleSharp`, live, default 0):** a jittered cell lattice in each lobe's OWN
  surface coordinates (angle around x vertical coordinate, wrapping around the lobe; `paBubbleF1`), so every cell is
  one head on the lobe. The heads are made by carving the creases between them inward (up to 12 % x strength of
  the radius) - no headroom needed, nothing leaves the tile footprint. Sides and top; the flat base is spared.
  - wide (11 / 22 cells around, crease over most of the cell): undulating blotches.
  - sharp (9 / 16 around, crease only in the outer ~quarter of each cell): round heads separated by narrow
    creases - reads as cauliflower on congestus, partly on mediocris; humilis barely changes (its small low lobes
    keep few heads in the bubble zone).
- **s47 (telephoto, morning), 4 % / 8 % detail:** congestus 0.058 / 0.097 -> sharp3 0.101 / 0.136 (photo 0.077-0.114
  / 0.115-0.144); mediocris 0.039 / 0.064 -> 0.046 / 0.071; humilis unchanged. Cost +0.1-0.35 ms per cloud at the
  close view (congestus 3.99 -> 4.27, mediocris 3.25 -> 3.61, humilis 1.92 -> 2.05).
- **Seen on the way:** heads line up in rows (the lattice's vertical axis); horizontal streaks on faces at camera
  height in every arm (the grazing-ray case, as on stratocumulus); a hard-edged bright rectangle on the production
  congestus (the E26 "box", reproduced) - both next to attribute.
- Gallery `s47a_bubbles_wide.png`, `s47b_bubbles_sharp.png`.

### E43 — 29 Oct 2026 — full-frame re-evaluation: E35 x3 density made the shapes worse; S33 rejected
- **User:** "the clouds look full pixelated and their shape is awful now - make sure to evaluate everything not just
  the fps". Agreed after looking at full frames (normal FOV, LANCZOS-downscaled) and 1:1 crops (`s48`):
  - x3 density: congestus / mediocris read as a stack of smooth balls (snowman), humilis as pill segments,
    stratocumulus gets dark dirty blotches. The pre-E33 haze was flatter but read as one cloud. The E34/E35
    fine-scale metric rose because the lobe-pile geometry became visible, not because the clouds became more
    cloud-like - the metric was the wrong judge.
  - S33 heads: torn, messy shards at full frame -> REJECTED (switch stays 0).
  - "Pixelated": the zoomed crops I sent were magnified with NEAREST resampling; at 1:1 the silhouettes are smooth.
    A fine grain remains in shaded areas. The T209 upscalers (EASU / Catmull-Rom) change little.
- Lesson: judge looks on full frames and 1:1 crops first; metrics second.
- Next: `s49` full-frame density / near-light sweep (x1 with and without near light, x1.5, x2, x3) for the user.
- **s49 full-frame sweep (normal FOV, morning, five families):** x1 soft and cohesive but flat; x2 / x3 the lobe
  pile (balls) and dark dirty patches on stratocumulus / nimbostratus; x1.5 the best compromise. Near light makes
  little difference at normal distance (kept, cheap at Low).
- **Production now x1.5 (`Tuning.FIELDS` 0.1725, uncommitted).** Gallery `s48_*`, `s49_density_sweep_fullframe.png`.
- **The real problem stands:** the lobe-union geometry is a pile of smooth balls; density can only hide or expose
  it. Shape work (one continuous body per cloud, E31/E32) is the fix, judged on full frames.

### E44 — 29 Oct 2026 — root cause of the "pile of balls" cumulus (measured), fix proposed
- **Measure (`silhouette.py`, same code for photos and renders):** share of the cloud's silhouette area in bumps
  narrower than ~2r, r = 1 / 2 / 4 / 8 % of the cloud height (morphological opening). Clean-sky shots only (s37
  close, s38 telephoto; s49 congestus shots touched terrain and were discarded).

  | | 1 % | 2 % | 4 % | 8 % |
  |---|---|---|---|---|
  | photo congestus | 0.0145 | 0.0247 | 0.0475 | 0.104 |
  | ours congestus | 0.005-0.009 | 0.009-0.012 | 0.016-0.018 | 0.040-0.108 |
  | photo mediocris | 0.0206 | 0.0481 | 0.150 | 0.307 |
  | ours mediocris | 0.0007-0.0037 | 0.0018-0.0101 | 0.0035-0.0149 | 0.007-0.062 |
  | photo humilis | 0.0061 | 0.0184 | 0.0575 | 0.233 |
  | ours humilis | 0.0004-0.0038 | 0.0005-0.0054 | 0.0025-0.0078 | 0.005-0.012 |

  Our outlines are 2-3x (congestus) to 10-40x (mediocris, humilis) smoother at every scale below a lobe. Congestus
  only matches at 8 % - the scale of its own lobes.
- **Root cause.** A cumulus is 8-17 smooth implicit ellipsoids (`directPuffLobeSample`) joined by a max of the top
  two (`accumulatePuffShape`), with a hard 0.18-depth surface (`resolvePuffContinuousField`) and the detail erosion
  switched off for puffs (`directPuffCarrierOwnsBoundary`). The only structure is the lobes themselves: no level of
  heads between "one lobe" and "noise grain", and each lobe keeps its own smooth surface and crease. Real cumulus
  is a hierarchy of rounded heads within heads (domes at ~30 %, ~12 %, ~5 % of the cloud). Density / lighting can
  only hide (x1) or expose (x3) the pile; surface noise in world space gives blotches (S9, T242, S33-wide), carved
  creases give torn shards (S33).
- **Proposal:** hierarchical one-body cumulus (see the summary to the user); acceptance = full-frame side by side
  with the old look and the photos, plus `silhouette.py` inside the photo ranges per family.

### E45 — 30 Oct 2026 — hierarchical heads on the one-body cumulus (S26 + heads, experimental)
- **User:** "continue" (build the E44 proposal).
- **Code (`paPuffLobeField`, S26 body mode, `PaPuffBodyTurrets`):** heads are explicit spheres in the summed field.
  - mode 1 (`heads1`): 7 seeded heads (0.36 lobe radii, 0.72 out) + 3 small heads (0.16) on each, near-surface only.
  - mode 2 (`dense`, strength - 1): 16 heads on a Fibonacci spiral over the sides and top (0.30-0.40), the parent
    ball weakened to 0.8, small heads (0.15) on the three heads nearest the sample. Reach <= 1.18 lobe radii; the
    segment AABB vertical padding covers 0.20 span in body mode.
- **s50 (full frames + 1:1, morning):**
  - humilis: production reads as a balloon / pill string; dense = fluffy cotton cauliflower.
  - mediocris: production = smooth balls; dense = a real cauliflower mass - clearly better at 1:1.
  - congestus: heads show as equal-size bright round spots ("bubble wrap") on the shaded body - too regular.
  - silhouette bumps (4 %): humilis 0.0019 -> 0.0049-0.0079, mediocris 0.0022 -> 0.0084-0.0093 (photos 0.058 /
    0.150: still far smoother); congestus unchanged (its instance already bumpy at lobe scale).
  - cost vs production (close): humilis 2.47 -> 2.81, mediocris 3.63 -> 3.99, congestus 4.13 -> 4.73 ms.
- **Also seen (s50 run 1):** vertical bright / dark stripes across one congestus body in every arm incl. production
  (instance / view dependent; did not reproduce on s51's clouds) - open.
- Next: congestus heads - wider size spread, fewer / smaller small heads, heads embedded deeper on the lit side.
