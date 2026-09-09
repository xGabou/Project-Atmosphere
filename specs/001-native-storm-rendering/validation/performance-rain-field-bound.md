# T192 - the bound is sound, the field is closed

Branch `experiment/cloud-descriptor-k`, base `63e3cf0` (T191). Nothing merged.
One campaign 09Sep2026, Ultra, raw scale 0.2500, cloud target 480x270,
framebuffer 1920x1080, 96 steps, 60 samples per cell, three anchor-bracketed
blocks per arm per pose, 25 arms x 2 poses. `T192_REJECTED count=0`.

## 1. HARD GATE: FAIL. The rain-field line is CLOSED.

| Gate | Required | Measured | Verdict |
|---|---|---|---|
| Quality | not the T188 10-16% false-rain class | 4.32-5.30% false rain | pass |
| **Performance** | **net SIDE >= 1.10x** | **1.0792** (spread 3.92%) | **FAIL** |

Per the brief's own rule - quality passes but net SIDE is below 1.10x - **rain
field optimization closes completely. There is no sixth campaign.**

The closed form did everything it was asked to do. It is sound, it is cheap, and
it made the field measurably faster than T190's classifier. It was not enough,
and the reason is arithmetic that was already on the table.

## 2. A correction that framed this task

T190's report carried the section header "what killed it was the classification".
Its own body gave fallbacks at **+2.52 ms** against classification at
**+0.62 ms** - the fallback is **four times** the classification. The header
contradicted the numbers beneath it, and this brief inherited the error as
"fallback cost is not the dominant problem."

That set the budget before any code was written. SIDE anchor ~32.7 ms; net 1.10x
needs <= 29.7 ms; T189's field-only total was 28.25 ms. So classification **plus**
fallback had about **1.47 ms** to fit into, and T190 spent 3.14. Eliminating
classification *entirely* still leaves 2.52 ms, which is net ~1.063.

The closed-form bound attacks the 0.62. It could never reach 1.10 by itself, and
it didn't.

## 3. Task 2 - the derivation

`paRainColumnOwnedExact` owns a column when
`dot((worldXZ - centre) / radii, itself) <= 1` for any descriptor. **The scaling
is per-axis and strictly positive**, so an axis-aligned cell stays an
axis-aligned box after scaling, and the minimum of `u2 + v2` over such a box is
closed form: per axis, zero if the interval spans zero, and the nearer endpoint
squared otherwise.

`min > 1` for every descriptor therefore **proves** the whole cell unowned. One
squared inequality per lobe. No sampling grid, no repeated
`directStormRainSupportAt`, no exact SDF, no noise, no square root, and no
division that could not be hoisted.

**Only the dry direction is used, and that is the load-bearing decision.** The
converse - a cell entirely inside an ellipse - would prove ownership only if the
ellipse test were equivalent to `ownsDescriptorGroup`, and it is not: ownership
also requires the group union to carry coverage there. "Inside the ellipse" is a
**superset** of "owned", so using it as SAFE OWNED would be unsound in exactly
the direction that invents rain. The complement is a proof; the implication is
not.

A provably dry cell needs nothing else checked, because its stored triple is
exactly right everywhere in it: ownership false throughout, no coverage so
`directSupport` zero throughout, and `localRainSupportAt` discards `stormBaseY`
entirely for an unowned column, taking `weatherBaseY` - which never came from the
field.

## 4. Task 3 - the bound is proven, not inspected

| Quantity | Value |
|---|---|
| trials | 40,000 |
| proven dry | 12,699 |
| not provable | 27,301 |
| **false-safe** | **0** |

Random ellipse/cell pairs at the field's real scale, deliberately placed to
straddle the boundary, each checked against a 25x25 sample grid. Both directions
are asserted non-vacuous, so a bound that proved nothing - or everything - would
also fail. Zero false-safe was required before any performance measurement, and
it was obtained before any was taken.

**In the campaign the bound proves 82.97-87.58% of cells dry**, which is the
triage rate it was designed to deliver.

## 5. Tasks 4 and 7 - it is genuinely cheaper, and it is not enough

| | T190 sampled | **T192 closed form** |
|---|---|---|
| **field build p50, SIDE** | 0.8684 ms | **0.6021 ms** |
| field build p50, FAR | 0.8141 ms | 0.4895 ms |
| cells needing corner samples | 100% | **12.4-17.0%** |
| fallback fraction | 2.91% / 5.34% | 3.67% / 5.44% |
| **net SIDE** | 1.0437 (accepted) | **1.0792** (spread 3.92%) |

**`T192_CLASSIFIER_COST` = 1.0313 at SIDE, accepted at 2.45% spread**
(1.0240 at FAR, accepted). The closed form makes the whole field **3.1% cheaper**
than T190's classifier, and cuts the build by **31%**.

Two things are worth stating precisely. First, the triage removes corner sampling
from ~85% of cells but only cuts the build by 31%, because the bound itself is
not free: ten lobes at three descriptor texel fetches each, on every one of
262,144 cells, costs about as much as one full evaluation pass. Second, the
fallback rate did not fall - **the bound changes what classification costs, not
what it decides**, which is exactly what it was designed to do and exactly why it
cannot reach the gate.

## 6. Task 6 - quality, and a confirmation

| Pose | arm | rain IoU | false rain | missed rain | thin ret. | continuity | new gaps |
|---|---|---|---|---|---|---|---|
| SIDE | `t190_rain_mask_safe` | 0.942487 | 5.299% | 0.757% | 0.9746 | 0.9809 | 25 |
| SIDE | **`t192_rain_mask_bound`** | **0.942487** | **5.299%** | **0.757%** | 0.9746 | 0.9809 | 25 |
| FAR | `t190_rain_mask_safe` | 0.955959 | 4.324% | 0.270% | 0.9884 | 0.9919 | 3 |
| FAR | **`t192_rain_mask_bound`** | **0.955959** | **4.324%** | **0.270%** | 0.9884 | 0.9919 | 3 |

**Byte-identical to T190, at both poses.** That is the intended result and a
direct confirmation of the design claim: the closed form reaches the same
decision more cheaply rather than a different decision. Ownership disagreement is
**0**, mean support error 0.0010, mean attach error 0.0123 blocks.

False rain at 4.3-5.3% is well clear of T188's 10-16% failure class, but it is
still not the correctness class of the exact reference. The performance gate
fails outright regardless, so quality is not what decides this.

## 7. Task 9 - the stack, recorded but not banked

| Pose | ratio | spread | verdict | session-local cloud + field |
|---|---|---|---|---|
| SIDE | **1.1802** | **2.50%** | **accepted** | 14.27 + 0.60 = **14.87 ms** |
| FAR | 1.3042 | 46.06% | REJECTED | 7.26 + 0.49 = **7.75 ms** |

The SIDE stack composition is accepted at 1.1802 - the cleanest stack number of
the series. **It is recorded and not banked**, because the brief gates the stack
on the hard gate passing, and it did not.

| Target | Result |
|---|---|
| SIDE <= 10 ms locally | no (14.87 ms) |
| SIDE <= 8 ms locally | no |
| FAR <= 8 ms locally | yes (7.75 ms) |

## 8. A T191 defect this campaign found

Two full 57-minute runs died as
`infrastructure_invalid:world_entry_lost_during_T135_SAMPLE` with zero cells
recorded, and I attributed the first to memory pressure. It was not.

**T191 scoped shader registration by each program's own name prefix, on the
assumption that a campaign only selects its own arms.** That assumption was false
when it was written: arm matrices deliberately reuse earlier campaigns' programs
as controls, and T190's own matrix already did it with `T172_STACK_PRE`. T192's
matrix selects `T190_FIELD_SAFE` as the classifier it must beat and
`T172_STACK_PRE` as its control. Neither campaign was armed, so neither program
was registered, `volumeShader` returned null, and the renderer session-disabled
into a lost world connection.

T190 ran before scoping existed. **T192 was the first campaign to run under it,
and it broke immediately** - which is the useful part: the failure was loud,
reproducible and total rather than a silent wrong number.

Registration now takes the **union** of prefix-matched programs and every program
an armed campaign's arm tables can actually select, resolved by reflection over
those tables rather than a hand-maintained map - a map would be one more thing to
forget the next time a campaign borrows a control. After the fix:
`registered=7 skipped=135 crossCampaignControls=8`. The invariant now enumerates
cross-campaign controls and fails the build if registration returns to
prefix-only.

## 9. Decision

**38. HARD GATE: FAIL** - net SIDE 1.0792 against a required 1.10x.

**39. Do not productionize the rain field.**

**40. The rain-field line is CLOSED.** Five campaigns establish the shape of it
completely:

| | net SIDE | false rain |
|---|---|---|
| T188 approximate | 1.1158 | 12.36% |
| T189 discrete ownership | 1.1228 | 12.36% (unchanged) |
| T190 conservative sampling | 1.0437 | 2.99% |
| **T192 closed-form triage** | **1.0792** | **5.30%** |

The architecture's problem is not any one classifier. **The field is cheap where
it does not matter and expensive where it does.** Away from storms it is free and
provably exact; on the owned columns where rain actually is, its support value is
the one quantity that cannot be bounded cheaply - the union is eroded by noise -
so correctness there costs a fallback, and fallbacks there are frequent enough to
consume the gain. The closed-form bound removed the last avoidable cost and the
remaining one is intrinsic.

**Task 10 - variants removed from active campaign use.** The T192 marker is
removed and no campaign is armed. T191's scoping means every rain-field arm now
costs nothing on an ordinary startup - `registered=0 of 142` - so they are inert
without being deleted. All declarations, generated variants and evidence stay in
the tree, per the brief.

**42. Fresh dominant workload.** The accepted production path has no rain field,
so its attribution is the pre-field one. The most recent production-path capture
is T190's run C, one task ago on the same fixture: **rain segment 55.26%, light
tap 17.10%, empty-span probe 15.77%, primary body 7.30%, refinement 3.29%, rain
shaft 1.27%**, accounting closed with nothing untagged.

I did not re-capture it in this session, and should say why rather than present a
number I did not take: every counter capture in this campaign ran with the field
forced onto the monolith, which makes them post-field by construction. A
production-path capture needs a run with that force off, and spending another
40 minutes on it for a line that is closing was the wrong trade.

**43. Next task: a fresh production-path SIDE attribution as its first action**,
then the largest remaining *non-rain* uniform consumer - light tap and empty-span
probe, together about a third of the descriptor work. Rain stays the largest
single consumer and is now out of scope: five campaigns have priced it, and both
the micro-optimization line (T186) and the field line (here) are closed.

## 10. Validation

- `T192_OWNERSHIP_BOUND trials=40000|provablyDry=12699|notProvable=27301|
  falseSafe=0|sqrtFree=true|dryDirectionOnly=true`
- `T192_REJECTED count=0`; `T191_VARIANT_SCOPE crossCampaignControls=8|
  productionAlwaysLoaded=2|ordinaryStartupPrograms=2`
- `T190_CONSERVATIVE_FALLBACK`, `T189_DISCRETE_OWNERSHIP`, `T188_FIELD_SOURCE`,
  `T188_FINAL_CLEAN`, `T188_RAIN_MASK_HARNESS` all green.
- `T170_WIRING campaigns=35|armMatrices=31|violations=0`,
  `T175_WORKLOAD_VIEWS declared=44|allEnabled=true`, `T182_CONSUMER_TAGS 9/9`,
  `T183_RAIN_RENDER programs=3`, `T145_RAIN_GATE` false negatives 0.
- Variants **140 -> 143** declared; **2** compiled on an ordinary startup.

## 11. Status

Not merged. T186 `443e742`, T187 `6f315fa`, T188 `9394ac7`, T189 `31c13bb`,
T190 `1deb75f`, T191 `63e3cf0`.
