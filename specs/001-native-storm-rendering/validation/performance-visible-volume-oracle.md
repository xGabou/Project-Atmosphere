# T153 - Oracle empty-space / visible-volume ceiling

**Verdict: STOP. The combined oracle returns 1.63x, below the ~2x gate. T154 does not
unlock, and T155-T159 do not open behind it.**

Run: `runclient-t153.out.log`, 2026-09-03, outcome `t153_complete`, 35 cells recorded,
0 overflow pixels on any recorded cell.

---

## 1. What was measured, and what the number is a ceiling of

Four diagnostic replay arms were compared against unmodified production at seven canonical
poses, all at ULTRA / 96 steps / 1920x1080 with the shipped 0.250 internal resolution
(480x270 marched pixels), on one descriptor-owned ten-member post-T134 severe storm,
fixture group `3d86b883-79c4-47a0-97b8-16289266bba4`.

| arm | what it is given for free |
|---|---|
| A `t153_perfect_empty_skip` | omits expensive density work at ground-truth-empty samples |
| B `t153_perfect_occupied_intervals` | jumps directly between ground-truth occupied intervals |
| C `t153_perfect_optical_relevance` | stops at the exact point beyond which nothing contributes |
| D `t153_combined` | B + C together |

The ground truth is produced by a separate publication pass that runs the **real production
`cloudDensity` path** and writes up to sixteen exact occupied intervals per ray into a
4x-wide RGBA32F target. That pass is deliberately outside the GPU timestamp query
(`VolumetricCloudRenderer.java`: publication at 549-575, `GPU_TIMER.begin()` at 581), so
interval discovery and ground-truth construction cost nothing in the reported figure.

**Every number below is therefore an upper bound that no shippable design can reach.** A
real representation must build, store, update, upload and fetch what this harness is handed
for nothing. The replay does pay to *read* its intervals - the four texel fetches and the
interval walk are inside the timed region - so the arms are not free of consumption cost,
only of production cost.

Interval endpoints are quantised to 8 bits per ray and decoded expanded by one quantisation
cell, so the replay cannot lose a production-positive boundary sample. That biases the
measurement **against** the oracle, which is the correct direction for a ceiling.

---

## 2. Result

GPU p50 (ms), cloud pass only, and speedup against production at the same pose.

| pose | production | A empty-skip | B occupied | C optical | **D combined** |
|---|---|---|---|---|---|
| PLAY_VIS_NEAR | 100.28 | 59.20 (1.694x) | 60.59 (1.655x) | 94.91 (1.057x) | **58.33 (1.719x)** |
| PLAY_VIS_MID | 64.80 | 42.34 (1.531x) | 46.13 (1.405x) | 62.05 (1.044x) | **45.60 (1.421x)** |
| SIDE | 114.25 | 77.32 (1.478x) | 77.20 (1.480x) | 119.30 (0.958x) | **75.03 (1.523x)** |
| FAR | 53.60 | 26.09 (2.054x) | 23.93 (2.239x) | 51.98 (1.031x) | **23.65 (2.266x)** |
| ABOVE | 104.11 | 99.58 (1.045x) | 98.31 (1.059x) | 104.55 (0.996x) | **95.67 (1.088x)** |
| BELOW | 23.58 | *invalid* | *invalid* | 22.81 (1.034x) | *invalid* |
| NEAR_EDGE | 190.28 | 108.65 (1.751x) | 109.85 (1.732x) | 186.62 (1.020x) | **106.81 (1.781x)** |

**Combined mean over the six valid poses: 1.633x. Representative gameplay
(PLAY_VIS_NEAR, PLAY_VIS_MID) mean: 1.570x.**

T153's own gate: *"Stop below approximately 2x combined; >=2x unlocks T154."* One pose
clears 2x - FAR, at 2.266x - and it is the cheapest pose that contains a storm at all
(53.6 ms). The poses that actually need help return least: NEAR_EDGE at 190.3 ms returns
1.78x, SIDE at 114.3 ms returns 1.52x, and ABOVE at 104.1 ms returns 1.09x. The ceiling is
inversely correlated with the cost it would have to remove.

---

## 3. Why: the empty space is behind the storm, not inside it

Skippable distance per pixel, combined arm, classified by where it lies:

| pose | total | pre-cloud | **holes** | post-cloud | post-opacity |
|---|---|---|---|---|---|
| PLAY_VIS_NEAR | 1172.3 | 88.7 | **2.5** | 1055.8 | 25.3 |
| PLAY_VIS_MID | 1141.9 | 46.2 | **0.8** | 1088.0 | 6.8 |
| SIDE | 1242.6 | 139.5 | **2.4** | 1061.0 | 39.7 |
| FAR | 1298.3 | 40.7 | **0.6** | 1250.9 | 6.1 |
| ABOVE | 584.0 | 2.3 | **0.9** | 270.8 | 310.0 |
| NEAR_EDGE | 1121.2 | 231.4 | **13.1** | 739.1 | 137.5 |

Holes - the transparent openings between lobes - are **0.6 to 13.1 blocks per pixel, 0.05%
to 1.2% of the skippable distance**. Sixty-six to ninety-six percent of it is *post-cloud*:
the tail of the ray after it has left all material and is marching empty sky to the render
distance.

This falsifies the premise T154-T155 were built on. T155 exists to prove conservative
`occupied -> empty -> occupied` traversal through holes and re-entry, and to forbid hollow
shells and lost re-entry. That machinery would be guarding a quantity worth about one part
in two hundred of the available saving. The saving that exists is almost entirely "know
where this ray's last material is and stop there", which is a ray-tail termination problem,
not a volume-representation problem.

---

## 4. Why: step count is not the cost, and this is the fourth confirmation

Arm B skips *between* occupied intervals, so it removes march iterations outright. Arm A
removes none - it only omits work at empty samples. Compare what each removes against what
each returns:

| pose | prod steps/px | A steps/px | A speedup | B steps/px | B speedup |
|---|---|---|---|---|---|
| PLAY_VIS_NEAR | 28.55 | 28.17 | 1.694x | **2.39** | 1.655x |
| PLAY_VIS_MID | 26.88 | 26.40 | 1.531x | **0.84** | 1.405x |
| SIDE | 30.99 | 31.12 | 1.478x | **5.16** | 1.480x |
| FAR | 28.62 | 28.57 | 2.054x | **0.71** | 2.239x |
| NEAR_EDGE | 34.48 | 35.42 | 1.751x | **13.82** | 1.732x |

**B removes up to 97% of the march's iterations and returns no more than A, which removes
none.** At PLAY_VIS_MID it returns measurably less. The loop iteration is close to free;
what costs is the density, descriptor and fetch work at the samples. Both arms cut texture
fetches by roughly the same factor - at PLAY_VIS_NEAR, 346.8M to 75.2M (A) and 74.8M (B),
about 4.6x - and that reduction, not the step count, is what buys the time in both.

This is the **fourth independent confirmation** of the same property of this shader, after
T141's tighter descriptor bound (+4.3 to +7.6%), T151's interleaving ceiling (1.42x/1.67x
for half/quarter the marched pixels), and T149's graded lighting and detail (27-40% of light
evaluations removed for ~1.02x). It is the sharpest of the four: here even a 12x reduction
in loop iterations converts to nothing beyond the fetch reduction that accompanied it.

---

## 5. Arm C is closed: there is no work after opacity worth removing

Arm C is 0.958x to 1.057x - inside noise at every pose, and *slower* at SIDE and ABOVE.
Production's existing `transmittance < 0.015` exit already captures essentially all of it.

Production density evaluations occurring after the ray has passed each opacity threshold:

| pose | total | after a50 | after a90 | after a95 | after a98 |
|---|---|---|---|---|---|
| PLAY_VIS_NEAR | 1,833,426 | 48.0% | 25.3% | 15.8% | 3.7% |
| PLAY_VIS_MID | 625,883 | 48.1% | 24.8% | 15.5% | 3.7% |
| SIDE | 3,889,999 | 55.4% | 28.2% | 17.7% | 4.3% |
| FAR | 542,021 | 46.3% | 23.4% | 14.8% | 3.5% |
| ABOVE | 10,565,381 | 72.3% | 36.3% | 22.5% | 5.2% |
| BELOW | 1,415,910 | 48.5% | 32.1% | 11.8% | 2.5% |
| NEAR_EDGE | 11,474,708 | 47.7% | 24.0% | 15.0% | 3.5% |

Roughly half of all density work happens after the ray is already 50% opaque, which looks
like a large target until the arm is run: cutting at the alpha-98 point removes only 2.5-5.2%
of evaluations, and that is all a *perfect* optical-relevance oracle can take. The work
between alpha 50 and alpha 98 is genuinely contributing to the image, and removing it is a
visual change, not an optimisation. "Stop earlier once substantially opaque" is closed.

---

## 6. BELOW is excluded: harness defect, not a result

BELOW's three traversal arms report 0.136x-0.141x - seven times *slower* than production.
That is structurally impossible for an arm that can only skip work, so it is a defect in the
harness and is excluded rather than averaged in.

| BELOW arm | p50 | steps/px | density calls | fetches | emptyStepsRemoved | intervals |
|---|---|---|---|---|---|---|
| production | 23.58 | 8.40 | 1,415,910 | 426.4M | 0 | 0 |
| A empty-skip | 167.05 | 22.87 | 11,264,076 | 1,122.9M | **0** | 241,712 |
| B occupied | 169.95 | 12.15 | 11,228,439 | 1,112.6M | **0** | 240,691 |

The arms execute **8x more density evaluations and 2.6x more fetches than production while
removing zero empty steps**, against ten times the interval count of any other pose (241k vs
~21k). BELOW is the camera-inside-cloud regime - production reports `light=0` there because
the in-cloud path replaces the light cone with a single forward probe, exactly as T149
recorded - and the replay's control flow diverges from that fast path instead of subsetting
it. The oracle is not skipping BELOW's work; it is defeating BELOW's early-out.

Arm C at BELOW (1.034x) is unaffected and retained, because it only adds a break and does
not touch march progression.

BELOW's production p50 of 23.58 ms is also an order of magnitude below T149's 187.5 ms for
that label, confirming this is a different camera regime, not a contradiction of T149.

Two cells were rejected and retried during the run, both at BELOW:
`t153_perfect_empty_skip` for `oracle_interval_overflow` (1 overflow pixel) and
`t153_perfect_occupied_intervals` for `t150_rendered_no_storm` (175 density calls). Both
retried successfully; no recorded cell carries a nonzero overflow.

---

## 7. Decision

**T153 stops at 1.63x combined against a ~2x gate.** The gate is not met, so:

- **T154 does not unlock.** Its own text conditions it on T153 reaching >=2x.
- **T155-T157 do not open**, and their premise is independently falsified: holes and
  re-entry are 0.05-1.2% of the skippable distance, so the conservative multi-lobe
  traversal they specify would guard almost nothing.
- **T158-T159 do not open**, both being conditional on a banked T157.
- **T140** consumes this document as "the recorded T153--T157 stop task", which its
  dependency clause already anticipates.

Production is unchanged by this task. No shader, descriptor, quality profile or equation was
modified; the four arms are diagnostic replay only, reachable solely through
`StormOptimizationDiagnosticMode` under a marker-gated run, and `NORMAL_PRODUCTION` remains
the only mode an ordinary frame uploads.

Do not reopen visible-volume traversal without new evidence that invalidates either the
1.63x ceiling or the post-cloud/hole distribution in section 3. In particular, a proposal
justified by "skipping the gaps between lobes" is already answered: the gaps are 2.5 blocks
per pixel at PLAY_VIS_NEAR.

**What section 3 does leave open** - and what this task does not itself evaluate - is ray-tail
termination: 66-96% of skippable distance is empty sky *after* the ray's last material, and
that is a bound on the ray's far endpoint rather than a volume representation. Whatever
fraction of the 1.63x is reachable that way would need its own measurement against its own
gate. It is recorded here as an observation, not as a recommendation, and no task is opened
for it.
