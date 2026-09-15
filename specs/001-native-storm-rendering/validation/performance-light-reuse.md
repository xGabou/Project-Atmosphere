# T177 - Group reuse is closed, and the fixture has only one group

Branch `experiment/cloud-descriptor-k`, parent `b047bc3` (T176). Nothing merged.
One campaign 06Sep2026, Ultra, raw scale 0.2500, cloud target 480x270,
framebuffer 1920x1080, 96 steps, 60 samples per cell. 74 cells, six programs,
three anchor-bracketed blocks per arm per pose. `T177_REJECTED count=0` on
anchor drift.

## 1. Headline

**Primary-to-light group reuse is closed.** The hard ceiling - every light tap
forced to reuse the primary sample's group - measures **0.6873x at SIDE** and
**0.6708x at FAR**. It is 45% *slower*, not faster, and both readings are stable
(ratio spread 1.24% and 0.85% across three blocks).

The reason is structural and is now measured rather than argued:
**production already performs 0.9997 descriptor group walks per light tap.**
That is the floor. Reuse cannot remove a group walk because there is never a
second group to skip.

**And a fixture limitation that has been invisible until now: this fixture
contains exactly one descriptor group.** `lobesVisited / groupFieldCalls =
10.0000` against `StormLobeCount = 10`. Reuse validity therefore measures
100.00%, but trivially so - see section 3 before using that number for anything.

**The one accepted gain: flat 4 -> 3 light taps is worth 1.0634x on the stack**,
measured within-block so the timing mode cancels, spread 0.48% across three
blocks. It independently corroborates T176's 1.0701x.

## 2. Task 3 (report items 3-7) - the paired protocol, and what it caught

Each arm is bracketed `anchor, arm, anchor` and repeated three times. Acceptance
is agreement between the three **local ratios**, not between absolute readings.

**Local anchor variance is small: SIDE CV 0.84% over 36 anchor cells, FAR CV
2.97%.** The anchor is not what moves.

| Pose | Arm | blocks | ratioMin | ratioMax | ratioMean | spread | verdict |
|---|---|---|---|---|---|---|---|
| SIDE | `t177_reuse_hard` | 3 | 0.6836 | 0.6921 | **0.6873** | 1.24% | accepted |
| SIDE | `t176_light3` | 3 | 1.0542 | 1.0639 | **1.0588** | 0.91% | accepted |
| SIDE | `t176_light2` | 3 | 1.1366 | 1.1428 | **1.1402** | 0.54% | accepted |
| SIDE | `t172_stack_pre` | 3 | 1.7413 | 1.9508 | 1.8786 | 11.15% | **REJECTED_ratio_spread** |
| SIDE | `t176_stack_light3` | 3 | 1.8504 | 2.0698 | 1.9937 | 11.00% | **REJECTED_ratio_spread** |
| SIDE | `t177_stack_reuse_hard` | 3 | 1.2699 | 1.4663 | 1.4006 | 14.02% | **REJECTED_ratio_spread** |
| FAR | `t177_reuse_hard` | 2 | 0.6680 | 0.6737 | **0.6708** | 0.85% | accepted |
| FAR | `t176_light3` | 2 | 1.0107 | 1.0297 | **1.0202** | 1.86% | accepted |
| FAR | `t176_light2` | 2 | 0.9091 | 1.0463 | 0.9777 | 14.03% | **REJECTED_ratio_spread** |
| FAR | `t172_stack_pre` | 3 | 1.5283 | 2.5412 | 1.8743 | 54.04% | **REJECTED_ratio_spread** |
| FAR | `t176_stack_light3` | 1 | - | - | 1.5082 | - | **REJECTED_insufficient_blocks** |
| FAR | `t177_stack_reuse_hard` | 3 | 1.0236 | 1.9128 | 1.3233 | 67.20% | **REJECTED_ratio_spread** |

### Do sticky modes still appear? Yes, and they are program-specific

The anchor holds at CV 0.84% while the stack arms swing 11-14%. So the mode is
**not** a machine-wide state that moves everything together - if it were, the
anchor would move too and the ratio would be stable. It attaches to particular
programs.

That refines T176's reading rather than confirming it. T176 inferred a machine
mode from FAR arms straddling two clusters; T177 shows the anchor sitting still
through the same swings.

### Do paired ratios survive mode changes? Only the slow-drift kind

Anchor-relative pairing did **not** rescue the stack arms - it rejected them,
correctly. But a stronger pairing does work at SIDE. Comparing an arm against
`t172_stack_pre` **within the same block** (measured seconds apart, same mode):

| Pose | Arm vs stack | b1 | b2 | b3 | mean | spread |
|---|---|---|---|---|---|---|
| SIDE | `stack_light3` | 1.0658 | 1.0607 | 1.0637 | **1.0634** | **0.48%** |
| SIDE | `stack_reuse_hard` | 0.6535 | 0.8385 | 0.7424 | 0.7448 | 24.84% |
| FAR | `stack_light3` | 0.6015 | 1.7135 | 0.9597 | - | 101.87% |

The SIDE stack blocks show why. `stack_pre` reads 10.3526 / 11.5098 / 10.2973 and
`stack_light3` reads 9.7137 / 10.8513 / 9.6809: **block 2 is slow for both, by
almost exactly the same factor**, so the within-block ratio is stable to 0.48%
even though each arm's absolute value moves 11%.

At FAR it fails, because there the switching is per-cell rather than per-block -
`stack_pre` reads 4.99 / 8.29 / 7.95 while `stack_light3` reads 8.30 / 4.84 /
8.29, landing in opposite modes within the same block. **Within-block pairing
cancels slow drift, not per-cell mode switching.** Switch-induced bimodality is
not solved.

## 3. Task 1 (items 8-10) - reuse validity, and why the number is not usable

| Per pose | SIDE | FAR |
|---|---|---|
| light taps classified | 1,458,641 | 215,205 |
| taps resolving no group | 439 | 59 |
| **primary group sufficient** | **1,458,641 (100.00%)** | **215,205 (100.00%)** |
| primary group partial, another needed | **0 (0.00%)** | 0 (0.00%) |
| primary group wrong | **0 (0.00%)** | 0 (0.00%) |
| sufficient at ordinal 1 / 2 / 3 / 4 | 364,746 / 364,696 / 364,733 / 364,466 | uniform |
| **groups entered per light tap** | **0.9997** | **0.9997** |

Validity is flat across all four tap ordinals, so it does not decay with
distance from the originating sample (ordinal is distance here: taps are at 14,
28, 42 and 56 units along `LightDir`).

**This is 100% for a reason that makes it uninformative.** The fixture's storm is
a single descriptor group of ten lobes: `lobesVisited / groupFieldCalls` is
exactly 10.0000 and `StormLobeCount` is 10. With one group in the world, every
sample resolves the same group and reuse is valid by construction. The
interesting cases - a tap crossing into a neighbouring storm's group - **do not
occur on this fixture and were therefore not tested.**

I am reporting the measurement the brief asked for, and reporting that it cannot
carry the conclusion it was meant to support. A multi-storm fixture would be
needed to answer the validity question properly. It is not worth building,
because section 4 closes the line on cost grounds regardless of validity.

## 4. Tasks 2 and 3 (items 11-15) - the reuse ceiling

`t177_reuse_hard` skips the light tap's candidate resolution entirely and walks
the primary sample's group directly.

| | SIDE | FAR |
|---|---|---|
| paired ratio | **0.6873x** | **0.6708x** |
| ratio spread | 1.24% | 0.85% |
| session-local p50 | 29.37 ms | 18.39 ms |
| group walks avoided per pixel | **0.00** | 0.00 |
| descriptor work avoided | **none** | none |
| image meanAbs | 1.472e-03 | 1.678e-04 |
| image maxAbs | 1.375e-01 | 1.277e-01 |
| changed pixels | 18,945 | 2,715 |

**Nothing is avoided.** Production already walks 0.9997 groups per light tap, and
the reuse path walks 1.0. The only work removed is one `stormCandidatesAt`
texelFetch and a four-rank scan - and T170 already measured descriptor fetches at
approximately 1.0x, i.e. free. Meanwhile the arm adds a second inlined call site
for `directStormGroupField` inside `directStormShape`, which is the plausible
source of the 45% regression.

So part of that 45% is an artifact of my implementation rather than a property of
reuse, and I am not claiming reuse "costs 45%". The load-bearing number is
**0.00 group walks avoided**: the ceiling for any reuse scheme, however
implemented, is the candidate scan alone, and that is already free.

The small image error (1.47e-03, maxAbs 0.137) is consistent with the 100%
validity result - the residual comes from the arm returning before the
multi-group union bookkeeping, not from resolving a wrong group.

**Verdict: below the brief's 1.10x gate, and below 1.0. Reuse is closed.** Task 4
(reuse signal design) is moot and was not built: a predicate cannot be worth
building for a ceiling that is already negative.

## 5. Task 5 (items 21-23) - light3

| Measure | SIDE | FAR |
|---|---|---|
| paired anchor-relative ratio | **1.0588** (spread 0.91%) | 1.0202 (1.86%) |
| **within-block, on the stack** | **1.0634** (spread 0.48%) | not measurable |
| image meanAbs, isolated | 3.192e-03 | 4.441e-04 |
| image meanAbs, on the stack | 4.479e-03 | 8.813e-04 |

Performance gain is **>3% and reproducible three ways**: 1.0588 anchor-relative
here, 1.0634 within-block on the stack here, and 1.0701 within-block on the stack
in T176 - three independent measurements inside 1.1% of each other.

**Quality: partially measured, and I am not marking it a production candidate on
this evidence.** The harness reports changed pixels, maxAbs and mean absolute
error. The brief also asked for cloud SSIM, edge SSIM, silhouette IoU, thin
retention, hole retention, self-shadow contrast, dark-interior retention,
localized shadow pockets and puff separation. **None of those metrics exist in
this harness and none were measured.** What can be said: light3's mean error is
3.19e-03 against light2's 1.25e-02, and it is the smallest tap-reduction error
measured in either campaign.

**Verdict: performance qualifies, quality is unproven.** The missing metrics are
exactly the ones that would detect flattened self-shadowing, which is the failure
mode tap reduction has. Recommend building the metrics before productionizing,
not productionizing on mean error alone.

## 6. Task 6 (items 24-25) - light2

| Measure | SIDE | FAR |
|---|---|---|
| paired ratio | **1.1402** (spread 0.54%) | **REJECTED** (14.03% spread) |
| image meanAbs | 1.253e-02 | 1.500e-03 |

SIDE is clean and reproduces T176's 1.139x. Its error is **3.9x light3's** for an
extra 7.7% of speed. FAR is rejected, as T176's FAR light2 also was.

**Not a production candidate.** The remaining room in tap-count reduction beyond
light3 is 1.140/1.059 = **7.7%**, bought at four times the error.

## 7. Task 7 (items 26-32) - stack

**Every anchor-relative stack figure this session is REJECTED on ratio spread.**
Reporting them as accepted would repeat exactly the T176 error this campaign
exists to correct.

Session-local absolute, SIDE, per block:

| Arm | b1 | b2 | b3 |
|---|---|---|---|
| `t172_stack_pre` | 10.3526 | 11.5098 | 10.2973 |
| `t176_stack_light3` | **9.7137** | 10.8513 | **9.6809** |
| `t177_stack_reuse_hard` | 15.8423 | 13.7267 | 13.8701 |

| Target | Status |
|---|---|
| stack + light3 <= 10 ms | **reached in 2 of 3 blocks** (9.71, 9.68) and missed in the third (10.85). **Not established.** |
| SIDE <= 8 ms | **no** |
| FAR <= 8 ms | **not measurable this session** - stack arms straddle 4.9 and 8.3 ms modes |
| >=1.25x vs the ~12.5 ms class | **cannot be claimed.** 12.5/9.68 = 1.29x and 12.5/10.85 = 1.15x depending on block, and it is a cross-session absolute comparison, which is the exact form of evidence this campaign's protocol distrusts. |

The session-independent claim, and the only one I would defend: **light3 adds
1.0634x on top of the validated stack, with 0.48% spread across three blocks.**

## 8. Decision (items 33-36)

**33. Group reuse: CLOSED.** Hard ceiling 0.6873x SIDE / 0.6708x FAR, with 0.00
group walks avoided per pixel. Production is already at the 0.9997 walks-per-tap
floor. Validity is 100% but on a single-group fixture, so it neither supports nor
rescues the idea.

**34. light3: performance qualifies, do not productionize yet.** 1.0634x on the
stack, reproduced three times across two sessions. But the quality metrics that
would detect flattened self-shadowing were not measured, and mean absolute error
is not a substitute for them.

**35. Remaining dominant workload: the ten-lobe group walk**, unchanged and now
the only large item left. At SIDE, 32.88 group walks per pixel, every one
visiting all ten lobes, while roughly five contribute.

**36. Next architecture.** Per the brief's own branch for a small reuse result,
and sharpened by this campaign's fixture finding: **the fixture's storm is one
group of ten lobes, so "group" and "storm" are the same object here and the lobe
walk is the entire descriptor cost.** The question is whether the group
representation can carry enough precomputed spatial information - in the spare
texel channels the T172 precompute already added - to skip provably
non-contributing lobes without ranking inside the hot loop. That is explicitly
not nearest-K, which T168 closed: no ordering, no selection, only a conservative
per-lobe test that is exact.

Second, before trusting another absolute stack figure: the harness needs the
image-quality metrics named in Task 5, and a mode detector. The within-block
pairing in section 2 is the cheapest available improvement and already turns an
11% swing into a 0.48% measurement at SIDE.

## 9. Harness notes

- `T177_REJECTED count=0` on anchor drift; every rejection here is a ratio-spread
  or insufficient-block rejection, which is the new rule doing its job.
- The T175 workload-view invariant covers the three new views:
  `T175_WORKLOAD_VIEWS declared=19|allEnabled=true`. Without it the reuse
  counters would have read zero exactly as T169's did.
- The T170 registry invariant covers T177, and its negative proof still detects
  all five mutations.
- The T123 instrumentation invariant passes: every reuse counter is written under
  its own `paWorkloadCaptureActive()` guard, and the classification runs after
  the walk so it cannot alter what the walk does.
- FINAL is unchanged: the reuse guard is present but never defined, and the T172
  precompute is still baked.

## 10. Status

Not merged. Productionization `4b34b12`; T170 `9239077`, T171 `aff6fc7`,
T172 `85ff4ef`, T173 `81476b3`, T174 `3af2015`, T175 `3cfd1f1`, T176 `b047bc3`.
