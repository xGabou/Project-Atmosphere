# T172 - Descriptor invariant precompute

Branch `experiment/cloud-descriptor-k`, parent `aff6fc7`. Nothing merged.
Campaign run 05Sep2026 21:20-21:22, Ultra, raw scale 0.2500, cloud target
480x270, framebuffer 1920x1080, 96 steps, 60 samples per cell. 9 programs x 2
repeats x 2 poses = 36 cells. `T172_REJECTED count=0` (no fixture rejections);
repeat-agreement verdicts are reported per arm below. History disabled.

Measured under T171's rule: every arm twice, a pair disagreeing by more than 3%
is **REJECTED and not averaged**, effects below 3% are **BELOW MEASUREMENT
FLOOR**.

## 1. Headline

**The precompute works and is bit-exact. The opportunity it was built to capture
was mostly not real.**

All three isolated precompute arms render images **bit-identical** to the
production anchor - `maxAbsRGBA=0.0`, zero changed pixels, at both poses. The
architecture is correct, costs 16 bytes per descriptor and **zero additional
fetches** in the march.

But it retains only **19.3%** of the T170 ceiling it was authorised against. The
reason is section 6, and it is a correction to T170 rather than a shortfall in
the implementation: **T170's ceiling arms did not isolate arithmetic.** They
substituted a value that made the existing conservative cull far more
aggressive, and the cull - not the removed arithmetic - was most of what they
measured.

## 2. Task 1 - classification

| Value | Computation | Depends on | Class |
|---|---|---|---|
| edge width | `stormEdgeWidthBlocksFromData(positionHeight, radiusRotation, shearMedia, role)` | descriptor payload, role. **No sample position, camera or frame.** | **STATIC PER DESCRIPTOR** |
| ownership radii | two `length()` over `radiusRotation` products, `* 1.85`, `max(_,1.0)` | radii, orientation | **STATIC PER DESCRIPTOR** |
| `lobeRadius` | `min(radiusRotation.x, .y)` | descriptor | static; one instruction, not worth a channel |
| `ownershipCenter` | `positionHeight.xy + shearMedia.xy * 0.5` | descriptor | static; one madd |
| `lobeStrength` | `saturate(shearMedia.z)` | descriptor | static; one saturate |
| role decode | `int(floor(lifecycleRole.w + 0.5))`, `% 8` | descriptor | static; two ALU on an already-fetched texel |
| conservative bound | `stormVerticalDistanceLowerBound(p, ...)` | **sample position** | **NOT SAFE** |
| exact SDF | `directStormLobeDistanceFromData(p, ...)` | **sample position** | **NOT SAFE** |
| ownership *test* | `length((p.xz - centre) / radii) <= 1` | **sample position** | **NOT SAFE** - only the radii were hoisted |
| smooth union | `stormSmoothMinimum`, blend radius, blend factor | accumulated union state | **NOT SAFE** |

Only the top two justify a channel. The rest are single instructions whose
removal would cost more in payload than it saves in ALU.

## 3. Task 2 - representation

Texel 3 was `(seed01, lifecycleStage, verticalDevelopment, packedGroupRole)` and
the shader reads **only `.w`** - verified by exhaustive search. Its other three
channels were not free, though: they feed a CPU round-trip through
`StormLobeDescriptor.fromTexels` into `StormRenderSnapshot`. So the displaced
fields moved rather than being discarded.

```
old  texel0 (cx, cz, baseY, topY)            texel1 (rMaj, rMin, sin, cos)
     texel2 (shearX, shearZ, densW, edgeSoft) texel3 (seed, lifecycle, vertDev, role)

new  texel0, texel1, texel2 unchanged
     texel3 (edgeWidth, ownRadiusX, ownRadiusZ, role)    <- precompute
     texel4 (seed, lifecycle, vertDev, 0)                <- displaced, CPU only
```

| | |
|---|---|
| bytes added per descriptor | **16** (64 descriptors: 1 KB total) |
| additional fetches per density call | **0** - texel 3 was already fetched for the role; texel 4 is never fetched by the shader |
| arithmetic removed per descriptor per density call | one edge-width evaluation (branch, 3 `max`, `mul`, `min`, plus `stormDescriptorVerticalBounds`) and two `length()` calls with four multiplies, a `*1.85` and a `max` |

This is the preferred option - spare channels, no extra bandwidth in the hot
loop - reached by displacing CPU-only fields rather than by adding a fetch.

The CPU side is a deliberate **float transcription of the GLSL**, not a call
into `StormLobeEvaluator.edgeWidthBlocks`, which computes the same quantity in
double for CPU geometry work. The precompute has to reproduce what the shader
produced, not to be more accurate. A sandbox invariant parses both sources and
fails the build if either formula drifts.

## 4. Tasks 3, 4, 6 - correctness. Exact.

Reference image comparison against `lean_final`, 129,600 pixels,
`epsilon=4.882813e-04`:

| Pose | Arm | passed | maxAbsRGBA | meanAbsRGBA | changed px |
|---|---|---|---|---|---|
| SIDE | `t172_pre_edge` | **true** | **0.0** | **0.0** | **0** |
| SIDE | `t172_pre_owner` | **true** | **0.0** | **0.0** | **0** |
| SIDE | `t172_pre_both` | **true** | **0.0** | **0.0** | **0** |
| FAR | `t172_pre_edge` | **true** | **0.0** | **0.0** | **0** |
| FAR | `t172_pre_owner` | **true** | **0.0** | **0.0** | **0** |
| FAR | `t172_pre_both` | **true** | **0.0** | **0.0** | **0** |

**Bit-identical, not merely close.** No SSIM, IoU, thin/hole retention or seam
index is reported because there is no difference to characterise: the images are
the same bytes.

One risk was predicted and did not materialise. GLSL `length()` uses a float
`sqrt`; the Java transcription uses `(float) Math.sqrt(double)`, which can differ
by one ULP and could in principle flip `ownsGroup` for a sample exactly on the
ownership boundary. It did not occur on this fixture at either pose.

`t172_stack_pre` is **not** image-identical to the anchor (SIDE meanAbs
2.702e-03, FAR 5.604e-04) and is not expected to be: it carries the whole T169
stack, whose own error T170 measured at 2.205e-03 SIDE. The precompute
contributes nothing to that difference, which is what the three isolated arms
establish.

## 5. Tasks 5, 7, 9 - performance

### 5.1 SIDE is not measurable in this run

| Arm | run 1 | run 2 | disagreement | verdict |
|---|---|---|---|---|
| `lean_final` | 21.1333 | 19.8124 | 6.45% | **REJECTED** |
| `t172_pre_edge` | 20.2189 | 19.3485 | 4.40% | **REJECTED** |
| `t172_pre_owner` | 20.6684 | 19.5666 | 5.48% | **REJECTED** |
| `t172_pre_both` | 19.4468 | 18.3204 | 5.96% | **REJECTED** |
| `t170_desc_constedge` | 14.9811 | 8.6692 | **53.4%** | **REJECTED** |
| `t170_desc_hoist` | 14.6309 | 8.2043 | **56.3%** | **REJECTED** |
| `t169_stack_fast` | 13.7073 | 11.5886 | 16.8% | **REJECTED** |
| `t172_stack_pre` | 11.1575 | 10.9394 | 1.97% | accepted |
| `t170_stack_hoist` | 9.4464 | 5.4753 | 53.2% | **REJECTED** |

**Eight of nine pairs rejected.** This is T171's unexplained bimodal mode, this
time taking most of a block rather than isolated cells: the second repeat is
systematically faster, by up to 56%.

Not GPU state. Telemetry across the whole SIDE sweep holds **2340 MHz pinned**,
54-59 C, 88-100% utilisation, no throttle - the same exclusion T171 reached.

The one accepted SIDE pair, `t172_stack_pre` at 11.0484, **is still not
bankable**, because its speedup is computed against an anchor whose own pair was
rejected. A ratio is only as good as its denominator. **No SIDE conclusion is
drawn from this run.**

### 5.2 FAR is clean and is what this campaign banks

Eight of nine pairs accepted, all disagreeing by 1.45% or less.

| Arm | run 1 | run 2 | disagreement | mean | speedup | verdict |
|---|---|---|---|---|---|---|
| `lean_final` | 13.0929 | 13.0836 | 0.07% | 13.0883 | 1.0000 | accepted |
| `t172_pre_edge` | 12.5041 | 12.4365 | 0.54% | 12.4703 | **1.0496** | accepted |
| `t172_pre_owner` | 12.8061 | 12.6290 | 1.39% | 12.7176 | 1.0291 | **BELOW FLOOR** |
| `t172_pre_both` | 12.0013 | 11.9009 | 0.84% | 11.9511 | **1.0952** | accepted |
| `t170_desc_constedge` | 12.0965 | 9.0634 | 28.7% | - | n/a | **REJECTED** |
| `t170_desc_hoist` | 8.6958 | 8.8228 | 1.45% | 8.7593 | 1.4942 | accepted (ceiling) |
| `t169_stack_fast` | 5.1108 | 5.0463 | 1.27% | 5.0785 | 2.5772 | accepted |
| `t172_stack_pre` | 4.7421 | 4.7974 | 1.16% | 4.7698 | **2.7440** | accepted |
| `t170_stack_hoist` | 4.3295 | 4.3100 | 0.45% | 4.3197 | 3.0299 | accepted (ceiling) |

**The ownership precompute alone is below the measurement floor** and is
reported as such rather than as a 2.9% win.

Pipeline timings, FAR:

| Arm | cloudP50 | cloudP95 | frameP50 | frameP95 |
|---|---|---|---|---|
| `lean_final` | 13.0883 | 14.86 | 13.84 | 16.17 |
| `t172_pre_both` | 11.9511 | 13.33 | 12.73 | 14.88 |
| `t169_stack_fast` | 5.0785 | 5.48 | 5.81 | 7.31 |
| `t172_stack_pre` | 4.7698 | 5.30 | 5.49 | 6.68 |

### 5.3 On the shipped stack

`t169_stack_fast` 5.0785 -> `t172_stack_pre` 4.7698 = **1.0647x**, a 6.1%
reduction, above the floor, image-equivalent apart from the stack's own error.

## 6. Task 10 - the real hoist falls short, and why

| Comparison | real | ceiling | **retained** |
|---|---|---|---|
| combined precompute vs anchor | 1.0952 | 1.4942 (`desc_hoist`) | **19.3%** |
| on the stack | 1.0647 | 1.1757 (`stack_hoist`) | **36.8%** |

Both are far below the 80% threshold, so per Task 10 this stops here rather than
piling on further optimisation.

**The cause is not in the implementation. T170's ceilings were not ceilings for
this change.**

`t170_desc_constedge` and `t170_desc_hoist` replace edge width with
`STORM_MIN_EDGE_BLOCKS` = 11.363636 - the **floor** of the distribution, not a
typical value. `lobeSoftness` then feeds the T121 conservative rejection:

```glsl
if (started && !paT121Off() && verticalLowerBound > max(
        lobeSoftness + STORM_T121_SOFTNESS_MARGIN_BLOCKS,
        groupDistance + STORM_MAX_BLEND_BLOCKS)) {
    // skip the exact SDF and the union for this descriptor
```

A smaller `lobeSoftness` makes that test **easier to satisfy**, so far more
descriptors are culled before the exact SDF runs. Those arms therefore measured
*cheaper arithmetic plus a much more aggressive cull*, and the evidence says the
cull is the larger term.

The proof is the image data. The real precompute is **bit-identical**, so it
provably cannot have changed which descriptors are culled - and it retains 19%.
The ceiling arm changes the image substantially (T170 measured `desc_hoist` at
SIDE meanAbs 1.497e-02, an order of magnitude above the shipped stack's own
error) - and it captured the other 81%.

**T170's "1.421x image-identical hoist opportunity" is withdrawn.** The
image-identical part of it is worth 1.0952x. This is the second T170 headline to
fall to a proper control, after the 7.31 ms figure T171 withdrew.

The candidates Task 10 lists - fetch latency, register pressure, packing
overhead, decode cost - are not implicated: the fetch count is unchanged, the
hot loop reads a texel it already had, and the arms are bit-identical, which
rules out any behavioural difference. The shortfall is entirely in what the
ceiling was measuring.

## 7. Task 8 - counters

Captured with the program override released, so per pose rather than per arm.

| Per pixel | SIDE | FAR |
|---|---|---|
| primary ray steps | 30.15 | 27.08 |
| density calls | 19.29 | ~4.2 |
| detail octave evaluations | 24.505 | 4.403 |
| light evaluations | 9.828 | 1.654 |

Against T170's run 2 on the same poses: primary steps 3,907,150 against
3,907,935 and density calls 2,500,455 against 2,573,204. **Call counts are
unchanged, as the design predicted** - the precompute alters cost per density
call, not how many there are. Nothing to investigate.

## 8. Targets

| Target | Status |
|---|---|
| SIDE <= 10 ms | **unmeasured** - anchor pair rejected |
| SIDE <= 9 ms | **unmeasured** |
| SIDE <= 8 ms | **unmeasured** |
| FAR <= 8 ms | **met** - `t169_stack_fast` 5.0785, `t172_stack_pre` 4.7698 |

FAR was already met by T169. SIDE remains the binding pose and this run produced
no usable SIDE measurement.

## 9. Decision

**Productionize the combined precompute: yes.**

It is bit-exact, so it carries no visual risk; it costs 16 bytes per descriptor
and **no additional fetch**; and it is worth **1.0952x** on the anchor and
**1.0647x** on the shipped stack at FAR, both above the measurement floor. A
permanent, free, image-identical 6% on the shipping configuration is worth
keeping even though it is a fifth of what was hoped.

Productionizing means defining `PA_ARM_DESC_PRECOMPUTE` in FINAL. It is
deliberately **not** done in this commit: the sandbox invariant currently
asserts FINAL does *not* define it, and flipping that belongs in a change whose
own campaign covers SIDE.

**Ship the edge-width precompute; keep the ownership precompute only as part of
the combined arm.** Alone it measures 1.0291x, below the floor. Combined it is
free to keep, since both values share a texel that is fetched either way.

### 9.1 New post-hoist dominant bottleneck

Unchanged in identity, changed in interpretation: **the exact descriptor SDF**,
and specifically **how many descriptors reach it**.

Section 6 is the finding. The ceiling arms accidentally demonstrated that
loosening the T121 rejection test is worth roughly four times what removing the
per-sample arithmetic is worth. That is now the largest measured lever in the
descriptor walk - and it is *not* free, because those arms changed the image.

### 9.2 Recommended next optimisation

**Investigate a tighter, still-conservative T121 rejection bound.**

The cull currently guards the exact SDF with
`verticalLowerBound > max(lobeSoftness + margin, groupDistance + blend)`. The
ceiling arms shrank `lobeSoftness` arbitrarily and got a large speedup with a
wrong image. The legitimate version of that is a *tighter lower bound* on the
lobe distance, which rejects more descriptors while remaining conservative - by
construction it cannot change the image, exactly as T141's box bound already
does for the horizontal term.

That is a correctness-led change with a measured 4x-larger prize than the one
just implemented, and it should be specified before it is measured.

**Before any of it: SIDE needs a clean campaign.** Two of the last three
campaigns have produced unusable SIDE blocks. The bimodal mode T171 characterised
is now the single largest obstacle to progress on the binding pose, and it is
still unexplained.

## 10. Harness notes

- Peak benchmark-owned memory 6,916 MB; minimum availability 3,427 MB; **0 Full
  GCs**.
- `cloudP50`/`cloudP95`/`cloudCv` from GPU timer queries. Frame timings are
  reported for the pipeline figure only and are not banked.
- The invariant system caught three real regressions during this campaign, all
  from legitimate edits: a `#if` that T172 turned into `#elif`, the hardcoded
  latch mutation going stale for the second campaign running, and a constant
  parse of my own. The latch mutation is now **derived from the registry** - it
  targets whichever campaign was registered last, which is the wire most likely
  to be forgotten - so it cannot rot again.

## 11. Status

Not merged. T170 `9239077`, T171 `aff6fc7`.
