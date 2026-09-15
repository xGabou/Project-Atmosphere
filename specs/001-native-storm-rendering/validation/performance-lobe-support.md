# T178 - light3 fails on quality, and the wasted lobes are not far away

Branch `experiment/cloud-descriptor-k`, parent `f868325` (T177). Nothing merged.
One campaign 07Sep2026, Ultra, raw scale 0.2500, cloud target 480x270,
framebuffer 1920x1080, 96 steps, 60 samples per cell, three anchor-bracketed
blocks per arm per pose. `T178_REJECTED count=0` on anchor drift.

## 1. Headline

**Two negative results, both decisive.**

**light3 must not ship.** With the missing quality metrics finally in place, flat
4 -> 3 light taps returns cloud SSIM **0.884**, keeps **7.2%** of the darkest
interior quartile and loses a third of localized shadow pockets
(**0.663** retention). The failure mode is not the flattening that was
anticipated - it is *brightening*.

**Lobe support is closed, but not for the reason the decision cases anticipated.**
The opportunity is real and large: **47.3% of every exact lobe SDF is wasted** -
3.78 of 7.98 per group walk are fully evaluated and then fail to move the union.
But the precomputed support bound rejected **exactly zero** additional lobes, and
measured **0.9410x** - slower. The wasted lobes are not spatially distant. They
are close but *dominated*, and no conservative spatial envelope can identify
them.

## 2. A correction: five of the twelve metrics already existed

T176 and T177 both stated that the quality metrics the briefs asked for "do not
exist in this harness". **That was wrong.** `StormArmQualityMetrics` has existed
since T167 (`a280c10`) and already provided silhouette IoU, cloud-region SSIM,
edge-band SSIM, thin retention, hole retention, alpha-mass ratio and a seam
index. It was **printing in the T177 log at the time I wrote that sentence** -
ten `armQuality evaluated=true` lines, including `cloudRegionSSIM=0.9347` for
light3 at SIDE.

I read past it, exactly the way T175 found T169's `lightConeMarches=0` had been
read past. The claim should have been "I have not looked", not "they do not
exist".

What genuinely did not exist, and what T178 added, is the half that decides the
question: **self-shadow contrast, dark-interior retention, shadow-pocket
retention and valley depth.** Those are the four metrics that produce the verdict
in section 3; the five pre-existing ones would, on their own, have passed light3.

Still not implemented: **puff separation**. Counting puffs requires a
segmentation of the body into lobes, which these frames do not carry. It is
emitted as `puffSeparation=not_implemented_needs_segmentation` rather than
silently replaced. The valley-depth measure that stands near it is labelled
`Proxy` in its own field name.

## 3. Tasks 0 and 7 (items 1-11) - light3 quality

Reference: FINAL at four light taps. Arm: flat light3. SIDE, 24,656 interior
pixels.

| Metric | SIDE | FAR | Reading |
|---|---|---|---|
| changed pixels | 22,095 (T176) | 2,443 | - |
| maxAbs / RMS | 3.78e-01 / see log | 3.76e-01 | - |
| **cloud SSIM** (T178) | **0.884278** | 0.905216 | **fails** |
| cloud-region SSIM (pre-existing) | 0.9151 | 0.9415 | fails |
| **edge-band SSIM** | 0.993807 | 0.992743 | passes |
| **silhouette IoU** | **1.000000** | 1.000000 | passes |
| **thin retention** | **1.000000** | 1.000000 | passes |
| **hole retention** | **1.000000** | 1.000000 | passes |
| self-shadow contrast ratio | 1.018008 | 0.976029 | passes |
| **dark-interior retention** | **0.071509** | 0.117705 | **fails badly** |
| **shadow-pocket retention** | **0.663199** | 0.831683 | **fails** |
| valley depth ratio (proxy) | 1.209695 | 1.058064 | deeper |

### The mechanism is brightening, not flattening

This is worth stating precisely, because the brief predicted flattening and the
data says something different.

Interior contrast is **preserved** - the ratio is 1.018, slightly *up* - and the
valleys between bright regions are **21% deeper**. Yet only **7.2%** of the
darkest quarter of the interior is still at or below its reference luminance.

Those are consistent, and together they mean the interior became *brighter* while
keeping its structure. Dropping the fourth tap removes 14 units of optical path
from every light march, so less extinction accumulates and the whole interior
lifts. Local relief survives; the absolute darkness does not. A third of the
small dark cores between puffs stop being local minima at all.

**Verdict (item 11): DO NOT SHIP light3.** Against Task 7's four criteria:
performance gain >3% **yes** (~1.06x, three independent measurements); thin/hole
regression **none**; SSIM/edge **fails** on cloud SSIM 0.884; self-shadow quality
**fails** on 7.2% dark-interior retention. Two of four fail, and the two that
fail are the ones that describe self-shadowing.

The ~1.06x remains real and remains unspent. It is not worth this.

## 4. Task 1 (items 12-18) - lobe attribution

Per group walk. `lobeVisitsPerGroupWalk` is exactly 10.0000 at both poses.

| Per group walk | SIDE | FAR |
|---|---|---|
| lobe visits | **10.0000** | 10.0000 |
| cheap rejects before the exact SDF (T121) | **2.0182** | 2.8599 |
| **exact SDF evaluations** | **7.9819** | 7.1402 |
| union contributors | **4.2033** | 3.9611 |
| **exact SDFs that did not move the union** | **3.7784** | 3.1788 |
| **wasted SDF fraction** | **47.34%** | 44.52% |

Per pixel at SIDE: **341.31 lobe visits, 272.43 exact SDFs, 128.96 of them
wasted.**

### Where are the non-contributing lobes rejected? (D - mixture, but mostly late)

Of the ~5.8 non-contributing lobes per walk at SIDE, only **2.02 are rejected
cheaply before the exact SDF**. The other **3.78 pay a full exact SDF and are
then discarded by the union** - option **C** in the brief's list, with a minority
in **A**.

So this is not CASE C as written. Most non-contributing lobes are *not* already
rejected before expensive work; the reverse is true.

### The light march carries the expensive half

| | SIDE | FAR |
|---|---|---|
| light share of lobe visits | 37.66% | 31.00% |
| **light share of exact SDFs** | **46.26%** | 41.34% |
| cheap-reject rate, light taps | **1.94%** | 4.76% |
| cheap-reject rate, everything else | **31.20%** | 39.31% |

**A light tap is sixteen times less likely to be cheaply rejected than any other
sample.** Light taps march from inside the cloud along `LightDir`, so they sit
where the conservative bound cannot reject anything - which is why they supply
46% of the exact SDFs from 38% of the visits.

## 5. Tasks 2, 3 and 6 (items 19-24) - what was precomputed, and why it found nothing

`stormLobeDistanceLowerBound` recomputes three descriptor-only quantities at
every lobe of every sample: the role's radial profile range, the widest and
narrowest scaled radii, and the shear magnitude - then **divides** by the widest
radius. T173 re-enabled that bound as written and measured 4.6-7.1% slower.

T178 moved the invariant half upstream. Payload layout:

| Texel | Channels | Owner |
|---|---|---|
| 0-2 | position/height, radii/rotation, shear/media | original |
| 3 | edgeWidth, ownRadiusX, ownRadiusZ, packedGroupRole | T172 |
| 4 | seed01, lifecycleStage, verticalDevelopment, spare | original |
| **5** | **1/maxRadius, minRadius, shearLength, spare** | **T178** |

- Bytes added per descriptor: **+16** (TEXELS_PER_DESCRIPTOR 5 -> 6)
- Texture fetches added: **+1 per lobe visit**
- Hot-loop arithmetic **removed**: the role-profile branch, two scaled-radius
  `max` pairs, one `length()`, and **one division**
- Hot-loop arithmetic **added**: one multiply, one compare, one `max`

### It rejected nothing

```
lobeSupportRejects=0        (SIDE and FAR)
```

**The horizontal term never once exceeded the vertical bound at any sample the
walk actually visits.** The tighter bound has no rejection power on this fixture,
so the arm is pure overhead - which is also the real explanation for T173's
slowdown. T173 was not slow because its arithmetic was expensive; it was slow
because it bought nothing with it.

Image equality confirms the implementation rather than excusing it: every metric
reads **1.000000** at both poses and FAR reports `digestsEqual=true`. The bound
is correct. It is simply never binding.

**I should have caught this before benchmarking.** The brief's own instruction was
to stop if the design still performed comparable hot-loop arithmetic to T173 -
and the deeper check I skipped was whether T141's horizontal term ever binds at
all. One counter, added upstream of the arm, would have closed the line without a
campaign.

## 6. Task 4 (item 25) and Task 5 (items 26-30) - ceiling and arms

| Pose | Arm | blocks | ratioMean | spread | verdict |
|---|---|---|---|---|---|
| SIDE | `t178_nosdf` (all exact SDFs removed) | 3 | 1.5328 | 6.80% | REJECTED_ratio_spread |
| FAR | `t178_nosdf` | 2 | **1.7428** | 1.62% | **accepted** |
| SIDE | `t178_support` | 1 | 0.9359 | - | REJECTED_insufficient_blocks |
| FAR | **`t178_support`** | 3 | **0.9410** | 2.49% | **accepted** |
| SIDE | `t178_stack_support` | 2 | 1.9224 | 18.88% | REJECTED_ratio_spread |
| FAR | `t178_stack_support` | 3 | 2.5494 | 2.97% | accepted |
| FAR | `t172_stack_pre` | 3 | 2.5214 | 2.72% | accepted |

**The ceiling is real: removing every exact SDF is worth 1.53x at SIDE and
1.7428x at FAR**, so exact SDFs are ~34.8% of SIDE frame time. Removing only the
47.34% that are wasted would be worth **~1.197x** - comfortably above CASE A's
1.10x gate.

**The realized arm is 0.9410x.** Rejection rate 0. Exact-SDF reduction 0. Image
equality exact. The gap between a 1.197x opportunity and a 0.94x result is the
whole finding.

Within-block against the stack control: `t178_stack_support` vs `t172_stack_pre`
is **1.0033** at FAR (spread 4.02%, rejected) - no gain on the stack either.

## 7. Task 8 (items 31-37) - stack

| | SIDE | FAR |
|---|---|---|
| `t172_stack_pre` session-local p50 | rejected | **5.0633** (2.5214x, accepted) |
| `t178_stack_support` session-local p50 | 11.0515 (rejected) | 5.0473 (2.5494x, accepted) |
| within-block support gain | rejected | 1.0033 (rejected) |

| Target | Status |
|---|---|
| SIDE <= 10 ms locally | **no** - session-local 11.05, and rejected on ratio spread |
| SIDE <= 8 ms locally | **no** |
| FAR <= 8 ms | **yes** - ~5.05 ms, accepted at 2.52x |

No cumulative gain to report: light3 is disqualified on quality and the support
bound returns nothing.

## 8. Decision (items 38-40)

**38. Lobe support: CLOSED.** Not because the opportunity is small - 47.3% of
exact SDFs are wasted, worth ~1.197x - but because it is **not a spatial
problem**. The wasted lobes are close to the sample; what makes them irrelevant
is that another lobe already owns the union minimum. A conservative support
envelope, by construction, can only exclude lobes that are *far*, and these are
not. That is a structural reason, and it applies to any envelope, not just this
one.

CASE C is selected in outcome but its stated premise is false: most
non-contributing lobes are *not* already cheaply rejected. CASE B's remedy - a
different spatial structure - does not follow either, for the same reason: the
problem is dominance ordering, not spatial indexing.

**39. Remaining dominant workload.** The exact lobe SDF: 272.43 per pixel at
SIDE, 34.8% of frame time, **46.3% of it issued by light taps** that the
conservative bound almost never rejects (1.94%).

**40. Recommended next architecture.** The waste is order-dependent: a lobe is
wasted when it arrives at the union after one that already dominates it. Sorting
would fix it and is closed (T168 nearest-K). What is *not* closed, and what this
campaign's attribution points at directly:

1. **A running-minimum early exit inside the lobe loop.** The union already knows
   `groupDistance` when lobe *i* is evaluated. The T121 test compares a
   conservative *lower bound* against it; the reason 3.78 lobes slip through is
   that the bound is loose, not that the test is wrong. Tightening the *incoming
   distance estimate* rather than the envelope is a different lever from T173.
2. **Light taps deserve their own bound.** They supply 46% of exact SDFs and are
   rejected 1.94% of the time. A bound designed for a ray that starts inside the
   medium is a distinct problem from one designed for a primary sample outside
   it, and no campaign has treated it as such.
3. **Do not re-derive light3.** Its ~1.06x is real and its quality cost is now
   measured and unacceptable. Revisit only with an adaptive scheme that preserves
   dark-interior retention, and measure that metric specifically.

## 9. Payload note before any merge

Texel 5 is dead weight while this line stays closed: +16 bytes per descriptor
that FINAL never reads. It is kept on this experiment branch so the arm remains
reproducible. **If T178's line is not reopened, TEXELS_PER_DESCRIPTOR should be
returned to 5 before anything here merges.**

## 10. Harness notes

- `T178_REJECTED count=0` on anchor drift; all rejections are ratio-spread or
  insufficient-block, i.e. T177's rule working.
- `T175_WORKLOAD_VIEWS declared=21|allEnabled=true` covers the two new lobe views.
- `T170_WIRING campaigns=23|violations=0`, negative proof still detects all five
  mutations.
- `T172_PRECOMPUTE texels=6|channelsPinned=true` - the invariant was updated
  deliberately, with its message rewritten to name both owners.
- An incident worth recording: a malformed `newline` argument in a patch script
  truncated `StormT132AutoDriver.java` to zero bytes. It was committed at
  `f868325` with no uncommitted work, so `git checkout --` restored it exactly and
  the re-applied wiring is additive-only.

## 11. Status

Not merged. Productionization `4b34b12`; T170 `9239077`, T171 `aff6fc7`,
T172 `85ff4ef`, T173 `81476b3`, T174 `3af2015`, T175 `3cfd1f1`, T176 `b047bc3`,
T177 `f868325`.
