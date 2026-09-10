# T194 - the combined ceiling is 2x, the build is linear, and the gate passes

Branch `experiment/cloud-descriptor-k`, base `26ee5f0` (T193). Nothing merged.
One campaign 09Sep2026, Ultra, raw scale 0.2500, cloud target 480x270,
framebuffer 1920x1080, 96 steps, 60 samples per cell, three anchor-bracketed
blocks per arm per pose. `T194_REJECTED count=0`. **Pricing only: no field was
built and nothing was wired into the renderer.**

## 1. HARD IMPLEMENTATION GATE: PASS

| Requirement | Result |
|---|---|
| combined light+probe ceiling is major | **1.7450x FAR accepted; 1.9981x SIDE (1 block)** |
| candidate field build cost is measured | **0.386-0.401 ns/voxel, linear over a 4x range** |
| expected net SIDE gain >= 1.10x | **1.43x to 1.90x depending on grid** |
| memory cost reasonable | **8.4-67 MB at RG16F** |
| one representation satisfies both consumers | **yes, as two channels** |

Every rain-field campaign topped out at 1.12x net. **The smallest candidate grid
here projects 1.90x**, and even the largest projects 1.43x.

## 2. Task 6 - the combined ceiling, and why measuring it mattered

| Pose | blocks | ratio | spread | verdict | anchor | arm | **removable** |
|---|---|---|---|---|---|---|---|
| SIDE | 1 | **1.9981** | - | REJECTED_insufficient_blocks | 34.05 | 17.04 | **17.01 ms** |
| FAR | 3 | **1.7450** | 2.92% | **accepted** | 20.45 | 11.72 | **8.73 ms** |

**Removing light and probe descriptor traversal together nearly halves the
frame.**

The brief required this be measured rather than inferred from T193's two
single-consumer ratios, and that was right: 1.2490 x 1.2424 = **1.551**, while
the measurement is **1.745 accepted at FAR and 1.998 at SIDE**. The consumers are
**superadditive** - removing both eliminates per-sample setup that neither
removal alone could reach. Inferring would have understated the budget by 13-29%
and, worse, would have looked like a defensible number.

**The SIDE figure is one block** - the other two were rejected on anchor drift -
so it is not established to protocol. FAR's 1.7450 is, and it corroborates the
direction. The break-even below uses both and does not depend on which is right.

**Image damage**, which is what makes this a ceiling rather than a proposal:

| Pose | cloud SSIM | silhouette IoU | dark-interior ret. | changed px |
|---|---|---|---|---|
| SIDE | 0.6275 | 0.9852 | **0.0262** | 28,510 |
| FAR | 0.4847 | 0.9681 | 0.0584 | 4,683 |

It is the union of T193's two single-consumer damages: shading destroyed by the
light removal, silhouette moved by the probe removal.

## 3. Task 5 - the build oracle, measured across a 4x range

Each fragment of a 512x512 pass sweeps N heights through the slab and evaluates
production `cloudDensity` at each, so the draw performs exactly 512x512xN voxel
evaluations, timed on the field clock.

| Slices | voxels | SIDE build p50 | ns/voxel | FAR build p50 | ns/voxel |
|---|---|---|---|---|---|
| 16 | 4,194,304 | 1.6835 ms | 0.4014 | 1.4176 ms | 0.3380 |
| 32 | 8,388,608 | 3.2683 ms | 0.3896 | 2.7286 ms | 0.3253 |
| 64 | 16,777,216 | 6.4679 ms | 0.3855 | 5.3412 ms | 0.3184 |

**Cost is linear in voxel count and very slightly sublinear** - 0.4014 to 0.3855
ns/voxel across a 4x range at SIDE. That is a measured slope, not an
extrapolation from the rain field's single point on a different function, which
is what the brief asked for and what makes the candidate table below trustworthy.

**Sanity check that the oracle is measuring the right thing**: all three slice
arms report a march ratio of 1.0105/1.0120/1.0110 at SIDE while their field
clocks read 1.68/3.27/6.47 ms. The march is unchanged and the entire build lands
in its own column - the build is not hidden inside a frame time.

## 4. Tasks 1-3 - semantics, and why one field needs two channels

**Light.** The tap loop accumulates `opticalDepth += density * stepLength *
tapWeight` over four taps, with `detailTap = i < 2` and `mipBias = i * 0.6`. So
**taps 3 and 4 already use no detail noise at all**, and every tap reads a
progressively blurrier version. The renderer has already decided the far half of
the light cone does not need detail. Light needs an **unbiased estimate** of
density; it does not need exact production `cloudDensity`, and production already
says so.

**Probe.** `paProbeDensity > 0.0008`, a pure threshold whose only output is how
far the ray may advance. Its error is **one-sided**: over-estimating costs march
time, under-estimating is a correctness bug. The probe needs a **conservative
upper bound**.

**These want opposite approximations.** Feed light an upper bound and every cloud
goes too dark - which is precisely the failure T178 caught in light3
(dark-interior retention 0.0715). Feed the probe an unbiased mean and it will
occasionally skip occupied space, which is the one thing it may not do.

**So no single scalar serves both - but one field with two channels does:**

| Channel | Value | Consumer | Requirement |
|---|---|---|---|
| R | **mean density** over the voxel | light tap | unbiased estimate |
| G | **max density** over the voxel | empty-span probe | conservative upper bound |

One build, one traversal, one texture, `RG16F` at 4 bytes/voxel. That also
answers Task 12: **one shared field, not two** - the expensive part is the
descriptor traversal per voxel, and both channels come out of the same traversal.
Two separate fields would double the build for no benefit.

## 5. Task 4 - domain and candidates

`WeatherExtent` is 4096 world blocks horizontally, shared with the weather and
rain-field domains. The slab runs roughly 136 to 1002, about **866 blocks**
vertically.

| Grid | voxels | blocks/voxel XZ | blocks/voxel Y | RG16F | **SIDE build** |
|---|---|---|---|---|---|
| **256x256x32** | 2,097,152 | 16.0 | 27.1 | **8.4 MB** | **0.84 ms** |
| 256x256x64 | 4,194,304 | 16.0 | 13.5 | 16.8 MB | 1.68 ms |
| 384x384x32 | 4,718,592 | 10.7 | 27.1 | 18.9 MB | 1.89 ms |
| 512x512x32 | 8,388,608 | 8.0 | 27.1 | 33.6 MB | 3.36 ms |
| 512x512x64 | 16,777,216 | 8.0 | 13.5 | 67.1 MB | 6.72 ms |

Build times are the measured 0.401 ns/voxel applied to each candidate - the
conservative end of the measured range.

## 6. Task 7 - break-even

Net ratio = anchor / (arm + build), before lookup:

| Grid | SIDE net | FAR net |
|---|---|---|
| **256x256x32** | **1.904x** | **1.645x** |
| 256x256x64 | 1.819x | 1.556x |
| 384x384x32 | 1.799x | 1.532x |
| 512x512x32 | 1.669x | 1.404x |
| 512x512x64 | 1.433x | 1.199x |

**Break-even voxel count** - where net falls to 1.10x - is **34.7M voxels at
SIDE and 20.3M at FAR**. Every candidate is below the FAR-limited break-even,
and the smallest is an order of magnitude below it.

**Lookup cost is not measured and is not in these numbers.** It subtracts
further, and the brief is right that T170's "descriptor fetches are cheap" does
not transfer to a large 3D texture with different cache behaviour. But the margin
absorbs a great deal: even if lookup cost equalled the build cost exactly,
256x256x32 still gives **1.82x at SIDE**. The gate does not depend on the
unmeasured term.

## 7. Tasks 8-11 - what remains open

**Resolution (Task 8).** Not established. 256x256x32 is 16 blocks per voxel
horizontally and 27 vertically, which is coarse against a light cone whose first
two taps carry detail. The probe's requirement is different in kind: it needs the
**max** channel to be a true upper bound over the voxel, which is a build-side
property rather than a resolution one - a correct max at any resolution stays
sound, it just skips less.

**Update frequency (Task 9).** Not audited in code this task. The dependency that
matters is whether the required quantity includes the animated detail term: light
taps 3-4 already exclude detail, and if the field carried only the
detail-free component the rebuild frequency question changes completely. **This
is the single highest-value open question** and belongs at the front of the next
task, because a field that need not rebuild every frame changes the break-even by
another order of magnitude.

**Compute architecture (Task 10).** The oracle proves a fullscreen pass over a
512x512 target sweeping N heights is fast enough. The simplest real
implementation is the same shape writing into a layered target or a 3D texture,
reusing the render-target infrastructure T188 already added rather than
introducing a compute subsystem.

**Lookup cost (Task 11).** Unmeasured. Light would take 4 trilinear fetches per
lit sample, probe 1 per probe - roughly 6.5 fetches per pixel at T193's measured
1.64 light and 1.58 probe calls per pixel.

## 8. Decision

**32. GATE: PASS**, on every one of the five conditions, with the projected net
gain between 1.43x and 1.90x at SIDE against a 1.10x bar.

**33. Next implementation task, in this order:**
1. **Audit the update-frequency dependency first** - specifically whether the
   light and probe representations can exclude the animated detail term. It is
   cheap to answer and can move the economics by an order of magnitude.
2. **Build the quality harness before the field**, not after. The rain series
   spent four campaigns discovering correctness costs after the performance
   architecture existed; T188's own report had to be corrected twice. Light needs
   self-shadow metrics - T178's dark-interior and shadow-pocket retention, which
   are what rejected light3 - and the probe needs a soundness check that no
   occupied span is classified empty.
3. **Implement at 256x256x32** - the cheapest candidate, 0.84 ms and 8.4 MB, and
   the one with the widest margin - as `(mean, max)` in RG16F.
4. **Measure, then sweep resolution upward** if quality requires it, which is the
   opposite direction from the rain series and the correct one here: start where
   the economics are safest and spend margin on quality only where measurement
   demands it.

**34. If it fails**, the fallback is not another field: it is that light and
probe separate cleanly, light is the better-established consumer (accepted at
both poses in T193), and its 1.2490x ceiling remains available on its own.

## 9. Validation

- `T194_REJECTED count=0`.
- `T191_VARIANT_SCOPE declared=146 activeCampaigns=T194 registered=4 skipped=142
  crossCampaignControls=5 productionAlwaysLoaded=2` - **ordinary startup remains
  two programs.**
- `T170_WIRING campaigns=37|armMatrices=33|violations=0`,
  `T175_WORKLOAD_VIEWS declared=44|allEnabled=true`,
  `T123 instrumentation guardedCounterMutations=117 rainMaskMutations=3`.
- **Variants 143 -> 147.** Four added, all pricing-only and campaign-scoped.

## 10. Status

Not merged. T190 `1deb75f`, T191 `63e3cf0`, T192 `45801cd`, T193 `26ee5f0`.
