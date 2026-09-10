# T190 - the fallback works, and it costs the gain it was protecting

Branch `experiment/cloud-descriptor-k`, parent `31c13bb` (T189). Nothing merged.
One campaign 08Sep2026, Ultra, raw scale 0.2500, cloud target 480x270,
framebuffer 1920x1080, 96 steps, 60 samples per cell, three anchor-bracketed
blocks per arm per pose, 25 arms x 2 poses. `T190_REJECTED count=0`.

## 1. Headline

**The conservative fallback does what it was designed to do, and it is still not
enough.**

| | T189 approximate | **T190 conservative** | |
|---|---|---|---|
| false rain, SIDE | 15.22% | **2.99%** | 5.1x better |
| false rain, FAR | 10.75% | **2.51%** | 4.3x better |
| rain IoU, SIDE | 0.8632 | **0.9675** | |
| rain IoU, FAR | 0.9029 | **0.9755** | |
| mean onset error, SIDE | 4.12 blocks | **0.79 blocks** | 5.2x better |
| newly broken shafts, SIDE | 39 | **20** | |
| **net gain, SIDE** | **1.1575** | **1.0230** | |
| **net gain, FAR** | **1.1451** | **1.0360** | |

**Quality improved on every metric. The net gain fell below the level at which
the brief says the architecture is worth having.** SIDE 1.0230 is under the
`<1.03x - architecture no longer worthwhile` line; FAR 1.0360 is in the
`1.03-1.05x borderline` band. And false rain did not reach zero, which was the
acceptance target.

## 2. Task 1 - what actually causes the disagreement

This was measured before the classifier was designed, because T188 designed
against an assumed cause and its fix changed zero pixels.

| Cause, of 129,600 sampled columns | SIDE | FAR |
|---|---|---|
| **ownership differs** | **0** | **0** |
| support-cutoff side differs | 111 | 63 |
| attach height differs > 1 block | 37 | 29 |
| **total** | **148 (0.114%)** | **92 (0.071%)** |

**Ownership disagreement is now exactly zero** - view 63 confirms it
independently at `fieldOwnershipDisagreements=0`, against T189's 32 and 50. The
classifier eliminated the ownership component completely.

Everything that remains is **support-cutoff crossing and attach-height
variation**: cells where the rain-existence decision changes strictly inside the
cell, between the centre and the four corners the build sampled. Five points do
not prove uniformity, and this is the price of that.

Per-column error fell from T189's 0.224% to 0.114%, and false rain fell 5.1x -
the amplification is steeply non-linear at these rates, which is consistent with
an OR over ~60 samples.

## 3. Tasks 2 and 3 - what was built

**Safe cell**: ownership, the side of the 0.01 support cutoff, and attach height
to within 1 block are all uniform across the centre and four corners.
**Mixed cell**: any of those three differs. Mixed cells are answered by
`directStormRainSupportAt` - the production path, not a second approximation.

Weather and morphology are deliberately excluded from the classification:
`localRainSupportAt` samples those directly and they never come from the field,
so they cannot be a source of field error.

**Task 9 - representation.** Unchanged: RGBA32F, 16 bytes per texel, 4 MB, one
texture. T188 wrote alpha as a constant `1.0` marker that nothing read, so the
certainty flag cost **no extra channel, no extra texture and no extra byte**.
The hot lookup is one `texelFetch`, one comparison, and a rare exact fallback.

## 4. Tasks 4 and 5 - the rates that decide it

| Quantity | SIDE | FAR |
|---|---|---|
| field cells | 262,144 | 262,144 |
| **mixed cells (sampled census)** | **1.42%** | **2.04%** |
| safe lookups | 2,002,113 | 2,002,113 |
| **fallback lookups** | **113,002** | 113,002 |
| **fallback fraction** | **2.91%** | 5.34% |
| fallbacks per pixel | 0.184 | 0.184 |

The mixed-cell fraction is small and the fallback rate is small. **The
amortization survives**: 262,144 cells plus 113,002 fallback traversals against
2,115,115 lookups is still 5.6 ray evaluations per descriptor evaluation, down
from T188's 8.0 but nowhere near breaking the trade.

**So the fallback rate is not what killed the gain.**

## 5. Task 6 - what killed it was the classification

| Cost | T189 | T190 | delta |
|---|---|---|---|
| **field build p50, SIDE** | 0.2437 ms | **0.8684 ms** | **+0.62 ms** |
| field build p50, FAR | 0.2335 ms | 0.8141 ms | +0.58 ms |

**Classification costs 3.6x the base build**, because proving a cell uniform
means evaluating its four corners as well as its centre - five full
`directStormRainSupportAt` calls per cell instead of one.

The rest went to the march. At SIDE the conservative arm costs 30.53 ms of cloud
time against T189's 28.01, so the 113,002 fallbacks add about 2.5 ms. Together:

```
T189:  28.01 cloud + 0.24 build = 28.25    net 1.1575
T190:  30.53 cloud + 0.87 build = 31.40    net 1.0230
```

**`T190_CORRECTNESS_COST`**, measured in the same blocks, is the cleanest figure
in the campaign: **0.9042 at SIDE, accepted at 0.14% spread** (0.9031 at FAR,
rejected at 4.50%). Correctness costs **9.6%** of the field's cost, which
consumes two thirds of a 15% gain.

## 6. Task 7 - performance

| Pose | arm | blocks | net ratio | spread | verdict |
|---|---|---|---|---|---|
| SIDE | `t189_field_nearest` | 2 | 1.1575 | 1.58% | accepted |
| SIDE | **`t190_field_safe`** | 1 | **1.0230** | - | REJECTED_insufficient_blocks |
| FAR | `t189_field_nearest` | 3 | 1.1451 | 2.55% | accepted |
| FAR | **`t190_field_safe`** | 2 | **1.0360** | 2.15% | **accepted** |

Applying the well-established 0.9042 correctness cost to T189's accepted 1.1575
gives a SIDE net of **1.047**, which is the better-supported figure than the
single surviving block's 1.0230. Either way it is under the 1.05 line.

## 7. Task 11 - stack

| Pose | arm | blocks | ratio | spread | verdict |
|---|---|---|---|---|---|
| SIDE | `t190_stack_safe` vs `t172_stack_pre` | 3 | **1.0761** | **1.49%** | **accepted** |
| FAR | same | 3 | 1.0057 | 45.96% | REJECTED |

**The SIDE stack composition is accepted for the first time since T181** - the
control finally held still (0.45% spread). It says the conservative field adds
**7.6%** over the T172 stack, which is real but modest.

Session-local: SIDE 14.15 cloud + 0.87 field = **15.02 ms**; FAR 7.90 + 0.81 =
**8.72 ms**. Anchors ran 32-33 ms this session, so these absolutes are not
comparable to other sessions and are not the result.

| Target | Result |
|---|---|
| SIDE <= 10 ms locally | no |
| SIDE <= 8 ms locally | no |
| FAR <= 8 ms locally | no (8.72 ms) |

## 8. Task 8 - the three campaigns

| | T188 | T189 | **T190** |
|---|---|---|---|
| false rain SIDE | 12.36% | 15.22% | **2.99%** |
| missed rain SIDE | 0.45% | 0.54% | **0.36%** |
| rain IoU SIDE | 0.8860 | 0.8632 | **0.9675** |
| ownership disagreement | 0.02% | 0.02% | **0** |
| fallback rate | 0 | 0 | 2.91% |
| **net SIDE** | **1.1158** | **1.1228** | **1.0230-1.047** |

T188 was fast and wrong. T189 corrected the diagnosis and changed nothing
measurable. **T190 is the first version that is substantially right, and it
retains only about a third of the gain.**

## 9. Decision

**Verdict: do not approve, and do not iterate on this classifier.**

Both failures have the same cause, and it is the *instrument*, not the
architecture: **sampling five points is simultaneously too expensive to be cheap
and too weak to be a proof.** It costs 3.6x the build and still leaves 0.114% of
columns wrong because variation strictly inside the cell is invisible to it.

**The attribution says exactly what to do instead**, and it is not another
threshold:

1. **Ownership needs no sampling at all - it needs a bound.** Ownership is an
   exact ellipse test, and the scaling in `paRainColumnOwnedExact` is per-axis,
   so a cell's axis-aligned box stays axis-aligned in scaled space. The minimum
   and maximum of `dot(scaled, scaled)` over that box are therefore closed-form:
   `max < 1` proves the cell entirely owned, `min > 1` proves it entirely
   unowned, and anything else is genuinely mixed. That is **provably**
   conservative rather than sampled, and it costs one cheap loop over ten lobes
   instead of four full union evaluations. Ownership disagreement is already 0
   with sampling; this makes it 0 by construction *and* returns most of the
   0.62 ms.
2. **The residual is support-cutoff and attach-height variation**, 0.114% of
   columns. Bound those separately or accept them - but they are now a named,
   measured, 148-column problem rather than an unexplained 12%.

If that combination lands the build back near 0.3 ms while keeping the fallback
rate under 3%, net SIDE returns to roughly 1.12-1.14 with false rain near zero,
and the architecture is worth having. If it does not, the field should be closed:
three campaigns have now shown the performance is real and the correctness is
expensive, and a fourth that only moves the cost around would not change that.

**Task 10 - the resolution sweep stays withheld.** It was gated on 512 being
quality-correct, and 512 is not: 2.5-3.0% false rain remains. A coarser field
raises both the mixed-cell rate and the residual, so the sweep would measure a
worse version of an unsettled question.

**Task 12 - post-field attribution is not re-run**, because the field is not
accepted. T188's light 38.41% / probe 37.96% stands as the last valid
measurement, and per the brief no other renderer optimization should be selected
until the field passes.

## 10. Harness

- `T190_REJECTED count=0`; `T190_CONSERVATIVE_FALLBACK classifiedAtBuild=true|
  exactFallback=true|flagInSpareAlpha=true|noNewTexture=true|
  compiledOutOfFinal=true`.
- The certainty flag is a **uniform, not a define**, because the diagnostic
  monolith is the only program that can read workload counters; behind a define
  it would have written an unclassified field and every mixed-cell counter would
  have read zero for want of classification rather than for want of mixed cells.
- The classifier reads its cell grid from the weather map, because the field is
  the render target of the pass writing it and sampling a bound target is
  undefined. The invariant asserts the two are the same size.
- `T170_WIRING campaigns=34`, `T175_WORKLOAD_VIEWS declared=44`.
- **Shader variants 137 -> 140.** Three added: the field, its stack and the
  rain-only capture. The prune remains its own task and is now overdue.

## 11. Status

Not merged. T185 `4c2941e`, T186 `443e742`, T187 `6f315fa`, T188 `9394ac7`,
T189 `31c13bb`.
