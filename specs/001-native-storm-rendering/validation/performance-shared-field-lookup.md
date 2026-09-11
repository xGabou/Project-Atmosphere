# T195 - the lookup is measured, the SIDE ceiling is established, and the gate holds

Branch `experiment/cloud-descriptor-k`, base `21758ee` (T194 `47fd756` merged with
`origin/Forge-1.20.1` `8e22da4`). Nothing merged. One campaign 11Sep2026, Ultra, raw
scale 0.2500, cloud target 480x270, framebuffer 1920x1080, 96 steps, 60 samples
per cell, **four** anchor-bracketed blocks per arm per pose. **Pricing only: no
field was built and nothing was wired into the renderer.**

This is the continuation of T194. It exists because T194's own report left four
things open that the implementation gate depends on, and this task's brief was
to recover T194 and close them rather than start again:

1. the SIDE combined ceiling was established on **one** accepted block;
2. the lookup cost was **estimated**, not measured, and the estimate used a
   per-pixel fetch count that does not match the production counters;
3. the update-frequency dependency was not audited in code;
4. whether the probe's max channel can be a bound at one evaluation per voxel
   was not addressed.

## 1. Recovery

Working tree at start: clean at `47fd756` (T194). T194 had committed its four
pricing variants, the driver matrix, the registry row and its evidence document,
but had **not** added a `tasks.md` entry, had left its marker
`run/t194-shared-field-pricing.txt` armed, had no `gate-t194.log`, and had no
`validation/t194-campaign/` evidence directory. No process from the previous
attempt was alive. `runClient-t194.out.log` holds the full campaign; every number
in T194's report was re-checked against its `T194_BLOCK`/`T194_ARM` lines and
matches.

T194's build oracle was re-inspected before being trusted:

- `RAIN_FIELD_TIMER` brackets only `FullscreenQuad.draw` of the field pass;
  `shader.apply()` and descriptor upload precede `begin()`. No compile or link
  is inside the timer.
- The field target is `profile.weatherMapSize()` = 512 on Ultra, so the slice
  arms perform exactly 512x512xN evaluations.
- The three slice arms' march ratios are 1.0105 / 1.0120 / 1.0110 (SIDE) while
  their field clocks read 1.68 / 3.27 / 6.47 ms: the build is not hidden in the
  frame time, and the control is not charged for it.
- The slice arms are `rainFieldGeneration()` programs and nothing reads their
  output; they are campaign-scoped and absent from every ordinary startup.

The T194 marker was retired (campaign complete, evidence banked) and the T195
marker armed in its place; no two campaign markers were ever armed together.

## 2. Task 5 - the combined ceiling, established at SIDE

| Pose | blocks | ratio | spread | verdict | anchor | arm | removable |
|---|---|---|---|---|---|---|---|
| SIDE | 4 | **1.8595** | **1.02%** | **accepted** | 30.36 | 16.33 | **14.04 ms** |
| FAR | 4 | 1.6353 | 7.00% | REJECTED_ratio_spread | 19.03 | 11.64 | 7.39 ms |

**SIDE is now established to protocol** - four blocks, every anchor drift
between 1.0% and 2.2%, ratio spread 1.02%. T194's one accepted block read
1.9981 and its two drift-rejected blocks 1.9351 and 1.9518; today's session runs
about 11% faster on the anchors (30.4 ms against 34.1) and gives a lower ratio.
The bound this task carries forward is the accepted one, **1.8595x**, and it is
the lower of the two sessions, which is the safe side.

FAR did not accept today: the four arm cells wandered from 11.26 to 11.96 ms
while their anchors held to 1.9%, so the spread is in the arm, not the anchor.
T194's FAR ceiling, **1.7450 accepted at 2.92%**, stands as the FAR figure;
today's rejected 1.6353 is reported beside it and used below as the conservative
FAR input where it matters.

## 3. Task 7 - the lookup, measured

`t195_lookup` removes light and probe traversal exactly as `t194_noboth` does
and then pays what a field would pay on the ray: one trilinear fetch per light
tap from a stand-in 8 MB volume, feeding the **production scatter chain**, and
13 fetches per empty-span scan event on the scan lattice (production averages
11.3 at SIDE and 12.5 at FAR). The stand-in is the resident 128^3 RGBA8 base
noise, addressed over `WeatherExtent` and the slab - the footprint and access
pattern of the 256x256x32 RG16F candidate, not its contents. The image is
invalid by construction (cloud SSIM 0.72 SIDE / 0.52 FAR), which is the point:
this prices the fetches, not a proposal.

| Pose | blocks | ratio | spread | verdict | arm p50 | vs `noboth` | **lookup + scatter** |
|---|---|---|---|---|---|---|---|
| SIDE | 4 | **1.8178** | **1.94%** | **accepted** | 16.74 | 16.33 | **0.41 ms** |
| FAR | 4 | 1.5957 | 5.09% | REJECTED_ratio_spread | 11.92 | 11.64 | 0.27 ms |

**Reading the field back costs 0.41 ms at SIDE - 2.9% of the 14.04 ms it
removes.** That figure includes the scatter chain that the no-light ceiling
had removed and a field cannot: the ceiling was generous by that term, and the
lookup arm corrects it. Fetch count on the SIDE frame: 14.77 light + ~9.1 probe
stand-ins per pixel (13 per scan event, 0.70 events per pixel), 3.1M fetches
in 0.41 ms - about 0.13 ns per fetch, throughput-bound rather than
bandwidth-bound at this footprint. T170's "descriptor fetches are cheap" did
not need to transfer; the volume fetch was measured on its own.

**What the stand-in does not price**: `NEAREST` filtering for the probe channel
(cheaper than the trilinear used here), and the cache behaviour of a real
256x256x32 layout against the 128^3 stand-in - the same bytes, a different
shape. Both are second-order against a 0.41 ms term with a 13.7 ms margin.


## 4. Task 8 - update frequency, audited in code

What the light cone (taps 3-4) and a detail-free probe consume is
`cloudDensity(p, mipBias, useDetail=false, nearCamera, includePrecipitation=false)`,
whose value depends on:

| Term | Source | Changes |
|---|---|---|
| `sampleWeather`, `sampleMorphology` | WeatherMap / MorphologyMap | re-rendered **every frame** by `CloudWeatherMapRenderer.render()` from `VolumetricCloudRenderHook`, camera-snapped origin |
| `directStormShape` | descriptor texture | refreshed every frame by `StormGeometryBuildCoordinator.update()`; positions drift with the cells |
| `samplePos = p - MaterialOffset` | CPU-integrated advection of UUID-matched cell positions (`VolumetricMaterialAdvectionTracker`) | every frame the cells move |
| `SlabBaseY/SlabTopY`, `WindVec` | per-frame uniforms | slowly |
| `WorldTime` | **only** `funnelDensityAt` (tornado swirl), the funnel noise union, and `rainShaftDensityAt` | never reaches the light cone (`rainFraction` gate, `PA_PRECIPITATION_ABSENT`) or a precipitation-free probe |

There is **no animation phase** in `baseNoiseDomain`, `stormBaseNoiseDomain`,
`lowFrequencyDomainWarp` or `detailNoiseDomain`. The body noise is a static
function of `p - MaterialOffset`; the "animation" the player sees is the whole
material domain translating with the cells.

**Answer:** the field need not rebuild on a clock, only on dirty state - a change
in the descriptor generation, the weather bake, `MaterialOffset` or the domain
origin. In practice every one of those changes on every frame in which a storm
moves, so dirty-state rebuild degenerates to every-frame rebuild while a storm is
in motion. That is fine: the every-frame build cost is what the break-even below
already charges, and no every-N-frame lag is introduced or needed. A static or
frozen scene rebuilds nothing.

## 5. Tasks 1-2 - semantics, re-verified against the shader

The T194 audit was checked line by line and holds:

- Light: `detailTap = i < 2`, `mipBias = float(i) * 0.6`, four taps at 14, 20,
  28, 40 blocks (`stepLength *= 1.42`), `opticalDepth += density * stepLength *
  tapWeight`. Taps 3 and 4 already read a detail-free, mip-biased density. The
  consumer integrates, so it needs an **unbiased estimate**. The optical depth
  then feeds `evaluateLightingFromOpticalDepth` - the scatter chain - which a
  field does **not** remove.
- Probe: `cloudDensity(..., DetailQuality > 0, ...) > 0.0008`, one-sided. T180
  proved the detail-free density is an upper bound on the detailed one for
  descriptor storms (`cloud = max(cloud - (1 - fbm) * STORM_EROSION, 0)` is
  subtractive), so a detail-free build is conservative at the **sampled point**.
- **What the doc did not say**: a single evaluation per voxel is a point sample,
  not a bound over the voxel. For the max channel to be a bound the build must
  either evaluate k sub-samples per voxel (priced below via T194's measured
  linear slope: 2x2x2 at 256x256x32 is exactly the 512x512x64 arm's voxel
  count, **6.47 ms measured**) or use a closed-form bound of the kind T192
  built for the rain field. The lookup for the probe must then be `NEAREST`
  (or a max over the 8 neighbours), because trilinear interpolation of maxima
  is not a bound either. This is a build-side correctness requirement, not a
  resolution one.
- Fetch counts, from the production counters captured beside the T194
  anchors (single-frame, 480x270 = 129,600 pixels): SIDE
  `lightConeTaps=1,914,680` (**14.77 / px**), `probeCalls=1,020,878`
  (**7.88 / px**), `scanEvents=90,208` (0.70 / px, **11.32 probes / scan**);
  FAR `lightConeTaps=268,608` (2.07 / px), `probeCalls=249,280` (1.92 / px),
  `scanEvents=20,016` (**12.45 probes / scan**). T194's "6.5 fetches per
  pixel" estimate was low by 3.5x at SIDE; it used T193's share-weighted
  figures, which are not per-pixel tap counts.

## 6. Task 9 - break-even, measured against derived

MEASURED: SIDE anchor 30.43 ms, SIDE lookup arm 16.74 ms, FAR anchor 19.01 ms,
FAR lookup arm 11.92 ms (T195, FAR to be read with its rejected spread); the
build slope 0.4014 ns/voxel SIDE and 0.3380 ns/voxel FAR (T194, linear over
4.2M-16.8M voxels).

DERIVED: every build time below is the measured slope times the voxel count;
the 2x2x2 max-channel column at 256x256x32 is 16.8M evaluations, which is the
512x512x64 arm's count and was **measured at 6.47 ms**.

Net = anchor / (lookup arm + build). Nothing estimated remains in the SIDE row.

| Grid | voxels | RG16F | build SIDE | **net SIDE, 1 eval/voxel** | net FAR, 1 eval | net SIDE, 2 Y-samples | net SIDE, 2x2x2 | net FAR, 2x2x2 |
|---|---|---|---|---|---|---|---|---|
| **256x256x32** | 2,097,152 | 8.4 MB | 0.84 ms | **1.731x** | 1.506x | 1.652x | 1.296x | 1.081x |
| 256x256x64 | 4,194,304 | 16.8 MB | 1.68 ms | 1.652x | 1.426x | 1.501x | 1.007x | 0.817x |
| 384x384x32 | 4,718,592 | 18.9 MB | 1.89 ms | 1.633x | 1.407x | 1.473x | 0.954x | 0.770x |
| 512x512x32 | 8,388,608 | 33.6 MB | 3.37 ms | 1.513x | 1.289x | 1.296x | 0.697x | 0.549x |
| 512x512x64 | 16,777,216 | 67.1 MB | 6.73 ms | 1.296x | 1.081x | 1.007x | 0.431x | 0.332x |

Break-even voxel count at 1.10x, one evaluation per voxel: **27.2M at SIDE,
15.9M at FAR** (T194 had 34.7M / 20.3M before the lookup and today's lower
ceiling).

**The max channel is where the economics can turn.** At one evaluation per
voxel the smallest grid clears the bar by 0.63 at SIDE and 0.41 at FAR. If the
probe's bound needs a full 2x2x2 super-sample the same grid falls to 1.30x at
SIDE and to **1.08x at FAR - below the 1.10x bar** - and every larger grid
closes. Two samples along Y (the coarse axis, 27 blocks per voxel) costs one
extra build and keeps 1.65x / 1.43x. So the bound has to come from a
closed-form term or a cheap directional super-sample, not from brute force;
this is the same shape as T192's rain-field lesson and is the first thing the
implementation has to decide.

## 7. Task 10 - implementation gate

| Requirement | Result |
|---|---|
| combined light+probe ceiling large enough | **1.8595x SIDE accepted (4 blocks, 1.02%)**; 1.7450x FAR accepted (T194) |
| field build cost measured | 0.4014 ns/voxel SIDE, linear 4.2M-16.8M voxels (T194) |
| lookup cost bounded/measured | **0.41 ms SIDE, 0.27 ms FAR, measured** |
| expected net SIDE gain >= 1.10x | **1.731x** at 256x256x32, one evaluation per voxel; 1.296x even at 2x2x2 |
| VRAM reasonable | 8.4 MB |
| one representation serves both consumers | yes - `(mean, max)` RG16F, with the max channel's bound to be established build-side |

**GATE: PASS**, on measured values at SIDE. The single condition that could
still fail it is the cost of a sound max channel, and that is bounded above by
the measured 2x2x2 column (1.296x SIDE), which passes.

## 8. Tasks 11-12 - existing implementation, quality

No real field implementation exists; T194 built only the pricing oracle and
this task built only the lookup stand-in, both campaign-scoped and absent from
production. Nothing to validate or discard. The quality harness (T178's
dark-interior and shadow-pocket retention for light; false-empty / missed-material
/ thin retention for the probe) is not built here: this task's brief was to
finish the price, and it is finished.

**Next implementation step, in order:**

1. **Decide the max channel's bound first**, because it is the only term that
   can still close the architecture: a closed-form per-voxel upper bound from
   the descriptor union (T192 has the ownership-bound machinery) or a 2-sample
   Y super-sample. Price whichever is chosen with the existing slice oracle -
   one more variant, no new infrastructure.
2. **Build the quality harness before the field**: T178's light metrics exist;
   the probe needs a soundness check that counts probe decisions where the
   field says empty and production says `> 0.0008`.
3. **Implement at 256x256x32 as `(mean, max)` RG16F**, generated by the same
   fullscreen-pass shape as the oracle into a layered target, rebuilt on dirty
   state (which is every frame while the storm moves), read by the light cone
   with trilinear and by the probe with `NEAREST`.
4. Measure, then move resolution only where quality demands it.

**If it fails**: light alone is accepted at both poses (T193, 1.2490x SIDE)
and separates cleanly.

## 9. Validation

- `T195_REJECTED count=0`.
- `T191_VARIANT_SCOPE declared=147 activeCampaigns=T195 registered=2 skipped=145
  crossCampaignControls=3 productionAlwaysLoaded=2` - ordinary startup remains
  two programs; T195 loads its own arm plus `t194_noboth` as the cross-campaign
  control.
- One variant added, `cloud_atmosphere_volume_t195_lookup` (147 -> 148),
  pricing-only and campaign-scoped. It keeps `PaDiagnosticEvalEpsilon` live so
  the probe fetch sink cannot be folded; every other program still bakes it.
- Campaign infrastructure, from this run's own timeline: the T194 client sat
  idle for 30 of its 45 minutes after `T132_AUTORUN_FINISHED`, and this run's
  for 22. Three cuts landed with this task and take effect from the next
  launch: the client stops itself when `run/t132-autorun-exit.txt` is present;
  program-arm campaigns finish after their report instead of running the
  unused 3.5-minute T098 capture set; and the 44-stage production-context
  workload capture, 7-9 s of every 11 s cell and identical for every cell of a
  pose, is taken once per pose. Expected campaign time for a T194-sized
  matrix: about 6 minutes of a 15-minute launch, against 45 today.
- Gate: see `gate-t195.log`.

## 10. Status

Not merged. T190 `1deb75f`, T191 `63e3cf0`, T192 `45801cd`, T193 `26ee5f0`,
T194 `47fd756`, merge with `origin/Forge-1.20.1` `21758ee`.
