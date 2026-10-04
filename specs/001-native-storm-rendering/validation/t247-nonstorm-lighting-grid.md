# T247 - Non-storm cloud lighting grid / panel artefacts

Branch `quality/t217-deep-interior-lighting`, from `eae18293` (T246 accepted). Lighting only: no cloud shape,
density, opacity or silhouette change. Evidence: `validation/t247-campaign/` (sheets, `t247-metrics.txt`,
`t247-timing.log`).

## 1. Defect

Dark / light rectangular panels on non-storm clouds, aligned to a world 16-block grid: vertical dark slabs on the
nimbostratus deck face (side / close), dark slab chips under and beside stratocumulus clumps (below / side /
close), dark boxes on humilis and mediocris fragments (close / side), dark facets on congestus, dark chips at the
stratus deck edge. Visible in the T246 reference and final galleries.

## 2. Affected families

Every family lit through the T199 shared field's NON-storm path: nimbostratus, stratocumulus, stratus, cirrus and
the cumulus puffs (whose tiles T246 made uncovered). Storm bodies are lit through the covered (storm) path and are
not touched by this task.

## 3. Attribution - two compounding faults in the non-storm light lookup

`paStormFieldLightDensityAt` serves every light-cone tap from the 256 x 256 x 32 field (16-block XZ nodes). For a
node the field does not model (`R` = -10, "uncovered": a weather-map family is painted nearby) it returned the
node channel `A`, hardware-bilinear across the four nodes, linear in Y.

1. **Clearance contamination (the dark panels).** Production enables consumer bit 32 (T202), which stores a
   COVERED node's clearance floor in `A` - 4096 on a provably empty node. The uncovered branch is chosen from the
   bilinear `R` (`<= -5`), so any light tap in a cell between an uncovered and a covered node with up to 45 %
   weight on the covered one blended up to 0.45 x 4096 into its density: the light ray is opaque and the sample
   renders dark. Every cell on the boundary of non-storm material (deck rim columns, clump edges, puff tile
   edges) printed a grid-aligned dark panel. This also explains the humilis rectangle after T246 made puff tiles
   uncovered next to covered empty tiles, and the stratocumulus being ~7 levels too dark overall.
2. **Point-sampled 16-block density (the remaining plates).** `A` is one detail-free point sample at the node
   centre; bilinear tents of it give C0-only shading with slope breaks on the node lines - faint plates on the
   nimbostratus face.

The weather map origin snaps to 8-block texels as the camera moves, so the 16-block node grid shifts by half a
node at every other snap: the field-lit shading also POPPED under motion (section 9).

## 4. Exact-lighting control (quality ceiling)

Diagnostic arm C (monolith, same instance): the light cone evaluates the exact `cloudDensity` wherever the node
is uncovered. Panels vanish on every family; mean luminance +0.7 (nimbostratus) / +7 (stratocumulus) / -2
(humilis) levels against production; silhouettes identical (mask differences 0.2-2 %, equal to the identical-arm
noise M2). Not shipped as is: every light tap of non-storm material would run the full density.

## 5. Candidates (one coherent change each, same instance, monolith arms, sessions `t247a` / `t247b`)

Metric (`t247_metrics.py`): `rel` = RMS luminance error to the exact reference inside the cloud mask, divided by
production's (0 = exact, 1 = production); `sil` = cloud-mask membership change; the identical-arm control M2
gives the noise floor (rel 0.85-1.0 on nimbostratus, whose deck rendering is noisier).

| id | change | nimbostratus rel | stratocumulus rel | humilis rel | look | verdict |
|---|---|---|---|---|---|---|
| L0 | non-storm bilinear excludes covered nodes (they carry no non-storm material) | 0.64-0.87 | 0.49 | 0.88 | dark panels / boxes / chips gone; faint plates remain on the deck face | needed, not sufficient |
| L2 | build pass averages 5 taps per uncovered node | (with L0) no visible gain over L0 | | | | rejected (build cost for nothing) |
| L4 | L0 + the FIRST light tap of a non-storm sample is exact | 0.37-0.52 | 0.00 | 0.00 | indistinguishable from the exact reference in every view | **accepted** |
| L5 | L0 + the first TWO taps exact | 0.25-0.32 | 0.00 | 0.00 | same as L4 | rejected (twice L4's extra work, no visible gain) |

## 6. Rejected reasons

L2: the build-pass supersampling did not change the image beyond L0. L5: no visible gain over L4. Full exact
lighting: L4 already matches it visually; the remaining taps on the field are where the field is accurate enough.
L4 with two `cloudDensity` call sites: +0.25 ms at SIDE from codegen alone (section 10) - restructured.

## 7. Accepted solution (production, `cloud_atmosphere_volume.fsh`)

* `paStormFieldUncoveredDensity(x, z, k)`: manual bilinear over the four nodes (`texelFetch`), a covered node
  (`R > -5`) contributing 0.
* `paStormFieldLightDensityAt(p, detailTap, exactUncovered)`: on an uncovered node returns -1 when
  `exactUncovered`, else the covered-excluding bilinear on the two bracketing slices, linear in Y.
* Light march: `exactUncovered = (i == 0)`; the field-off path and the exact tap share ONE `cloudDensity` call
  (`density < 0` -> exact). Storm (covered) taps are unchanged; the T198 audit path passes `false`.
* Test: the T198 sandbox's source check now names the new signature (same invariant).

## 8. Visual metrics

`t247-metrics.txt`. Production A vs T247 B (lean FINAL galleries, sessions `t247ga` / `t247gb`, sheet
`t247_ab`): the dark panels / boxes / chips are gone on nimbostratus (side / close / underside), stratocumulus
(side / below / close), humilis close, mediocris side, congestus close, stratus below; cirrus unchanged in
character. Same-instance arms (`t247b_zoom`): L4 is visually identical to the exact reference.

## 9. Motion

Sequence `motion0..5`: the `close` pose stepped 4 blocks sideways five times (two 8-block weather-texel snaps),
frames registered, `p99 |dL|` inside the cloud per step (`t247_session.py`):

| family | production max p99 | T247 max p99 |
|---|---|---|
| stratocumulus | 107.3 | 14.9 |
| stratocumulus i2 | 97.4 | 44.0 |
| nimbostratus | 44.4 | 34.0 |
| nimbostratus i2 | 59.2 | 22.6 |
| humilis | 40.8 (jumps from 24 to 40 at the snap) | 23.6 (flat) |
| cirrus | 55.8 | 12.5 |
| stratus | 9.4 | 7.6 |

Production's field-lit panels pop when the origin snaps; T247's first tap is exact in world space and the field
taps no longer carry the clearance contamination, so the sequences are flat. No crawl or swim was seen.

## 10. Performance (build against build)

Storm benchmark, `t208` marker (lean FINAL, 0.25 phase scale, ULTRA, 96 steps, 1920x1080); A = `eae18293`
volume shader, only that file differs. Log `t247-timing.log`.

| build | SIDE total (ms) | SIDE raw p50 | runs (SIDE primary steps) |
|---|---|---|---|
| A (HEAD) | 5.454 | 3.71 | 4 runs at 4.63-4.67 M (A tc2 5.71 ms at 5.24 M excluded: different workload) |
| L4, two call sites | 5.699 | 3.85 | 2 runs at 4.62-4.64 M (+0.25 ms) |
| **L4m (production)** | **5.547** | **3.75** | 2 runs at 4.62-4.65 M (**+0.09 ms**) |

PLAY_VIS_NEAR at matched workload (~1.57 M steps): A 4.45-4.49 ms, L4m 4.61 ms (+0.13 ms; the view holds
non-storm cloud). The L0-only build ran a different SIDE workload (3.99-4.02 M steps) in every run and is not
comparable.

Non-storm scenes (lean FINAL galleries, mean cloud-pass gpuMs per family; different realizations per session, so
indicative): production / T247 - stratus 1.25 / 1.36, stratocumulus 1.27 / 1.42, humilis 2.29 / 2.48,
nimbostratus 2.05 / 2.48, mediocris 3.42 / 3.81, cirrus 3.81 / 4.16, congestus 3.76 / 4.34 ms: +0.1..0.6 ms where
the renderer is at 1.3..4.3 ms, far below the storm scenes' 5.5 ms and the 10 ms ceiling. Registers /
instructions: no offline NVIDIA compiler this session (venv lost); the sandbox compiles every lean variant.

## 11. Production validation

`campaignGate` 0, `check` 0 (includes the geometry sandbox's shader compiles of FINAL and the field variants),
`stormMorphologySandbox` 0 (`gate-t247.log`, `check-build-t247.log`, `sandbox-t247.log`). Final production
gallery (`validation/t246-final-gallery`): nimbostratus, stratocumulus, humilis, mediocris, congestus, stratus and
cirrus re-captured with T247 (session `t247gb`); storms unchanged.

## 12. Remaining limitations

* Nimbostratus keeps a little residual error against exact (rel 0.37-0.52, partly noise: the identical arm sits
  at 0.85-1.0) - not visible as panels.
* The non-storm cost (+0.1..0.6 ms) is measured across different realizations; a fixed-seed non-storm benchmark
  pose would pin it.
* The 16-block field still shifts by half a node on alternate weather-origin snaps; with the first tap exact it no
  longer shows, but snapping the field origin to 16 blocks would remove the cause.
