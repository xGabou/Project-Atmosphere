# T175 - Density-call necessity, and what the descriptor walk is actually for

Branch `experiment/cloud-descriptor-k`, parent `3af2015`. Nothing merged.
Two campaigns 06Sep2026, Ultra, raw scale 0.2500, cloud target 480x270,
framebuffer 1920x1080, 96 steps, 60 samples per cell, anchor-bracketed.
34 cells each. Run 2 is the banked run: run 1's histogram read zero for the
reason in section 2.

## 1. Headline

**Both oracles are at the measurement floor. Neither empty-space skipping nor
occupied-sampling reduction of the primary march is the lever, because the
primary body density call is only 13% of the descriptor work.**

| Arm | SIDE r1 | SIDE r2 | FAR r1 | FAR r2 |
|---|---|---|---|---|
| `density_every2` (halves primary density calls) | 1.0146 | 0.9977 | 1.0002 | 1.0068 |
| `clearance_first_group` (frees the safe advance) | 1.0080 | 0.9903 | 1.0065 | 1.0008 |

All four **BELOW MEASUREMENT FLOOR**, both runs, both poses.

**And the framing this line has used for three campaigns was wrong.** The
primary march makes **4.99** body density calls per pixel at SIDE, not the 25.37
T174 reported or the 11.28 I corrected it to. The descriptor walk runs **38.25**
times per pixel. The primary body density call accounts for **13%** of it.

## 2. A dead instrumentation gate, found by building on it

Run 1's histogram read exactly zero. The cause was not the new counters:

```glsl
bool paWorkloadCaptureActive() {
    return DebugView == 22 || DebugView == 23 || DebugView == 24
        || DebugView == 25 || DebugView == 26
        || (DebugView >= 28 && DebugView <= 34);   // <- stops at 34
}
```

**Views 35 and 36 - T169's light and detail attribution - have been dead since
T169.** They were emitted by the shader, plumbed through the capture, printed in
the campaign log, and every counter they carry incremented never. T169's own log
records `lightConeMarches=0 lightConeTaps=0 lightConeEarlyOuts=0
lightCheapProbes=0 tapsPerConeMarch=n/a`, and that was read past at the time.

It is the omission class the T170 registry invariant exists for - declared,
wired most of the way, dead at one gate - in a file that invariant does not
cover. T175's histogram would have shipped the same way and reported a confident
zero.

Fixed, and closed structurally: the predicate is now a contiguous range, and
`validateWorkloadViewsAreEnabled` parses it together with every
`STORM_WORKLOAD_*` id in the enum and fails the build if any declared view is
not enabled.

```
T175_WORKLOAD_VIEWS declared=16|allEnabled=true
```

**No banked T169 conclusion depends on those counters.** T169's results came from
timing arms and image comparisons - the bit-identical `lightsteps5`/`lightsteps4`
finding, the lighting and detail ceilings - none of which read views 35 or 36.
The dead counters were supporting detail that was never used to draw a
conclusion. T169 does not need revisiting.

## 3. Task 1 - the primary density histogram

Primary body samples only, counted at the single body call in the march loop, so
lighting cannot leak in. Bins anchored on the shader's own material threshold,
`0.0008`, not invented cut points.

| Per pixel | SIDE | FAR |
|---|---|---|
| primary steps | 30.90 | 28.64 |
| **primary body density calls** | **4.99** | **0.82** |
| density calls as % of steps | **16.2%** | 2.9% |
| light-march taps | 14.98 | 1.89 |
| `directStormShape` calls | **45.04** | 10.87 |
| `directStormGroupField` calls | **38.25** | 6.21 |
| lobes visited | **382.5** | 62.1 |

Histogram of the 4.99 primary calls at SIDE:

| Bin | SIDE | FAR |
|---|---|---|
| exactly zero | 14.2% | 21.8% |
| negligible (<= 0.0008) | 0.04% | 0.06% |
| low (<= 0.05) | 2.9% | 3.2% |
| medium (<= 0.25) | 17.1% | 15.1% |
| high (> 0.25) | **65.7%** | 59.8% |
| **material fraction** | **85.8%** | 78.2% |
| mean material run | 14.90 samples | 12.01 |
| mean empty run | 9.34 samples | 8.70 |

**86% of primary density calls return material, and two thirds return
high density.** Empty-space skipping is essentially exhausted: 14.2% of an
already-small 4.99 calls per pixel is 0.71 wasted calls per pixel.

## 4. Tasks 4, 5, 6 - the ceilings, and why they are empty

### 4.1 Occupied sampling reduction: 1.006x

`density_every2` halves primary body density calls - 4.99 to ~2.5 per pixel -
and returns **1.0146 / 0.9977** at SIDE. That is close to the theoretical
maximum for any occupied-sampling scheme, and it is at the floor.

The arithmetic explains it. Halving 4.99 removes 2.5 of the 38.25 descriptor
walks per pixel: **6.5% of the walks**, and walks are not the whole frame.

### 4.2 Group clearance: 0.999x

`clearance_first_group` lets only the first entered group constrain the safe
advance while every group's density contribution is still evaluated. It returns
**1.0080 / 0.9903** at SIDE.

So the answer to the question T174 left open is **no**: irrelevant groups do not
materially shorten the march. Group metadata is not worth building for clearance
either, which closes the last thread T174 left open.

### 4.3 Perfect empty-space skip: DERIVED, ~1.02x at most

Not measured directly. It cannot exceed removing the 14.2% of primary calls that
return empty - 0.71 calls of 38.25 walks per pixel, under 2% of walks - and
`density_every2` shows that removing 2.5 walks per pixel is worth 0.6%. **The
empty-skip ceiling is below the measurement floor.** Labelled DERIVED; it did not
warrant its own arm once the histogram was in.

## 5. Task 2 - what the descriptor walk is actually for

This is the finding. Per pixel at SIDE the walk runs **38.25** times, and the
primary body density call accounts for **4.99** of them:

| Consumer | walks/pixel | share |
|---|---|---|
| **light march** | **14.98** | **39%** |
| primary body density | 4.99 | 13% |
| segment tests, clearance probes, quadrature | ~18.3 | 48% |

`directStormShapeCalls` (45.04) exceeds `groupFieldCalls` (38.25) and both
dwarf the body density count. **The light march alone does three times the
descriptor work of the primary march**, and it does it at exactly 4.0 taps per
cone march - 485,477 marches, 1,941,908 taps, `tapsPerConeMarch=4.0000`.

That reinterprets earlier results rather than contradicting them:

- T170's `desc_nosdf` at 1.58-1.78x was mostly removing the **light march's**
  exact SDFs, not the primary march's.
- T169's `nolight` at 1.243x removed the 39% share.
- T168's footprint step LOD is the only lever that ever worked at SIDE because
  it removes **steps**, and steps carry lighting and clearance probes with them.

## 6. Stack

| Pose | run 1 | run 2 | repeat | verdict |
|---|---|---|---|---|
| SIDE `t172_stack_pre` | 12.5215 | 11.9009 | 5.08% | **REJECTED_repeat** |
| FAR `t172_stack_pre` | 5.3565 | 5.2808 | 1.42% | accepted, **2.406x** |

SIDE anchor ~21.7, FAR anchor ~12.8. FAR stack ~5.32 ms. The SIDE stack pair is
rejected again; the working figure stays ~12.5 ms from T173.

| Target | Status |
|---|---|
| SIDE <= 10 ms | **not met** - ~12.5, gap 1.25x |
| SIDE <= 8 ms | **not met** - gap 1.56x |
| FAR <= 8 ms | **met** - ~5.3 |

## 7. Decision

**Neither CASE A nor CASE B as framed.** Most primary calls return material
(86%), which formally selects CASE B - but the occupied-sampling ceiling is
1.006x, so CASE B's recommended direction is also empty. The reason both are
empty is that the premise behind both was wrong: **the primary march's density
calls are not a significant share of the work.**

**Realistic remaining opportunity in the primary march: none.** Every lever
against it is now measured and at the floor - group entry 1.136x (T174, and
insufficient), tighter per-lobe bound 0.95x (T173), occupied sampling 1.006x,
clearance 0.999x, empty skip <1.02x derived.

**Can the chosen direction close SIDE <= 10 ms?** Not from the primary march. The
1.25x needed is larger than every measured primary-march ceiling combined.

**Recommended T176: attribute and attack the light march.** It is 39% of the
descriptor walk, 14.98 taps per pixel against the primary march's 4.99, and it
has never been profiled at that level - because the counters that would have
shown it were dead from T169 until this campaign. Concretely worth asking:

1. The light tap samples 28 units along `LightDir` from a point whose group
   field was just computed. Whether it can reuse the primary sample's group
   resolution instead of walking again is a real question with a 39% share
   behind it.
2. `tapsPerConeMarch` is exactly 4.0, and T169 showed 6 -> 5 -> 4 taps are
   bit-identical at SIDE, meaning the cone already terminates early. The cost is
   the **number of cone marches** (3.75/pixel), not the taps within one.

Both are new questions, not retries of closed lines.

## 8. Harness notes

- 68 cells across two campaigns, `T175_REJECTED count=0`. All 12 run-2 arms
  accepted on anchor drift; one stack pair rejected on repeat agreement.
- The T123 instrumentation invariant caught the histogram's first form: an
  `else if` chain put later increments out of its three-line window. Each bin is
  now individually guarded. It also caught `paPrimaryStepParity++`, arm-local
  control state in the `pa*` counter namespace, now renamed and behind its arm's
  define - the same class it caught in T174.
- **T174's termination was not an OS watchdog.** There is no Windows event; the
  Claude Code background-task manager stopped the session's shell commands under
  host memory pressure, killing Gradle while the detached client survived. T169's
  finding that no OS watchdog exists still stands, and the earlier wording in the
  T174 report was wrong.

## 9. Status

Not merged. Productionization `4b34b12`; T170 `9239077`, T171 `aff6fc7`,
T172 `85ff4ef`, T173 `81476b3`, T174 `3af2015`.
