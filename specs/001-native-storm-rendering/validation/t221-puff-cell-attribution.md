# T221 - Puff Cell / Structural Shading Attribution

**The question:** what actually creates the flat / cellular puff appearance, now that "aliasing"
has been ruled out?

**The answer: lighting, not shape - CASE B - through two different lighting-response mechanisms,
one per region. The single candidate built from that attribution is REJECTED, and no production
value changes.**

- **Upper dome - shadow-floor flatness.** Its light-path optical depth sits at 6.7-7.9 (median),
  where every term in the model is constant, so it renders as one flat plateau (59-91% flat
  pixels) - while the density under it demonstrably still varies. The shape is present; the
  shadowed lighting discards it.
- **Lower mass - vein cells.** It is *lit* (light-path optical depth 0.64-0.88), on the steep part
  of the response, and its thin bright/dark vein network is the light path's own structure:
  production's ridge map matches the light-path view at **0.90-0.98**. Measured NOT to be the
  shared field, the light-tap spacing, the local density, or the detail/erosion on the light taps;
  by elimination it is the base field / envelope sampled sunward.
- **The candidate** (a shadow form term) gave the dome real billowing but **imported the local
  density's erosion veins** - the same defect class in a new place - and is rejected on stills.
- **Structural disconnect is a separate defect.** The thin neck and the lower mass's coherence are
  shape (alpha) properties; the cells are not. Two mechanisms, not one.

Four same-storm runs, every comparison adjacent, every arm audited frame by frame, every metric
calibrated on synthetic images first - one of them (edge coherence) was dropped because it failed
that calibration.

## RECOVERY

1. Starting HEAD: `042ab1b` (T220, "internal resolution is not the lever, and T219's premise was
   wrong").
2. Branch: `quality/t217-deep-interior-lighting`, continued.
3. Production state, verified and unchanged at exit: `PRODUCTION_TEMPORAL_MODE=1`,
   `PRODUCTION_SPATIAL_UPSCALE=-1`, `PRODUCTION_RAIN_LATTICE_MODE=0`,
   `PRODUCTION_ORIGIN_JITTER_MODE=0`, `PRODUCTION_MACRO_ENVELOPE_MODE=3`,
   `PRODUCTION_LOWER_BODY_MODE=1`, `PRODUCTION_DEEP_SCATTER_MODE=1`, shared-field consumer bits
   `1|2|8|32 = 43`. `PAchangelog.md` the only working-tree change. `campaignGate` PASS before any
   client work (`gate-t221.log`).

   Facts established before building, because they shaped every arm:

   - **The primary body density is exact.** The shared field feeds the **light cone** (bit 1, and
     bit 8 which interpolates a node's threshold and height and evaluates the noise itself) and
     traversal (2 probe, 32 clearance). Bit 16, the body from the field, never shipped. So the
     field can only reach the image through lighting or traversal - never through alpha directly.
   - **Field nodes are 16 blocks horizontal x ~27.6 vertical** (256 x 32 over the slab) - a
     plausible cell scale, which is why it was tested rather than assumed away.
   - **Any non-production field bits, lighting mode or step cap route the frame to the diagnostic
     monolith**, so every monolith arm is paired against a **monolith control** (M: production
     settings, step cap 128 = `MAX_STEPS`, i.e. behaviourally neutral) rather than against FINAL.
     M vs FINAL: mean |d| 0.10-0.13, gradient correlation 0.979-0.994 - a valid control.
   - **Inside the slab the light cone is capped at 4 taps**, first tap 14 blocks sunward, growth
     x1.42, reach 102.2 blocks. The cap applies whenever the **camera** is vertically inside the
     slab, cloud or not - which is every capture pose here.

## REFERENCE (Tasks 1, 2)

4. Reference pose: `SIDE` (1.7 R, mid height) - the lower mass and dome both in frame, where the
   defect is strongest - with `NEAR_EDGE` alongside; plus the mandatory low-side structural view
   `LOWSIDE` (1.45 R, base + 0.10 height, looking up through the neck).
5. Reference generation: the **same storm, same launch, adjacent capture** at internal scale 1.0.
   The phase is 1600x900 and the temporal lattice 3200x1800 - exactly 2x the 1600x900 frame - so
   the composite's 4-tap bilinear at an exact 2x minification is a **2x2 box downsample**: a
   genuine supersampled, box-filtered reference, not a different launch or storm.
6. Production/reference alpha error: silhouette **IoU 0.9994 (SIDE) / 0.9995 (NEAR_EDGE)**, area
   **+0.005% / -0.004%**. Noise floor (production repeat P0B): IoU 1.0000.
7. Luminance error: body mean |d| **0.135 / 0.119** 8-bit, almost all of it in the high band
   (0.138 / 0.128); in the silhouette band **1.03 / 1.05** - edge filtering, T220's territory.
   Production repeat: 0.017 / 0.005.
8. Gradient error: body mean |grad difference| **0.109 / 0.101**; gradient correlation
   **0.980 / 0.989**. The cell statistics are identical: flat 83.6% vs 83.6%, plateau diameter
   51.0 vs 52.7 px, sparsity 0.989 vs 0.989.

**The defect is in the supersampled reference too.** Production reproduces its reference to
within ~1 level; whatever makes the cells, it is in the image a correct renderer of this model
produces, which is what licenses looking at the model rather than the sampling (confirming T220).

## QUANTIFYING THE "CELL" LOOK (Task 3)

Metrics calibrated on synthetic images before being trusted - the T219/T220 lesson:

| synthetic | flat fraction | plateau diam | gradient sparsity | edge coherence |
|---|---|---|---|---|
| smooth field | 11.9% | 15.9 | 0.394 | **0.954** |
| same field posterised into flat cells | 74.9% | 22.3 | 0.561 | 0.737 |
| Voronoi flat cells | 91.2% | 39.7 | 1.000 | **0.333** |

- **Flat fraction and gradient sparsity** (share of gradient energy in the top 10% of pixels)
  separate smooth from cellular cleanly. **Edge coherence does not** - a smooth field scores
  highest and true cells lowest - so it is withdrawn and not used below.
- **8-bit control:** a *smooth* 8-bit field at the real image's gradient scale (~0.5 per px) scores
  57% flat and sparsity 0.48. Production scores 80-84% flat and sparsity 0.97-0.99 - far beyond
  what quantization of a smooth surface produces. The plateau-and-ridge structure is real.
- **Vein score:** 95th-percentile thin-line (Hessian ridge) response over the region's own SD,
  because the flat/sparsity pair does not score vein *networks* well. Plus ridge-map correlation
  between arms, which says *whose* lines production's lines are.

9. Cell-size estimate: plateau equivalent diameter **30-58 px** in production (SIDE 52.7,
   NEAR_EDGE 30.6, LOWSIDE 33.4), i.e. ~40-70 blocks at SIDE's 1.27 blocks/px.
10. Flat-region statistic: whole body **78.6-83.7%** flat; split at the neck, **dome 59-91%**,
    **lower mass 20-39%** - the pooled figure mixed two different phenomena (below).
11. Boundary statistic: gradient sparsity **0.97-0.99** pooled; dome 0.80-0.92, lower mass
    0.65-0.83, against 0.38-0.48 for a smooth field at matched gradient. Curvature was not
    measured directly - the coherence proxy failed calibration - and the vein score replaces it.

## SHAPE vs LIGHTING (Task 4) - the primary split

Arms, adjacent per pose (run 1, fixture `d8a9fe74`, radius 656): **U** shape only (constant
radiance: the frame is alpha and nothing else), **L** light path only (the production light cone,
field lookups included, written as grey transmittance - new view), **F** exact light cone (field
bits 34: probe and clearance kept, light lookups 1 and 8 off), **M** monolith control, the
composite's **ALPHA** and **DEPTH** views of the production frame, **REF**, **P0B**.

12. **Alpha / density diagnostic: no cells.** U is uniformly flat inside the body (98.5% flat,
    plateau 202 px - one region); the ALPHA view is saturated white across the mass. At this scale
    coverage has no internal structure at all. The local-density view **D** (run 2, new view) is
    *not* flat - 0.5-8% flat pixels, gradient energy 3.5-10 - so shape information is present
    everywhere, including under the dome's plateaus; but production's structure does not follow
    it (gradient correlation **0.15-0.63**, ridge correlation **0.04-0.63**).
13. **Constant-light result:** as item 12 - the shape carries none of the cells.
14. **Production-light result:** the cells are in the light path. The L view shows the same
    plateau-and-vein network as production; production's ridge map matches L's at **0.90-0.98**
    in every region of every pose.
15. **Controlling stage: lighting - specifically the light-path optical depth mapped through the
    lighting response.** Recovering per-pixel optical depth from L (`od = -2 ln L`) and splitting
    at the neck:

| pose | region | light-path od p10 / 50 / 90 | od in 1-4 | flat | sparsity |
|---|---|---|---|---|---|
| SIDE | dome | 3.58 / **6.92** / 8.89 | 12.0% | 63.7% | 0.890 |
| SIDE | lower | 0.42 / **0.79** / 8.31 | 7.5% | 26.7% | 0.816 |
| NEAR_EDGE | dome | 5.80 / **7.86** / 8.89 | 0.5% | 90.6% | 0.802 |
| NEAR_EDGE | lower | 0.30 / **0.66** / 1.24 | 9.0% | 38.7% | 0.807 |
| LOWSIDE | dome | 5.09 / **7.50** / 8.89 | 2.6% | 59.0% | 0.857 |
| LOWSIDE | lower | 0.40 / **0.64** / 0.88 | 2.2% | 20.1% | 0.652 |

    Production's flat plateau pixels sit at a median od of **7.5-7.9**; its ridge pixels at
    **0.7-1.6**. Pooled over the interior, 57-61% of pixels have od > 6, where production's
    luminance SD is only **2.9-4.5 levels** and 69-93% of pixels are flat. The band od 1-4 - where
    the response is smooth and shading varies gently - holds only **0.5-12%**. The optical depth
    is bimodal and the response turns that into plateaus and ridges.

    **Two regimes, one per region.** The **dome** is in its own shadow (sun at `(0, 0.99, 0.12)`,
    nearly overhead) and saturates: od > ~4 is where T218 measured `d(8bit)/d(od)` collapsing to
    -0.25 and below, and every ambient term there is constant (height, storm factor and a fixed
    retention). The **lower mass** is lit, on the steep part of the response (`d(8bit)/d(od)`
    -52 at od 0.5, T218), so small light-path variations become high-contrast veins.

    **Why is the lower mass lit at all**, under a ~400-block anvil with an overhead sun? The
    4-tap in-slab cap reaches 102 blocks, and the gap from the lower mass (BASE lobes to y≈599 in
    this generator's layout, T215) to the anvil (from y≈772) is 170-370 blocks, so the cone never
    reaches it. **A camera below the slab - the gameplay altitude - runs 6 taps reaching 240 blocks
    and can.** The lower mass's shadowing therefore depends on camera altitude: a real lighting
    inconsistency, recorded as its own finding and not changed here.

## WHICH PART OF THE LIGHT PATH (Tasks 5, 6, 7, 8)

16. **Base / detail / erosion result.** Run 4 (fixture `6db45416`): **N** turns the detail
    (erosion) octaves off on every light tap - production reads them on the first two - paired
    against **M**, nothing else changed.

| pose | region | vein score M -> N | ridge corr N vs M | sparsity M -> N |
|---|---|---|---|---|
| SIDE | lower | 0.340 -> **0.379** | 0.979 | 0.776 -> 0.786 |
| NEAR_EDGE | lower | 0.159 -> **0.175** | 0.969 | 0.787 -> 0.784 |
| LOWSIDE | lower | 0.238 -> **0.246** | 0.989 | 0.570 -> 0.584 |

    **Removing detail from the light path does not remove the veins** - it leaves them in place
    (ridge correlation 0.97-0.99) and slightly stronger. The veins are not the detail/erosion
    field. What remains in a light tap is the descriptor envelope times the base-field remap;
    the envelope is smooth at 50-400 blocks and cannot draw a dense thin-line network except
    along lobe boundaries, while the base carrier's Worley octaves (12.5 / 25 / 50 blocks) can.
    **By elimination the source is the base field sampled sunward** - stated as elimination, not
    as a direct isolation (see limits).

    Tap spacing, run 2 (fixture `810b1176`): **R** starts the cone at 3.5 blocks instead of 14,
    doubles the taps and grows by 1.35 - reach matched, 100 vs 102 blocks - so the segment the
    production first tap jumps over is actually sampled; paired against **F** (same exact cone,
    production spacing). Lower-mass sparsity moves **-0.02 to -0.035** and the vein score not at
    all (0.316 vs 0.314 at SIDE); in the dome R is *flatter* (61 -> 67%). **Tap discretization is
    not the source.**

17. **Shared-field result: not the source of the cells.** F (field off in lighting) vs M: cell
    statistics unchanged (flat 83.1% vs 83.7%, sparsity 0.985 vs 0.988); the difference is
    **low-frequency** (SIDE body error 1.62 of which 1.34 in the low band). In the SIDE dome the
    field shifts broad brightness substantially (P0 - F RMS **8.55 levels**), but its spectrum
    peaks at the window's lowest bin (128 px), **not at the node spacing** (12.6 x 21.8 px there);
    at NEAR_EDGE its RMS is 0.29. **No lattice signature.** The field's dome brightness offset is
    an accuracy finding in its own right, not the cell mechanism.
18. **Descriptor/lobe correlation: not visualized, and not needed for the decision.** The cells
    are absent from alpha, where a lobe-ownership boundary would have to show, and the vein
    network's density (plateaus of 30-58 px) is far finer than lobe spacing. Lobe boundaries
    sampled *sunward* remain the one light-path source not directly excluded (limits).

## STRUCTURE (Task 9)

19. Tower/anvil continuity, from the composite's ALPHA view split at the neck (runs 1-2):

| pose | neck width | lower-mass max width | neck / lower | lower components | largest component | lower bbox occupancy | lower holes |
|---|---|---|---|---|---|---|---|
| SIDE | 563 / 559 px | 563 px | ~1.0 | 2 / 3 | 99.8% / 99.2% | 0.487 / 0.482 | 7.1% / 7.0% |
| NEAR_EDGE | 263 / 257 px | 787 px | 0.33 | 6 / 5 | 99.8% / 99.6% | 0.750 / 0.763 | 1.7% / 3.9% |
| LOWSIDE | 253 / 236 px | 460 px | **0.55** | 7 / 4 | 98.2% / 98.7% | 0.456 / 0.466 | 8.6% / 9.6% |

    The lower mass is **one coherent body** (largest component 98-99.8% of its solid area) with a
    handful of small fragments; the neck is 0.33-0.55 of the lower mass's width from the low
    views. These are properties of **coverage** - they are in U and ALPHA - and the cells are not.
20. **Same mechanism: NO.** The structural disconnect is shape; the cell look is lighting. They
    are not combined and nothing here touches the structure.

## CANDIDATE (Tasks 10-13)

21. **One candidate, from the attribution: the shadow form term.** Item 12 showed the density
    varies under the dome's plateaus while the shadowed ambient ignores it. So ambient is
    modulated by local density, weighted by how shadowed the sample is:

    ```glsl
    float formDensity  = saturate(localDensity / (0.80 * DensityMul));
    float shadowWeight = 1.0 - directTransmission;          // 0 lit, 1 in shadow
    float form         = 0.5 - smoothstep(0.10, 0.85, formDensity);
    ambient *= 1.0 + 0.40 * shadowWeight * form;            // +/-20%, mean-preserving
    ```

    Mean-preserving by construction, because T217 showed that *lifting* the shadow floor washes
    the anvil underside; lit surfaces untouched; no fetch. A live uniform on the production FINAL
    program (confirmed `leanFinal=true` on every C1 frame).

22. **Quality result: REJECT.** Run 3 (fixture `cade76ce`), P0 / C1 / P0B adjacent at six poses;
    the repeat P0B is bit-identical to P0 in every region, so the noise floor is zero.

| pose | dome flat P0 -> C1 | dome sparsity | dome vein score, upper 2/3 | dome vein score, lower 1/3 |
|---|---|---|---|---|
| SIDE | 62.2 -> **33.2%** | 0.916 -> 0.818 | 0.069 -> 0.073 | 0.199 -> **0.230** |
| NEAR_EDGE | 88.7 -> **57.7%** | 0.775 -> 0.715 | 0.064 -> **0.096** | 0.197 -> **0.266** |
| LOWSIDE | 58.9 -> **26.5%** | 0.917 -> 0.790 | 0.084 -> 0.095 | 0.291 -> 0.301 |

    It does what the attribution predicted - the plateau breaks up and the dome gains real form
    (the 1:1 crop shows soft lobes and darker folds where production is a flat slab) - **and it
    imports the local density's erosion veins**: the lower third of the dome grows a fine
    thin-line network (vein score +16% SIDE, **+35%** NEAR_EDGE), visible at 1:1. That is the
    same defect class the task exists to remove, moved to a new place. By the acceptance rule
    ("puff surfaces read continuously rather than like flat cells") this fails.

23. **Structural result:** unchanged - silhouette IoU 0.9995-0.9999 and area within 0.04% at the
    storm poses. The term cannot move alpha.

## TEMPORAL (Task 14)

24. Motion, alpha: **not run.** The candidate failed on stills, and the brief's rule is that a
    serious candidate runs motion; a rejected one would be validating something that is not
    proposed. The `T221M` route was wired (registry row, ArmSet, the `Phase.T152_BEGIN` OR-chain)
    and then removed with the candidate.
25. Motion, luminance: not run, same reason. Had it run, the luminance metrics from T218 were the
    right instrument - the term changes shadowed radiance and never alpha.

## PERFORMANCE (Task 15)

26. SIDE: capture-pin P0 **38.79 ms**, C1 34.39, P0B 36.03 - the candidate delta is inside the
    production repeat's own 2.8 ms spread. Structurally one `smoothstep` and a few multiply-adds
    per lighting sample, no fetch.
27. PVN: not in this shot set; production is unchanged, so the T218/T219 gameplay-phase figure
    stands (`gpuMs` p50 1.541).
28. Whole renderer <= 10 ms: **yes, and unchanged** - no production path was altered.

    The attribution arms were priced for the record (capture pin, monolith): shape-only / light
    path / monolith control all ~9-10x FINAL (the monolith itself), exact light cone +63-71% over
    the monolith control, fine-tap cone +39% over the exact cone. Diagnostics, not proposals.

## DECISION

29. **REJECT the candidate. Attribution: CASE B (lighting).** No production constant changes.
30. **Exact next target: the shadowed-ambient form term, driven by a SMOOTH density input.**
    The attribution and the rejection together say precisely what the fix needs: the dome is flat
    because shadowed ambient ignores density; feeding it density works - it broke the plateaus
    (flat 62 -> 33%) - but *eroded* density carries the erosion veins into the image. The same
    term fed by the **un-eroded body density** (the base carrier and envelope, 50-400-block
    structure: billows, not veins) should keep the form and drop the veins. That needs
    `cloudDensity` to expose the pre-erosion body alongside the density it returns - one extra
    output, no extra fetch. Judge it on the vein score and the dome flat fraction together, then
    on motion (luminance metrics) before anything ships.

    Two further findings, ranked behind it and **not** to be folded into the same change:

    - **The in-slab 4-tap light-cone cap makes self-shadowing depend on camera altitude.** The
      lower mass is lit under the anvil because a camera inside the slab gets 102 blocks of reach;
      a gameplay camera below the slab gets 240. Worth its own task: decide whether the cap should
      key on `cameraInsideCloud` rather than slab membership, priced first - the cap exists for
      cost.
    - **The lower mass's vein network** traces, by elimination, to the base field sampled sunward
      through the steep lit response. Next isolation arm: base-field-only / envelope-only light
      taps, and a lobe-ownership overlay, before designing anything for it.

## VALIDATION

31. `campaignGate` PASS: before client work (`gate-t221.log`), after each arm set
    (`gate-t221b/c/d/e.log`), and after the collapse (`gate-t221f.log`).
32. `check build` PASS (`check-build-t221.log`).
33. Morphology sandbox PASS - 10 `PHASE4S_RESULT ... PASSED`, standalone after the collapse.
34. Markers: `run/t132-autorun-exit.txt` armed throughout; `run/t211-captures.txt` the only
    campaign marker, retired. No motion marker was ever created.
35. `git status`: `PAchangelog.md` left modified and uncommitted as required. No evidence, campaign
    log or unrelated log deleted.
36. Committed on `quality/t217-deep-interior-lighting`. **Not merged.**

### Program scope

Removed, as T218-T220 removed theirs: the tap-geometry arm `PaLightRefine` (and the doubled loop
bound it needed), the candidate `PaShadowForm`, the T221 shot block and per-shot arm plumbing, and
the `T221M` motion wiring. `grep -rn "PaLightRefine\|PaShadowForm" src/` returns nothing, and the
light cone is byte-identical to `042ab1b`.

Kept, because each is an instrument rather than an arm:

- **Two new attribution views** on the existing `PaDiagnosticLightingMode` (T136's uniform, which
  already had mode 1): **2 light path only** and **3 local density**. They decided this task, cost
  nothing unless selected, and route to the monolith. The mode is now an integer
  (`VolumetricCloudDebugConfig.setDiagnosticLightingMode`), with T136's boolean still forcing 1.
- **`lightingMode`, `fieldBits` and `leanFinal`** on every `T098_CAPTURE_CONTROLS` line, so an arm
  that was meant to run on FINAL - or on the monolith - is provably on it.
- **A harness bug fix:** when a T152 route ended it restored deep scatter to **0**, but production
  has been **1** since T217, so any later frame in the same session rendered without it. It now
  restores `PRODUCTION_DEEP_SCATTER_MODE`.
- The analysis scripts, calibrated, in `t221-campaign/` (`t221_cells.py`, `t221_regions.py`,
  `t221_ridge.py`).

## HONEST LIMITS

- **The vein source is by elimination.** Field, tap spacing, local density and light-tap detail
  are each excluded by a paired arm; the base field vs a lobe boundary sampled sunward is not
  separated. The network's density argues for the base field; it is not proof.
- **Four fixtures across four runs.** Every comparison is inside one run, as the harness requires;
  the conclusions repeat across all four storms, but the numbers are per-storm.
- **Run 1 lost its fixture at `T221_L_LATERAL`**; that frame and all of THROUGH in run 1 are
  excluded. Run 3's automatic body masks were unreliable at SIDE-lower, THROUGH and ABOVE (a
  percentile threshold captured sky, or moved between identical frames) and those rows are not
  used; the dome and structure results use the ALPHA-based masks from runs 1, 2 and 4.
- **The optical-depth recovery `od = -2 ln L`** reads an alpha-weighted mix along the view ray,
  not a single sample's od. It is exact for a single-sample pixel and a smoothed estimate
  otherwise; it is used for distributions and region medians, not per-pixel claims.
- **Every capture pose is inside the slab**, so every frame here ran the 4-tap cone. Gameplay below
  the slab runs 6 taps, and the lower mass will shade differently there (item 15).
