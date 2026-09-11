# T196 - the shared field is built, the probe floor is sound, and SIDE nets 2.06x

Branch `experiment/cloud-descriptor-k`, base `3b2f696` (T195) merged with
`origin/Forge-1.20.1` `f5272e6`. Nothing merged. Six launches on 11Sep2026 of the
same 31-arm matrix, Ultra, raw scale 0.2500, cloud target 480x270, framebuffer
1920x1080, 96 steps, 60 samples per cell, three anchor-bracketed blocks per arm
per pose; the first five calibrated the probe floor against the soundness
oracle, the sixth is the evidence. Every launch was about six minutes end to
end and exited itself.

## 1. What was built

One shared coarse 3D storm field, 256x256x32 nodes over `WeatherExtent` (16
blocks a node horizontally) and the slab (about 27 blocks a node on this
fixture), stored as an 8x4 atlas of 256x256 tiles in one 2048x1024 **RG16F**
render target - four bytes a node, **8.4 MB** - built by one fullscreen draw
before the timed march, on the field clock, and bound on the rain-field texture
unit. Nothing is read back and nothing is built on the CPU.

| Channel | Value | Consumer | Read |
|---|---|---|---|
| R | detail-free production `cloudDensity` at the node | light cone, all four taps | bilinear in the tile, blended across the two bracketing slices |
| G | the probe floor (section 2) | empty-span probe | one `texelFetch` of the nearest node, then one base-noise fetch at the probe point |

Both channels come out of **one descriptor walk per node**: `cloudDensity`
already walks the union once, and the walk now publishes its union distance and
softness range, from which the floor is formed after the density returns.

The light cone is otherwise unchanged - four taps, the same weights, spacing and
early-out (T178 closed fewer taps). The probe loop is unchanged in structure:
the field's verdict replaces the density test, and the scan advances or falls to
a fine step exactly as before.

## 2. The probe bound (Task 1) - derivation

For a descriptor-owned storm, with `E = 1 - smoothstep(-soft, soft, d)` the
strength-free envelope of the smooth-union distance `d`:

    density > 0  <=>  stormBody > 0  <=>  baseField(p) > L(p)
    L = 1 - coverage * (1 + fill),   coverage = E * strength

`strength * (1 + fill(strength))` is bounded: `fill` grows as `strength` falls
(`1/(0.717 s) - 0.979`, floored at 0.45), and the product stays at most 1.45
without convective overlap and at most 1.65 with it (`stormCoreFillForCoverageAndStrength`
peaks near coverage 0.9). So everywhere:

    L(p) >= 1 - 1.65 * E(p)

`E` falls with `d`, and `d` at any point of a node's dual cell is at least the
node's `d` minus the largest drop `d` can take across the cell. Hence the
**floor** stored at the node:

    floor = 1 - 1.65 * (1 - smoothstep(-soft, soft, d_node - dilation))

with `soft` the largest softness among the groups that entered (the value that
maximises `E` outside the surface; the smallest inside), and the probe's test
`baseField(p) > floor`, `baseField` evaluated exactly at the probe point with
one base-noise fetch. `floor = 1` means provably empty; `-10` marks a node the
field does not cover (weather-map families in the 3x3 texels around it, or any
funnel), where the probe assumes material. Erosion is subtractive and the
material terms are positive scalings, so neither can make production positive
where `stormBody` is zero; the density threshold 0.0008 is treated as 0, which
is conservative.

**The dilation is what the oracle had to teach.** The lobe pseudo-distance is
`(radial - 1) * effectiveRadius`, about `|o| - R(h)`: horizontally a
gradient-normalised ellipse distance with constant bounded by the ellipse
aspect, but vertically its drop per block is `|dR/dy|` - the role profile's
height derivative times the lobe's radius over its height - which reaches
**8.6 blocks per block** on this fixture's anvil flare, measured. So:

    dilation = (halfXZ * sqrt2 * maxAspect + halfY * maxVerticalSlope) * 1.2

where both bounds are published per node by the lobes the walk visited; the
vertical slope is the profile's finite difference over the cell's own height
band (widened 1.5x for curvature), not the profile's global maximum, because
the anvil is flat above its flare and a global 3.5 dilated every anvil node by
130 blocks. Two further terms keep the node's walk from missing what a cell
point sees: T121's per-lobe vertical rejection is judged with the cell's
vertical half-extent as margin in build mode, and the T143 reach early-out is
lifted for the build (the walk far from the storm is cheap: its candidate tiles
are empty).

A node's dual cell is exactly its candidate tile - both grids share
`WeatherOrigin` and 16-block spacing - so a probe anywhere in the cell walks
the tile the node walked; no neighbouring tiles are needed.

Cost against the brief's rejected 2x2x2: **one walk per node** for both
channels, against eight. `stormFieldNodeShapeCallsPerPixel=1.0000`,
1.46 exact SDFs per node at SIDE, 1.00 at FAR.

## 3. Soundness oracle (Task 2)

Pass 2 of the same shader writes, for every node, the floor beside a dense
reference: production's exact threshold `L` (its own walk at each point -
reach early-out, own tile, own vertical rejection, ownership) at 27 lattice
points of the dual cell (corners, edge and face centres, centre, strictly
inside) plus 16 hashed points, 43 per node, **2,097,152 nodes per pose**. The
CPU reads the pair back once and counts every node whose floor exceeds its
reference minimum.

| Launch | change | underestimates SIDE / FAR | binary misses | useful empty retained |
|---|---|---|---|---|
| 1 | isotropic dilation 1.25 x half-diagonal | 11,667 / 7,667 | - | 99.4% |
| 2 | global axis constants (1.25, 4.0), reference unions tiles | 5,976 / 3,340 | - | 98.5% |
| 3 | + vertical rejection margin | 779 / 332 | - | 97.8% |
| 4 | per-lobe bounds, global profile slope; measured L_y max 8.30 | 0 / 0 | 0 | 97.8% / 98.5% |
| 5 | cell-local profile slope | 0 / 0 | 0 | 98.5% / 99.1% |
| **6** | single tile, lattice strictly inside | **0 / 0** | **0** | **98.5% / 99.0%** |

Launch 6 at SIDE: 2,097,152 nodes, 21,366 boundary nodes (cells with both
material and empty reference samples), **underestimates 0, worst 0.00000**,
`fieldEmpty` 1,887,250 of `referenceEmpty` 1,915,794 retained - the bound is
not vacuous. Overestimate over the 181,358 nodes where material is possible:
mean 0.30 floor units, histogram by 0.1: 19206, 14121, 97941, 17442, 14452,
3336, 2427, 12433 (the last bucket is 0.7+). Launches 2-3 had 1e9-slack
"misses" that were an artefact: the reference sampled the cell boundary,
which belongs to the neighbouring node; the reference now samples strictly
inside and is exactly production's walk.

**On the ray**, the monolith audit (view 67 only, so no other counter sees the
production evaluation it makes beside the field): SIDE
`stormFieldProbeFalseEmpty=0`, false-occupied 69,245, agree-empty 373,569,
agree-occupied 11,498; FAR false-empty 0, false-occupied 20,125, agree-empty
102,415, agree-occupied 2,305. The field says empty 82% of the time and is
right every time; where it says possible it is right 14% of the time - the
price of the bound, paid in fine steps (primary shape calls 432k -> 759k at
SIDE).

## 4. Field build (Task 4)

| Pose | build p50 | p95 | nodes | walks/node | exact SDF/node | VRAM |
|---|---|---|---|---|---|---|
| SIDE | **1.77 ms** | 2.14 | 2,097,152 | 1.00 | 1.46 | 8.4 MB |
| FAR | 1.65 ms | 1.85 | 2,097,152 | 1.00 | 1.00 | 8.4 MB |

Twice T195's 0.84 ms projection: the build walks every node without the reach
early-out and with the vertical margin (fewer T121 rejections, more exact
SDFs), and reads nine weather and morphology texels per node for the coverage
test. Launch 1 had 3.5 ms with two walks per node; launch 4-5 had 2.5 ms with
the four-tile candidate union, which turned out unnecessary. Every frame while
a storm moves (T195 Task 8); nothing rebuilds on a clock.

## 5. Light lookup (Task 5), SIDE unless noted

| | production | field |
|---|---|---|
| light descriptor walks (`shapeLight`) | 1,181,752 (9.12 / px) | **0** |
| field fetch pairs (`stormFieldLightFetches`) | 0 | 1,029,340 (7.94 / px) |
| light-only march ratio / net | - | 1.2239 / **1.1395** (2 blocks accepted) |
| FAR | 211,768 -> 0 | 1.1531 / 1.0456 |

Quality, SIDE light alone: cloud SSIM 0.945, edge SSIM 0.986, silhouette IoU
1.000, thin and hole retention 1.000, **dark-interior retention 0.956**,
**self-shadow contrast ratio 0.938**, **shadow-pocket retention 0.704**, valley
depth 0.888, changed pixels 15,152 of 129,600, maxAbs 3.77, RMS 1.17. FAR:
SSIM 0.949, dark-interior 0.948, pockets 0.781, valley 0.845.

## 6. Probe lookup (Task 6), SIDE unless noted

| | production | field |
|---|---|---|
| probe descriptor walks (`shapeProbe`) | 745,951 (5.76 / px) | **0** |
| field fetches (`stormFieldProbeFetches`) | 0 | 437,197 (3.37 / px), plus one noise fetch each |
| provably-empty verdicts | - | 29,782 |
| uncovered fallbacks (outside slab) | - | 17,083 |
| false-empty / false-occupied | - | **0** / 69,245 |
| scan events / primary steps | 64,135 / 3,897,076 | 98,581 / 4,204,980 |
| probe-only march ratio / net | - | 1.1790 / 1.1004 (one block; 1.2001 / 1.0642 in launch 5 on three) |
| FAR probe-only | 209,613 -> 0 | 1.1672 / 1.0578 |

Quality, SIDE probe alone: SSIM 0.966, silhouette IoU 0.992, alpha mass 0.991,
thin retention 0.957 / 0.988, hole retention 0.647 / 0.927, seam 0.006,
changed pixels 804 (0.6%). The probe never skips material production finds;
the differences are sample placement - a "possible" verdict enters fine mode
where production advanced, and the later lattice shifts.

## 7. Combined (Task 8) and the stack (Task 12)

FINAL already carries every quality-approved optimisation (T163, T174), so the
control is FINAL and the stack is FINAL plus the field, both consumers.

| Pose | control p50 / p95 | field build | stack march p50 / p95 | **net** | spread | blocks |
|---|---|---|---|---|---|---|
| SIDE | 29.42 / 31.73 | 1.61 | 12.69 / 13.94 | **2.0570x** | 0.11% | 3 accepted |
| FAR | 18.66 / 20.21 | 1.51 | 10.53 / 10.80 | **1.5489x** | 1.22% | 3 accepted |

Stack frame at SIDE: 12.69 + 1.61 = **14.3 ms** against 29.4; at FAR 10.53 +
1.51 = **12.0 ms** against 18.7. Neither meets SIDE <= 10 ms or FAR <= 8 ms on
this session's clocks - the same fixture read 24.7 ms at SIDE two launches
earlier and 33.2 in the morning - so the absolute targets stay session-local
and the relative gain is the evidence, as the brief says.

**Against T195's 1.731x expectation, SIDE returns 2.057x.** The combined arm
beats the T195 removal ceiling (1.8595x) because the ceiling arm lost the
scan's ability to skip empty spans (no probe means a fine step) while the
field probe keeps it at one fetch; fewer primary steps also means fewer light
cones, so the two consumers are superadditive in the field just as they were
in the ceiling.

The hybrid (first two taps exact) costs half the light gain - 1.437 march,
**1.323x** net SIDE - and does not restore the pockets (0.723 against 0.704)
while losing dark-interior retention (0.899): the pocket loss comes from the
far taps' envelope edge being smeared over a 16-block node, not from the
detail taps. Closed.

## 8. Workload proof (Task 9), SIDE, single frame, 129,600 pixels

| counter | production | field | |
|---|---|---|---|
| `directStormShapeCalls` | 4,584,518 | 3,364,350 | -26.6% |
| `lobeExactSdf` | 28,443,973 | 15,581,572 | **-45.2%** |
| `descriptorGroupsEntered` | 3,699,357 | 2,479,290 | -33.0% |
| `shapeLight` | 1,181,752 | **0** | |
| `shapeProbe` | 745,951 | **0** | |
| `shapePrimary` | 431,753 | 759,065 | +75.8% (fine steps on false-occupied) |
| `shapeRefine` | 157,063 | 191,287 | |
| `shapeRainSegment` | 2,026,677 | 2,276,891 | |
| `shapeRainShaft` | 41,987 | 137,381 | |
| `shapeUntagged` | **0** | **0** | |
| accounting residual | 841 | 274 | of 4.6M / 3.4M |
| field fetches | 0 | 1,029,340 light + 437,197 probe | |

**The new largest consumer is the rain segment** at 2.28M of 3.36M shape calls
(67.7%) - the class T182 and T187-T192 could not move - followed by the primary
march at 22.6%, which the probe's conservativeness grew. The next architecture
if more is needed is therefore not a consumer removal but a tighter probe floor
(section 10), which pays back primary steps directly.

## 9. Quality verdict (Task 7) - not from SSIM alone

| metric | light | probe | both | light3 (T178, rejected) |
|---|---|---|---|---|
| cloud SSIM | 0.945 | 0.966 | 0.912 | 0.884 |
| edge SSIM | 0.986 | 0.975 | 0.960 | |
| silhouette IoU | 1.000 | 0.992 | 0.992 | 1.000 |
| dark-interior retention | **0.956** | 1.000 | 0.956 | **0.072** |
| self-shadow contrast | 0.938 | 1.122 | 1.065 | 1.018 |
| shadow-pocket retention | **0.704** | 0.962 | 0.680 | 0.663 |
| valley depth | 0.888 | 1.076 | 0.964 | 1.21 |
| thin retention | 1.000 | 0.957 / 0.988 | 0.957 / 0.988 | 1.000 |
| hole retention | 1.000 | 0.647 / 0.927 | 0.647 / 0.927 | 1.000 |
| changed pixels | 15,152 | 804 | 15,644 | |

The light channel keeps the two properties whose loss rejected light3 -
absolute interior darkness and self-shadow contrast - and loses a third of the
shadow pockets and a tenth of valley depth, which is what a 16-block node does
to an envelope edge. The probe channel is near-lossless in luminance and moves
the silhouette by 0.8%. **Verdict: performance and soundness pass; light
quality is a conditional pass with the pocket metric below what T178 accepted,
and the resolution question (Task 10) is now a quality question, not an
economics one.** Puff separation remains unimplemented in the harness.

## 10. What is next (Tasks 10, 11)

1. **XZ resolution for the pockets.** The economics now allow it: at 512x512x32
   the build scales to about 7 ms and the SIDE net would fall to roughly
   29.4 / (12.7 + 7.1) = 1.48x, still the top band, if the pocket metric
   recovers. Y stays at 32: the vertical dilation is per node and the pockets
   are a horizontal-edge effect. Measure 384x384x32 and 512x512x32 with the
   same matrix and oracle before choosing.
2. **A tighter floor.** 86% of the field's "possible" verdicts are false and
   they cost 327k primary shape calls at SIDE. Two cheap improvements are
   available and neither touches the soundness argument: use the node's own
   overlap class instead of the universal 1.65 gain, and drop the 1.2 headroom
   on the horizontal term where the measured aspect is 1.
3. **Dirty-state reuse (Task 11).** The fixture freezes storm motion, so a
   dirty-state rebuild would have skipped the build on every measured frame and
   hidden its cost; it was deliberately not enabled. In production the
   descriptor upload, the weather bake and `MaterialOffset` all refresh every
   frame while a storm moves, so the rebuild is every frame in motion and zero
   at rest. Implement as a hash of those three, measure on a moving fixture.
4. **Production wiring.** The field is still campaign-scoped: FINAL bakes
   `PaStormFieldPass` and `PaStormFieldEnabled` to zero and the sandbox proves
   it. Promotion means a FINAL variant with the pass live and both consumers
   baked on, plus the dev-client `leanFinalEligible` contract.

## 11. Infrastructure

- `T191_VARIANT_SCOPE declared=152 activeCampaigns=T196 registered=5
  skipped=147 crossCampaignControls=6 productionAlwaysLoaded=2`. Five
  variants added (147 -> 152), all campaign-scoped; FINAL unchanged.
- Three workload views added (67 probe audit, 68 node build cost, 69 fetches),
  `T175_WORKLOAD_VIEWS declared=47|allEnabled=true`; the `StormField` record
  joins `WorkloadResult`; `T182` consumer tags unchanged at 9, untagged 0.
- The T196 sandbox invariant compiles all five field programs in a GL context
  and proves FINAL bakes the field out and the sampler is a manual binding.
- Forge-1.20.1 `f5272e6` made Simple Clouds a mandatory dependency; the dev
  client has it `compileOnly`, so FML rejected the mod before its mixin config
  could be read. `mods.toml` now expands `simpleclouds_mandatory`, `true` for
  the release jar and `-PsimpleCloudsMandatory=false` for a campaign launch, so
  the dev client boots as every campaign since T166 has.
- `PaStormFieldPass` and `PaStormFieldEnabled` are re-uploaded every frame:
  launch 4's FAR production capture had kept the previous forced frame's
  lookups enabled on the monolith against no field at all.
- Campaign gate 23-49 s (`campaignGate`); full `check build` in section 12.
- Launch timeline: 14:32:03 Gradle start, 14:38:06 client self-exit, 62 cells,
  two soundness oracles, six image captures.

## 12. Validation

- `./gradlew.bat campaignGate` PASS (`gate-t196-campaign.log`).
- `./gradlew.bat check build` PASS (`gate-t196.log`).
- `T196_REJECTED count=0`; every `T196_ARM` of launch 6 accepted except
  `t196_field_probe` at SIDE (one block after two anchor-drift rejections; its
  launch-5 value on three accepted blocks was 1.2001 / 1.0642).
- Markers: `run/t196-shared-field.txt` retired; `run/t132-autorun.txt` and
  `run/t132-autorun-exit.txt` remain, as harness machinery.

## 13. Status

Not merged. T194 `47fd756`, T195 `c77308c`, merge `373c35c`, infra `3b2f696`.
