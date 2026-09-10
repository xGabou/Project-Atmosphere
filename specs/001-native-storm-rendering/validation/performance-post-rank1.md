# T147 — the renderer re-measured at the Rank 1 ladder

**Feature**: `001-native-storm-rendering`
**Task**: T147 [PERFORMANCE]
**Follows**: `performance-internal-resolution-frontier.md` (T146)
**Date**: 2026-09-03
**Hardware**: NVIDIA GeForce RTX 4070 Laptop GPU, OpenGL 4.6
**Decision**: **CASE A — interleaved reconstruction next**, with distance/LOD queued behind it.
**No production change.** Every arm here is diagnostic and defaults off.

---

## 0. A correction to the brief before anything is measured against it

The brief asks to "re-test the **existing** interleaved reconstruction
candidate" and not to "rely only on the historical 2.0x result".

**There is no implementation to re-test.** `grep` over the whole tree finds no
interleaving or checkerboard code, and T146 §5 recorded explicitly that
candidate F "was deliberately not started here". The 2.0x is an **inference from
two measured frontier points** — 113.2 ms at scale 0.250 against 55.4 ms at
0.125 — under the assumption that interleaving delivers 0.250-class spatial
information at 0.125-class march cost. T146's own wording, "worth a measured
2.0x", was too strong and is corrected in that document.

What T147 can do, and does, is confirm that the frontier ratio still holds on
5b9e916 and measure the *alternative* candidate properly. Both are below.

---

## 1. Phase 1 — the new baseline

Shipped ladder, `resolutionScale` released so every cell renders at the quality
mode's own value. All storm cells 480x270 (Ultra), one fixture, descriptor count
held for the whole sample, contaminated cells rejected automatically.

| pose | cloud p50 / p95 | total frame p50 / p95 | storm visible |
|---|---|---|---|
| PLAY_VIS_NEAR | 101.34 / 108.03 | 102.4 / 109.6 | yes |
| PLAY_VIS_MID | 65.14 / 74.18 | 66.2 / 75.5 | yes |
| SIDE | 105.45 / 114.23 | 106.5 / 115.4 | yes |
| FAR | 53.65 / 59.17 | 54.7 / 60.3 | yes |
| ABOVE | 98.06 / 101.88 | 99.0 / 103.0 | yes |
| BELOW | 172.40 / 194.73 | 173.5 / 196.0 | yes |
| NEAR_EDGE (stress) | 89.82 / 100.58 | 90.9 / 101.7 | yes \* |
| CLEAR (control) | 4.00 / 4.24 | 5.1 / 5.4 | n/a |

\* NEAR_EDGE's cell in this run disagrees with the frontier run's 179.4 ms and
its counters show a 4.3x difference in descriptor evaluations, so its scene is
not the same one. Its attribution row is **discarded**; the frontier figure
stands as the stress baseline.

Per-pixel workload at the shipped ladder (129,600 marched pixels everywhere):

| pose | steps/px | density calls/px | shape calls/px | evals/px | fetches/px | light evals/px | empty-step share |
|---|---|---|---|---|---|---|---|
| PLAY_VIS_MID | 26.91 | 5.86 | 18.6 | 80 | 779 | 2.47 | 96.0 % |
| SIDE | 30.94 | 29.29 | 47.8 | 353 | 2659 | 15.61 | 83.0 % |
| FAR | 28.65 | 4.88 | 11.4 | 58 | 533 | 2.04 | 97.0 % |
| ABOVE | 16.93 | 79.64 | 80.2 | 641 | 3653 | 66.52 | 29.8 % |
| BELOW | 26.08 | 45.92 | 157.9 | 642 | 10187 | 0.00 | 0.3 % |

**A harness reliability problem worth recording**: `PLAY_VIS_NEAR` rendered *no
storm at all* in two of three runs on this fixture archetype — 100 % empty-space
rejects, zero density calls, 12.2 ms — while `PLAY_VIS_MID` and `SIDE` had
material in the same runs. This is the third time a pose has silently rendered a
stormless scene (T142's `PLAY_NEAR`, the T138 ladder, now this). The profile
rejects cells whose *descriptor count* falls; it does not reject cells that
render *no cloud*. **A storm-visibility guard should be added before the next
measurement task** — a one-frame counter capture after the pose settles,
requiring `cloudDensityCalls > 0` for a storm pose.

---

## 2. Phase 2 — the new cost distribution

Each share is measured by removing that class of work outright, so it is a
ceiling for anything that cheapens the class rather than deletes it.

| pose | production | **lighting share** | **detail-octave share** | rain-gate value (T145) | distance ceiling |
|---|---|---|---|---|---|
| PLAY_VIS_NEAR | 101.34 | **−23.2 %** | −17.5 % | +14.2 % | −29.5 % |
| PLAY_VIS_MID | 65.14 | −19.2 % | −17.5 % | +16.5 % | −92.1 % |
| SIDE | 105.45 | **−24.6 %** | −18.5 % | +21.6 % | −25.1 % |
| FAR | 53.65 | −15.8 % | −8.6 % | +25.0 % | −86.0 % |
| ABOVE | 98.06 | **−72.7 %** | −41.0 % | +26.7 % | −8.7 % |
| CLEAR | 4.00 | −55.8 % | −6.7 % | −0.8 % | −56.5 % |

Three results that do **not** carry over from before Rank 1:

1. **Lighting is 16–25 % at the representative and severe poses and 73 % at
   ABOVE.** T137 carried it as "~6.5–11.4 % representative". That figure is
   superseded: lighting is now a 1.2–1.3x lever, and at ABOVE a 3.7x one.
2. **The detail-noise octaves are 9–41 %** — never separately measured before.
3. **T145's rain gate is worth more at the new ladder than at the old**:
   removing it costs +14 % to +27 %, against the +12 % to +21 % it measured at
   0.75. Fewer pixels did not make the gate less valuable.

The **reconstruction and composite remain negligible**: 0.085–0.121 ms, 0.08 %
of a 101 ms frame, unchanged by Rank 1. History cost remains unmeasurable above
noise. The old worry that "fixed and reconstruction costs may now matter much
more" is measured and is **not** the case — the pass is still overwhelmingly the
march.

The distance column is a **visibility** ceiling, not an LOD one: halving the
render distance deletes the storm outright at FAR and PLAY_VIS_MID, which is why
those two collapse 86–92 %. T098a forbids a disappearing FAR, so this number
bounds distance work but is not itself adoptable.

---

## 3. Phase 3 — elasticities after Rank 1

| pose | eval work | eval time | **eval elasticity** | fetch work | fetch time | **fetch elasticity** |
|---|---|---|---|---|---|---|
| PLAY_VIS_NEAR | +75.5 % | +13.4 % | 0.178 | +58.2 % | +23.4 % | 0.401 |
| PLAY_VIS_MID | +76.9 % | +11.3 % | 0.147 | +60.2 % | +25.1 % | 0.417 |
| SIDE | +90.1 % | +15.4 % | 0.171 | +80.2 % | +24.2 % | 0.302 |
| FAR | +82.1 % | +13.5 % | 0.164 | +65.5 % | +24.1 % | 0.367 |
| ABOVE | +100.0 % | +19.7 % | 0.197 | +131.3 % | +26.0 % | 0.198 |

**The descriptor elasticities survive Rank 1 essentially unchanged** — evaluation
0.15–0.20 against 0.15–0.31 before, fetch 0.20–0.42 against 0.20–0.81. Cutting
the pixel count by 9x did not change which dimensions move GPU time. T141's
rejections of descriptor evaluation and fetch work therefore still stand at the
new ladder and do not need re-litigating.

---

## 4. Phase 4 — interleaved reconstruction

Nothing to re-measure; the ceiling is re-derived from T146's frontier, which was
measured on this build:

| | scale | dims | representative cloud p50 |
|---|---|---|---|
| shipped Ultra | 0.250 | 480x270 | 113.2 ms |
| one interleave phase | 0.125 | 240x135 | 55.4 ms |

**Ceiling 2.04x**, achieved only if a 2x2 phase pattern accumulated into a
480x270 resolve target really delivers 0.250-class spatial information. Cost of
the resolve pass is bounded by the existing composite's 0.09 ms — the same work
at the same resolution — so the ceiling is not eroded by overhead.

Its risk is specific and documented in the shader itself: animating the sample
lattice made thin silhouette pixels alternate between hit and miss, which is why
`searchBlue` is a static screen-space phase today. Interleaving *requires* moving
that lattice.

---

## 5. Phase 5 — distance / LOD, measured

Two diagnostic ceilings, both removing a class outright.

| pose | lighting only | detail only | **both** | independent prediction | interaction |
|---|---|---|---|---|---|
| PLAY_VIS_NEAR | −21.4 % | −16.6 % | **−30.0 %** | −34.4 % | overlapping |
| PLAY_VIS_MID | −20.2 % | −11.0 % | −31.4 % | −29.0 % | independent |
| SIDE | −24.5 % | −18.2 % | **−37.6 %** | −38.2 % | independent |
| FAR | −17.7 % | −12.5 % | −27.9 % | −28.0 % | independent |
| ABOVE | −70.8 % | −42.9 % | **−89.0 %** | −83.3 % | super-additive |

| pose | production | both removed | speedup | remaining vs 8 ms |
|---|---|---|---|---|
| PLAY_VIS_NEAR | 104.33 | 73.04 | **1.43x** | 9.1x |
| PLAY_VIS_MID | 65.55 | 44.94 | 1.46x | 5.6x |
| SIDE | 104.48 | 65.23 | **1.60x** | 8.2x |
| FAR | 55.66 | 40.15 | 1.39x | 5.0x |
| ABOVE | 98.10 | 10.75 | **9.13x** | 1.3x |

**The two are independent at four of five poses and super-additive at ABOVE**,
where the detail octaves are consumed inside the light march, so removing detail
makes the lighting removal worth more than its own share.

**Ceiling for distance/LOD is 1.39–1.60x at representative and severe poses.**
That is the value of deleting lighting and detail *entirely*; a graded policy
that only cheapens them at distance captures a fraction of it, and it trades
quality away — the opposite direction from candidate A.

---

## 6. Phases 6, 7 and 8 — A against B, and the remaining gap

| | A. interleaved reconstruction | B. distance / LOD |
|---|---|---|
| measured representative ceiling | **2.04x** | 1.43x |
| severe ceiling | 2.04x (uniform, it is a pixel lever) | 1.60x SIDE, 9.13x ABOVE |
| stress | 2.29x (967.1 → 78.5 at 0.125 vs 179.4 at 0.250, frontier) | not measured on the stress pose |
| implementation | **new subsystem**: resolve target, phase offsets, reprojection, disocclusion | policy over two existing uniforms |
| complexity | high | low |
| image-quality direction | **positive** — buys spatial resolution back | negative — removes lighting and detail at distance |
| temporal artefacts | **the main risk**; moving the sample lattice is what the shader avoids today | popping at LOD transitions |
| T098a risk | silhouette stability is exactly what T098a measures | FAR readability, which T098a protects |
| T098b regrade scope | large, but in the direction of improvement | moderate, in the direction of regression |

Stacked at their ceilings, assuming independence (which §5 shows holds between
the two LOD components and which A does not touch, being a pixel lever):

| stage | factor | representative ms | vs 8 ms |
|---|---|---|---|
| shipped Ultra today | — | 101.3–104.3 | **~13.0x** |
| + A at its ceiling | 2.04x | 51.1 | 6.4x |
| + B at its ceiling | 1.43x | 35.7 | **4.5x** |

**Neither candidate, nor both together at their ceilings, reaches the 8 ms
budget.** The gap falls from 13.0x to about 4.5x, which is real progress and
still not budget. That is the Phase 10 CASE D condition partially met: a further
architectural change will be needed after A and B, and it should be named
honestly rather than deferred indefinitely.

---

## 7. Phase 9 — T098b reconnaissance at the shipped ladder

Diagnostic only, on the T146 capture set. Not an acceptance.

- **The dominant visual defect has changed.** At the shipped 480x270 the
  silhouette rim is visibly stair-stepped — the ABOVE frame shows quantisation
  along the whole anvil edge. **Reconstruction artefacts now outrank the ANVIL
  surface-flatness finding** that T098b previously carried as its top blocker.
- **The old ANVIL blocker is no longer dominant.** The canopy reads with *more*
  apparent structure at the lower internal resolution, not less, because the
  noise is no longer being averaged across as many samples. The "too smooth"
  complaint is not what the shipped frames show.
- **The 4-pixel beat is unchanged in period and mixed in strength.** Both the old
  0.750 and the new 0.250 have denominator 4, so the predicted period is 4 px at
  both — and it measures 4 px at both. Amplitude ratio against control periods:
  SIDE 2.95 → 1.91 (weaker), FAR 1.51 → 1.65, ABOVE 1.66 → 2.52 (stronger),
  PLAY_VIS_NEAR 0.67 → 1.97 (stronger). **The beat did not go away and did not
  systematically worsen; it moved with the pose.**
- **Structure is intact.** SIDE at the shipped Ultra still reads as a connected
  anvil, neck and billowed base, consistent with T098a passing at every scale.

This strengthens the case for candidate A: the artefact that now leads the
visual defect list is exactly the one interleaving addresses.

---

## 8. Phase 10 — decision, and Phase 14 — SC-006

**CASE A. Interleaved reconstruction is the next implementation task.**

It outranks distance/LOD on four grounds, in order of weight:

1. **Larger measured ceiling** — 2.04x against 1.43x representative.
2. **It moves quality the right way.** A buys spatial resolution back, so it can
   be spent *either* as performance at equal quality *or* as quality at equal
   performance. B can only trade quality away.
3. **It attacks the defect that now leads the list.** §7 finds silhouette
   quantisation has displaced the ANVIL flatness as the dominant artefact.
4. **B remains fully available afterwards.** The two attack disjoint work — A is
   a pixel lever, B is a per-sample lever — and §5's interaction data shows the
   LOD components themselves stack, so nothing is lost by ordering A first.

Its higher implementation risk is real and is the reason for one hard
precondition: **the first milestone must be a silhouette-stability measurement
on a moving-camera fixture**, because static poses cannot see the hit/miss
flicker that made the current shader freeze its sample lattice.

**SC-006 is not credible as written.** Representative Ultra is 13.0x over the
8 ms cloud budget and total-frame p95 is 6.5x over SC-006's 16.7 ms. Even A and B
at their ceilings leave 4.5x. It stays unrescoped, per instruction, and reported
separately from the stress case, which the frontier puts at 179.4 ms — 22.4x over
budget — at the shipped Ultra.

---

## 9. Next recommended task

**T148 — interleaved reconstruction**, sequenced as:

1. a moving-camera fixture and a silhouette-stability metric, *before* any
   interleaving code, so the failure mode has a measurement;
2. a 2x2 phase pattern marching 240x135 into a 480x270 resolve target with
   reprojection and disocclusion fallback;
3. the same-fixture A/B against the shipped ladder, with T098a as a hard gate.

**T149 — distance/LOD**, queued behind it, targeting the measured 16–25 %
lighting and 9–41 % detail shares with a graded policy rather than deletion.

**T150 — the storm-visibility guard** in `StormT135PerformanceProfile`, which
should land before either, because a pose that silently renders no storm has now
corrupted three separate measurement runs.

---

## Appendix — evidence

| artefact | path |
|---|---|
| baseline and seven-arm attribution | `run/logs/t147-baseline.log` |
| eight-arm attribution including lighting | `run/logs/t147-attribution.log` |
| lighting/detail interaction | `run/logs/t147-combined.log` |
| reconnaissance captures | `run/screenshots/t138/22ff00c4/` |
| arms | `T147_HALF_DISTANCE`, `T147_DETAIL_OFF`, plus the constant-lighting and combined arms in `StormT132AutoDriver` — all diagnostic, default off |
