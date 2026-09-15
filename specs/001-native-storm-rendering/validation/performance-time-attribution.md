# T201 - FINAL time attribution at 0.25: what each consumer costs in milliseconds

Measurement and target selection only. No temporal, rain, field-resolution,
light-quality, probe-floor or architecture change. One launch
(`t201-campaign/t201-run.log`, 10:58-11:12 on 12Sep2026, 8,202 frames):
SIDE and PLAY_VIS_NEAR at 0.25 (480x270), Ultra 96 steps, history off, one
deterministic fixture a pose (`T150_VISIBILITY_CONFIRMED` SIDE 22,182
density calls at 0.25, PLAY_VIS_NEAR 7,519), 91 arms a pose: every consumer
arm bracketed by a fresh FINAL anchor, three repeats, 60 samples a cell,
anchor drift <= 3% or the block is dropped (`T201_REJECTED count=0`).

## BASELINE (MEASURED)

1. **Starting HEAD**: `a5f062d` (T200). The field FINAL of T199 (bits 11,
   built every frame), unchanged.
2. **SIDE 0.25** (46 anchor cells): field build **1.84 / 2.19 ms** p50/p95,
   ray **12.98 / 14.17 ms**, composite 0.082 ms, combined **14.82 ms** p50.
3. **PLAY_VIS_NEAR 0.25** (46 anchors): field **1.96 / 2.11 ms**, ray
   **12.35 / 13.21 ms**, composite 0.081, combined **14.31 ms** p50.

These are the local controls every ceiling below is paired against; T200's
13.17 / 12.21 ms from another session are not used.

## CEILINGS (MEASURED, paired against the bracketing anchors)

Each arm is FINAL's own specialization (precompute, precipitation absent,
the field built and read at bits 11 - the lean map is shared, so every arm
inherits it) with one consumer compiled out. Reused: `t181_norefine`
(`PA_ARM_NO_REFINE`), `t176_nolight` (`PaDiagnosticLightingMode = 1`),
`t180_noprobe` (`PA_ARM_NO_PROBE`), `t175_density_every2`. New, because no
existing arm removed the consumer on the current specialization:
`t201_primary_field` (the primary body density read from the field's A
channel, one trilinear fetch, march structure intact -
`PA_ARM_PRIMARY_FIELD_DENSITY`), `t201_nodetail` (`PA_ARM_NO_DETAIL` on the
current specialization; `t166_nodetail` predates the T174 precompute),
`t201_norain_shaft` (`PA_ARM_NO_RAIN_SHAFT`, the shaft's two Gauss samples a
segment off, the segment test itself untouched), `t201_primary_norefine`
(the two together). `T191_VARIANT_SCOPE declared=159 registered=14`.

| consumer | arm | pose | control ray | removed ray | **delta ms** | ratio | ratio spread |
|---|---|---|---|---|---|---|---|
| 4. primary body density | `t201_primary_field` | SIDE | 12.97 | 11.12 | **1.85** | 1.167x | 0.4% |
| | | PLAY_VIS_NEAR | 12.41 | 10.48 | **1.94** | 1.185x | 0.3% |
| 5. refinement | `t181_norefine` | SIDE | 12.99 | 12.13 | **0.86** | 1.071x | 2.6% |
| | | PLAY_VIS_NEAR | 12.44 | 11.45 | **0.98** | 1.086x | 2.3% |
| 6. rain shaft (rain family) | `t201_norain_shaft` | SIDE | 12.99 | 10.50 | **2.49** | 1.237x | 3.9% |
| | | PLAY_VIS_NEAR | 12.44 | 9.98 | **2.46** | 1.247x | 2.2% |
| 7. detail noise (everywhere) | `t201_nodetail` | SIDE | 12.99 | 12.19 | **0.80** | 1.066x | 0.2% |
| | | PLAY_VIS_NEAR | 12.37 | 11.62 | **0.75** | 1.065x | 2.5% |
| 7. light cone (field taps + scatter chain) | `t176_nolight` | SIDE | 12.99 | 12.72 | **0.27** | 1.021x | 1.9% |
| | | PLAY_VIS_NEAR | 12.39 | 12.08 | **0.31** | 1.026x | 1.0% |
| 7. probe scan removed | `t180_noprobe` | SIDE | 12.97 | 13.60 | **-0.63** | 0.954x | 2.1% |
| | | PLAY_VIS_NEAR | 12.37 | 13.74 | **-1.38** | 0.900x | 3.1% |
| (not a ceiling) primary density every 2nd step | `t175_density_every2` | SIDE | 13.01 | 16.91 | -3.90 | 0.769x | 1.9% |
| | | PLAY_VIS_NEAR | 12.30 | 15.68 | -3.38 | 0.785x | 1.7% |

8-9. Paired ratios and spreads are in the table; every block accepted.
Three readings need a sentence. The **probe scan is a saving, not a cost**:
removing it makes FINAL 0.6-1.4 ms slower, so the field probe earns its
keep and stays. **The every-2nd-step hold is slower, not faster**: holding a
density across a step lengthens the material runs the march is in, so it
takes more steps and spawns more lighting than it saves in evaluations -
it was never a ceiling on this march and is reported only to close it. And
**the light cone is now 0.3 ms**: what T176 measured at 28.8% of the
pre-field frame's shape calls is, with the field, four trilinear reads plus
noise a tap and a scatter chain, 2% of the ray.

## COMBINED (MEASURED, not multiplied)

10. Primary body density + refinement together (`t201_primary_norefine`):
    delta **2.62 ms SIDE** (13.00 -> 10.38), **2.70 ms PLAY_VIS_NEAR** (12.39
    -> 9.69); the sum of the two singles is 2.71 / 2.92, so the pair is
    slightly sub-additive - the refinement that remains after the primary
    goes to the field is cheaper than the refinement of the exact body.
11. Combined ratio **1.253x SIDE / 1.279x PLAY_VIS_NEAR**, spreads 0.7% / 1.4%.

The whole non-rain structural budget, every one of the four consumers to
zero: 1.85 + 0.86 + 0.80 + 0.27 = **3.8 ms SIDE**, 1.94 + 0.98 + 0.75 + 0.31
= **4.0 ms PLAY_VIS_NEAR**. That is the most any non-rain architecture, or
all of them together, could return; the ray would still be 9.2 / 8.4 ms and
the combined cost 11.0 / 10.4 ms.

## FIELD (MEASURED)

12. Field build on the FINAL anchors: **1.84 ms p50 / 2.19 p95 SIDE, 1.96 /
    2.11 PLAY_VIS_NEAR** (T200: 2.1-2.2; T199: 1.65 - the build moves 20%
    between sessions for the same program while the march moves 1-2%).
13. Breakdown: the timer (`RAIN_FIELD_TIMER`) wraps the build draw alone;
    the clear-and-bind and the uniform uploads around it are outside it and
    not separately timed (no second timer was added - measurement only).
    Within the draw: the **empty-sky build is 0.54 ms** (`lean_final@d0`,
    three repeats a pose, 0.539-0.546 p50, 0.85-1.08 p95) - the atlas's own
    2,097,152 fragments against no descriptor - and the remaining **1.3-1.4
    ms is the node work descriptors cause**: 10 descriptors resident, the
    build-mode walk per node. No removable component shows in that split
    short of the empty-descriptor early-out T199 named (which is worth 0.54
    ms on a clear frame and nothing on a storm frame). Composite 0.08 ms.
14. Empty-sky comparison retained: 0.54 ms here, 0.53 in T199 - descriptor
    occupancy is 70% of the build.

## PRIMARY AUDIT

Counters at production bits (the field on) come from T199's `|final`
capture at SIDE (`t199-campaign/t199-run.log`; T201's own counter phase ran
the monolith at the campaign-start default, field off, and is not used),
per pixel over 129,600 pixels at 480x270; the per-class costs come from the
T166 fixed-work ladder timed in this run (64 evaluations a fragment, one
class added a rung, spreads <= 0.7%):

| rung | adds | p50 ms (SIDE / PVN) | delta | share of one evaluation |
|---|---|---|---|---|
| fw1 address | ray address arithmetic | 0.016 / 0.016 | - | 0.7% |
| fw2 candidate | candidate-tile fetch | 0.580 / 0.589 | 0.56 / 0.57 | **25%** |
| fw3 descriptor | descriptor texel fetch (pre-T174 path) | 0.717 / 0.788 | 0.14 / 0.20 | 6-9% |
| fw4 shape | descriptor union / SDF shape | 1.811 / 1.808 | **1.09 / 1.02** | **46-49%** |
| fw5 nodetail | base noise + threshold (no detail) | 2.196 / 2.188 | 0.39 / 0.38 | 17% |
| fw6 density | detail octaves | 2.231 / 2.228 | 0.035 / 0.040 | 1.6-1.8% |

2.23 ms for 8.29M coherent evaluations = **0.27 ns an evaluation**, half of
it the shape union.

15. **Steps / pixel**: 4,255,320 primary ray steps = **32.8 a pixel** (FAR
    29.3); empty-space rejects 3.41M (26.3 a pixel) - most steps are skipped
    by the segment test and the probe, not marched.
16. **Density calls / pixel**: 845,393 = **6.52 a pixel** (FAR 1.48); of
    which **427,836 returned zero - 50.6%** (FAR 61.6%). Under the pre-field
    monolith (T201's own counters, same pose class) 513,501 calls with
    80,901 zero (15.8%): the field probe's conservative floor lets the march
    evaluate 3.3x more empty points than the exact scan did, and still nets
    0.6-1.4 ms (the probe row above).
17. **Group walks / pixel**: `directStormShapeCalls` 3.50M = **27.0 a
    pixel**, `groupFieldCalls` 2.62M = 20.2, `lobesVisited` 26.2M = **202 a
    pixel**, conservative descriptor rejects 9.8M = 75.7 - four shape calls
    and twenty lobes visited for every primary density call.
18. **Exact SDFs / pixel**: `descriptorEvaluations` 21.2M = **164 a pixel**;
    `bracketExactSdf` 0 (the bracket walk is gone with the field).
19. **Noise / detail**: `detailFetchPrimary` 462,868 = 3.57 a pixel, 25.8
    detail-octave evaluations a pixel; measured detail ceiling 0.80 ms
    (everywhere) against a ladder cost of 0.035 ms per 64 evaluations - the
    detail's cost is in the divergent fetch, not the arithmetic. Base noise
    is 17% of a coherent evaluation.
20. **Identified dominant primary cost: the descriptor traversal per call,
    not the evaluation.** The measured primary ceiling is 1.85 ms for 845k
    calls = **2.2 ns a call**, eight times the ladder's 0.27 ns for one
    coherent evaluation; the difference is the four shape calls, three group
    walks and twenty lobe visits behind each production call, and their
    divergence across a warp. The shape union is already half of even the
    coherent cost. The next primary-march architecture should target the
    per-call group traversal (fewer shape calls per density call, coherent
    lobe visits), not "the primary march" and not the noise.

## PROBE CONSEQUENCE (DERIVED)

21. Upper bound on primary-march time that exists because the conservative
    probe says "possible" where the region is empty: the zero-density share
    of primary calls times the primary density ceiling - **0.506 x 1.85 =
    0.94 ms SIDE**, 0.616 x 1.94 = **1.19 ms** using FAR's share with
    PLAY_VIS_NEAR's delta. This is a bound, not a saving: it assumes a
    tighter floor would remove every zero-density call at the ceiling's
    per-call cost, and the field probe's net effect is already +0.6-1.4 ms in
    FINAL's favour.
22. **Tighter floor still justified: NO.** Under 1.2 ms at its most generous,
    below the 2 ms band, and consistent with T199's 4.7% estimate (0.7 ms
    of this frame). **Probe-floor work is closed for this pass.**

## RANKING (non-rain, by measured removable ms)

23. **#1: the primary body density evaluation** - specifically its per-call
    descriptor traversal (item 20).
24. Removable: **<= 1.85 ms SIDE / <= 1.94 ms PLAY_VIS_NEAR** (the ceiling;
    a real architecture keeps some evaluation). **Secondary band (1-2 ms).**
25. **#2: refinement.**
26. Removable: **<= 0.86 / <= 0.98 ms**. Below the 1 ms line on its own; the
    pair with #1 measures 2.62 / 2.70 ms, the **strong band**, only when
    taken together. Detail (0.80 / 0.75) and the light cone (0.27 / 0.31)
    are below the line.

Outside the ranking by the brief, but measured because the brief asked: the
**rain shaft is 2.49 / 2.46 ms**, the largest single consumer this campaign
priced, and the rain segment test - 2.06M shape calls at SIDE, 59% of all
shape calls under the field - was not priced (out of scope). The
arithmetic of what is left says where it is: control 12.98 ms minus every
ceiling above (1.85 + 0.86 + 0.80 + 0.27 + 2.49 = 6.27) leaves **6.7 ms** for
the segment test, the march's own control and the composition, and no
non-rain consumer is in it.

## DECISION

27. **Selected next architecture: the primary body density's per-call
    traversal** (T202) - the only non-rain consumer with a measured ceiling
    over 1 ms - taken together with refinement as the pair the campaign
    measured at 2.62 / 2.70 ms. Concretely: reduce the shape calls and lobe
    visits behind each primary density call (four shape calls and twenty
    lobes a call today; the shape union is half of a coherent evaluation),
    keeping the exact body. Not a noise change, not a step change.
28. **Expected realistic gain: 1.0-1.5 ms** at 0.25 (50-80% of the pair's
    ceiling; the traversal is most of the primary cost but not all of it).
29. **Expected raw 0.25 cost after it: ~13.3-13.8 ms SIDE, ~12.8-13.3
    PLAY_VIS_NEAR** (from 14.8 / 14.3 combined).
30. **Temporal reopen plausible after the next architecture: NO.** The
    reopen condition is <= 8 ms (prefer 6) combined at SIDE with
    PLAY_VIS_NEAR in the same class. Every non-rain consumer at zero gives
    11.0 / 10.4 ms; the next architecture lands ~13.5. **The 6-8 ms class is
    not reachable inside the current scope.** The measured rain shaft (2.5
    ms) and the unmeasured segment test (the bulk of the 6.7 ms residual)
    are where the rest of the frame is, and rain is closed by the brief.
    That constraint is now the binding one, and it is the user's to
    reconsider, not this task's: the cheapest next fact would be one arm
    (`t182_norainseg`, which exists on the current specialization) timed in
    the same way to put a number on the segment test - a measurement, not a
    reopening - so the rain decision can be taken on milliseconds.

## VALIDATION

31. `./gradlew.bat campaignGate`: `gate-t201-campaign.log` PASS
    (`T170_WIRING campaigns=43 violations=0`, T191 reachability PASS, T161
    PASS).
32. `./gradlew.bat check build`: PASS (`gate-t201.log`, 47 invariants, jar).
33. **Variant scope**: four campaign-scoped variants added
    (`t201_nodetail`, `t201_primary_field`, `t201_norain_shaft`,
    `t201_primary_norefine`; two new shader hooks, `PA_ARM_PRIMARY_FIELD_DENSITY`
    and `PA_ARM_NO_RAIN_SHAFT`, both `#ifdef`-only, absent from FINAL);
    159 declared, T201 registered 14 (the four, the four reused ceilings,
    the six ladder rungs), FINAL and the monolith always loaded.
34. **Markers**: `run/t201-time-attribution.txt` retired to `.done`;
    `run/t132-autorun.txt` and `run/t132-autorun-exit.txt` remain.
35. `git status`: `build.gradle`, `cloud_atmosphere_volume.fsh`,
    `CoreCostDiagnosticProgram.java`, `StormCampaignRegistry.java`,
    `StormT132AutoDriver.java`, `tasks.md` modified; this report and
    `validation/t201-campaign/` new; unrelated untracked logs preserved.
36. Committed on `experiment/cloud-descriptor-k`; not merged.

Harness notes for the next campaign: the `@d0` repeat is excluded from the
consumer report by the anchor predicate (its program is `LEAN_FINAL`) - its
cells are in the log and were read from `T135_PROFILE` lines; and a campaign
that wants counters at production bits must set them in its counter phase
as T199 did - T201's counter capture inherited the campaign-start reset to 0.
