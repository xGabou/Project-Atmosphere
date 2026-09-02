# Rank 2 — per-sample descriptor cost: measured and rejected

**Feature**: `001-native-storm-rendering`
**Follows**: `performance-baseline.md` (T136), `performance-architecture.md` (T137)
**Date**: 2026-09-01
**Decision gate result**: **CASE C — rank 2 was overestimated. Proceed to rank 1.**
**Production shader unchanged.**

---

## 1. Why this was measured before it was implemented

T137 ranked per-sample descriptor cost second at an estimated 1.5–3x, and
flagged that estimate as its weakest because the descriptor counters were never
wired into the T136 sweep. This closes that gap. **No production optimization
was written**, because the measurement removed the case for one.

## 2. Harness gaps fixed

1. **Workload counters wired into the sweep.** Every cell now follows its
   timing sample with the two-frame counter readback, so descriptor
   evaluations, texture fetches, primary ray steps and light-march evaluations
   are recorded per (pose, arm, mode).
2. **Retry loop bounded.** The zero-descriptor refusal path now consumes a
   retry and advances after two attempts instead of ping-ponging between
   refusal and respawn — the bug that lost PLAY_MID/Ultra, PLAY_HIGH and CLEAR.
3. **Clear scenarios accept zero descriptors.** Storm scenarios still require
   descriptors to hold for the whole sample; a CLEAR cell no longer counts as
   a fixture failure.
4. **Per-pose re-resolution preserved.**
5. **Two attribution arms added**, both diagnostic-only and defaulting off: a
   constant-radiance arm (lighting share) and the existing T122 arm (descriptor
   fetch share).

## 3. Exact descriptor fetch structure

Descriptor data is four RGBA texels per descriptor in a single 4x64 texture —
**4 KB in total** — read through `stormDescriptorTexel` /
`texelFetch(StormDescriptorSampler, ivec2(texel, index), 0)`.

Per descriptor inside `directStormGroupField`'s loop: **exactly 4 fetches**,
loaded once at the top and reused by the ownership test, the softness form, the
vertical lower bound, the exact SDF, strength and height. **T122 already
implemented candidates A and D** — load-once-and-reuse — and its counter shows
it avoids six further wrapper fetches per lobe.

Redundancy that remains, all of it outside the loop:

| site | fetches | needed | redundant |
|---|---|---|---|
| `stormDescriptorIsValid(witness)` | 1 (texel 3) | — | shares texel 3 with the next |
| `stormDescriptorGroupSlot(witness)` | 1 (texel 3) | 1 | **1** |
| `stormGroupFirstIndex` → `memberIndex` | 1 (texel 3) | — | — |
| `stormGroupEndIndex` → recomputes `stormGroupFirstIndex` | 1 (texel 3) | 0 | **1** |
| `stormGroupEndIndex` → `memberCount` | 1 (texel 3) | 1 | — |

Texel 3 of the witness descriptor is fetched **five times per group pass**, all
returning the identical value. Across up to eight candidate ranks that is
roughly **19 removable fetches per `directStormShape` call**.

Measured counters, SIDE/Low, 480x270:

| quantity | value | derived |
|---|---|---|
| primary ray steps | 1,736,160 | **13.4 steps per pixel** |
| descriptor evaluations | 23,276,616 | **13.4 per step** |
| descriptor texture fetches | 234,990,263 | **10.09 per descriptor evaluation**, 1813 per pixel |
| fetches T122 already avoids | 116,207,560 | production would otherwise be 351 M |
| conservative descriptor rejects | 9,295,330 | T121 working |

So the shipped path issues **235 million descriptor texel fetches per frame**
and has already eliminated another 116 million.

## 4. The decisive experiment

The T122 arm re-issues the six fetches per lobe that production keeps in
registers — **+49 % fetch volume, identical arithmetic and identical output**.
That is a direct measurement of what descriptor fetches cost, not an estimate.
Same run, same fixture, same pose, back to back:

| pose | mode | production | +49 % fetches | cost of +49 % | lighting share |
|---|---|---|---|---|---|
| SIDE | Low | 48.14 | 59.16 | **+22.9 %** | 12.7 % |
| SIDE | Low 24 | 127.08 | 153.20 | **+20.6 %** | 19.3 % |
| SIDE | Medium | 221.00 | 275.43 | **+24.6 %** | 18.2 % |
| SIDE | High | 267.37 | 340.63 | **+27.4 %** | 18.0 % |
| SIDE | Ultra | 550.69 | 680.08 | **+23.5 %** | 19.8 % |
| **PLAY_NEAR** | Low | 7.30 | 8.37 | **+14.6 %** | 6.5 % |
| **PLAY_NEAR** | Low 24 | 17.09 | 19.80 | **+15.8 %** | 9.6 % |
| **PLAY_NEAR** | Medium | 31.63 | 34.44 | **+8.9 %** | 9.3 % |
| **PLAY_NEAR** | High | 41.35 | 44.33 | **+7.2 %** | 11.4 % |
| **PLAY_NEAR** | Ultra | 101.17 | 106.14 | **+4.9 %** | 8.8 % |

### What this means

Fetch elasticity of GPU time — the fraction of a fetch-volume change that shows
up as time:

- **stress framing (SIDE): ~0.47** (49 % more fetches → ~23 % more time)
- **representative gameplay (PLAY_NEAR/Ultra): ~0.10** (49 % → 4.9 %)

Removing *every* descriptor fetch would therefore save at most ~47 % at SIDE
and ~10 % at PLAY_NEAR. The removable share is ~19 fetches of roughly 100 per
group pass, about **19 %**, which maps to:

| scenario | expected saving | speedup |
|---|---|---|
| SIDE (stress) | 19 % x 0.47 ≈ **9 %** | **1.10x** |
| PLAY_NEAR/Ultra (representative) | 19 % x 0.10 ≈ **2 %** | **1.02x** |

Against T137's estimate of **1.5–3x**.

The reason is visible in the structure: the descriptor texture is 4 KB and
entirely L1-resident, so these are cache hits rather than memory traffic, and
T122 already removed the large redundancy in 2026-08. What remains is
address arithmetic on a handful of duplicated reads.

## 5. Candidates, and why none is implemented

| candidate | current | achievable | verdict |
|---|---|---|---|
| A load record once per evaluation | already done by T122 | — | **already shipped** |
| B pack fields to reduce reads | 4 texels/lobe | 4 | no gain without changing layout semantics |
| C cache group-invariant data | partially done | small | not worth the register pressure |
| D avoid re-evaluation within a sample | already done by T122 | — | **already shipped** |
| E reorganise so metadata is not refetched | 5 redundant texel-3 reads | 1 | **~19 % of fetches, ~2–9 % of time** |
| F exploit packed topology better | that is what E is | — | folded into E |
| G precompute CPU-side | possible | small | changes upload semantics for ~2 % |

Only **E** is real, and its measured ceiling is 1.02x in representative
gameplay. Per the standing instruction not to build machinery for a small
theoretical gain, and per the decision gate's CASE C, it is **not implemented**.
It remains available as a trivial cleanup if the shader is opened for another
reason.

## 6. Decision gate

**CASE C.** Fetch reduction achievable is small; rank 2 was overestimated.
Move to rank 1 — internal resolution and temporal reconstruction.

Recorded for T137's benefit, since both arms ran together:

- **Lighting is 18–20 % at stress framing but only 6.5–11 % in representative
  gameplay**, so rank 4's ceiling is lower than T137 assumed for the case that
  matters (1.10x, not 1.29x).
- The cost of representative gameplay is **neither descriptor fetches nor
  lighting** — together they are under 20 % at PLAY_NEAR/Ultra. Over 80 % is
  the per-pixel march itself, which is precisely what rank 1 attacks.

## 7. SC-006, both scenarios, unrescoped

| scenario | Ultra now | budget | required | rank 1 alone (4x res + 2–3x reconstruction) |
|---|---|---|---|---|
| **Representative** (PLAY_NEAR) | 101.2 ms | 8.0 | **12.6x** | 8–12x — **plausibly sufficient** |
| **Stress** (NEAR_EDGE parked) | 961.8 ms | 8.0 | **120x** | 8–12x — **an order of magnitude short** |

The stress case is not representative and is not being treated as such; it is
also not dismissed. Nothing in this task rescopes SC-006.

## 8. Updated cumulative path

T137's stack, corrected by measurement:

| lever | T137 estimate | measured / revised |
|---|---|---|
| 1 internal resolution + reconstruction | 8–16x | unchanged — still the only order-of-magnitude lever |
| 2 per-sample descriptor cost | 1.5–3x | **1.02–1.10x — rejected** |
| 3 distance / LOD | 1.5–2x | unchanged, untested |
| 4 lighting | 1.29x | **1.07–1.25x**, lower in gameplay |
| 5 sample count | 1.2–1.5x | unchanged, untested |

Revised realistic stack: **10–24x**, down from 27–40x, because rank 2
contributed nothing and rank 4 is smaller than assumed in the case that
matters. Representative gameplay still closes; the stress case still does not.

**Next: rank 1.**
