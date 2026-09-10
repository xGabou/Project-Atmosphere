# T174 - Group entry, and the precompute in production

Branch `experiment/cloud-descriptor-k`. Productionization banked as `4b34b12`,
parent `81476b3`. Nothing merged. Campaign 06Sep2026, Ultra, raw scale 0.2500,
cloud target 480x270, framebuffer 1920x1080, 96 steps, 60 samples per cell.
34 cells: 9 anchors bracketing 8 arms, at SIDE then FAR. `T174_REJECTED count=0`.

## 1. Headline

**The precompute is in production and measurably working: 1.09x on the bare
anchor at both poses, image-identical.** The SIDE anchor moved 23.50 -> 20.6 ms
and FAR 13.09 -> 12.4 ms.

**The group-entry line clears its gate and still cannot close SIDE.** Skipping
every group beyond the first is worth **1.136x** at SIDE - above the 1.10x gate,
below the 1.255x the 10 ms target needs. And it is *exact*: the ceiling arm is
bit-identical to the anchor, so groups 2+ are entered but never change the
result.

**The exact SDFs inside groups 2+ cost nothing.** Removing them is worth 0.99x.
T121 already culls them. What groups 2+ actually cost is the walk itself.

## 2. Productionization

`PA_ARM_DESC_PRECOMPUTE` is now in FINAL's defines and in the four T140 oracle
variants. The precompute and the old derivation are `#if`/`#else` branches, so
the recomputation is **not compiled into FINAL** rather than left live beside it.

The T172 invariant is inverted, not removed: FINAL must now *define* the
precompute, must not define either isolated half, and every T140 oracle variant
must carry it. `cloud_atmosphere_volume_t174_no_precompute` is FINAL's exact
specialization minus the precompute, and exists so the shipped path can be
measured against itself.

**Proof FINAL consumes the precomputed fields** - the control is image-identical
and measurably slower:

| Pose | control run 1 | run 2 | repeat | precompute worth |
|---|---|---|---|---|
| SIDE | 22.6191 | 22.2781 | 1.52% | **1.079-1.104x** |
| FAR | 13.5127 | 13.6059 | 0.69% | **1.090-1.096x** |

`maxAbsRGBA=0.000000e+00`, **0 changed pixels**, at both poses. Same image,
~9% less time: the define is doing something, which a define-presence check
could not have established.

One consequence caught by the invariants rather than by review: the T171
program-identity controls are asserted byte-identical to FINAL, and FINAL
changing broke that immediately. They now track FINAL's specialization. Had they
drifted, every future program-identity control would have measured a shader
difference while claiming to measure linking.

## 3. Baseline

| Pose | anchor (productionized FINAL) | before productionization |
|---|---|---|
| SIDE | **~20.6 ms** | 23.50 (T173) |
| FAR | **~12.4 ms** | 13.09 (T172) |

All 16 arms accepted on anchor drift (worst 2.56%). Bracketing held throughout.

## 4. Task 1 - group attribution

Workload counters, 129,600 pixels, captured per cell:

| Per density call | SIDE | FAR |
|---|---|---|
| groups entered | **1.443** | 1.388 |
| lobes visited | 14.43 | 13.89 |
| **lobes per group entered** | **10.000** | **10.000** |
| descriptor evaluations | 13.03 | 11.80 |
| T121 rejects before exact SDF | 2.675 (**18.5%**) | 3.96 (**28.5%**) |

| Per pixel | SIDE | FAR |
|---|---|---|
| primary steps | 30.83 | 28.61 |
| density calls | **25.37** | 4.16 |
| density calls per step | **82%** | 15% |

T168's exactly-10.000 lobes per group entered holds again: groups are entered
whole.

**The 1/2/3+ histogram was not obtained.** It needs new counters and a new debug
view, and the ceiling arm answers the decision question directly without it, so
the plumbing was not built. What the data does establish is the average - 1.443
groups per density call at SIDE, so roughly 44% of calls enter beyond the first -
and, more importantly, that whatever the distribution is, the *whole* prize is
1.136x. Reported as a gap rather than inferred.

## 5. Tasks 2 and 4 - the ceilings

Both ceilings carry FINAL's specialization and differ from it only at group
level. Both are anchor-bracketed and repeated.

| Arm | SIDE r1 | SIDE r2 | repeat | SIDE speedup | FAR speedup |
|---|---|---|---|---|---|
| `first_group_only` | 18.0357 | 18.2712 | 1.30% | **1.1389 / 1.1340** | 1.1201 / 1.1296 |
| `group2_no_sdf` | 20.9961 | 20.6858 | 1.49% | 0.9836 / 0.9969 | 0.9809 / 1.0025 |

### 5.1 Ceiling A: the entire group-entry prize is 1.136x

Processing only the first entered group - removing the candidate scan, the
ten-descriptor walk, the bounds, the exact SDFs and the group union for every
group beyond it - returns **1.136x at SIDE** and 1.125x at FAR, with both
repeats agreeing to 1.3% and 0.2%.

### 5.2 The ceiling is exact, which is the surprise

`first_group_only` renders **bit-identical** to the anchor: `maxAbsRGBA=0.0`,
0 changed pixels, at both poses.

That was expected to be visually invalid. It is not, and the reason is the
finding: **on this fixture, groups beyond the first are entered ~44% of the time
and never change the density result.** Their contribution is always outside the
group-level smooth-union blend radius. A group-entry rejection would therefore be
image-preserving here, not a tradeoff.

This is a single-fixture result and should not be generalised to storms whose
groups genuinely overlap. It is a statement about what the ceiling costs, not a
correctness proof for a future bound.

### 5.3 Ceiling B: the exact SDFs in groups 2+ cost nothing

Entering groups 2+ but replacing their exact SDF with the bound returns
**0.9836 / 0.9969** at SIDE - neutral to fractionally slower, and below the
measurement floor either way.

So the 1.136x is **not** exact-SDF work. T121 already rejects those descriptors
before evaluation. What groups 2+ cost is the walk overhead itself: four texel
fetches, the decode, the ownership test and the bound, for ten descriptors that
contribute nothing - about 4.4 lobes per density call.

The gap between the two ceilings is the entire answer to Task 2: **group entry
costs 1.136x; group contents cost nothing.**

## 6. Task 3 - there is no group metadata to reject on

Audited before designing anything. Everything the shader knows before
`directStormGroupField`:

```glsl
vec4 candidates = stormCandidatesAt(p.xz);   // per-XZ-tile descriptor indices
int witnessIndex = decodeStormCandidate(candidates, rank);
int groupSlot = stormDescriptorGroupSlot(witnessIndex);
if ((groupVisited & groupBit) != 0) continue;  // dedupe only
```

A witness descriptor index, a group slot, and a **2D XZ candidate tile**. No
group bounding sphere, no AABB, no vertical range, no precomputed extents.
`stormGroupFirstIndex`/`EndIndex` return an index range and nothing else. The
only conservative bound in scope is `paStormColumnOutside`, which is whole-storm.

**Every option Task 5 lists would have to be invented and maintained**, not
enabled. That is buildable - T172 showed per-frame CPU precompute into spare
descriptor channels is cheap and exact - but it is new architecture, not a
switch.

## 7. Task 7 - stack: rejected, not banked

| Pose | run 1 | run 2 | repeat disagreement | verdict |
|---|---|---|---|---|
| SIDE `t172_stack_pre` | 10.6967 | 12.5020 | **15.56%** | **REJECTED_repeat** |
| FAR `t172_stack_pre` | 8.1316 | 4.7780 | **51.96%** | **REJECTED_repeat** |

Both pairs passed the anchor-drift check and failed the repeat check, so the rule
rejects them. No stack number is banked from this run.

For context and not as a substitute: each pose's r2 matches prior independent
campaigns closely - SIDE 12.5020 against T173's 12.5092 (0.06%), FAR 4.7780
against T172's 4.7698 (0.17%) - while r1 is the outlier at both poses. That is
consistent with the unresolved switch-induced bimodality, which remains
contained by bracketing and not fixed. **Cherry-picking r2 because it agrees
with what was expected is exactly what the rule exists to prevent**, so it is
not done.

Working figures from banked campaigns remain SIDE ~12.5 ms and FAR ~4.77 ms.

## 8. Targets

| Target | Status |
|---|---|
| SIDE <= 10 ms | **not met** - best banked ~12.5, gap 1.25x |
| SIDE <= 8 ms | **not met** - gap 1.57x |
| FAR <= 8 ms | **met** - ~4.77 |

**The decisive arithmetic: a perfect group-entry filter is 1.136x. Applied to
12.5 ms that gives ~11.0 ms. SIDE still misses 10 ms even at the ceiling**, and a
real conservative bound would capture only a fraction of it while adding a
per-group test and new precomputed metadata.

## 9. Decision

**Group-entry verdict: the line is real, exact and insufficient.** It passes the
1.10x gate at 1.136x, and it would be image-preserving on this fixture - but it
cannot reach the target alone, and it costs new per-group metadata to attempt.
Closing it for the purpose of hitting 10 ms; recording it as a genuine 1.136x
ceiling available later, and the only remaining lever in the descriptor walk.

Against the stated cases this is closest to **CASE B without the bound having
been built**: the oracle is meaningful but the practical structure has to be
invented, and even at its ceiling it does not clear the target. It is explicitly
**not CASE D** - samples do not need multiple groups; the extra groups contribute
nothing at all.

**Remaining dominant cost: the number of density calls at SIDE.** 25.37 per pixel
against FAR's 4.16, on nearly the same step count - 30.83 against 28.61. SIDE
evaluates density on **82% of its primary steps**; FAR on 15%. Per-call cost has
now been mined about as far as it goes: T172 shipped the invariant arithmetic,
T170 showed fetches are free, T173 showed the tighter per-lobe bound is slower,
and T174 shows group contents cost nothing.

**Recommended next: empty-space skipping / clearance quality, not per-call cost.**
The march advances by `minDescriptorClearance`, which is fed by the same
conservative bounds. A larger safe advance means fewer density calls, and that is
the one quantity with a 6x pose gap behind it. Note that T173's box bound would
have *improved* clearance and still lost on arithmetic - so the target is a
better clearance that is cheap, or a clearance computed at group granularity
rather than per lobe, which is where the group metadata from section 6 would
actually pay.

## 10. Harness notes

- The campaign was killed by the host's low-memory watchdog after all 34 cells
  and both decision blocks had been written; the data is complete and the
  orphaned client was stopped by hand. Peak client 5.3 GB against 3.5 GB free.
- One self-inflicted invalid gate: a background `check build` was raced against a
  foreground `compileJava`, both writing `build/`, producing a spurious
  `NoClassDefFoundError` on an unrelated invariant. Reset to HEAD and redone
  cleanly rather than reasoned about.
- The T123 instrumentation invariant caught a real defect in this change: the
  group ordinal added for ceiling B was an unguarded per-sample increment in the
  production march. It now lives behind the arm's own define and outside the
  `pa*` counter namespace, so production does not carry it.

## 11. Status

Not merged. Productionization `4b34b12`; T170 `9239077`, T171 `aff6fc7`,
T172 `85ff4ef`, T173 `81476b3`.
