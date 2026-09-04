# T162 - Post-T161 cost attribution

Status: **COMPLETE** 2026-09-04

/ Outcome: **CASE C** - descriptor traversal is not dominant. The largest single
  cost is an unreachable precipitation path inside `cloudDensity`: removing it is
  bit-identical and worth ~1.53x. Lighting is second at ~1.44x. Descriptor binning
  ranks third and is NOT recommended next.

/ Feature: 001-native-storm-rendering
/ Starting commit: `bd47124` (T161 banked at `4844cc8`, T140 at `122a566`)
/ Branch: `worktree-t098-production-ray-trace`

T140 established that the remaining cost is inside cloud-relevant rays and
closed whole-pixel culling. T162 asks the next question: **within a density
evaluation, where does the time go?** It implements no optimization.

The specific hypothesis under test was the T140 clue - 390.6M descriptor texture
fetches for 2.50M density calls with ten resident descriptors - which suggested
descriptor traversal might dominate. **It does not.**

## 1. Method

### Compile-time arms, not runtime branches

T161 measured that dormant diagnostic paths behind runtime uniforms cost 3.08x.
An attribution campaign built from runtime switches would therefore measure its
own scaffolding. Every arm here is a **separately generated, separately linked,
lean-specialized program**; FINAL defines none of their macros, and a build-time
gate asserts that.

Two kinds of arm:

| Kind | What it is | What it can answer |
|---|---|---|
| **Fixed-work ladder** | 64 fixed world-space samples per fragment; identical control flow in every arm | per-evaluation cost of one class, isolated |
| **Production-context** | the real raymarch with exactly one class compiled out | what that class costs in situ |

The ladder is cumulative, so each rung is a delta over the one below:

```
1 ADDRESS          ray/loop/address arithmetic only        (control floor)
2 CANDIDATE        + candidate tile fetch, decode, group walk
3 DESCRIPTOR       + the four-texel descriptor payload reads
4 SHAPE            + ownership, profile, warp/SDF, role union
5 DENSITY_NODETAIL + weather/morphology, base noise, erosion
6 DENSITY_NORAIN   + detail octaves
7 DENSITY_FULL     + precipitation shafts
```

Because the sample points are fixed, no arm can look cheap by terminating
early or by covering less of the screen - the trap the task warned about.

### Evidence hygiene

The Ultra recovery sweep qualified its pose once and kept measuring after the
storm stopped contributing, producing cells about 9x too cheap. Descriptor count
alone did not catch it: the count stayed at ten throughout.

Every T162 cell is therefore qualified **independently, at both ends**:

1. resolve the fixture and confirm it exists,
2. confirm the descriptor count still equals the count the pose started with,
3. re-evaluate the T150 geometric visibility verdict,
4. run the timing cell,
5. repeat 2 and 3 immediately afterwards,
6. discard the whole cell if anything moved.

`T162_QUALIFY before` / `T162_QUALIFY after` lines record each check and
`T162_REJECT` names any discarded cell. **Across every campaign run reported
here, zero cells were rejected.**

### Fixture

PLAY_VIS_NEAR, Ultra, 0.25 scale, 1920x1080 framebuffer, 480x270 cloud target,
10 descriptors, 60 sampled frames per cell, history disabled for the whole
matrix so every arm shares one temporal state. Ultra was not changed; the ladder,
morphology and FINAL semantics are untouched.

## 2. Fresh production baseline

Re-measured on this fixture rather than reusing the T140 figure.

| Run | cloud p50 | cloud p95 | frame p50 | frame p95 |
|---|---|---|---|---|
| 1 | 37.3176 | 41.2293 | 38.3423 | 43.9866 |
| 2 | 35.6239 | 39.3933 | 36.6 | - |

Production workload counters for the same cell (captured on the diagnostic
monolith, which is the only program that carries the counters - so they describe
production work, not any arm):

| Counter | Value | Per cloud pixel | Per density call |
|---|---|---|---|
| primary ray steps | 3,797,016 | 29.30 | 1.94 |
| **cloud density calls** | **1,960,462** | **15.13** | 1.00 |
| light-march density evaluations | 759,740 | 5.86 | 0.388 |
| descriptor evaluations | 36,085,470 | 278.4 | 18.41 |
| **descriptor texture fetches** | **365,666,506** | **2,821** | **186.5** |
| directStormShape calls | 7,020,735 | 54.2 | 3.58 |
| lobes visited | 40,106,812 | 309.5 | 20.46 |
| detail octave evaluations | 2,168,775 | 16.73 | 1.11 |
| conservative descriptor rejects | 13,850,644 | 106.9 | 7.07 |
| avoided descriptor fetches (T122) | 185,225,620 | 1,429 | 94.5 |
| early terminations | 14,164 | 0.11 | - |

### Why the fetch ratio is what it is

186.5 texel fetches per density call against ten resident descriptors looks
impossible until the intermediate counters are read in order:

- each density call makes **3.58** `directStormShape` calls (the primary sample
  plus the light-march probes that re-enter the same evaluator),
- each shape call visits **5.71** lobes,
- each visited lobe costs **9.12** texel fetches - about four texels re-read
  roughly twice across the ownership, profile and union stages.

3.58 x 5.71 x 9.12 = 186. The ratio is not one sample touching 186 descriptors;
it is a modest per-lobe payload multiplied three times over by re-entry.

T122 reuse is working: **185.2M fetches are already avoided**, 34% of the 551M
that would otherwise be issued.

### Density-call shares

| Source | Share of density calls |
|---|---|
| light-march probes | **38.8%** (759,740 of 1,960,462) |
| precipitation | **0%** - every one of the 19 production call sites passes `includePrecipitation = false` |

The rain share of *calls* being zero is the first hint of the finding in
section 4.

## 3. Fixed-work attribution ladder

64 fixed samples per fragment, identical control flow in every arm. Run 5 shown;
runs 1 and 2 agree within 3%.

| Rung | Class added | cloud p50 | delta | share of full density call |
|---|---|---|---|---|
| 1 | control floor (ray/loop/address) | 0.0164 | 0.0164 | 0.3% |
| 2 | candidate traversal | 0.5837 | **0.5673** | 11.6% |
| 3 | descriptor payload fetch | 0.7823 | 0.1987 | 4.1% |
| 4 | shape / profile / warp / SDF / union | 1.7940 | **1.0117** | 20.7% |
| 5 | weather, morphology, base noise, erosion | 2.1914 | 0.3973 | 8.1% |
| 6 | detail octaves | 2.2231 | 0.0317 | 0.6% |
| 7 | precipitation shafts | 4.8865 | **2.6634** | **54.5%** |

Reproducibility of the deltas (p50, ms):

| Class | run 1 | run 2 | run 5 |
|---|---|---|---|
| candidate traversal | 0.5663 | 0.5642 | 0.5673 |
| descriptor fetch | 0.1905 | 0.1976 | 0.1987 |
| shape/profile/SDF | 0.9800 | 0.9902 | 1.0117 |
| weather + noise + erosion | 0.3840 | 0.3891 | 0.3973 |
| detail octaves | 0.0338 | 0.0317 | 0.0317 |
| precipitation | 2.6767 | 2.6849 | 2.6634 |

**Descriptor work - candidate traversal plus payload fetch together - is 15.7%
of a density evaluation.** It is not the dominant class. The T140 fetch-count
clue pointed at the wrong thing: the fetches are numerous but individually
cheap, and section 2 showed the ratio comes from re-entry rather than from
touching many descriptors.

The heaviest single function is `rainShaftDensityAt` at 54.5%. Which leads to
the main finding.

## 4. The dead precipitation path

`cloudDensity` takes an `includePrecipitation` parameter. **All 19 production
call sites pass `false`.** The only `true` in the file is the fixed-work arm
this campaign added. Rain is rendered, but through a separate integrator
(`rainShaftDensityOverSegment`, called once from the march) that this arm does
not touch.

So the `rainShaft` branch inside `cloudDensity` is unreachable in production.
Its only consumer is one addition:

```glsl
float density = (max(cloud, 0.0) * familyDensityScale + rainShaft) * DensityMul;
```

With `rainShaft` provably `0.0`, that addition is a numeric no-op. Compiling the
branch out should therefore change nothing. It does not:

```
T162_IMAGE_AB arm=t162_norain a=lean_final evaluated=true passed=true
  maxAbsRGBA=0.000000e+00 changedPixelCountAboveEpsilon=0 totalComparedPixels=129600
```

**Bit-identical.** And in the same run the lighting arm, captured the same way,
is *not* - 18,062 changed pixels, maxAbsRGBA 0.389 - which is what makes the
zero credible rather than a harness that cannot detect a difference.

| Run | anchor | rain compiled out | saving | speedup |
|---|---|---|---|---|
| 1 | 37.318 | 23.605 | 13.712 ms (36.7%) | **1.581x** |
| 2 | 35.624 | 22.692 | 12.932 ms (36.3%) | **1.570x** |
| 5 | 33.617 | 22.629 | 10.988 ms (32.7%) | **1.486x** |

**Roughly a third of the Ultra frame is spent carrying code that never runs.**

This is T161 again, one level down. T161 found dormant diagnostic paths costing
3.08x across the *program*; this is a dormant path costing ~1.53x inside a single
*function*. The mechanism is presumably the same - a branch the compiler cannot
prove dead forces register allocation and depresses occupancy for every call -
but that remains a hypothesis: it is not provable without hardware counters. What
is proven is the cost and the bit-identity.

## 5. Lighting

| Run | anchor | lighting compiled out | saving | speedup |
|---|---|---|---|---|
| 1 | 37.318 | 25.279 | 12.039 ms (32.3%) | 1.476x |
| 2 | 35.624 | 24.618 | 11.006 ms (30.9%) | 1.447x |
| 5 | 33.617 | 24.171 | 9.446 ms (28.1%) | 1.391x |

Unlike rain, this is real executed work: 38.8% of all density calls are
light-march probes, and removing it changes 18,062 pixels. It is a feature cost,
not dead weight, so it can only be reduced, not deleted.

## 6. Descriptor-count scaling

Fixed-work arms with the uploaded `StormLobeCount` capped. Only the fixed-work
ladder is used here, because it evaluates the same 64 points regardless of what
the storm looks like - capping descriptors in production context would change
coverage and make the timings incomparable. Capping does change what is drawn;
that is acceptable precisely because these arms do not depend on it.

| Descriptors | descriptor arm p50 | shape arm p50 |
|---|---|---|
| 1 | 0.5243 | 0.5693 |
| 2 | 0.5243 | 0.5693 |
| 4 | 0.5243 | 0.5693 |
| 6 | 0.5243 | 0.5693 |
| 8 | 0.7178 | 1.5892 |
| 10 | 0.7782 | 1.7951 |

**Flat to six descriptors, then a step.** Ten times the descriptors costs 1.48x
on the fetch arm and 3.15x on the shape arm - strongly sublinear, and not a
smooth curve. The plateau to six says the candidate structure is already
admitting only a subset per sample; the step at eight is a second group becoming
reachable.

For binning this cuts both ways. The shape arm does scale (0.569 -> 1.795), so
evaluating fewer lobes per sample is worth something: perfect binning down to
the one or two descriptors that can own a sample would recover about 1.23 ms of
the 4.89 ms fixed-work density call, **~25%**. But it is not the linear
relationship that would make binning obviously correct, and it is well behind
both rain and lighting.

## 7. Isolated versus production context

The task asked for this distinction explicitly, and it matters here.

| Class | Fixed-work (isolated) | Production context |
|---|---|---|
| precipitation | 54.5% of a density call - the cost of **executing** it | 32.7-36.7% of the frame - the cost of **carrying** it, since production never executes it |
| descriptor traversal | 15.7% of a density call | not separately measured in situ |
| lighting | not in the ladder (it is a march-level structure) | 28.1-32.3% of the frame |

The precipitation row is the clearest example of why the distinction was worth
insisting on: the isolated number measures something production never does, and
the production number measures something the isolated arm cannot see. Reporting
either alone would have been wrong.

## 8. Historical T136 comparison

T136/T137 ranked lighting at a 1.29x ceiling with ~1.10x representative
(`performance-internal-resolution.md`, Rank 4).

Post-T161 lighting measures **1.39x-1.48x**. The historical representative
figure no longer holds - lighting is a materially larger share now, because
T161 removed roughly two thirds of everything else. This is a concrete instance
of the general warning: **shares measured before T161 do not transfer.**

## 9. T153 re-derivation

**Not attempted.** Re-running the T153 oracle would have required its
ground-truth pass, its interval buffer and its own program family alongside this
campaign, which would have substantially expanded the task. It is left as an
explicit follow-up.

The historical 1.63x must not be quoted as a current number, for the same reason
the T136 lighting share no longer holds.

## 10. Dominant remaining cost class

**Dead code, then lighting. Not descriptor traversal.**

Approximate decomposition of the ~35 ms Ultra frame:

| Class | Share | Nature |
|---|---|---|
| carrying the unreachable precipitation path | **~35%** | dead weight, removable at zero visual cost |
| lighting (light-march probes) | **~30%** | real feature work |
| everything else, including all descriptor traversal | ~35% | of which descriptor work is ~16% of a density call |

This is **CASE C** by the task decision logic - descriptor traversal is not the
dominant contributor - with an outcome the case list did not anticipate: the
largest single class is code that never executes.

## 11. Ranked next architecture candidates

| Rank | Candidate | Measured upper bound | Likely retained | Complexity | Visual risk | Stacks |
|---|---|---|---|---|---|---|
| **1** | Compile the unreachable precipitation path out of `cloudDensity` | **1.49x-1.58x** | near all of it - it is deletion, not approximation | trivial | **none** (bit-identical) | yes |
| 2 | Reduce light-march work | 1.39x-1.48x if removed entirely | fraction; it is a real feature | moderate-high | real | yes |
| 3 | Descriptor binning / candidate reduction | ~25% of a density call | fraction | high | moderate | yes |

### Recommendation

**Do rank 1 next.** It is measured at ~1.53x mean, proven bit-identical against
the shipped renderer, and amounts to making `includePrecipitation` a
compile-time fact rather than a runtime parameter - by splitting the rain-free
evaluator out, or by removing the parameter now that every call site passes
`false`. It carries no visual risk because the code it removes cannot run.

One caveat to settle during that work: the parameter presumably exists because
something is expected to pass `true` eventually. If rain shafts are meant to be
sampled through `cloudDensity` in future, the fix should preserve that
capability behind a compile-time variant rather than delete it outright - which
is exactly the pattern T161 established.

**Do not build descriptor binning yet.** It ranks third, its scaling is
sublinear with a plateau, and both classes above it are larger and cheaper.

### Composition is not yet measured

Rain removal is dead-code elimination; lighting reduction removes real work.
They plausibly stack, but **this campaign did not measure them together** - a
combined arm was dropped from the program list to keep shader compile time down,
which in hindsight was the wrong economy. Building
`cloud_atmosphere_volume_t162_nolight_norain` is a cheap follow-up and would
settle whether ~1.53x and ~1.44x compose toward ~2.2x or overlap.

## 12. Resolution retesting

Current Ultra 0.25 costs 33.6-37.3 ms on this fixture. Previous valid
measurements: 0.375 = 60.91 ms, 0.50 = 98.97 ms.

| Question | Answer |
|---|---|
| Retest **0.375** after rank 1? | **Yes.** 60.91 / 1.53 = **~39.8 ms**, which is about what 0.25 costs today. Rain removal alone plausibly brings 0.375 into the range 0.25 currently occupies, and it is the first change in this program that would. |
| Retest **0.50** after rank 1? | **No.** 98.97 / 1.53 = ~64.7 ms, still far too high. |
| Retest 0.50 after ranks 1 and 2? | Worth a look if they stack: 98.97 / (1.53 x 1.39) = ~46.5 ms. Contingent on the composition measurement above. |

SC-006 remains 8 ms and remains unmet; nothing here rescopes it. Rank 1 alone
would take Ultra from ~4.4x the budget to ~2.9x.

## 13. Validation

- **T161 specialization invariant**: `T161 lean FINAL shader specializes and
  compiles` PASSED. FINAL defines none of `PA_T162_FIXED_WORK`,
  `PA_T162_NO_RAIN` or `PA_T162_ARM`, still bakes
  `PaDiagnosticLightingMode = 0`, and a dedicated gate
  (`T162 attribution arms compile and stay out of FINAL`) asserts all of that
  and compiles all nine arms on a real GL context.
- **No accidental selection**: every arm is reachable only through an explicit
  diagnostic program override, which `resetDefaults()` clears on every
  session/world transition and `selfCheck()` verifies.
- **Production image unchanged**: the anchor arm is the shipped lean FINAL
  program; the campaign changes no FINAL semantic, ladder value or morphology.
- **Evidence hygiene**: 44 qualification checks across 22 cells, **0 rejected**.

### Corrections made during this campaign

Two harness defects were found and fixed before any number here was trusted, and
both are recorded because each would have produced a confidently wrong answer:

1. The first image A/B captured each arm at the start of its own arm, minutes
   apart. The fixture drifted between captures and both arms reported the *same*
   maximum error - the signature of a moving fixture, not of the code under
   test. Fixed by capturing all three frames back to back.
2. The capture then ran *after* `applyT141Arm()`, which re-pins the program
   override every tick, so all three captures silently rendered the anchor and
   compared identical - including the lighting arm, which cannot be identical.
   That impossible result is what exposed it. Fixed by capturing before the
   re-pin.

The lighting arm showing a real difference is now the harness self-check: a run
where it compares equal is a broken run.
