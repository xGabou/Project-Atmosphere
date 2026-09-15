# T184 - The support query is exactly column-invariant, and the ray never revisits a column

Branch `experiment/cloud-descriptor-k`, parent `6defa18` (T183). Nothing merged.
One campaign 09Sep2026, Ultra, raw scale 0.2500, cloud target 480x270,
framebuffer 1920x1080, 96 steps, 60 samples per cell, three anchor-bracketed
blocks per arm per pose. 38 cells, `T184_REJECTED count=0` on anchor drift.

## 1. Headline

**The hypothesis is exactly right about the function and exactly wrong about the
ray.**

`localRainSupportAt` is **provably column-invariant within a frame** - it reads
nothing but `worldXZ` and frame uniforms. The one-entry exact-XZ cache built on
that proof is **bit-identical: 0 changed pixels at both poses**.

And it is worth nothing, because **the ray almost never queries the same column
twice**: exact reuse is **0.40% at SIDE and 0.17% at FAR**. The arm measures
**0.9973x SIDE / 0.9911x FAR**, both accepted.

Task 3's own gate - "if exact reuse is <1.05x, close this line" - is met with
room to spare. **Closed.**

**Task 7's premise also does not hold**, and that was determinable without a
campaign: the two sample points are Gauss-Legendre nodes, not endpoints, so
consecutive segments share no position at all.

## 2. Task 1 (items 3-4) - dependency classification

Every input to `localRainSupportAt`, classified:

| Value | Depends on | Varies with Y? | Varies within a frame? |
|---|---|---|---|
| `weather = sampleWeather(worldXZ)` | **XZ** + weather state | no | no |
| `morphology = sampleMorphology(worldXZ)` | **XZ** | no | no |
| `precipitation = morphology.a` | **XZ** | no | no |
| `weatherCoverage` | XZ, `CoverageMul` (uniform) | no | no |
| `weatherBaseY` | XZ, `SlabBaseY/TopY` (uniforms) | no | no |
| `profileId`, `familyStrength` | XZ | no | no |
| `directStormRainSupportAt(worldXZ, ...)` | **XZ** + descriptor set | no | no |
| `MaxPrecipitation`, `paRainOwnCentre/Radius` | uniforms | no | no |

**Answer: yes, exactly invariant.** No term depends on `Y`, on the segment
endpoints, on the camera, or on any per-frame jitter. Two queries at the same XZ
in one frame must return identical results, so reuse keyed on XZ equality is
exact rather than approximate - which is what made the arm worth building even
though the reuse rate turned out to be negligible.

**Rain remains reachable** throughout: no arm in this campaign touches
`localRainSegment` or `rainShaftDensityOverSegment`, and T183's invariant
(`T183_RAIN_RENDER programs=3|rainReachablePostSpecialization=true`) passes.

## 3. Task 7 (item 10) - endpoint duplication does not exist

Both `rainSegmentMayContribute` and `rainShaftDensityOverSegment` sample at:

```glsl
const float FIRST_SAMPLE  = 0.2113248654;
const float SECOND_SAMPLE = 0.7886751346;
```

These are the two-point **Gauss-Legendre** nodes - interior points at 21% and
79% along the segment. Segment N samples at `t + 0.211L` and `t + 0.789L`;
segment N+1 samples at `t + L + 0.211L'` and `t + L + 0.789L'`. **No point is
shared.** The `support(A), support(B)` then `support(B), support(C)` pattern the
brief hoped to exploit never occurs.

**Endpoint carry-forward is not available.** Exact shared-endpoint rate: **0%,
structurally.**

One duplication that *is* real: when a segment passes the reachability test,
`rainShaftDensityOverSegment` re-evaluates `localRainSupportAt` at the **same two
Gauss XZ positions** the test just used. But T182 bounds it - `shapeRainShaft`
43,958 against `shapeRainSegment` 2,081,333 - so the density path follows only
about 2% of reachability tests. Real, and too small to chase.

## 4. Task 2 (items 5-9) - the recomputation measurement

| Per frame | SIDE | FAR |
|---|---|---|
| `localRainSupportAt` calls | 2,340,061 (**18.06/px**) | 2,272,800 (**17.54/px**) |
| T145-pruned before traversal | 283,551 (**12.12%**) | 1,744,225 (**76.74%**) |
| **same exact XZ as previous** | 9,267 (**0.40%**) | 3,758 (**0.17%**) |
| same integer block column | 11,387 (0.49%) | 5,105 (0.22%) |
| same 8-block tile | 167,471 (7.16%) | 68,243 (3.00%) |
| rain-segment shape calls | 2,016,321 (15.56/px, 42.74%) | 512,556 (3.95/px, 47.15%) |

**Reuse distance is the whole answer.** Even at the coarsest granularity
measured - an 8-block tile, which is already an approximation and not exact -
only 7.16% of queries repeat. At exact XZ it is four tenths of one percent.

The reason is geometric: the ray advances a coarse step between calls and the
two Gauss points sit 57% of a segment apart, so consecutive queries are
separated by a substantial XZ distance at every pose that looks at a storm from
outside it. The "mostly vertical ray" the hypothesis assumed is not the SIDE or
FAR pose.

**An observation worth carrying forward:** the call count barely changes with
distance - 18.06/px at SIDE against 17.54/px at FAR - because the test runs once
per coarse step regardless. What changes is the **prune rate**: 12.12% at SIDE
against 76.74% at FAR. At FAR most columns are outside the rain ownership radius
and T145 rejects them on a weather fetch; at SIDE the camera is near the storm,
almost every column is inside that radius, and the full descriptor traversal is
paid.

## 5. Tasks 3 and 8 (items 15-23) - the exact reuse arm

| Pose | Arm | blocks | ratioMean | spread | verdict |
|---|---|---|---|---|---|
| SIDE | `t184_reuse_exact` | 3 | **0.9973** | 1.44% | **accepted** |
| FAR | `t184_reuse_exact` | 3 | **0.9911** | 1.55% | **accepted** |
| FAR | `t184_stack_reuse` vs control | 3 | **0.9983** | 1.21% | **accepted** |
| SIDE | `t184_stack_reuse` vs control | 3 | 0.9635 | 11.55% | REJECTED_ratio_spread |

**Image equality (item 24-26):**

| Arm | pose | passed | meanAbs | changed px |
|---|---|---|---|---|
| **`t184_reuse_exact`** | SIDE | **true** | **0.000000** | **0** |
| **`t184_reuse_exact`** | FAR | **true** | **0.000000** | **0** |

**The exactness claim is verified, not assumed.** Zero changed pixels at both
poses is the empirical confirmation of the Task 1 dependency proof: if any term
had depended on Y or on the segment, the cache would have returned a wrong value
somewhere in 2.3 million calls and the comparison would have caught it.

`t184_stack_reuse`'s 20,747 changed pixels at SIDE are the stack's footprint
step LOD, which the control does not carry - the reuse contributes zero, as the
standalone arm shows.

**Support evaluations avoided: 0.40% SIDE / 0.17% FAR. Descriptor walks avoided:
the same fraction.** Rain coverage, continuity, and onset/termination are
unchanged by construction and confirmed by the bit-identical comparison, so items
27-29 are all "no change".

## 6. Task 5 - the T145 comparison

T145's prune is not a reusable representation; it is a **rejection test**:

```glsl
if (paT145RainLocality()
        && precipitation <= 0.02
        && paRainOwnRadius >= 0.0
        && distance(worldXZ, paRainOwnCentre) > paRainOwnRadius) {
    return 0.0;
}
```

- **What it fetches:** `sampleMorphology(worldXZ).a` for raster precipitation,
  plus two scalar uniforms describing one bounding circle over all rain-owning
  descriptors.
- **Granularity:** per query, not cached; the circle is per frame.
- **What it proves:** a column outside every ownership ellipse and with no raster
  precipitation cannot be owned, so the traversal cannot change the answer.

**It is not semantically sufficient to replace the support value.** It returns
only "definitely zero" - it can prove absence, never produce `attachY`,
`localSupport` or `precipitation`. Extending it into a support *cache* would mean
storing per-column results, which the measurement above shows would hit 0.4% of
the time. **Stating that plainly rather than forcing the analogy.**

What T145 *does* show is where the remaining opportunity is: it is the only thing
that makes FAR cheap (76.74% pruned), and it is nearly inert at SIDE (12.12%).

## 7. Task 6 (items 11-14) - what a support evaluation costs

One `localRainSupportAt`, in call order:

| Stage | Cost | Removed by column reuse? |
|---|---|---|
| `sampleWeather` + `sampleMorphology` | 2 texture fetches | yes |
| coverage/base/profile arithmetic | ~15 scalar ops | yes |
| T145 prune test | 1 distance, 2 compares | yes |
| **`directStormRainSupportAt`** | **`directStormLocalBaseAt` descriptor walk + `directStormFinalDensity` full candidate/group union** | **yes - this is the expensive part** |
| ownership/precipitation resolution | ~10 scalar ops | yes |

Column reuse removes **all** of it - which is why the arm is a fair test of the
idea rather than a partial one. It returns 0.997x because it fires 0.4% of the
time, not because it saves little when it fires.

## 8. Task 11 (items 30-36) - stack

| Pose | Arm | ratioMean | spread | verdict | session-local p50/p95 |
|---|---|---|---|---|---|
| FAR | `t172_stack_pre` (control) | 2.6173 | 2.84% | accepted | 4.806 / 5.229 |
| FAR | `t184_stack_reuse` | 2.6135 | 2.70% | accepted | 4.815 / 5.233 |
| FAR | within-block vs control | **0.9983** | 1.21% | **accepted** | - |
| SIDE | `t184_stack_reuse` | 1.8097 | 1.69% | accepted | 10.920 / 12.149 |
| SIDE | within-block vs control | 0.9635 | 11.55% | REJECTED_ratio_spread | - |

| Target | Status |
|---|---|
| SIDE <= 10 ms locally | **no** - 10.920 session-local, and the composition ratio is rejected |
| SIDE <= 8 ms locally | **no** |
| FAR <= 8 ms locally | **yes** - 4.815 |

## 9. Decision (items 37-40)

**37. Rain-support reuse: CLOSED.** Exact reuse rate 0.40% SIDE / 0.17% FAR;
arm measures 0.9973x / 0.9911x, both accepted; bit-identical. The function is
exactly cacheable and the access pattern is exactly wrong for caching.

**38. Realistic production gain: none.** There is nothing to ship from this line.

**39. Remaining dominant workload: the rain-segment reachability test, still** -
42.74% of `directStormShape` calls at SIDE, 47.15% at FAR, at 18.06 support
calls per pixel. T184 establishes what *cannot* reduce it (reuse, endpoint
carry-forward) without touching what it costs.

**40. Recommended T185: widen the prune, do not cache the result.** The
measurement points at one number: **T145 rejects 76.74% of support calls at FAR
and only 12.12% at SIDE.** The prune requires `precipitation <= 0.02` **and** the
column to be outside a single bounding circle covering every rain-owning
descriptor. At SIDE the camera sits near the storm, so almost every column falls
inside that circle and the full traversal is paid.

Two concrete questions, both uniform and compile-time-shaped rather than
per-lane:

1. **Is one circle over all rain-owning descriptors too coarse?** A per-group or
   per-descriptor ownership bound - the ownership radii T172 already precomputed
   into texel 3 - could reject columns that the union circle admits, without
   changing what the traversal would have returned.
2. **Can the `precipitation <= 0.02` conjunct be relaxed?** It forces the
   traversal for any column with raster precipitation, even far outside every
   ownership ellipse where the descriptor path cannot change the answer.

Both preserve rain exactly, which is the constraint T183 established and this
campaign kept.

## 10. Harness (items 41-45)

- `T184_REJECTED count=0` on anchor drift; the only rejections are ratio spread.
- `T183_RAIN_RENDER programs=3|rainReachablePostSpecialization=true` - rain
  survives; no arm here suppresses it.
- `T182_CONSUMER_TAGS assigned=9|counted=9|untaggedCounterPresent=true`.
- `T175_WORKLOAD_VIEWS declared=34|allEnabled=true` covers views 55-56.
- `T170_WIRING campaigns=28|violations=0`, negative proof detects all five
  mutations.
- FINAL verified free of the arm: `#define PA_ARM_RAIN_REUSE_EXACT` count 0, with
  only the two `#ifdef` guards present as source text.

## 11. Status

Not merged. Productionization `4b34b12`; T170 `9239077`, T171 `aff6fc7`,
T172 `85ff4ef`, T173 `81476b3`, T174 `3af2015`, T175 `3cfd1f1`, T176 `b047bc3`,
T177 `f868325`, T178 `cb1197b`, T179 cleanup `42e655d`, T179 `655bec1`,
T180 `738b5d7`, T181 `624d7f0`, T182 `1bbda6a`, T183 `6defa18`.
