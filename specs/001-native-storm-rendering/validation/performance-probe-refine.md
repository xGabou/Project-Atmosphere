# T181 - The cap binds, capping pays a little, and T180's refinement number was wrong

Branch `experiment/cloud-descriptor-k`, parent `738b5d7` (T180). Nothing merged.
One campaign 08Sep2026, Ultra, raw scale 0.2500, cloud target 480x270,
framebuffer 1920x1080, 96 steps, 60 samples per cell, three anchor-bracketed
blocks per arm per pose. 74 cells, `T181_REJECTED count=0` on anchor drift.

## 1. Headline

**A correction first. T180 attributed 32.0% of group walks to the march's
union-distance refinement. That was wrong.** T180 identified the call site by
elimination and assigned it the whole untagged residual. T181 tags the site
directly: the refinement runs **1.50 events per pixel** and about **1.62 group
walks per pixel**, roughly **4.7%** of walks - not 32%. The rest of that residual
is still unattributed, and naming it is the main open item.

**The refinement is also not removable.** `t181_norefine` measures **0.9454x at
SIDE** - slower - with 7,607 changed pixels. Removing ~4.7% of group walks costs
5.5% of frame time, because the clearance it computes is what lets the march take
a coarse stride at all. It earns its cost.

**The probe cap does bind, contrary to my own prediction**, and capping it is a
real but minor win: **cap 4 measures 1.0850x SIDE / 1.1056x FAR** with cloud
SSIM **0.9969** and 454 changed pixels.

## 2. Tasks 1 and 3 (items 5-13) - the probe cap sweep

| Pose | Arm | blocks | ratioMean | spread | verdict |
|---|---|---|---|---|---|
| SIDE | `t181_probe8` | 3 | 1.0605 | 1.68% | accepted |
| SIDE | **`t181_probe4`** | 3 | **1.0850** | 1.36% | **accepted** |
| SIDE | `t181_probe2` | 3 | 1.0704 | 0.82% | accepted |
| SIDE | `t180_noprobe` (T180) | 3 | 1.1972 | 1.15% | accepted |
| FAR | `t181_probe8` | 3 | 1.0836 | 3.18% | REJECTED_ratio_spread |
| FAR | **`t181_probe4`** | 2 | **1.1056** | 1.06% | **accepted** |
| FAR | `t181_probe2` | 2 | 1.0286 | 2.11% | accepted |

**The curve is non-monotonic and peaks at 4, at both poses.** 16 -> 8 -> 4 gains,
4 -> 2 loses again. Two effects pull against each other: fewer probes is less
descriptor work, but a shorter scan advances the ray less
(`paLastEmptyOffset` shrinks) and costs march iterations. Four is where they
balance.

**Capping captures at most 43% of the no-probe ceiling** - 0.085 of 0.197 at
SIDE. The remaining 57% is unavailable to any cap, because it is the work of
probes that genuinely run and genuinely earn their advance.

### The cap binds, heavily

I predicted before the run that 16 would rarely be reached, since the loop exits
on the first material hit or at the span end. **That was wrong**, and the
distribution says so plainly:

| SIDE | value |
|---|---|
| scan events | 71,363 (0.55/px) |
| **probes per scan** | **10.91** |
| **scans reaching the cap without finding material** | **53.11%** |
| scans finding material | 41.27% |
| scans using 1-2 probes | 17.5% |
| scans using 3-4 probes | 5.5% |
| scans using 5-8 probes | 9.3% |
| **scans using 9-16 probes** | **64.8%** |

FAR is the same shape and stronger: 12.38 probes per scan, 61.9% cap-bound,
75.7% in the 9-16 bucket.

So **16 is not an excessive limit that never binds - it is a limit the scan hits
on most invocations**, and lowering it genuinely truncates work.

## 3. Task 2 (item 12) - control damage

| Arm | pose | cloud SSIM | edge SSIM | silhouette IoU | thin | hole | meanAbs | changed px |
|---|---|---|---|---|---|---|---|---|
| `probe8` | SIDE | 0.997312 | 0.988735 | 0.997719 | 0.9247 | 0.9811 | 1.117e-04 | 430 |
| **`probe4`** | SIDE | **0.996860** | 0.984310 | 0.997836 | 0.9355 | 0.9937 | 1.501e-04 | 454 |
| `probe2` | SIDE | 0.993548 | 0.978879 | 0.997364 | 0.9247 | 0.9937 | 2.444e-04 | 597 |
| `probe8` | FAR | 0.996735 | 0.998585 | 0.998557 | 0.9688 | 1.0000 | 1.320e-05 | 93 |
| `probe4` | FAR | 0.996573 | 0.998577 | 0.998316 | 0.9531 | 1.0000 | 1.404e-05 | 106 |
| `norefine` | SIDE | - | - | - | - | - | 2.040e-04 | **7,607** |

**Quality does not collapse.** Cap 4 holds cloud SSIM at 0.9969 and silhouette
IoU at 0.9978. For scale, T178 rejected light3 at cloud SSIM **0.884** - these
arms are two orders of magnitude closer to the reference on that metric.

The degradation that does appear is orderly: thin retention drifts 0.925-0.935,
hole retention stays above 0.98, and changed pixels rise 430 -> 454 -> 597 as the
cap falls. Nothing falls off a cliff between 16 and 2.

**Control-path consequences** were measured through the scan counters rather than
as separate metrics: probes per scan, cap-bind fraction and material fraction are
in section 2. Missed material intervals and falsely skipped material were **not**
measured as distinct quantities - the changed-pixel and thin/hole retention
figures are what stands in for them, and I am labelling that rather than implying
a dedicated measurement.

## 4. Tasks 4, 5 and 7 (items 14-23) - the refinement

**Task 7, semantics.** The refinement is a **single** `directStormShape` call per
event, not an iterative solve. It reads the union distance and the minimum
clearance at the current march position, then forms
`paSafeAdvance = paMinClearance - STORM_MAX_BLEND_BLOCKS`. If that exceeds a fine
step the ray takes a coarse stride; otherwise the empty-span scan runs.

Classification: **C, clearance correction.** It needs a conservative *distance*,
never a density. **There is no iteration count**, so Task 6's sweep has nothing
to sweep - the correct answer to that task is that the premise does not hold.

**Task 4, attribution:**

| SIDE | value |
|---|---|
| refinement events | 193,867 = **1.50/px** |
| group walks per event | ~1.09 |
| refinement group walks | ~210,535 = **1.62/px**, ~**4.7%** of walks |

The walk figure is **DERIVED** by differencing T180's untagged bucket
(1,440,052) against T181's (1,229,517) once the refinement was tagged out of it.
That is a cross-session subtraction and is labelled as such; the event count
itself is measured directly.

**Task 5, the removal ceiling - mandatory, and it is negative:**

| Pose | `t181_norefine` | spread | verdict |
|---|---|---|---|
| SIDE | **0.9454x** | 2.36% | accepted |
| FAR | 1.0247x | 1.34% | accepted |

**Removing the refinement makes SIDE 5.5% slower.** Without the clearance there
is no coarse stride, so the ray falls into the scan path on every iteration. The
32% of descriptor work T180 thought was here does not exist, and the ~4.7% that
is here is load-bearing.

This is **CASE E**: a consumer whose removal ceiling underperforms its share -
except that here the share itself was the error, and the execution behaviour is
straightforwardly explained by losing the advance.

## 5. Task 8 (items 24-26) - duplication

Now measured, after T180 left it open.

The scan samples "exactly the lattice the fine march would have sampled", so a
scan that finds material is followed by a fine march over that same lattice. Every
probe spent in such a scan is duplicated by construction.

| | SIDE | FAR |
|---|---|---|
| scans finding material | 41.27% | 32.03% |
| **probes spent in those scans** | 158,723 | 38,561 |
| **wasted probe fraction** | **20.39%** | 18.02% |

**Close caching.** One fifth of probe work is duplicated, and probes are 18.6% of
exact SDFs, so a perfect probe cache addresses about **3.8%** of exact SDF work -
below the level at which T179 and T180 showed selective, per-lane mechanisms
return anything. Exact repeated positions and near-duplicate positions were not
enumerated individually; the structural argument plus the wasted-probe count is
what the ceiling rests on.

## 6. Tasks 9 and 10 (items 27-36) - stack

| Pose | Arm | ratioMean | spread | verdict | session-local p50/p95 |
|---|---|---|---|---|---|
| SIDE | `t172_stack_pre` (control) | 1.6651 | 4.65% | REJECTED | 12.029 / 13.398 |
| SIDE | **`t181_stack_probe8`** | 1.9766 | 0.72% | **accepted** | **10.097 / 10.663** |
| SIDE | within-block vs control | 1.1913 | 5.31% | REJECTED_ratio_spread | - |
| FAR | `t181_stack_probe8` | 2.4959 | 1.61% | accepted | **4.982 / 5.587** |

The stack with the scan capped at 8 reads **10.097 ms p50** session-local, and its
within-block gain over the control is **1.1913x but rejected on 5.31% spread** -
the control itself spread 4.65% at SIDE and 60.31% at FAR, so the comparison is
not sound this session.

| Target | Status |
|---|---|
| SIDE <= 10 ms locally | **marginal** - 10.097 p50, and the composition ratio is rejected |
| SIDE <= 8 ms locally | **no** |
| FAR <= 8 ms locally | **yes** - 4.982 |

The stack arm used cap 8, chosen before the sweep ran. **Cap 4 measured better
standalone**, so the composition of the best cap with the stack is **not
measured** - a gap created by having to fix the arm table in advance.

## 7. Decision (items 37-41)

**37. Probe verdict: cap 4 is a minor production candidate.** 1.0850x SIDE /
1.1056x FAR, cloud SSIM 0.9969, IoU 0.9978, 454 changed pixels. It lands in the
brief's **1.03-1.10x "minor but potentially stackable"** band, not the >=1.10x
strong band - at SIDE. It is quality-safe by a wide margin.

**38. Refinement verdict: closed.** Removal is 0.9454x. It is 4.7% of walks, not
32%, and it pays for itself by enabling the coarse stride.

**39. Combined uniform-work verdict.** Only one of the two produced a candidate,
so there is nothing to stack this campaign. The uniform-reduction thesis still
holds - every arm here that removed work uniformly moved the clock in proportion,
unlike T179's per-lane pruning - but this particular class is now nearly spent:
cap 4 captures 43% of a 1.20x ceiling and the rest is load-bearing.

**40. Remaining dominant workload: unattributed.** After tagging primary, light,
probe, bracket and refinement, **27.7% of group walks at SIDE still carry no
consumer tag** (1,229,517 of 4,438,355), and `directStormShapeCalls` exceeds
`cloudDensityCalls` by 2.30 million. T180 assumed that residual was the
refinement; it is not. **This is the single most important open question.**

**41. Recommended T182:**

1. **Find the untagged 27.7%.** Tag every remaining `directStormShape` entry -
   including the conditional path inside `cloudDensity` itself - until the buckets
   close against `directStormShapeCalls` rather than only against
   `cloudDensityCalls`. Do not infer a call site by elimination again; T180 did
   and was wrong.
2. Only then decide whether it is worth attacking.
3. If cap 4 is to ship, measure it **on the stack** - this campaign fixed the
   stack arm at cap 8 before knowing the sweep result.

## 8. Harness notes

- `T181_REJECTED count=0` on anchor drift; all rejections are ratio spread.
- `T175_WORKLOAD_VIEWS declared=29|allEnabled=true` covers views 49-51.
- `T170_WIRING campaigns=26|violations=0`, negative proof detects all five
  mutations.
- Consumer tagging changed between T180 and T181: the refinement moved from the
  untagged bucket to tag 5, which is why the two runs' `otherGroupWalks` differ
  and why the refinement walk count is a difference rather than a direct read.
  A direct counter for tag 5 would have been better and is the obvious fix.

## 9. Status

Not merged. Productionization `4b34b12`; T170 `9239077`, T171 `aff6fc7`,
T172 `85ff4ef`, T173 `81476b3`, T174 `3af2015`, T175 `3cfd1f1`, T176 `b047bc3`,
T177 `f868325`, T178 `cb1197b`, T179 cleanup `42e655d`, T179 `655bec1`,
T180 `738b5d7`.
