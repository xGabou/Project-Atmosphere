# T170 - Primary march and per-sample descriptor cost

Branch `experiment/cloud-descriptor-k`, parent `11f9ee4`. Nothing merged.
Two campaign runs, 05Sep2026. Ultra, raw scale 0.2500, cloud target 480x270,
framebuffer 1920x1080, 96 steps, 60 samples per cell. Run 1: 14 arms x 2 poses
= 28 cells. Run 2: 18 arms x 2 poses = 36 cells. `T170_REJECTED count=0` in
both. All four drift controls stable. History disabled across the matrix.

Run 2 is the banked run: it carries `t169_stack_fast` as a within-run control,
which run 1 lacked.

## 0. Task 0 - the harness wiring invariant

### 0.1 What was built

`StormCampaignRegistry` is now the single declaration of all 15 campaigns -
marker file, predicate, per-run flag, arm table, routing class. It is
Minecraft-free so the sandbox can load it without a client.

Two dispatch sites became **derived** rather than written:

- `performanceRunRequested()` is now `StormCampaignRegistry.performanceMarkerPresent()`.
  This is the exact disjunction that lost T169.
- `programArmCampaign()` replaces the four-way `t166Run || t167Run || t168Run ||
  t169Run` chain that appeared at **six** sites and had T169 added to five.

One further defect was fixed in passing: `T166Arm.label()` did not include the
optimization mode, so T169's five T149 arms all labelled as `lean_final`,
collided with the anchor in the results map, and the decision block printed the
anchor's row five times. The label now carries the mode.

### 0.2 The invariant

`validateCampaignWiring()` reads the driver's own source and checks, against the
registry:

| Check | Catches |
|---|---|
| every `Path.of("*.txt")` marker in the driver is a registered campaign or a listed harness marker | a campaign declared but never routed |
| `performanceRunRequested()` still delegates to the registry | the hand-written chain returning |
| `poseGuardsArmed()` is exactly `return t141EvaluationRun;` | guards keyed to one campaign's flag |
| every arm campaign's table is referenced by `activeEvaluationArms()` | a declared, unreachable arm table |
| every arm campaign's flag appears in the `t141EvaluationRun` latch | a sweep running with guards disarmed |
| every campaign's predicate and flag exist in the driver | a registry row with no implementation |

Structural rather than behavioural because the driver cannot be loaded without a
client. That is enough to catch all three historical defects, which is the bar
it has to clear.

### 0.3 Proof it catches an omitted wire

`validateCampaignWiringCatchesOmissions()` mutates **the real driver source**
five ways and requires each to be rejected, then requires the unmutated source
to pass:

| Mutation | Reproduces |
|---|---|
| delete the `T170_OPTIMIZATION_ARMS` branch from `activeEvaluationArms()` | T169 gap 2 |
| add an unregistered `Path.of("t171-unregistered.txt")` | T169 gap 1 |
| `poseGuardsArmed()` returns `t166Run` | T167 |
| replace the derived routing with a hand-written chain | regression of the T169 fix |
| drop `t170Run` from the `t141EvaluationRun` latch | a sweep with guards disarmed |

```
T170_WIRING campaigns=15|armMatrices=11|violations=0
T170_WIRING_NEGATIVE mutations=5|allDetected=true
```

## 1. Task 1 - the footprint clamp. NOT ANSWERABLE, and the question was wrong

**`PA_ARM_FOOTPRINT_MAX` is not limiting SIDE, and the sweep cannot be measured
on this harness.** Two independent findings, both reproducible.

### 1.1 The clamp stops binding above ~5

Reference image comparison, SIDE, run 2. Arms are grouped by the value of
`PA_ARM_FOOTPRINT_MAX` they carry:

| Arms | meanAbsRGBA | rmsRGBA |
|---|---|---|
| `fpmax3`, `stack3` | 2.195255e-03 | 8.815518e-03 |
| `fpmax4`, `t169_stack_fast` | 2.204611e-03 | 8.879704e-03 |
| `fpmax5`, `fpmax6`, `fpmax8`, `stack`, `stack8` | **2.209734e-03** | **9.056396e-03** |

`fpmax5`, `fpmax6` and `fpmax8` render **byte-identical images**. Run 1 shows the
same collapse (all three at meanAbs 3.197e-03, 27344 changed pixels). Raising the
ceiling from 5 to 8 changes nothing, at either pose, in either run, because the
projected footprint multiplier `clamp(coefficient * t, 1.0, MAX)` almost never
reaches 5 before some other bound - the conservative clearance - takes over.

**There is no headroom in the ceiling to release.** The premise that MAX=4 limits
SIDE is false above 5; below 5 it binds, but see 1.2.

### 1.2 The clamp sweep is not measurable here

Those five SIDE arms produce identical output, so they perform identical work.
Their measured times in run 2:

| Arm | SIDE cloudP50 |
|---|---|
| `fpmax5` | 10.5677 |
| `fpmax6` | 11.9286 |
| `fpmax8` | 11.9890 |
| `stack` | 12.0637 |
| `stack8` | 12.2696 |

**A 13% spread across cells that render the same image.** That is the noise floor
for footprint arms, against an anchor whose own drift control is stable to 0.5%.

Run 1 and run 2 also invert at FAR. Same arms, same code, different runs:

| Arm | run 1 FAR speedup | run 2 FAR speedup |
|---|---|---|
| `fpmax4` | **2.591** | 1.533 |
| `fpmax5` | 1.538 | 1.630 |
| `fpmax6` | 1.537 | **2.884** |
| `fpmax8` | 1.526 | **2.823** |

The FAR results are bimodal - arms land in either a ~1.5x cluster or a ~2.85x
cluster - and which arm lands where is not a property of the arm. The anchors and
drift controls are stable in both runs, so this is specific to the footprint
path, not to the fixture or the harness.

**No footprint-clamp conclusion is banked from this campaign, and the clamp must
not be re-tuned on this data.** The T168/T169 value of P=0.75 with MAX=4 stands
unchanged.

## 2. Tasks 2/3/4 - per-descriptor attribution

Unlike the clamp arms, these deltas are large relative to the noise and
reproduce across both runs. Each arm removes exactly one stage of the group walk
and preserves iteration count and control shape.

Speedup vs the same run's anchor (`-` = faster):

| Arm | Stage removed | FAR r1 | FAR r2 | SIDE r1 | SIDE r2 | Evidence |
|---|---|---|---|---|---|---|
| `desc_nosdf` | exact descriptor SDF | 1.700 | 2.025 | 1.829 | 1.582 | DIRECT |
| `desc_constedge` | edge width | 1.496 | 1.731 | 1.463 | 1.655 | DIRECT |
| `desc_hoist` | edge width + ownership extents | 1.539 | 1.763 | 1.507 | 1.731 | DIRECT |
| `desc_cheapowner` | ownership ellipse extents | 1.021 | 1.025 | 1.068 | 1.035 | DIRECT |
| `desc_hardunion` | ordered smooth union | 0.996 | 1.028 | 0.998 | 1.011 | DIRECT |
| `desc_constfetch` | all 4 payload fetches | 0.857 | 1.003 | 1.172 | 0.986 | DIRECT, confounded |

### 2.1 Texture fetches are not the bottleneck

`desc_constfetch` replaces all four per-descriptor payload reads with one cached
read hoisted out of the walk, and returns **nothing**: 1.003x and 0.986x in run 2,
and it was *slower* than the anchor at FAR in run 1. At SIDE the walk issues
**2148 descriptor texel fetches per pixel** and removing essentially all of them
buys no time.

Confounded, and reported as such: with every descriptor identical the T121
conservative rejection changes behaviour, so the arm does not isolate fetch cost
cleanly. But the confound can only make the arm look *better* than pure fetch
removal - it removes real work as well as fetches - and it still returns nothing.
The conclusion is safe in the direction that matters.

**Answer to Task 3's fetch ceiling: ~1.0x. There is no bandwidth or latency
problem to solve.**

### 2.2 The arithmetic ceiling, and what of it is genuinely removable

The exact SDF is the largest single class (1.58-2.03x), but it is the thing
being computed - it cannot be removed, only approximated, and that is a
different campaign.

The second-largest is **removable without approximating anything**.
`stormEdgeWidthBlocksFromData(positionHeight, radiusRotation, shearMedia, role)`
takes **no sample position**. It is a pure function of the descriptor payload and
the role, so its value is fixed for the life of a frame's descriptor set. It is
nevertheless evaluated once per descriptor per density sample. At SIDE that is
259 evaluations per pixel of a value with at most 10 distinct results.

The ownership ellipse's two rotated `length()` extents are invariant in exactly
the same way, and worth a further 1.03-1.07x.

**Answer to Task 4: the hoistable set is edge width plus the ownership extents,
combined ceiling 1.73x at SIDE and 1.76x at FAR (run 2).**

| Candidate | Frequency today | Hoist level | Register risk | Cache/bandwidth | Measured opportunity |
|---|---|---|---|---|---|
| edge width | per descriptor per sample (259/px SIDE) | per descriptor per frame | none - fewer live values | +1 fetch, and 2.1 sets fetches are free | 1.655x SIDE |
| ownership extents | same | per descriptor per frame | none | same | 1.035x SIDE |
| role decode | same | per descriptor per frame | none | none | not isolated |
| smooth union | per admitted descriptor | not hoistable | - | - | 1.011x - nothing to win |

The two findings compose into a concrete design: **precompute edge width and the
ownership extents once per frame into spare descriptor-texture channels and read
them back.** That trades removable arithmetic for one extra fetch, and 2.1 says
fetches are free. It is image-identical by construction, unlike the arm that
bounds it.

T161's warning about register pressure applies to the implementation, not to the
direction: every candidate here *reduces* live values.

## 3. Task 5 - the primary march

Workload counters, 129,600 pixels. Captured with the program override released,
so these describe the pose's production workload, not the arm's - the counters
live behind debug views that every lean variant bakes off. **DIRECT for the pose,
not attributable per arm.**

| Per pixel | FAR | SIDE | ratio |
|---|---|---|---|
| primary ray steps | 28.62 | 30.83 | **1.08x** |
| density calls | 4.19 | 24.90 | **5.94x** |
| density calls per step | 0.146 | 0.807 | 5.53x |
| descriptor evaluations | 49.65 | 326.1 | 6.57x |
| descriptor texel fetches | 439.8 | 2438.3 | 5.54x |
| descriptor groups entered | 5.81 | 35.95 | 6.19x |
| lobes per group entered | 10.006 | 10.000 | 1.000 |
| descriptor evaluations per density call | 11.85 | 13.10 | 1.11x |
| fetches per density call | 105.0 | 97.9 | 0.93x |
| pixels terminating early | 2.9% | 18.8% | - |

Run 2 reproduces the shape: 28.62 and 30.15 steps per pixel, lobes per group
entered 10.000.

**SIDE does not take more steps. It takes 1.08x the steps and evaluates density
on 81% of them against FAR's 15%.** The per-density-call cost profile is nearly
identical between the poses - 11.85 vs 13.10 descriptor evaluations, 105 vs 98
fetches - which rules out "close range makes each call more expensive."

T168's finding holds exactly: 10.000 lobes per group entered, so groups are still
entered whole.

**Answer to Task 5: C, both - but they are different questions.** The *pose gap*
is entirely call count. The *absolute cost* at either pose is dominated by
per-call arithmetic, a third of which is provably invariant. Reducing calls
closes the gap between poses; hoisting closes the gap to the budget.

## 4. Task 6 - the stack

Run 2, all within-run.

| Arm | FAR P50 | FAR P95 | SIDE P50 | SIDE P95 | <=10 ms | <=8 ms |
|---|---|---|---|---|---|---|
| anchor `lean_final` | 14.2152 | 15.4757 | 21.4036 | 23.1342 | no | no |
| `t169_stack_fast` (banked) | 9.2989 | 9.4525 | 12.2122 | 13.3775 | no | no |
| `t170_stack` (MAX 6) | 4.9623 | 5.3668 | 12.0637 | 13.9551 | no | no |
| `t170_stack8` (MAX 8) | 4.9900 | 5.3545 | 12.2696 | 14.1128 | no | no |
| `t170_stack3` (MAX 3) | 9.5150 | 9.7075 | 12.6392 | 14.6104 | no | no |
| **`t170_stack_hoist`** (ceiling) | **4.0724** | **4.2885** | **7.3052** | **7.7558** | **yes** | **yes** |

Every shippable stack sits at 12.06-12.64 ms at SIDE. Their differences are
inside the 13% noise floor established in 1.2 and none of them is distinguishable
from `t169_stack_fast`. **T170 produced no shippable improvement on T169's banked
stack.**

`t170_stack_hoist` is the stack plus the descriptor hoist, and it **clears both
budgets at both poses** - SIDE 7.3052 P50 / 7.7558 P95. It is a **ceiling, not a
candidate**: the arm substitutes a constant edge width and its image is
correspondingly wrong (SIDE meanAbs 1.606e-02 against the stack's 2.210e-03, a
7.3x degradation). A real hoist computes the same edge width once per descriptor
per frame and is image-identical, so the timing is the honest target and the
image is not.

### 4.1 Quality of the shippable arms

SIDE, reference comparison against `lean_final`, 129,600 pixels,
`epsilon=4.882813e-04`:

| Arm | maxAbs | meanAbs | rms |
|---|---|---|---|
| `t169_stack_fast` | 9.917e-01 | 2.205e-03 | 8.880e-03 |
| `t170_stack` | 9.897e-01 | 2.210e-03 | 9.056e-03 |
| `t170_stack3` | 9.883e-01 | 2.195e-03 | 8.816e-03 |
| `t170_stack_hoist` (ceiling) | 9.902e-01 | 1.606e-02 | 9.065e-02 |

The shippable stacks are indistinguishable from each other in quality as well as
in time. `t169_stack_fast` and `t170_fpmax4` render **identical** SIDE images,
which independently reconfirms T169's result that `PA_ARM_DETAIL_FOOTPRINT 1.0`
and `PA_ARM_LIGHT_STEPS 4` change nothing at this pose.

## 5. Decision

**CASE C.** Descriptor texture fetches are cheap - 2148 per pixel at SIDE and
removing them buys nothing - and repeated per-sample arithmetic dominates. The
recommended next step is to hoist the invariant descriptor arithmetic, not to
change the descriptor payload architecture (CASE B is ruled out by 2.1).

- **Too many calls or too expensive calls?** Both, at different scopes. The
  FAR/SIDE gap is 5.94x density calls at 1.08x the steps. The absolute cost is
  per-call arithmetic, a third of which is invariant.
- **Next dominant bottleneck:** the per-descriptor invariant arithmetic inside
  `directStormGroupField` - edge width first, ownership extents second.
- **Recommended production candidate:** `t169_stack_fast`, unchanged from T169.
  Nothing measured here beats it, and the clamp sweep is not trustworthy enough
  to move MAX off 4.
- **Recommended next architecture:** precompute edge width and ownership extents
  per descriptor per frame into the descriptor texture and fetch them. The
  ceiling for that change is `t170_stack_hoist`: **SIDE 7.31 ms, which is the
  first configuration in this line to clear 8 ms at the binding pose.**

Before that is built, the footprint arms' 13% noise needs a cause. Two arms that
render identical images must not differ by 13%, and until that is understood no
march-level result from this harness is safe to bank.

## 6. Harness notes

- Peak benchmark-owned memory 7,099 MB; minimum system availability 3,807 MB;
  **0 Full GCs** across both runs; client capped at `-Xms1G -Xmx4G` against
  stock ergonomics of 508 MB / 7.93 GB.
- `cloudP50`/`cloudP95` are GPU timer queries and are banked. `frameP95` is not.
- Anchors differ between runs by 4.6% (FAR) and 10.0% (SIDE), so **only
  within-run ratios are used**. Run 2 carries the T169 control for this reason.

## 7. Status

Not merged, not committed by this campaign. Working tree carries the T170 arms,
the campaign registry and its invariant, the T169 wiring fixes, the arm-label
fix, and the opt-in benchmark heap flags.
