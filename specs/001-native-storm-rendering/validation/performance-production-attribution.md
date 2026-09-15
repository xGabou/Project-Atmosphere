# T193 - light and probe are both major-band, and both are the right shape

Branch `experiment/cloud-descriptor-k`, base `45801cd` (T192). Nothing merged.
One campaign 09Sep2026, Ultra, raw scale 0.2500, cloud target 480x270,
framebuffer 1920x1080, 96 steps, 60 samples per cell, three anchor-bracketed
blocks per arm per pose. `T193_REJECTED count=0`. **No new shader variants.**

## 1. Headline

**Both remaining non-rain consumers price in the major band, and unlike rain,
both are structurally suited to precomputation.**

| Ceiling | ratio | spread | verdict | band |
|---|---|---|---|---|
| **SIDE light removal** | **1.2490** | 2.22% | **accepted** | **major** |
| SIDE probe removal | 1.2424 | 4.33% | REJECTED_ratio_spread | major |
| **FAR light removal** | **1.2302** | 2.67% | **accepted** | major |
| FAR probe removal | 1.2771 | 4.69% | REJECTED_ratio_spread | major |

Every rain-field campaign topped out at 1.12x net. **Each of these consumers on
its own is worth more than the entire rain architecture was**, and they are 43%
of descriptor work between them.

## 2. Phase 1 - fresh production-path attribution

This is the capture T192 could not provide: no rain-field campaign is armed, so
the monolith is not forced to build a field and its counters describe the
shipping renderer.

**SIDE, `shapeUntagged=0`, residual 56 of 966,836 (fraction 0.0001).**

| Consumer | shape calls | per pixel | share |
|---|---|---|---|
| rain segment | 401,142 | 3.09 | **41.49%** |
| **light tap** | **212,336** | **1.64** | **21.96%** |
| **empty-span probe** | **204,655** | **1.58** | **21.17%** |
| primary body | 94,583 | 0.73 | 9.78% |
| refinement | 37,623 | 0.29 | 3.89% |
| rain shaft | 16,489 | 0.13 | 1.71% |
| bracket | 8 | 0.00 | 0.00% |
| **total tagged** | **966,836** | **7.46** | **100%** |

The six named consumers plus bracket sum to `shapeTagged` exactly. **Nothing is
attributed by subtraction and there is no residual bucket** - the requirement
T182 added after T180 assigned an untagged remainder by elimination.

`exactSdfPerGroupWalk` is **7.04** across every capture in the run, so exact-SDF
work tracks group walks almost exactly and the consumer ranking by group walks is
also the ranking by exact-SDF work.

Two captures were taken and agree to within 0.03% (966,584 vs 966,836), so the
shares below are not a single noisy sample.

## 3. Phase 2 - ranking

**Rain segment is still the largest single consumer at 41.49% and is out of
scope**: the micro-optimization line closed at T186 and the field line closed at
T192, both on measurement rather than fatigue.

| Rank (non-rain) | Consumer | Share | Ceiling |
|---|---|---|---|
| 1 | **light tap** | 21.96% | 1.2490 SIDE |
| 2 | **empty-span probe** | 21.17% | 1.2424 SIDE |
| **combined** | | **43.13%** | not measured |

The two are within 0.8 percentage points of each other. Neither is a clear
first choice on share alone, which is what makes the shared-representation
question in section 5 the deciding one.

## 4. Phase 3 - the ceilings are ceilings

Both arms delete a whole consumer and are visually invalid by construction. The
damage is what makes them bounds rather than proposals:

| Arm | pose | cloud SSIM | silhouette IoU | dark-interior ret. | shadow-pocket ret. | changed px |
|---|---|---|---|---|---|---|
| `t176_nolight` | SIDE | 0.6848 | **1.0000** | **0.0032** | 0.5391 | 19,669 |
| `t176_nolight` | FAR | 0.5959 | 1.0000 | 0.0063 | 0.4688 | 3,908 |
| `t180_noprobe` | SIDE | 0.9176 | 0.9785 | 0.9996 | 0.9188 | 1,619 |
| `t180_noprobe` | FAR | 0.7955 | 0.9579 | 0.9989 | 0.8542 | 603 |

The two damage signatures are cleanly different and both are diagnostic of what
the consumer does. Removing light leaves **silhouette IoU at exactly 1.0000** and
takes dark-interior retention to **0.003** - the shape is untouched and the
shading is gone. Removing the probe scan leaves dark-interior retention at
**0.9996** and moves the silhouette instead, because it changes where samples
land rather than how they are lit.

Production anchor: SIDE 29.79-29.84 ms, FAR 18.54-18.56 ms, session-local.

**Both probe arms were rejected on ratio spread** (4.33% and 4.69% against a 3%
floor), so the probe ceiling is real in magnitude but not established to
protocol. Its band does not change - the minimum block was 1.2179 at SIDE - but
it should be re-measured on a stable session before anything is built on the
exact figure.

## 5. Phases 4-6 - the semantics, and why these differ from rain

This is the part that decides the architecture, and it is a code audit rather
than a measurement.

**What the probe consumes.** `paDensityConsumer = 3`, then:

```glsl
float paProbeDensity = cloudDensity(CameraPos + rayDir * paProbeT, ...);
if (paProbeDensity > 0.0008) { paScanFoundMaterial = true; break; }
```

It is a **threshold test** whose only output is how far the ray may safely
advance. It does not need exact density - only which side of 0.0008 a point
falls. And critically, **its error is one-sided and benign**: a field that says
"material may be present" where there is none costs march time and nothing else.
Only "definitely empty" has to be sound.

**What the light tap consumes.** `paDensityConsumer = 2`, four taps along the
light direction, each `cloudDensity(pos + offset, ...)`, accumulated into
extinction. It is an **integrated** quantity. It does not need exact density
either - it needs a good approximation of extinction along a short ray, and
errors in the four taps **average** rather than compound.

**Now the contrast that matters.** Rain reachability is an *existence* test
evaluated of order sixty times per ray and ORed together. T190 measured the
consequence exactly: a 0.22% per-column error became 12% false rain, because
`1-(1-0.00224)^60 = 12.6%`. That amplification is what closed the rain line, and
it was intrinsic to the OR.

Neither of these consumers has that structure:

- **the probe amplifies nothing**, because its unsafe direction is the one a
  conservative field is naturally correct in - being wrong toward "occupied"
  costs time, not correctness;
- **the light tap averages**, because four taps are integrated into one
  transmittance rather than ORed into a boolean.

**So the failure that closed the rain field does not transfer.** What
transferred from the rain series is the part that worked: moving descriptor
traversal out of the ray produced a real 1.15x class gain, and the build was
cheap (0.23 ms for 262,144 cells). The rain-specific correctness cost is what
made it not worth having, and that cost has no analogue here.

**Can one representation serve both?** Both consume `cloudDensity` at arbitrary
3D points - the probe as a threshold, the light tap as a magnitude. A single
coarse storm-density or conservative-occupancy field could answer both: the
probe reads it as "is any material in this voxel" and the light tap reads it as
extinction. That is one precomputation replacing descriptor walks in **43.13%**
of the ray's shape calls.

**The open risk is dimensionality, and it must be priced before anything is
built.** The rain field was 2D and cheap precisely because rain support is
XZ-invariant - T184 proved that. Storm density is not: this field is 3D, and a
3D field is a different cost proposition entirely. 262,144 cells cost 0.23 ms;
a 128x64x128 voxel field is 1.05M cells and a 256x64x256 is 4.2M. **Whether the
build cost leaves any of the 1.24x standing is exactly the question T187 asked
for rain before T188 built anything**, and asking it first is why the rain series
never wasted a campaign on an architecture that was dead on cost.

## 6. Phase 7 - selection

**Selected: a shared coarse 3D storm-occupancy/density field serving both the
empty-span probe and the light tap.**

**Why this rather than either consumer individually.** They are within 0.8
points of each other in share and within 0.007 in ceiling, so choosing one on the
numbers would be arbitrary. They consume the same underlying quantity at
different precisions, and a representation that serves both amortises one build
across 43% of the work instead of 21%.

**Why this is not the per-lane pruning that failed.** T179's dominance pruning
removed 46.75% of exact SDFs per lane and returned 1.0159x; T185's rain prune
removed 71.50% per lane and returned 1.0155x. Both were per-lane predicates that
changed what each thread decided without changing what the shader executed. This
is the other kind: the traversal stops being in the ray at all, for whole
coherent workloads, which is the shape that produced T180's 1.1972x, T188's
1.1158x and the two ceilings here.

**Expected realistic gain: not yet estimable, and deliberately not guessed.**
The measured ceilings are 1.2490 and 1.2424 individually; the combined ceiling
is **not measured** and neither is the 3D build cost. Both are cheap first steps
and both belong in the next task before any implementation.

## 7. Decision

**30. Next task: semantics and ceiling pricing for the shared field - not an
implementation.** In order:
1. **Measure the combined ceiling.** One arm removing both consumers uniformly,
   which bounds the whole architecture in a single number. The individual
   ceilings do not compose arithmetically and guessing is what T188 did.
2. **Re-measure the probe ceiling to protocol** - both its arms were rejected on
   spread this session.
3. **Establish the field's semantics**: what resolution the probe threshold needs
   to stay conservative, what resolution the light tap needs for extinction to
   hold its quality gates, and whether one resolution serves both.
4. **Price the 3D build cost** against the 43% of traversals it would remove, the
   way T187 priced the rain field's 7.61:1 before T188 built it.

Only then implement, and only if the arithmetic survives.

**31. Shared precomputation is justified as a question, not yet as a build.** The
evidence for it is strong - two major-band ceilings, one shared quantity, and
both error structures forgiving where rain's was not - but the 3D build cost is
unpriced and is the same class of unknown that would have killed the rain field
at T187 had it not been checked first.

**32. Both lines stay open**, and neither should be pursued alone until the
shared question is answered. If the combined ceiling and build cost do not
support one field, they separate cleanly and light is the better-established of
the two (accepted at both poses; the probe was rejected on spread at both).

## 8. Validation

- `T193_REJECTED count=0`; accounting closed, `shapeUntagged=0`, residual
  fraction 0.0001.
- `T191_VARIANT_SCOPE declared=142 activeCampaigns=T193 registered=2 skipped=140
  crossCampaignControls=3 productionAlwaysLoaded=2` - **this campaign is the
  clean test of T192's fix**: T193 owns no programs of its own and borrows both
  ceilings from T176 and T180, which is exactly the cross-campaign case that
  broke under T191's original prefix scoping.
- `T170_WIRING campaigns=36|armMatrices=32|violations=0`,
  `T175_WORKLOAD_VIEWS declared=44|allEnabled=true`.
- **Shader variants unchanged at 143.** Both ceilings already existed; this
  campaign added none.

## 9. Status

Not merged. T188 `9394ac7`, T189 `31c13bb`, T190 `1deb75f`, T191 `63e3cf0`,
T192 `45801cd`.
