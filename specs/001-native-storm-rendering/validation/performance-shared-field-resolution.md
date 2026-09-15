# T197 - XZ resolution does not recover the shadow pockets; the field stays 256

Branch `experiment/cloud-descriptor-k`, base `69c4914` (T196). Nothing merged. One
launch on 11Sep2026 (15:14 Gradle start, 15:20:30 client self-exit) of a 19-arm
matrix at both poses, Ultra, raw scale 0.2500, cloud target 480x270, framebuffer
1920x1080, 96 steps, 60 samples per cell, three anchor-bracketed blocks per
resolution per pose; six soundness oracles, twelve quality captures, eight
counter captures. The campaign took 2 min 19 s inside the client.

## 1. The question and the answer

T196 lost a third of the reference's shadow pockets (retention 0.704 at SIDE)
and attributed the loss to the 16-block node smearing the storm envelope's
edge, because the hybrid arm (near taps exact) had not restored them. T197
asks whether a finer horizontal grid recovers them and what it costs.

**It does not.** At SIDE, the pose that binds the budget, the light channel
keeps 0.705 / 0.684 / 0.698 of the pockets at 256 / 384 / 512 nodes - the
same number at 16, 10.7 and 8 blocks a node, within the scatter of a single
capture. The pocket diagnostic (section 5) shows why: the pockets are not
smeared shut, they are flattened. The arm is darker than the reference all
around each pocket by the same amount at every resolution, the wells are
12-14% shallower at every resolution, and the walls sit the same +0.95 px
outward at every resolution. Nothing in the loss scales with the node. The
16-block-smearing hypothesis is falsified, the resolution line is stopped,
and **256x256x32 stays** - it is also the cheapest arm by a wide margin
(section 8): 512 costs 4x the build for the same pockets.

## 2. Parameterization (Task 1)

The shader's `const int PA_STORM_FIELD_XZ = 256` became `uniform int
PaStormFieldXZ`, kept live in the five T196 field programs and baked to 256 in
FINAL and every other variant. Everything derives from it in the shader -
node spacing `WeatherExtent / PaStormFieldXZ`, texel centres, atlas
coordinates (8x4 tiles of `PaStormFieldXZ` square, because Y stays 32), the
probe's `texelFetch` tile offset, the build pass's fragment-to-node mapping,
and the conservative dilation, whose horizontal term is `0.5 *
paStormFieldVoxelXZ()` - the resolution's own half-cell, not a constant. On
the Java side `StormFieldGeometry` is the single derivation (node count,
spacing, half-cell, atlas size, bytes, and whether cells nest in the 16-block
candidate tiles); `VolumetricCloudRenderTargets` allocates the RG16F atlas
from it and logs `T197_FIELD_ALLOC` with the bytes actually allocated;
`VolumetricCloudRenderer` uploads the resolution every frame beside the pass
and consumer bits. **No shader program was added**: `T191_VARIANT_SCOPE
declared=152 activeCampaigns=T197 registered=2 skipped=150
crossCampaignControls=3 productionAlwaysLoaded=2`.

The world extent is identical at every resolution (4096 blocks, `WeatherExtent`),
proven headless by the T197 sandbox invariant, which also checks the
derivation against the shader's formulae and records the nesting property.

| grid | blocks / node XZ | Y | nodes | atlas | bytes | nests in candidate tiles |
|---|---|---|---|---|---|---|
| **256** | 16.00 | 32 (27 blocks) | 2,097,152 | 2048x1024 | 8,388,608 (8.0 MiB) | yes |
| 384 | 10.67 | 32 | 4,718,592 | 3072x1536 | 18,874,368 (18.0 MiB) | **no** |
| 512 | 8.00 | 32 | 8,388,608 | 4096x2048 | 33,554,432 (32.0 MiB) | yes |

**The intermediate grid.** T196's single-tile soundness argument rests on the
node's dual cell being inside the candidate tile the node walked. That holds
when the 16-block tile is an integer multiple of the cell (256, 512) and fails
at 384, whose 10.67-block cells straddle tile boundaries. There is no square
grid between 256 and 512 for which it holds. 384 was measured anyway - it is
the intended intermediate and the oracle, not the argument, decides - and on
this fixture the oracle found no underestimate at 384 either (section 3). That
is evidence, not a proof; the argument still covers only 256 and 512. It did
not matter for the decision, since 384 recovers nothing.

## 3. Probe soundness (Task 3)

Same oracle as T196 (43 production-exact reference samples per node, CPU
readback, every node), run at every resolution at both poses in the same
frame as the post-field counter capture.

| pose | grid | nodes | boundary nodes | underestimates | useful empty retained | floor overestimate mean | lipschitz Y max |
|---|---|---|---|---|---|---|---|
| SIDE | 256 | 2,097,152 | 21,405 | **0** | 98.55% | 0.306 | 8.27 |
| SIDE | 384 | 4,718,592 | 42,114 | **0** | 98.60% | 0.298 | 8.27 |
| SIDE | 512 | 8,388,608 | 68,008 | **0** | 98.63% | 0.294 | 8.27 |
| FAR | 256 | 2,097,152 | 13,871 | **0** | 99.04% | 0.308 | 8.27 |
| FAR | 384 | 4,718,592 | 26,937 | **0** | 99.08% | 0.299 | 8.27 |
| FAR | 512 | 8,388,608 | 44,041 | **0** | 99.09% | 0.295 | 8.27 |

Binary misses 0 everywhere. On the ray (monolith audit, view 67):

| pose | grid | false-empty | false-occupied | agree-empty | agree-occupied | false-occupied fraction | provably-empty verdicts | primary shape calls | inflation over production |
|---|---|---|---|---|---|---|---|---|---|
| SIDE | 256 | **0** | 76,522 | 392,494 | 19,006 | 0.157 | 35,495 | 1,022,099 | +53.5% (666,021) |
| SIDE | 384 | **0** | 73,981 | 412,726 | 18,985 | 0.146 | 52,638 | 1,003,812 | +50.7% |
| SIDE | 512 | **0** | 73,188 | 422,482 | 18,873 | 0.142 | 62,273 | 994,794 | +49.4% |
| FAR | 256 | **0** | 20,876 | 104,623 | 2,492 | 0.163 | 8,269 | 201,651 | +97.6% (102,070) |
| FAR | 384 | **0** | 20,336 | 110,389 | 2,493 | 0.153 | 12,507 | 196,784 | +92.8% |
| FAR | 512 | **0** | 20,054 | 113,110 | 2,468 | 0.148 | 14,826 | 194,372 | +90.4% |

**The envelope tightens, but barely.** Halving the horizontal half-cell from 8
to 4 blocks moves useful-empty retention from 98.55% to 98.63% and the mean
overestimate from 0.306 to 0.294, and takes 4% off the false-occupied count.
The dilation is dominated by its vertical term - the slab's 13.5-block
half-slice times a cell-local slope that reaches 8.27 - which T197 held
constant. That is where the floor's slack is (section 10).

## 4. Light quality (Task 4), light consumer alone, against the LEAN_FINAL anchor

| pose | grid | **pockets** | dark interior | contrast | valley | cloud SSIM | edge SSIM | IoU | thin / hole | changed px | RMS | maxAbs |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| SIDE | 256 | **0.705** | 0.974 | 0.849 | 0.821 | 0.951 | 0.995 | 1.000 | 1.000 / 1.000 | 24,907 | 0.0124 | 0.378 |
| SIDE | 384 | **0.684** | 0.975 | 0.855 | 0.841 | 0.956 | 0.997 | 1.000 | 1.000 / 1.000 | 25,048 | 0.0118 | 0.313 |
| SIDE | 512 | **0.698** | 0.976 | 0.856 | 0.847 | 0.957 | 0.996 | 1.000 | 1.000 / 1.000 | 25,070 | 0.0118 | 0.313 |
| FAR | 256 | 0.710 | 0.944 | 0.902 | 0.723 | 0.947 | 0.980 | 1.000 | 1.000 / 1.000 | 3,118 | 0.0066 | 0.373 |
| FAR | 384 | 0.744 | 0.947 | 0.906 | 0.769 | 0.955 | 0.980 | 1.000 | 1.000 / 1.000 | 3,132 | 0.0063 | 0.373 |
| FAR | 512 | 0.768 | 0.948 | 0.904 | 0.768 | 0.955 | 0.980 | 1.000 | 1.000 / 1.000 | 3,128 | 0.0063 | 0.373 |

Both consumers (the production candidate): SIDE pockets 0.686 / 0.664 / 0.676,
cloud SSIM 0.930 / 0.936 / 0.938; FAR pockets 0.662 / 0.715 / 0.734. Puff
separation remains unimplemented in the harness; the valley proxy stands in.

Of the 129,600 pixels, 24,907 change at 256 and 25,070 at 512: the higher
grid changes the same pixels by slightly less (RMS 0.0124 to 0.0118, SSIM
0.951 to 0.957) and does not change which pockets survive. FAR moves from
0.710 to 0.768, but FAR has 207 pocket pixels against SIDE's 831 and its
recovery is 12 pixels net (section 5); SIDE, the primary pose, moves by less
than the capture's own scatter. This session's SIDE contrast ratio (0.849)
sits below T196's (0.938) because the fixture's storm has evolved between
launches (production SIDE shape calls 5.94M against T196's 4.58M); every
comparison here is within the launch.

## 5. Pocket diagnostic (Task 5) - the mechanism

`StormImageQualityMetrics.pocketDiagnostics` takes every reference pocket
(an interior pixel strictly darker than its eight neighbours, exactly the
metric's definition), clusters them into regions (Chebyshev radius 2), and
per pocket measures the luminance error over its 3x3, the well depth (5x5 ring
maximum minus floor) in both frames, and the wall position along the four
axes at the reference's half-depth level in both frames. Against the 256
capture of the same program it classes each pocket as recovered, newly lost,
kept in both or lost in both - all arms of a pose share the anchor, so the
reference pockets are the same 831 pixels.

| pose | grid | pockets (regions) | kept | lost | recovered vs 256 | newly lost vs 256 | net | signed error | error / depth | depth ratio | wall shift px | walls filled | walls beyond reach |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| SIDE | 256 | 831 (547) | 586 | 245 | - | - | - | **-0.0065** | 0.190 | **0.858** | **+0.95** | 105 | 706 / 2,489 |
| SIDE | 384 | 831 (547) | 568 | 263 | 51 | 69 | **-18** | -0.0066 | 0.188 | 0.874 | +0.95 | 97 | 710 |
| SIDE | 512 | 831 (547) | 580 | 251 | 55 | 61 | **-6** | -0.0065 | 0.187 | 0.883 | +0.96 | 97 | 708 |
| FAR | 256 | 207 (118) | 147 | 60 | - | - | - | -0.0057 | 0.138 | 0.870 | +0.41 | 23 | 79 / 597 |
| FAR | 384 | 207 (118) | 154 | 53 | 17 | 10 | +7 | -0.0054 | 0.133 | 0.880 | +0.37 | 20 | 68 |
| FAR | 512 | 207 (118) | 159 | 48 | 22 | 10 | +12 | -0.0056 | 0.134 | 0.881 | +0.37 | 20 | 70 |

Three facts, each the same at 16, 10.7 and 8 blocks a node:

1. **The arm is darker around the pockets, not brighter.** The signed error
   over the pocket neighbourhoods is -0.0065 luminance, about 11% of the
   reference's mean pocket depth (0.057). A pocket filled in by a smeared edge
   would read brighter. Only 105 of 2,489 measured walls are "filled" (the
   pocket centre risen above the reference's half-depth level); 706 are
   "beyond reach" - the arm never climbs back to that level within 8 px in
   that direction, because the whole neighbourhood is darker. A pocket is a
   local minimum; when its surroundings darken by as much as it does, it
   stops being one. That is the loss, and it matches dark-interior retention
   staying at 0.97 while contrast falls to 0.85 and the valley proxy to 0.82.
2. **The wells are shallower by the same 12-14% at every grid** (depth ratio
   0.858 / 0.874 / 0.883), and the walls sit the same +0.95 px outward. A
   node-size smear would shrink with the node; this does not.
3. **512 recovers 55 of the 245 pockets 256 lost and loses 61 that 256
   kept.** The mask images
   (`t197-campaign/SIDE_t196_field_light@x256_mask.png` and `@x512`: green
   kept, red lost, cyan recovered, magenta newly lost) show the recovered and
   newly lost pixels interleaved at random across the anvil, not gathered at
   the envelope's edge; the difference images (`_diff.png`, red brighter and
   blue darker within 3 px of a pocket, scaled to the pocket depth) are
   indistinguishable between 256 and 512 - blue across the anvil interior and
   the tower's base, red only along the anvil's top rim.

**Evidence against the XZ-smearing hypothesis, for the record:** the pocket
loss is invariant in resolution across a 2x range of node size; its sign is
darkening, not filling; its wall shift is constant; and its spatial pattern is
resolution-independent. What remains is what T197 held constant and what the
field is: a **detail-free** density read along a cone that is mostly
vertical, on **27-block slices**. Detail erosion is what production subtracts
from the base density at every tap; a density without it overstates the
material along the cone and darkens everything under cover by a constant -
which is the signature measured. The hybrid arm's exact near taps did not fix
it because the far taps carry most of the extinction. The Y slice remains the
other candidate and was deliberately not varied here.

## 6. Field build (Task 6), measured on the arm's own clock

| pose | grid | nodes / frame | build p50 | p95 | walks / node | exact SDF / node | bytes | VRAM |
|---|---|---|---|---|---|---|---|---|
| SIDE | 256 | 2,097,152 | **1.61 ms** | 2.00 | 0.180 | 1.474 | 8,388,608 | 8.0 MiB |
| SIDE | 384 | 4,718,592 | 3.76 ms | 4.08 | 0.181 | 1.482 | 18,874,368 | 18.0 MiB |
| SIDE | 512 | 8,388,608 | 6.28 ms | 6.92 | 0.180 | 1.474 | 33,554,432 | 32.0 MiB |
| FAR | 256 | 2,097,152 | 1.51 ms | 1.71 | 0.124 | 1.001 | 8,388,608 | 8.0 MiB |
| FAR | 384 | 4,718,592 | 3.54 ms | 4.16 | 0.124 | 1.001 | 18,874,368 | 18.0 MiB |
| FAR | 512 | 8,388,608 | 5.94 ms | 6.96 | 0.124 | 1.001 | 33,554,432 | 32.0 MiB |

The build is linear in nodes: 2.25x nodes costs 2.33x, 4x costs 3.89x, and
the per-node walk cost is identical at every grid (view 68). VRAM is the
atlas; the oracle's float target (four times the atlas) exists only for the
oracle frame and is released after readback.

## 7. Lookup and ray cost (Task 7)

| pose | grid | light fetch pairs / px | probe fetches / px | march p50 | march p95 |
|---|---|---|---|---|---|
| SIDE | 256 | 14.31 | 3.63 | 13.45 ms | 14.78 |
| SIDE | 384 | 14.32 | 3.76 | 13.17 ms | 15.14 |
| SIDE | 512 | 14.33 | 3.82 | 13.15 ms | 14.64 |
| FAR | 256 | 1.61 | 0.95 | 10.85 ms | 11.22 |
| FAR | 384 | 1.62 | 0.99 | 10.85 ms | 11.21 |
| FAR | 512 | 1.62 | 1.00 | 10.84 ms | 11.11 |

The fetch counts hold to within 0.1% for the light and 5% for the probe (more
provably-empty verdicts mean more scan events), and the march is flat to 2%:
**a 4x larger field costs nothing on the ray**. Cache and bandwidth are not a
factor at this footprint; the whole price of resolution is the build.

## 8. Net (Task 8), paired against the LEAN_FINAL anchors

| pose | grid | anchor p50 | march p50 | build p50 | **combined** | march ratio | **net** | spread | blocks |
|---|---|---|---|---|---|---|---|---|---|
| SIDE | **256** | 32.40 | 13.45 | 1.61 | **15.06 ms** | 2.410 | **2.151x** | 2.66% | 3 accepted |
| SIDE | 384 | 32.55 | 13.17 | 3.76 | 16.93 ms | 2.471 | 1.923x | 1.19% | 3 accepted |
| SIDE | 512 | 32.37 | 13.15 | 6.28 | 19.43 ms | 2.462 | 1.666x | 1.18% | 3 accepted |
| FAR | **256** | 19.55 | 10.85 | 1.51 | **12.37 ms** | 1.801 | **1.581x** | 0.28% | 2 accepted (block 3 anchor drift 3.05%) |
| FAR | 384 | 19.52 | 10.85 | 3.54 | 14.39 ms | 1.799 | 1.357x | 1.99% | 3 accepted |
| FAR | 512 | 19.37 | 10.84 | 5.94 | 16.78 ms | 1.787 | 1.155x | 1.28% | 3 accepted |

`T197_REJECTED count=0`; one FAR block rejected on anchor drift. 256 nets
2.151x at SIDE this session against T196's 2.057x on a lighter fixture. By
the brief's bands 512 would still be "excellent" at SIDE (1.67x) and only
"useful" at FAR (1.15x) - and buys no pockets. T196's 1.48x projection for
512 was pessimistic on the build (6.3 ms measured against 7 projected) and
optimistic on the premise.

**Absolute targets (Task 12):** SIDE 15.06 ms combined against the 10 ms
target, FAR 12.37 ms against 8 ms; anchors 32.4 / 19.5 ms. As in T196 the
targets stay session-local - this session's SIDE anchor is 10% above T196's -
and the relative gain is the evidence.

## 9. Winner (Task 9)

**256x256x32.** Quality: no resolution materially improves the pocket metric
at the primary pose (0.705 / 0.684 / 0.698), dark-interior retention is
0.974-0.976 at all three, and probe soundness is 0 false-empty at all three.
Performance: 256 is 2.151x net SIDE against 1.923x and 1.666x, 1.581x FAR
against 1.357x and 1.155x, and 8 MiB against 18 and 32. The brief's rule -
the lowest resolution that fixes the defect, and keep 256 if none does -
selects 256.

**Production-quality light: FAIL**, on the pocket metric, at every resolution.
The defect is real and is not a resolution defect; section 5 names what it is.

## 10. Post-field attribution (Task 11), 256, SIDE single frame, 129,600 px

| consumer | production | field | share of field |
|---|---|---|---|
| `directStormShapeCalls` | 5,935,367 | 3,812,325 | -35.8% |
| `lobeExactSdf` | 41,394,550 | 19,519,075 | **-52.8%** |
| `shapeRainSegment` | 2,095,206 | 2,363,766 | **62.0%** |
| `shapePrimary` | 666,021 | 1,022,099 | **26.8%** (+53.5%) |
| `shapeRefine` | 254,318 | 291,709 | 7.7% |
| `shapeRainShaft` | 45,061 | 134,926 | 3.5% |
| `shapeLight` | 2,066,948 | **0** | 0 |
| `shapeProbe` | 808,519 | **0** | 0 |
| `shapeUntagged` | 0 | 0 | |
| accounting residual | 726 | 175 | closed |

FAR: rain segment 76.2%, primary 15.7%, refine 4.5%, shaft 3.7%, light and
probe 0, residual -33, closed. Light and probe descriptor traversal are zero
at every resolution.

**The largest non-rain consumer is the primary march at 26.8%**, and the
counters say why it grew: at SIDE the probe answers "possible" 95,528 times
and is right 19,006 times - 80% of its possible verdicts are false, each one
a fine step. The resolution barely touches that (76,522 to 73,188 false
verdicts from 256 to 512) because the floor's slack is vertical.

## 11. Recommended next task

**Tighter probe floor, vertical first (T196 section 10 item 2, now
motivated by data).** The dilation's horizontal term halved between 256 and
512 and the false-occupied count moved 4%; the vertical term - 13.5 blocks
times a cell-local slope of up to 8.27 - is what keeps 80% of the possible
verdicts false and the primary march at 1.02M calls. Candidates that do not
touch the soundness argument: the node's own overlap class in place of the
universal 1.65 gain, and dropping the 1.2 horizontal headroom where the
measured aspect is 1; the candidate that does: a tighter vertical bound from
the profile's actual drop across the slice rather than its slope times the
half-slice. Measured against the same oracle, false-empty must stay 0.

Separately, for the light channel's pockets, the field's **R calibration** is
the open lever: the detail-free density overstates extinction along the cone
by a constant (section 5). A detail-aware node density (one detail fetch per
node in the build, or a per-node mean erosion) is a quality experiment, not a
consumer removal, and should be measured with the same pocket diagnostic; if
it does not move the pockets, Y is the last axis. Rain remains out of scope
(T186, T192).

## 12. Infrastructure

- `campaignGate` 28 s (`gate-t197-campaign.log`); `check build` in section 13.
- Variants: declared 152, ordinary registered 2, T197 registered 2
  (`t196_field_light`, `t196_field_both`), skipped 150, cross-campaign
  controls 3. No program added; the resolution is a live uniform.
- `T166Arm` carries a `stormFieldXZ`; the label and the pricing report key
  include it, so three resolutions of one program are three rows.
- The T196 sandbox invariant now requires the resolution uniform in every
  field arm; the T197 invariant checks the geometry derivation headless and
  records the candidate-tile nesting per grid.
- Pocket diagnostics: `T197_POCKETS` per capture, two PNGs per capture under
  `run/t197-pockets/`, four copied into `t197-campaign/` as evidence.
- Launch timeline: 15:14 Gradle, 15:18:11 `T197_SHARED_FIELD_RESOLUTION_BEGIN`,
  15:20:30 `T132_AUTORUN_FINISHED`, self-exit.

## 13. Validation

- `./gradlew.bat campaignGate` PASS (`gate-t197-campaign.log`, 28 s).
- `./gradlew.bat check build` PASS (`gate-t197.log`, 57 s).
- `T197_REJECTED count=0`; every `T197_ARM` accepted.
- Markers after completion: `run/t197-shared-field-resolution.txt` retired;
  `run/t132-autorun.txt` and `run/t132-autorun-exit.txt` remain.

## 14. Status

Not merged. T196 `69c4914`.
