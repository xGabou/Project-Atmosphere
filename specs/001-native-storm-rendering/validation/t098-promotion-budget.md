# T098 second divergence — premature fine promotion exhausting the march budget

**Feature**: `001-native-storm-rendering`
**Task**: T098 (US1 morphology acceptance gate)
**Date**: 2026-09-01
**Follows**: `t098-production-ray-trace.md` (first divergence: the composite depth sentinel)

---

## 1. The old promotion / fine state machine

`sinceHit` is the only state. `fine = sinceHit < 6`, and `sinceHit` is reset to
0 by four events, only one of which is actual material:

| reset | trigger | is it material? |
|---|---|---|
| puff promotion | `directPuffSegmentMayIntersect` | no — geometric |
| storm promotion | `directStormSegmentMayIntersect` and `safeAdvance <= fineStep` | no — geometric |
| bracket refinement | a coarse step landed in material | yes |
| material hit | `density > 0.0008` | yes |

Everything else increments it. So a zero-density sample refreshes nothing, but
**descriptor proximity does**: the promotion probe fires whenever the ray is
inside the conservative clearance, resets `sinceHit`, and forces one fine step.

That makes fine mode self-sustaining without any density. Five fine iterations
run (`sinceHit` 1..5), the sixth enters coarse, the promotion probe fires again
because the ray is still inside the same clearance, and `sinceHit` returns to 0.
Every iteration advances exactly one fine step. The answer to the PHASE 2
question is **D: repeated promotion probes**, on **A: geometric candidate
proximity**. `cloudDensity > 0` is required nowhere in the loop.

## 2. Why ~65 empty fine iterations occur

Two reaches that the marcher treated as one:

- **geometric envelope reach** — where a descriptor's conservative clearance is
  satisfied. `paSafeAdvance = paMinClearance - STORM_MAX_BLEND_BLOCKS`, so
  forced fine begins once clearance falls to about `48 + fineStep`.
- **density-support reach** — where the noise-formed body first clears the
  density threshold. `stormBody` is a *remap*: it maps a low coverage envelope
  to nothing over most of the envelope, which is what makes the body
  noise-formed rather than a balloon.

They are far apart. From the live production traces, six waist rays and their
controls:

| fixture | ray | first promotion t | envelope > 0 t | first material t | empty fine iters | empty fine blocks | iters left at material | final alpha | termination |
|---|---|---|---|---|---|---|---|---|---|
| 32123d75 | WAIST | 284.0 | 683.5 | 771.0 | 64 | 160.0 | 47 | 0.920 | step_cap |
| 4cb7d540 | WAIST | 282.2 | 763.0 | 780.5 | 43 | 107.5 | 42 | 0.987 | floor |
| 9b30c698 | WAIST | 284.0 | 679.0 | 754.0 | 59 | 147.5 | 46 | 0.986 | floor |
| 044d01de | WAIST | 242.6 | 802.5 | 887.5 | 79 | 197.5 | 30 | 0.920 | step_cap |
| 87c5c9de | WAIST | 282.2 | 722.0 | 832.0 | 76 | 190.0 | 20 | 0.985 | floor |
| ee4a801b | WAIST | 242.6 | 719.0 | 851.5 | 77 | 192.5 | 33 | **0.446** | step_cap |
| | **WAIST mean** | | | | **66.3** | **165.8** | **36.3** | 0.874 | 3/6 step_cap |
| | BASE mean | | | | 46.0 | 115.0 | 40.5 | 0.985 | 1/6 step_cap |
| | ANVIL mean | | | | 43.0 | 107.5 | 22.8 | 0.986 | 0/6 step_cap |

The waist ray is worst because it grazes the storm tangentially: it is inside
the conservative clearance longest and meets material latest.

## 3. The state distinction that was missing

Not "conservative bound" versus "material" — the marcher genuinely must sample
at fine resolution inside the conservative bound, and any rule that samples
more coarsely is unsafe (see the `bisectOnly` control below).

The missing distinction is between **sampling** at fine resolution and
**spending a march iteration** per sample. `MAX_STEPS` bounds iterations, not
density evaluations. The old policy conflated them.

## 4. Candidate policies, measured

Offline against a **one-block reference traversal**, nine rays on the severe
fixture, production constants (fineStep 2.5, stepBudget 96, MAX_STEPS 128,
ExtinctionScale 0.11499), including the four-bisection bracket refinement and
the transmittance floor.

| policy | reached material | step-capped | empty fine iters | material skipped |
|---|---|---|---|---|
| `production` (shipped) | 7/9 | 4/9 | **701** | 0 |
| `production384` (truth arm, PHASE 7) | 9/9 | 0/9 | 701 | 0 |
| **`scan16` (adopted)** | **9/9** | **0/9** | **23** | **0** |
| `scan-coarse2` | 9/9 | 0/9 | 28 | 0 |
| `bisectOnly` | 9/9 | 0/9 | 3 | **9 segments, 1.4–39.2 blocks** |

- **`bisectOnly`** drops the forced promotion and trusts the bracket refinement
  alone. It looks reasonable and is not: it skips material on *every* ray. This
  is why the promotion cannot simply be removed, and it is retained in the
  guard as a live control.
- **`scan-coarse2`** probes at twice the fine spacing. It happened to skip
  nothing on these rays, but it samples below the production fine resolution
  and so has no equivalence argument. Not adopted.
- **`production384`** is a *truth arm*, not a proposal. It shows what the old
  policy converges to given three times the budget: alpha 0.985–0.988. That is
  the target the corrected policy must hit at 128 steps.

## 5. The selected rule

When the conservative probe fires and `paSafeAdvance <= fineStep`, probe forward
on **exactly the lattice the fine march would have sampled**, inside this one
iteration, bounded by `PA_EMPTY_SPAN_PROBES = 16` — the coarse stride is capped
at sixteen fine steps, so sixteen probes cover a whole candidate span.

- every probe empty → the span is empty at the march's own resolution; cross it
  in one iteration instead of sixteen.
- a probe finds material → advance to the last empty probe and enter fine mode,
  so material entry is unchanged.

This is not a weakening of the conservative test. **The samples taken are the
same samples**, so anything the production fine march would have found is still
found. Measured: **0 false negatives**, material entry error **0.00 blocks** and
converged alpha error **0.00000** against the truth arm.

`MAX_STEPS` stays 128. No morphology, density, role-strength, `exteriorFineStep`,
history, composite or blend change.

## 6. Step-budget recovery

| metric | before | after |
|---|---|---|
| empty fine iterations, 9 rays | 701 | **23** |
| rays never reaching material | 2/9 | **0/9** |
| rays step-capped | 4/9 | **0/9** |
| worst final alpha | 0.000 | **0.985** |
| entry error vs truth arm | — | **0.00 blocks** |
| alpha error vs truth arm | — | **0.00000** |

## 7. Live exact-ray validation

Controlled SIDE pose, trace taken inside the capture set, ray identity proved
as before (`productionTexel.a == tracedAlpha`, `identity=AGREES`):

| ray | iterations | termination | final alpha |
|---|---|---|---|
| WAIST | 128 → **65** | step_cap → **transmittance_floor** | 0.075–0.63 → **0.98730** |
| BASE | 97 → 39 | transmittance_floor | 0.98682 |
| ANVIL | 69 → 38 | transmittance_floor | 0.98486 |

The centre column between anvil and base carries no sky pixel at the SIDE pose.

## 8. Cost

Same fixture, same poses, promotion policy the only difference
(`PaLegacyFinePromotion`), cloud-pass GPU time:

| view | legacy promotion | corrected | delta |
|---|---|---|---|
| SIDE | 433.34 ms | 565.82 ms | +132.48 (+30.6%) |
| FAR | 239.99 ms | 340.71 ms | +100.71 (+42.0%) |
| ABOVE | 623.18 ms | 644.91 ms | +21.74 (+3.5%) |
| BELOW | 637.86 ms | 850.18 ms | +212.32 (+33.3%) |

**The correction costs more, and the reason is not overhead.** The old policy
was cheap because it gave up: it hit the iteration cap and stopped before
integrating the storm, so it never paid for the material samples or their light
marches. The new policy finishes the ray. Against an equal-quality baseline —
the same policy given the budget to converge (`production384`) — the scan needs
about **5% more density evaluations** while using roughly **one third of the
march iterations**, and it takes *fewer* promotion probes (7–21 against 11–29).

This is a real frame-cost increase that should be weighed before shipping, and
it is a consequence of rendering the storm rather than of the scan itself.

## 9. Live campaign — five fresh severe fixtures

Seeds 776001–776005, measured on the centre column between the storm's own
projected ANVIL and BASE rows:

| seed | group | centre-column cloud share | longest inner sky run | 400px band fill |
|---|---|---|---|---|
| 776001 | `f5b87083` | 1.0000 | **0 px** | 0.8831 |
| 776002 | `6c8c6747` | 1.0000 | **0 px** | 0.8491 |
| 776003 | `7572f1a9` | 1.0000 | **0 px** | 0.8713 |
| 776004 | `e4204d6d` | 1.0000 | **0 px** | 0.8551 |
| 776005 | `f9ae3dc7` | 1.0000 | **0 px** | 0.8699 |
| **mean** | | **1.0000** | **0.0 px** | **0.8657** |

Against the previous pass (depth fix only): column share 0.928, longest sky run
9.4 px, band fill 0.8167. Against the unfixed baseline: 0.794, 31.8 px, 0.7193.

All fifteen traced rays (5 fixtures x WAIST/BASE/ANVIL) terminate on the
transmittance floor. **Zero step caps.** WAIST mean 61.6 iterations, mean alpha
0.9862.

## 10. T098 criteria grading

**FR-023 positive — all nine required:**

| # | criterion | verdict |
|---|---|---|
| 1 | broad continuous lower cloud base | present |
| 2 | dense convective/core region | present |
| 3 | vertical tower development emerging naturally from the base | **absent** — a narrow neck, not a tower |
| 4 | progressive vertical narrowing | **absent** — base, then an abrupt neck, then a wide cap |
| 5 | broad upper anvil | present |
| 6 | multi-scale billowing across the visible body | **absent on the anvil**; present on the base |
| 7 | surface variation at multiple spatial frequencies | **partial** — base yes, anvil no |
| 8 | irregular but coherent silhouette curvature | **absent on the anvil** — a smooth near-circular arc |
| 9 | continuous transitions base→tower→core→anvil | **present** (delivered by this correction) |

**FR-024 negative — none may be present:**

| # | rejected form | verdict |
|---|---|---|
| 1 | large smooth balloon surfaces | **PRESENT** — the anvil cap |
| 2 | large regions of visually uniform density | **PRESENT** — the anvil interior and the base disc |
| 3–8 | primitives, ears, seams, walls, slabs, uniform silhouettes | not observed |

Also observed, recorded and not investigated: horizontal stair-step banding on
the anvil's upper surface, visible at both SIDE and FAR.

## 11. Verdict

**T098 remains OPEN. T099 remains blocked.**

Both march defects are fixed and guarded, and the connectivity criterion they
blocked (FR-023 #9) now passes on 5 of 5 fresh fixtures with a fully continuous
centre column and zero step caps. The remaining failure is **independent of the
marcher**: the anvil renders as a large smooth balloon with uniform interior
density, which fails FR-024 #1 and #2 outright and takes FR-023 #3, #4, #6, #7
and #8 with it.

Per the T098 instruction that names this case, the exact remaining criterion is
recorded and **no further hypothesis is opened here**. The next investigation
is the anvil body: why the noise-formed body that works on the base does not
form billows on the anvil.
