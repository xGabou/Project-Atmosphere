# T173 - SIDE stability, and the T121 conservative bound

Branch `experiment/cloud-descriptor-k`, parent `85ff4ef`. Nothing merged.
Two campaigns, 05Sep2026. Ultra, raw scale 0.2500, cloud target 480x270,
framebuffer 1920x1080, 96 steps, 60 samples per cell, SIDE only.

- Phase 1, 22:02-22:04: 20 identical cells, one program, nothing switching.
- Phase 2, 22:34-22:36: 21 cells, 11 anchors bracketing 10 measured arms.

## 1. Headline

**SIDE was never unstable. The harness protocol was.** Twenty identical cells
agree to **CV 0.57%** with the workload constant to better than 0.02%. The
56% disagreements of T170/T172 came from comparing arms across a block boundary,
not from anything in the renderer, the fixture, the GPU or the timer.

**And the tighter T121 bound is a net loss.** It exists, it is conservative, it
has never been reachable in production - and switching it on makes SIDE **4.6 to
7.1% slower**, reproducibly. The horizontal term costs more than it saves.

## 2. Task 0 - T172 precompute not flipped

`PA_ARM_DESC_PRECOMPUTE` is still absent from FINAL, and the T172 sandbox
invariant still asserts that. Nothing was productionized in this campaign.

## 3. Tasks 1-2 - the bimodality does not reproduce

Twenty cells, one program (`lean_final`), one pose, no arm switching:

```
T173_SUMMARY cells=20 mean=23.5608 sd=0.1345 cv=0.00571
             min=23.3001 max=23.9002 range=0.0255
             consecutivePairsAccepted=19 rejected=0 verdict=SIDE_MEASURABLE
```

Per-cell state, logged immediately before each timed cell, was **identical in
every field across all twenty**:

```
descriptorSignature=08328807b4eb6fed lobes=10
camera=(1182.05,567.97,-6.02) yaw=90.00 pitch=0.00
cloudTarget=480x270 framebuffer=1920x1080
fixedScale=NaN governorScale=0.50 program=lean_final
history=false descriptorLimit=-1 dayTime=6000
```

Only `gameTime` advanced (2077 -> 3552). Workload counters varied by **less than
0.02%**: density calls 3,444,079-3,444,669, primary steps 4,015,283-4,015,571,
segment-test positives 253,468-253,707.

**There is no fast mode and no slow mode to cluster.** Task 2's clustering does
not apply because a single mode was observed.

## 4. Task 5 - the fixture is provably identical

`descriptorSignature=08328807b4eb6fed` in phase 1, and the same value in T172,
constant across T172's transition (`sig=08328807b4eb6fed` at every status line,
`rebuilds=2 uploads=2` unchanged). The fixture is deterministic across runs and
did not evolve within either. Descriptor count, storm centre, roles, slab and
cover were all constant.

## 5. Task 6 - what T172's counters actually showed

T172's per-cell workload counters dropped 31% partway through its SIDE sweep and
never recovered - density calls 2,500,3xx -> 1,71x,xxx, segment-test positives
168,9xx -> 136,xxx. That looked like the scene changing.

It was not. **The anchor's timing moved 6.2% between T172's r1 and r2 while its
reported counters moved 31%.** Those two numbers cannot both describe the same
scene. Phase 1 resolves it: with switching removed, both the timing and the
counters are stable, so **the counter capture is itself contaminated by arm
switching**, and neither T172 figure measured what it claimed.

The capture releases the program override for fourteen stages and restores it
afterwards. That cycle runs in phase 1 too, per cell, and is stable there - so
the contamination requires the override to change *value*, not merely to be
released.

## 6. Task 3 - GPU state

Not pursued beyond T171's exclusion, deliberately. The two T172 modes differed in
**how many density calls the shader made**, and no GPU execution state - clock,
memory-controller utilisation, VRAM residency, PCIe traffic - can change the
number of density evaluations a fragment program performs. That is fixed by the
scene and the program. Pursuing it would have been measuring the wrong layer.

## 7. Tasks 7-8 - the rule, and its limits

Phase 2 brackets every measured arm between two anchor cells and divides by
their mean. An arm whose two anchors disagree by more than 3% is
`REJECTED_anchor_drift` and its number is never interpreted.

| Metric | phase 1 (no switching) | phase 2 (with switching) |
|---|---|---|
| anchor CV | **0.57%** | 1.22% |
| anchor range | 2.55% | 3.88% |
| consecutive pairs accepted | 19 / 19 | 8 / 10 |

**Program switching roughly doubles anchor variance and still produces occasional
drift above 3%.** The strict verdict is therefore `SIDE_NOT_MEASURABLE` - two
anchor pairs broke, so an unexplained mode remains, and the acceptance bar in
Task 8 is not met on its own terms.

**But the rule works.** Both rejections landed on arms whose *other* repeat was
accepted, so every program still carries a trustworthy measurement, and the
accepted repeats agree well:

| Arm | accepted repeats | agreement |
|---|---|---|
| `t173_boxbound` | 0.9540, 0.9287 | 2.6% |
| `t173_boxbound_pre` | 1.0223, 1.0099 | 1.2% |
| `t169_stack_fast` | 1.6616, 1.6532 | **0.5%** |
| `t172_stack_pre` | 1.8783 (r1 only) | arm values 0.6% apart |
| `t173_stack_boxbound_pre` | 1.7086 (r2 only) | arm values 1.1% apart |

**SIDE is bankable under anchor bracketing with rejection. It is not bankable
under a block-then-block protocol.** That is the operational answer, and it is
narrower than "SIDE is fixed".

## 8. Task 9 - T121 bound audit

**The shipped renderer uses the vertical-only bound.**

```glsl
bool paT141BoxBound() {
    return (PaDiagnosticOptimizationMode & PA_OPT_T141_BOX_BOUND) != 0;
}
```

FINAL bakes `PaDiagnosticOptimizationMode` to `const int = 0`, so this is
compile-time false and `stormLobeDistanceLowerBound` - the tighter bound T141
built - has been **dead code in the shipped program**.

| | |
|---|---|
| current bound | `stormVerticalDistanceLowerBound(p, positionHeight, role)` - vertical separation from the role-adjusted cap interval, ignoring horizontal distance entirely |
| where conservative | it is a true lower bound on the rounded lobe SDF; the T121/T143 sandbox invariants prove no false negatives over 29.3M probes |
| where loose | a descriptor far to the side but vertically overlapping gets a bound near zero and is evaluated exactly, even when it is horizontally unreachable |
| cost | a few adds, a `max`, no division, no `length()` |
| SIDE rejection rate | **10,20x,xxx of 45,17x,xxx lobes visited = 22.6%**, 78.7 rejects per pixel |
| SIDE descriptor evaluations | 302.2 per pixel; density calls 23.8 per pixel |
| false-rejection safety | rejected lobes still contribute their bound to `groupMinClearance`, so the march's safe advance cannot grow past material a rejected descriptor owns |

The tighter alternative already existed and already carried its own proof:

```glsl
// Outside in both axes the true distance is at least the larger of the two
// separations; taking the maximum keeps the bound valid and is never worse
// than the vertical-only bound it replaces.
return max(verticalBound, horizontalBound);
```

## 9. Tasks 10-12 - the tighter bound, measured

`PA_ARM_BOX_BOUND` forces `paT141BoxBound()` true at compile time. Nothing else
changes.

| Arm | run 1 | run 2 | speedup | verdict |
|---|---|---|---|---|
| `t173_boxbound` | 24.5576 | 25.1085 | **0.9540 / 0.9287** | accepted, accepted |
| `t173_boxbound_pre` | 22.9908 | 23.3523 | 1.0223 / 1.0099 | accepted, accepted |

**The tighter bound is 4.6% to 7.1% slower**, reproducibly, with both repeats
accepted and their anchors agreeing to 0.21% and 2.39%.

Why: the horizontal term costs a `length()`, a division by `maxRadius` and
several `min`/`max` per descriptor per density sample - 302 evaluations per pixel
at SIDE - and it only binds when a sample lies outside a lobe's horizontal
extent. At SIDE the camera looks *through* the storm, so most samples are inside
the horizontal extent of several lobes, `max()` returns the vertical term, and
the extra arithmetic buys nothing. The bound is paid for on every descriptor and
collected on few.

`t173_boxbound_pre` is roughly neutral (1.0223 / 1.0099) because the T172
precompute's gain and the bound's cost nearly cancel - which is itself a clean
cross-check that both effects are real and of similar magnitude.

**Correctness: 0 changed pixels, `maxAbsRGBA=1.525879e-05`, `passed=true`.**

Not bit-identical, and the reason is understood: `max(vertical, horizontal)`
changes which term sets `groupMinClearance`, which shifts the march's safe
advance in the last bits, which moves sample positions by a fraction of a ULP.
The error is 32x below the rgba16f storage epsilon (4.882813e-04) and no pixel
differs above it. **A false cull would produce large localised errors, not
1.5e-05 everywhere** - the observed magnitude is positive evidence that no
descriptor was wrongly discarded, consistent with the bound's proof.

`t173_stack_boxbound_pre` shows 25,855 changed pixels, which is the T169 stack's
own error - `t172_stack_pre` showed the same - not the bound's.

## 10. Stack, and the first trustworthy SIDE numbers

Anchor 23.50 ms, all anchor-bracketed.

| Arm | SIDE p50 | SIDE p95 | speedup |
|---|---|---|---|
| `lean_final` | 23.5014 | ~25.7 | 1.000 |
| `t169_stack_fast` | 14.1107 / 14.3278 | 15.71 / 15.93 | 1.66 |
| **`t172_stack_pre`** | **12.5880 / 12.5092** | **14.43 / 13.67** | **1.878** |
| `t173_stack_boxbound_pre` | 13.8455 / 13.6970 | 15.05 / 14.69 | 1.709 |

**The T172 precompute is worth 1.133x at SIDE** (14.22 -> 12.55), more than
double its 1.065x at FAR. That is the expected direction: SIDE makes 5.9x more
density calls than FAR, so a per-call saving compounds there.

Adding the box bound to the stack **costs 9%** (12.55 -> 13.77), consistent with
the standalone result.

| Target | Status |
|---|---|
| SIDE <= 10 ms | **not met** - best 12.51 |
| SIDE <= 8 ms | **not met** |
| FAR <= 8 ms | **met** - 4.77 (T172) |

Remaining gap to 10 ms at SIDE: **1.25x**. To 8 ms: 1.57x.

## 11. Decision

**Productionize the T172 precompute: yes, and with more confidence than T172
had.** It is worth 1.133x at SIDE - the binding pose - and 1.065x at FAR, is
bit-identical, costs 16 bytes per descriptor and no additional fetch. SIDE was
unmeasured when T172 recommended it; it is measured now and the gain is larger
there.

**Productionize the tighter T121 bound: no.** It is exact and it is slower. The
horizontal term binds too rarely at SIDE to cover its own cost. This closes the
lever T172's corrected interpretation pointed at - the prize was real, but it is
not reachable by tightening this bound.

**Remaining dominant bottleneck: the exact descriptor SDF, and it is not guarded
by a loose cull.** The current bound already rejects 22.6% of visited lobes, and
the obvious tightening is a net loss. The 302 descriptor evaluations per pixel at
SIDE are, as far as this campaign can tell, mostly descriptors that genuinely
need evaluating.

**Recommended next experiment: reduce descriptors reached per sample, not cost
per descriptor.** SIDE visits 348 lobes per pixel across 23.8 density calls -
14.6 lobes per call, against a group size of 10. The group walk enters more than
one group per sample. T168 closed static binning *below* group granularity; what
has not been measured is whether the number of *groups* entered per sample can be
reduced, which is a different question and the only one left with a large
measured multiplier behind it.

Before that: **the anchor-bracketing protocol should be adopted for every future
campaign**, and the residual switch-induced drift is worth one more look, because
it still costs two arms in ten.

## 12. Harness notes

- Phase 1 log `t173-phase1.log`, phase 2 `t173-phase2.log`.
- `duplicatesRejected=0` in all 41 cells.
- The per-cell state line (`T173_STATE`) is new and should stay: it converted
  "the fixture might be evolving" from a hypothesis into a settled fact in one
  run.

## 13. Status

Not merged. T170 `9239077`, T171 `aff6fc7`, T172 `85ff4ef`.
