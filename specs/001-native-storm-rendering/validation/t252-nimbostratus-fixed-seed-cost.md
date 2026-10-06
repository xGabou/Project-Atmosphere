# T252 - Nimbostratus fixed-seed performance attribution

Branch `quality/t217-deep-interior-lighting`, from `dddf0de8` (T251). Evidence: `validation/t252-campaign/t252-results.txt`.

## 1. Builds (volume shader only differs; Java, lighting, settings identical)

| build | volume shader from | content |
|---|---|---|
| A | `e3a54fde` | pre-T248 (T247 lighting) |
| B | `a7e5348e` | T248: 9-tap 40-block coverage blur for the nimbostratus top taper / carve |
| C | `dddf0de8` (= `6482a691`) | T249: fractus layer decoupled from coverage (current production) |
| O1 | C + T252 | exact skip of the T248 blur where the sample is already zero |

T250 (Java grid snap) and T251 (Java layout) are in every build.

## 2-3. Seeds, poses, method

`CG_SEED=5000`, four nimbostratus instances (instance 1 = stepped-end / isolated-member case, 3 = sparse tail,
4 = ordinary broad deck, 2 = isolated member), views side / close / below / low_angle - 16 poses, identical member
layouts and cameras in every build (`CG_POSE` lines in the results file). Harness (`run/t226-harness/cloud_gallery.py`
with `CG_TIMING`): at each pose, 120 presented frames of `lastGpuMilliseconds()` in lean FINAL (p50 / p95 / mean), then
the workload census (`requestStormWorkloadCapture`, diagnostic monolith, production field bits) at the same pose.
Two full runs per build, alternating A B C A B C; O1 two runs alternating with two more HEAD runs.

## 4-7. Timings, workload, normalised cost (sums over the 16 poses, mean of runs)

| build | mean p50 / pose | primary density calls | all density | light taps | material rays | ms / 100k primary density | ms / 100k light taps |
|---|---|---|---|---|---|---|---|
| A | 4.27 ms | 101.6 M | 145.3 M | 200.6 M | 3.99 M | 0.067 | 0.034 |
| B | 5.13 ms (+0.86) | 100.5 M (-1 %) | 145.4 M | 208.3 M (+4 %) | 3.94 M (-1 %) | 0.082 (+22 %) | 0.039 |
| C | 6.23 ms (+1.10) | 109.8 M (+9 %) | 161.6 M | 252.5 M (+21 %) | 4.46 M (+13 %) | 0.091 | 0.039 (flat) |
| C (4 runs) | 6.43 ms | 110.3 M | 162.3 M | 253.3 M | 4.47 M | 0.093 | 0.041 |
| **O1** | **5.81 ms (-0.62 vs C)** | 108.7 M | 159.7 M | 248.4 M | 4.39 M | 0.086 | 0.037 |

Sum p95 over poses: A 77.9, B 94.5, C 110.5 / 115.2 (4 runs), O1 101.7 ms. Run-to-run spread is up to ~2 ms on the
`below` poses (the most expensive: 8-15 ms, the camera under the whole deck); the totals agree across runs. The
counters themselves drift +-1.7 % between sessions of the same build.

The old "3.4 -> 4.3 ms" figures came from unseeded / differently framed sessions; on identical seeded scenes the rise
is 4.27 -> 6.23 ms per pose (+1.96 ms, +46 %) before O1, 4.27 -> 5.81 ms (+1.54) after.

## 8. T248 contribution - code path

Workload unchanged (density -1 %, light taps +4 %, material rays -1 %) while the cost per density call rose 22 %: the
9-tap blur (eight extra `sampleWeather` fetches + arithmetic) runs on every visible nimbostratus density call. Light
taps and the field build were already excluded (T248). Not a shader-wide codegen effect: the storm benchmark (no
profile-5 material in the timed body), build vs build, raw-cloud p50 A 3.85 / 3.84, B 3.69 / 3.71 ms - flat.

## 9. T249 contribution - workload

Cost per light tap flat (0.039 -> 0.039 ms / 100k); light taps +21 %, material rays +13 %, density calls +9 % - the
fuller fractus underside puts more cloud (and more lit material samples) on screen, mostly in the `below` views
(+3.5..5 ms there). The storm benchmark's SIDE pose (whose sky includes nimbostratus) runs +13 % primary steps in C
with raw cost per step unchanged - the same workload effect.

## 10. Classification

* T248: **code-path regression** (per-call), confined to nimbostratus samples.
* T249: **geometry / workload cost** (more filled volume), per-step cost flat.

## 11. Optimization (O1, exact)

The blurred coverage only scales a sample that every later term multiplies (carve, fractus floor, erosion clamp,
material factors, the final `smoothstep(0.010, 0.080, coverageMod)` gate; no additive term on the nimbostratus path).
Where the carve input `cloud` is already <= 0, or `coverageMod <= 0.010`, the result is zero whatever the blur says,
so the eight taps are skipped there - exact by construction. Same-session arms on identical frames (monolith, seed
5000, 52 views: M = no skip, M2 = no skip repeat, O = skip): median per-view RMS luminance difference M-M2 1.93,
M-O 1.76 (p99 31.6 vs 32.3) - O1 is inside the renderer's own frame-to-frame noise. Timing: 6.43 -> 5.81 ms per pose
(-0.62, -10 %), recovering ~70 % of T248's code-path cost.

## 12. Final decision - CASE C for the under-deck view (stopped before changing morphology)

O1 is kept (exact, no morphology change). T248 / T249 morphology is NOT changed here, because the budget question
needs a decision:

Per-pose p50, cloud-pass GPU ms (gallery window 1600x900, cloud target 1200x675 = 0.75 scale - fewer pixels than the
1920x1080 storm benchmark, so in-game cost at 1080p is higher):

| pose | A pre-T248 | B T248 | C T249 (HEAD) | O1 |
|---|---|---|---|---|
| side / close / low_angle (12 poses) | 2.4-5.0 | 2.7-5.0 | 3.4-4.6 | 3.3-4.4 |
| **below** (camera at y 153, 90 blocks under the deck base, looking up; 4 instances) | **7.0-9.5** | **8.4-10.6** | **12.5-15.0 (p95 to 15.7)** | **10.8-12.8 (p95 to 13.6)** |

Every side-on view is comfortably inside the 10 ms ceiling, but standing under a nimbostratus deck - an ordinary
gameplay view in rain - now costs 10.8-12.8 ms after O1 at reduced resolution, above the 10 ms ceiling (it was
7-9.5 ms before T248). Of the +3.5..5 ms there, ~1 ms is T248's per-call blur (O1 recovers about half of it) and the
rest is T249's fuller underside: more lit material samples (light taps +27..35 % in these views). Options (need a
decision): accept; a pre-blurred coverage map to remove the rest of T248's per-call cost (~0.5-1 ms there); reduce
T249's underside light work (e.g. cheaper light cones for the fractus layer) or depth - the last two change lighting
or shape and were not done.

Validation: `campaignGate` 0, `check` 0, `stormMorphologySandbox` 0 (`gate-t252.log`, `check-build-t252.log`,
`sandbox-t252.log`).

## 13. Limitations

* The remaining T248 per-call cost (~0.25 ms per pose) is the blur on live samples; a pre-blurred coverage map
  (one fetch, exact for whole-texel offsets except where the per-tap saturate clamps) could remove it but needs a
  new render pass and texture binding - not low-risk, not done.
* `below` poses vary +-1-2 ms between runs; the workload counters drift +-1.7 % between sessions of the same build
  (seeded spawns are not perfectly identical in time), so per-pose deltas under ~0.5 ms are not resolved.
* Shader registers / instruction counts: no offline NVIDIA compiler in this environment; the sandbox compiles every
  production variant.
